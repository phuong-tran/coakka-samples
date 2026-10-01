#!/usr/bin/env python3
"""Run a randomized, CPU-partitioned HTTP/1.1 campaign on one Raspberry Pi 5."""

from __future__ import annotations

import argparse
import datetime as dt
import http.client
import json
import math
import os
import random
import re
import signal
import socket
import subprocess
import tempfile
import time
from pathlib import Path
from typing import Any, Callable
from urllib.request import urlopen

BODY = b"0123456789abcdef0123456789abcdef"


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--config", type=Path, default=Path("config/lanes.json"))
    parser.add_argument("--rounds", type=int)
    parser.add_argument("--duration", type=int)
    parser.add_argument(
        "--lane",
        action="append",
        default=[],
        help="run only the named lane; repeat to select more than one lane",
    )
    return parser.parse_args()


def command_output(command: list[str]) -> str:
    return subprocess.run(command, check=False, capture_output=True, text=True).stdout.strip()


def machine_facts() -> dict[str, Any]:
    os_release: dict[str, str] = {}
    for line in Path("/etc/os-release").read_text().splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            os_release[key] = value.strip('"')
    model = Path("/proc/device-tree/model").read_bytes().rstrip(b"\0").decode()
    memory_kib = next(
        int(line.split()[1])
        for line in Path("/proc/meminfo").read_text().splitlines()
        if line.startswith("MemTotal:")
    )
    return {
        "captured_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "hostname": socket.gethostname(),
        "model": model,
        "architecture": command_output(["uname", "-m"]),
        "kernel": command_output(["uname", "-r"]),
        "operating_system": f"{os_release.get('PRETTY_NAME', 'unknown')}",
        "operating_system_codename": os_release.get("VERSION_CODENAME", "unknown"),
        "cpu_model": command_output(
            [
                "bash",
                "-c",
                "lscpu | awk -F: '/Model name/{sub(/^ +/,\"\",$2); print $2; exit}'",
            ]
        ),
        "cpu_count": os.cpu_count(),
        "memory_kib": memory_kib,
        "root_filesystem": command_output(["df", "-h", "/"]),
        "governor_before": governors(),
        "throttled_before": throttled(),
        "tool_versions": Path("evidence/locks/tool-versions.txt")
        .read_text()
        .splitlines(),
        "source_identities": Path("evidence/locks/source-identities.txt")
        .read_text()
        .splitlines(),
        "source_manifest_sha256": Path("evidence/locks/source-manifest.sha256")
        .read_text()
        .split()[0],
        "built_artifacts": Path("evidence/locks/built-artifacts.sha256")
        .read_text()
        .splitlines(),
    }


def governors() -> dict[str, str]:
    paths = Path("/sys/devices/system/cpu").glob(
        "cpu[0-9]*/cpufreq/scaling_governor"
    )
    return {
        path.parts[-3]: path.read_text().strip()
        for path in sorted(paths)
    }


def set_governor(value: str) -> None:
    paths = sorted(
        Path("/sys/devices/system/cpu").glob(
            "cpu[0-9]*/cpufreq/scaling_governor"
        )
    )
    payload = "".join(f"printf '%s' '{value}' > '{path}'\n" for path in paths)
    subprocess.run(["sudo", "-n", "bash", "-c", payload], check=True)


def refresh_sudo_lease() -> None:
    """Keep a pre-authenticated sudo session live outside timed load work.

    A complete 57-lane campaign outlasts the default sudo timestamp. Failing
    this check before changing the governor, or during cooldown, is safer than
    discovering an expired credential only while restoring the machine.
    """
    checked = subprocess.run(
        ["sudo", "-n", "-v"], check=False, capture_output=True, text=True
    )
    if checked.returncode != 0:
        raise RuntimeError(
            "sudo session is not authenticated; run sudo -v in this terminal"
        )


def require_governors(value: str) -> dict[str, str]:
    """Return the live governor inventory after requiring one exact value."""
    current = governors()
    if set(current) != {"cpu0", "cpu1", "cpu2", "cpu3"}:
        raise RuntimeError("CPU governor inventory does not cover all four CPUs")
    unexpected = {cpu: governor for cpu, governor in current.items() if governor != value}
    if unexpected:
        raise RuntimeError(
            f"CPU governors did not enter {value}: {unexpected}"
        )
    return current


def restore_governors(values: dict[str, str]) -> None:
    """Restore every CPU's captured governor instead of assuming one value."""
    paths = sorted(
        Path("/sys/devices/system/cpu").glob(
            "cpu[0-9]*/cpufreq/scaling_governor"
        )
    )
    if {path.parts[-3] for path in paths} != set(values):
        raise RuntimeError("CPU governor inventory changed during the campaign")
    payload = "".join(
        f"printf '%s' '{values[path.parts[-3]]}' > '{path}'\n" for path in paths
    )
    subprocess.run(["sudo", "-n", "bash", "-c", payload], check=True)


def temperature_c() -> float:
    return int(Path("/sys/class/thermal/thermal_zone0/temp").read_text()) / 1000.0


def throttled() -> str:
    result = subprocess.run(
        ["vcgencmd", "get_throttled"],
        check=False,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip() or "unavailable"


def cpu_counters() -> dict[str, tuple[int, int]]:
    """Capture per-CPU total and truly idle ticks from Linux /proc/stat.

    The first eight counters are disjoint. Guest counters beyond them are
    already included in user/nice, so summing every column double-counts
    guest time. I/O wait is not idle headroom for a load generator that logs
    requests, so it remains in the non-idle side of the measurement.
    """
    values: dict[str, tuple[int, int]] = {}
    for line in Path("/proc/stat").read_text().splitlines():
        fields = line.split()
        if not fields or not re.fullmatch(r"cpu[0-9]+", fields[0]):
            continue
        ticks = [int(value) for value in fields[1:]]
        if len(ticks) < 8:
            raise RuntimeError(f"incomplete CPU counters for {fields[0]}")
        values[fields[0]] = (sum(ticks[:8]), ticks[3])
    if not values:
        raise RuntimeError("no per-CPU counters were exposed")
    return values


def busy_percent_between(before: tuple[int, int], after: tuple[int, int]) -> float:
    """Compute non-idle CPU occupancy from monotonic tick snapshots."""
    total_delta = after[0] - before[0]
    idle_delta = after[1] - before[1]
    if total_delta <= 0 or idle_delta < 0 or idle_delta > total_delta:
        raise RuntimeError("CPU accounting was not monotonic")
    return (total_delta - idle_delta) / total_delta * 100.0


def busiest_cpu_busy_percent(
    before: dict[str, tuple[int, int]], after: dict[str, tuple[int, int]]
) -> tuple[float, dict[str, float]]:
    """Reject missing cores and report the busiest load-generator core."""
    if not before or before.keys() != after.keys():
        raise RuntimeError("load-generator CPU inventory changed")
    by_core = {
        cpu: busy_percent_between(before[cpu], after[cpu]) for cpu in sorted(before)
    }
    return max(by_core.values()), by_core


def cpu_busy_percent(sample_seconds: float = 1.0) -> float:
    """Return the busiest CPU's utilization across one short idle sample."""
    before = cpu_counters()
    time.sleep(sample_seconds)
    after = cpu_counters()
    if before.keys() != after.keys():
        raise RuntimeError("CPU inventory changed during idle sampling")
    return max(busy_percent_between(before[cpu], after[cpu]) for cpu in before)


def wait_until_cool(
    maximum_c: float,
    minimum_seconds: int,
    maximum_cpu_busy_percent: float,
    keepalive: Callable[[], None] | None = None,
) -> tuple[float, float]:
    """Idle for the declared interval and require clean thermal/power state."""
    deadline = time.monotonic() + 600
    time.sleep(minimum_seconds)
    while True:
        if keepalive is not None:
            keepalive()
        current_temperature = temperature_c()
        throttle_state = throttled()
        busy_percent = cpu_busy_percent()
        if (
            current_temperature <= maximum_c
            and throttle_state == "throttled=0x0"
            and busy_percent <= maximum_cpu_busy_percent
        ):
            return current_temperature, busy_percent
        if time.monotonic() >= deadline:
            raise TimeoutError(
                "Raspberry Pi did not return to the thermal and power gate: "
                f"temperature={current_temperature:.1f}C state={throttle_state} "
                f"busiest_cpu={busy_percent:.1f}%"
            )
        time.sleep(5)


def free_port() -> int:
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return int(listener.getsockname()[1])


def ready(port: int, process: subprocess.Popen[str]) -> None:
    deadline = time.monotonic() + 30
    url = f"http://127.0.0.1:{port}/fixed"
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"server exited before readiness with {process.returncode}")
        try:
            with urlopen(url, timeout=1) as response:
                if (
                    response.status == 200
                    and response.headers.get("content-type") == "application/octet-stream"
                    and response.read() == BODY
                ):
                    return
        except OSError:
            pass
        time.sleep(0.1)
    raise TimeoutError("server readiness timed out")


def require_persistent_http1(port: int, lane_id: str) -> None:
    """Prove two fixed responses use one socket before timing a lane."""
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=2)
    try:
        first_socket: socket.socket | None = None
        for request_number in range(2):
            connection.request("GET", "/fixed")
            response = connection.getresponse()
            if (
                response.status != 200
                or response.getheader("content-type") != "application/octet-stream"
                or response.read() != BODY
                or response.will_close
                or connection.sock is None
            ):
                raise RuntimeError(f"{lane_id} does not serve persistent HTTP/1.1")
            if request_number == 0:
                first_socket = connection.sock
            elif connection.sock is not first_socket:
                raise RuntimeError(f"{lane_id} reopened its HTTP/1.1 connection")
    finally:
        connection.close()


def duration_ms(value: str) -> float:
    match = re.fullmatch(r"([0-9.]+)(us|ms|s)", value)
    if match is None:
        raise ValueError(f"unsupported duration {value}")
    number = float(match.group(1))
    return number * {"us": 0.001, "ms": 1.0, "s": 1000.0}[match.group(2)]


def parse_h2load(output: str) -> dict[str, Any]:
    rate = re.search(r"finished in (\S+),\s*([0-9.]+) req/s", output)
    request_time = re.search(
        r"time for request:\s+(\S+)\s+(\S+)\s+(\S+)\s+(\S+)", output
    )
    requests = re.search(
        r"requests:\s+(\d+) total,\s+(\d+) started,\s+(\d+) done,\s+"
        r"(\d+) succeeded,\s+(\d+) failed,\s+(\d+) errored,\s+(\d+) timeout",
        output,
    )
    statuses = re.search(
        r"status codes:\s+(\d+) 2xx,\s+(\d+) 3xx,\s+"
        r"(\d+) 4xx,\s+(\d+) 5xx",
        output,
    )
    if rate is None or request_time is None or requests is None or statuses is None:
        raise ValueError(f"could not parse h2load output:\n{output}")
    return {
        "benchmark_duration_ms": duration_ms(rate.group(1)),
        "requests_per_second": float(rate.group(2)),
        "request_time_min_ms": duration_ms(request_time.group(1)),
        "request_time_max_ms": duration_ms(request_time.group(2)),
        "request_time_mean_ms": duration_ms(request_time.group(3)),
        "request_time_sd_ms": duration_ms(request_time.group(4)),
        "requests_total": int(requests.group(1)),
        "requests_started": int(requests.group(2)),
        "requests_done": int(requests.group(3)),
        "requests_succeeded": int(requests.group(4)),
        "requests_failed": int(requests.group(5)),
        "requests_errored": int(requests.group(6)),
        "requests_timed_out": int(requests.group(7)),
        "responses_2xx": int(statuses.group(1)),
        "responses_3xx": int(statuses.group(2)),
        "responses_4xx": int(statuses.group(3)),
        "responses_5xx": int(statuses.group(4)),
    }


def h2load_command(
    workload: dict[str, Any],
    port: int,
    request_log: Path | None = None,
) -> list[str]:
    """Warm the same connections before a fixed-duration measured interval."""
    command = [
        "taskset",
        "-c",
        workload["load_cpus"],
        "h2load",
        "--h1",
        f"--duration={workload['duration_seconds']}s",
        f"--warm-up-time={workload['warmup_seconds']}s",
        "-c",
        str(workload["concurrency"]),
        "-t",
        str(workload["load_threads"]),
        "-m",
        "1",
    ]
    if request_log is not None:
        command.extend(["--log-file", str(request_log)])
    command.append(f"http://127.0.0.1:{port}/fixed")
    return command


def validate_load(
    lane_id: str, workload: dict[str, Any], returncode: int, raw: str
) -> dict[str, Any]:
    """Count successful completions; bound requests in flight at the timed cutoff."""
    if returncode != 0:
        raise RuntimeError(f"h2load failed for {lane_id}:\n{raw}")
    metrics = parse_h2load(raw)
    warmup = workload["warmup_seconds"]
    duration = workload["duration_seconds"]
    threads = workload["load_threads"]
    timing_banner = (
        f"Timing-based test with {warmup}s of warm-up time and "
        f"{duration}s of main duration"
    )
    if (
        raw.count(timing_banner) != threads
        or raw.count("Main benchmark duration is started for thread #") != threads
        or raw.count("Main benchmark duration is over for thread #") != threads
        or not (warmup + duration - 0.5) * 1000
        <= metrics["benchmark_duration_ms"]
        <= (warmup + duration + 3) * 1000
    ):
        raise RuntimeError(f"{lane_id} did not complete the declared timing interval")
    expected_requests = metrics["requests_total"]
    measured_rate = expected_requests / duration
    if (
        expected_requests <= 0
        or expected_requests > workload["max_measurement_requests"]
        or not math.isfinite(metrics["requests_per_second"])
        or abs(metrics["requests_per_second"] - measured_rate)
        > measured_rate * 0.05
        or not expected_requests
        <= metrics["requests_started"]
        <= expected_requests + workload["concurrency"]
        or metrics["requests_done"] != expected_requests
        or metrics["requests_succeeded"] != expected_requests
        or metrics["requests_failed"] != 0
        or metrics["requests_errored"] != 0
        or metrics["requests_timed_out"] != 0
        or metrics["responses_2xx"] != expected_requests
        or metrics["responses_3xx"] != 0
        or metrics["responses_4xx"] != 0
        or metrics["responses_5xx"] != 0
    ):
        raise RuntimeError(
            f"{lane_id} did not complete one successful 2xx response "
            "for each measured request"
        )
    return metrics


def process_group_metrics(process_group: int) -> dict[int, tuple[int, int]]:
    """Return CPU ticks and RSS KiB keyed by exact kernel process-group member."""
    page_kib = os.sysconf("SC_PAGE_SIZE") // 1024
    metrics: dict[int, tuple[int, int]] = {}
    for stat_path in Path("/proc").glob("[0-9]*/stat"):
        try:
            fields = stat_path.read_text().rsplit(")", 1)[1].split()
            if int(fields[2]) != process_group:
                continue
            ticks = int(fields[11]) + int(fields[12])
            resident_pages = int((stat_path.parent / "statm").read_text().split()[1])
            metrics[int(stat_path.parent.name)] = (ticks, resident_pages * page_kib)
        except (OSError, IndexError, ValueError):
            continue
    if not metrics:
        raise RuntimeError(f"server process group {process_group} has no members")
    return metrics


def request_log_metrics(path: Path, expected_requests: int) -> dict[str, float]:
    """Reduce h2load's per-request log, then let the caller remove it."""
    durations_ms: list[float] = []
    with path.open() as source:
        for line in source:
            columns = line.split()
            if len(columns) < 3 or columns[1] == "-1":
                raise RuntimeError("h2load request log contains a failed response")
            durations_ms.append(float(columns[2]) / 1000.0)
    if len(durations_ms) != expected_requests:
        raise RuntimeError(
            f"h2load request log has {len(durations_ms)} rows; "
            f"expected {expected_requests}"
        )
    durations_ms.sort()

    def percentile(fraction: float) -> float:
        index = max(0, math.ceil(fraction * len(durations_ms)) - 1)
        return durations_ms[index]

    return {
        "request_time_p50_ms": percentile(0.50),
        "request_time_p95_ms": percentile(0.95),
        "request_time_p99_ms": percentile(0.99),
    }


def require_request_log_tmpfs(max_requests: int) -> Path:
    """Keep per-request timing I/O off the benchmark boot drive.

    The h2load log is temporary reduction input, not retained evidence. A
    disk-backed log can cap the fastest lanes at storage throughput. Refuse
    measurement unless the Linux shared-memory mount has room for a generous
    bounded estimate; a full mount still makes h2load fail the run closed.
    """
    mount = Path("/dev/shm")
    if not any(
        fields[1:3] == [str(mount), "tmpfs"]
        for line in Path("/proc/mounts").read_text().splitlines()
        if len(fields := line.split()) >= 3
    ):
        raise RuntimeError("request timing log requires /dev/shm on tmpfs")
    filesystem = os.statvfs(mount)
    available = filesystem.f_bavail * filesystem.f_frsize
    required = max_requests * 256 + 32 * 1024 * 1024
    if available < required:
        raise RuntimeError(
            f"request timing log tmpfs has {available} bytes; "
            f"requires at least {required}"
        )
    return mount


def stop_server(process: subprocess.Popen[str]) -> None:
    if process.poll() is not None:
        return
    os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=5)


def write_campaign(
    output: Path,
    workload: dict[str, Any],
    facts: dict[str, Any],
    lanes: list[dict[str, Any]],
    results: list[dict[str, Any]],
    complete: bool,
) -> None:
    """Atomically checkpoint all validated lane results collected so far."""
    campaign = {
        "schema_version": 2,
        "complete": complete,
        "workload": workload,
        "machine": facts,
        "lanes": lanes,
        "measurements": results,
    }
    target = output / "campaign.json"
    temporary = output / "campaign.json.next"
    temporary.write_text(json.dumps(campaign, indent=2) + "\n")
    temporary.replace(target)


def validate_configuration(
    workload: dict[str, Any], lanes: list[dict[str, Any]]
) -> None:
    """Reject an incomplete campaign definition before changing the host."""
    positive_integer_fields = (
        "rounds",
        "duration_seconds",
        "warmup_seconds",
        "max_measurement_requests",
        "concurrency",
        "random_seed",
    )
    for name in positive_integer_fields:
        value = workload.get(name)
        if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
            raise ValueError(f"workload {name} must be a positive integer")
    safety_ceilings = {
        "rounds": 10,
        "duration_seconds": 60,
        "warmup_seconds": 30,
        "max_measurement_requests": 5_000_000,
        "concurrency": 512,
    }
    for name, ceiling in safety_ceilings.items():
        if workload[name] > ceiling:
            raise ValueError(f"workload {name} exceeds the safety ceiling {ceiling}")
    if (
        workload.get("method") != "GET"
        or workload.get("path") != "/fixed"
        or workload.get("status") != 200
        or workload.get("body") != BODY.decode("ascii")
    ):
        raise ValueError("workload response contract differs from the fixed servers")
    if workload.get("coakka_event_loop_threads") != 1:
        raise ValueError("CoAkka event-loop count must match the native one-loop contract")
    if workload.get("io_uring") is not False:
        raise ValueError("the framework campaign must keep io_uring disabled")
    if (
        isinstance(workload.get("load_threads"), bool)
        or workload.get("load_threads") != 3
    ):
        raise ValueError("the Pi campaign requires three load-generator threads")
    if workload["warmup_seconds"] < 5:
        raise ValueError("warmup_seconds must be at least five seconds")
    cooldown_seconds = workload.get("cooldown_minimum_seconds")
    if (
        isinstance(cooldown_seconds, bool)
        or not isinstance(cooldown_seconds, int)
        or cooldown_seconds < 15
        or cooldown_seconds > 300
    ):
        raise ValueError("cooldown_minimum_seconds must be at least 15 seconds")
    cooldown_temperature = workload.get("cooldown_maximum_c")
    if (
        isinstance(cooldown_temperature, bool)
        or not isinstance(cooldown_temperature, (int, float))
        or not 0 < cooldown_temperature <= 50
    ):
        raise ValueError("cooldown_maximum_c is outside the accepted range")
    cooldown_busy = workload.get("cooldown_maximum_cpu_busy_percent")
    if (
        isinstance(cooldown_busy, bool)
        or not isinstance(cooldown_busy, (int, float))
        or not 0 < cooldown_busy <= 5
    ):
        raise ValueError(
            "cooldown_maximum_cpu_busy_percent is outside the accepted range"
        )
    load_busy = workload.get("load_cpu_maximum_busy_percent")
    if (
        isinstance(load_busy, bool)
        or not isinstance(load_busy, (int, float))
        or not 0 < load_busy <= 90
    ):
        raise ValueError("load_cpu_maximum_busy_percent is outside the accepted range")
    identifiers = [lane.get("id") for lane in lanes]
    if any(not isinstance(identifier, str) or not identifier for identifier in identifiers):
        raise ValueError("every benchmark lane needs a non-empty identifier")
    if len(set(identifiers)) != len(identifiers):
        raise ValueError("benchmark lane identifiers must be unique")
    if not lanes:
        raise ValueError("the benchmark selection contains no lanes")
    if any(
        not isinstance(lane.get("ecosystem"), str) or not lane["ecosystem"]
        for lane in lanes
    ):
        raise ValueError("every benchmark lane needs a non-empty ecosystem")
    if any(
        not isinstance(lane.get("implementation"), str)
        or not lane["implementation"]
        for lane in lanes
    ):
        raise ValueError("every benchmark lane needs a non-empty implementation")
    if any(lane.get("role") not in {"coakka", "framework"} for lane in lanes):
        raise ValueError("benchmark lane role must be coakka or framework")
    if any(
        not isinstance(lane.get("command"), list)
        or not lane["command"]
        or any(not isinstance(part, str) or not part for part in lane["command"])
        for lane in lanes
    ):
        raise ValueError("every benchmark lane needs a non-empty command")
    # The Pi also has a system Node below the connector's version floor. Keep
    # every measured server on the checked, locally pinned toolchain instead
    # of inheriting whichever executable a login shell happens to find.
    pinned_commands = {"Node.js": "tools/node/bin/node", "Bun": "tools/bun/bin/bun"}
    for lane in lanes:
        environment = lane.get("environment", {})
        if not isinstance(environment, dict):
            raise ValueError("benchmark lane environment must be an object")
        expected = pinned_commands.get(lane["ecosystem"])
        if expected is not None and lane["command"][0] != expected:
            raise ValueError(f"{lane['ecosystem']} lane must use {expected}")
        if lane["ecosystem"] == "Kotlin/JVM" and environment.get(
            "JAVA_HOME"
        ) != "/usr/lib/jvm/java-21-openjdk-arm64":
            raise ValueError("Kotlin/JVM lane must use the Trixie JDK 21")
    ecosystems = {lane.get("ecosystem") for lane in lanes}
    for ecosystem in ecosystems:
        selected = [lane for lane in lanes if lane.get("ecosystem") == ecosystem]
        if sum(lane.get("role") == "coakka" for lane in selected) != 1:
            raise ValueError(f"ecosystem {ecosystem} must contain one CoAkka lane")


def cpu_set(value: str) -> set[int]:
    """Expand the small taskset list syntax used by the checked-in workload."""
    result: set[int] = set()
    for item in value.split(","):
        bounds = item.split("-", 1)
        try:
            first = int(bounds[0])
            last = int(bounds[-1])
        except ValueError as error:
            raise ValueError(f"invalid CPU set: {value}") from error
        if first < 0 or last < first:
            raise ValueError(f"invalid CPU set: {value}")
        result.update(range(first, last + 1))
    if not result:
        raise ValueError("CPU set is empty")
    return result


def validate_machine(facts: dict[str, Any], workload: dict[str, Any]) -> None:
    """Require the physical four-core Raspberry Pi 5 measurement authority."""
    if "Raspberry Pi 5" not in facts["model"]:
        raise RuntimeError("benchmark authority is not a physical Raspberry Pi 5")
    if facts["architecture"] not in {"aarch64", "arm64"}:
        raise RuntimeError("benchmark authority is not Linux AArch64")
    if facts["operating_system_codename"] != "trixie":
        raise RuntimeError(
            "benchmark authority requires a clean Raspberry Pi OS Trixie install"
        )
    if facts["cpu_count"] != 4:
        raise RuntimeError("benchmark workload requires exactly four logical CPUs")
    server_cpus = cpu_set(workload["server_cpus"])
    load_cpus = cpu_set(workload["load_cpus"])
    if (
        server_cpus != {0}
        or load_cpus != {1, 2, 3}
    ):
        raise ValueError("server and load CPU sets must partition CPUs 0 through 3")
    if set(facts["governor_before"]) != {"cpu0", "cpu1", "cpu2", "cpu3"}:
        raise RuntimeError("CPU governor inventory does not cover all four CPUs")
    if facts["throttled_before"] != "throttled=0x0":
        raise RuntimeError("firmware reports prior power or thermal throttling")


def measure(
    root: Path,
    lane: dict[str, Any],
    workload: dict[str, Any],
    round_number: int,
    raw_directory: Path,
) -> dict[str, Any]:
    port = free_port()
    command = [part.format(port=port) for part in lane["command"]]
    environment = os.environ.copy()
    environment.update(lane.get("environment", {}))
    host_library = root / "build/host-prefix/lib/libcoakka_http_host.so.1.0.0"
    javascript_addon = root / "build/javascript-connector/coakka_http_javascript.node"
    jvm_bridge = root / "build/jvm-connector/native/libcoakka_http_jvm.so"
    environment["LD_LIBRARY_PATH"] = ":".join(
        value
        for value in [
            str(root / "build/host-prefix/lib"),
            environment.get("LD_LIBRARY_PATH", ""),
        ]
        if value
    )
    environment["COAKKA_HTTP_HOST_PATH"] = str(host_library)
    environment["COAKKA_HTTP_JAVASCRIPT_ADDON"] = str(javascript_addon)
    environment["PYTHONPATH"] = ":".join(
        value
        for value in [
            str(root / "sources/connector/connectors/python"),
            environment.get("PYTHONPATH", ""),
        ]
        if value
    )
    java_options = environment.get("JAVA_TOOL_OPTIONS", "")
    environment["JAVA_TOOL_OPTIONS"] = (
        f"{java_options} -Dcoakka.http.host.path={host_library} "
        f"-Dcoakka.http.bridge.path={jvm_bridge}"
    ).strip()
    server_log = raw_directory / f"round-{round_number:02d}-{lane['id']}-server.log"
    with (
        server_log.open("w") as log,
        tempfile.TemporaryDirectory(
            prefix="coakka-http-benchmark-requests-",
            dir=require_request_log_tmpfs(workload["max_measurement_requests"]),
        ) as request_log_dir,
    ):
        request_log_root = Path(request_log_dir)
        process = subprocess.Popen(
            ["taskset", "-c", workload["server_cpus"], *command],
            cwd=root,
            env=environment,
            stdout=log,
            stderr=subprocess.STDOUT,
            text=True,
            start_new_session=True,
        )
        try:
            ready(port, process)
            require_persistent_http1(port, lane["id"])
            before_c = temperature_c()
            request_log = request_log_root / (
                f"round-{round_number:02d}-{lane['id']}-requests.tsv"
            )
            request_log.unlink(missing_ok=True)
            process_metrics_before = process_group_metrics(process.pid)
            load_cpu_names = {
                f"cpu{index}" for index in cpu_set(workload["load_cpus"])
            }
            load_snapshot_before = cpu_counters()
            load_counters_before = {
                cpu: load_snapshot_before[cpu] for cpu in load_cpu_names
            }
            measurement_started = time.monotonic()
            try:
                load = subprocess.run(
                    h2load_command(workload, port, request_log),
                    cwd=root,
                    check=False,
                    capture_output=True,
                    text=True,
                    timeout=(
                        workload["warmup_seconds"]
                        + workload["duration_seconds"]
                        + 30
                    ),
                )
                elapsed_seconds = time.monotonic() - measurement_started
                load_snapshot_after = cpu_counters()
                load_counters_after = {
                    cpu: load_snapshot_after[cpu] for cpu in load_cpu_names
                }
                process_metrics_after = process_group_metrics(process.pid)
            except BaseException:
                request_log.unlink(missing_ok=True)
                raise
            after_c = temperature_c()
            if process_metrics_before.keys() != process_metrics_after.keys():
                raise RuntimeError(
                    f"server process membership changed for {lane['id']}"
                )
            cpu_tick_delta = 0
            for process_id, (ticks_after, _rss_after) in process_metrics_after.items():
                ticks_before = process_metrics_before[process_id][0]
                if ticks_after < ticks_before:
                    raise RuntimeError(
                        f"server CPU accounting moved backwards for {lane['id']}"
                    )
                cpu_tick_delta += ticks_after - ticks_before
            server_rss_kib = sum(value[1] for value in process_metrics_after.values())
            if elapsed_seconds <= 0 or server_rss_kib <= 0:
                raise RuntimeError(f"server CPU accounting failed for {lane['id']}")
            # Averaging three cores could conceal one saturated h2load worker.
            (
                load_cpu_busy_percent,
                load_cpu_busy_percent_by_core,
            ) = busiest_cpu_busy_percent(load_counters_before, load_counters_after)
            raw = load.stdout + load.stderr
            (raw_directory / f"round-{round_number:02d}-{lane['id']}-h2load.txt").write_text(raw)
            try:
                metrics = validate_load(lane["id"], workload, load.returncode, raw)
                metrics.update(
                    request_log_metrics(request_log, metrics["requests_total"])
                )
            finally:
                request_log.unlink(missing_ok=True)
            ready(port, process)
            metrics.update(
                {
                    "lane_id": lane["id"],
                    "ecosystem": lane["ecosystem"],
                    "implementation": lane["implementation"],
                    "role": lane["role"],
                    "round": round_number,
                    "measurement_requests": metrics["requests_total"],
                    "load_wall_seconds": elapsed_seconds,
                    "server_process_count": len(process_metrics_after),
                    "server_rss_kib": server_rss_kib,
                    "load_cpu_busy_percent": load_cpu_busy_percent,
                    "load_cpu_busy_percent_by_core": (
                        load_cpu_busy_percent_by_core
                    ),
                    "server_cpu_percent": (
                        cpu_tick_delta
                        / os.sysconf("SC_CLK_TCK")
                        / elapsed_seconds
                        * 100.0
                    ),
                    "temperature_before_c": before_c,
                    "temperature_after_c": after_c,
                    "throttled": throttled(),
                }
            )
            if metrics["throttled"] != "throttled=0x0":
                raise RuntimeError(
                    f"power or thermal throttling invalidated {lane['id']}: "
                    f"{metrics['throttled']}"
                )
            if load_cpu_busy_percent > workload["load_cpu_maximum_busy_percent"]:
                raise RuntimeError(
                    f"load generator saturated for {lane['id']}: "
                    f"{load_cpu_busy_percent:.1f}% > "
                    f"{workload['load_cpu_maximum_busy_percent']:.1f}%"
                )
            return metrics
        finally:
            stop_server(process)


def main() -> int:
    args = arguments()
    root = Path(__file__).resolve().parent.parent
    os.chdir(root)
    config = json.loads(args.config.read_text())
    if config.get("schema_version") != 2:
        raise ValueError("benchmark configuration schema is not supported")
    workload = dict(config["workload"])
    lanes = list(config["lanes"])
    if args.lane:
        known_lane_ids = {lane["id"] for lane in lanes}
        unknown_lane_ids = sorted(set(args.lane) - known_lane_ids)
        if unknown_lane_ids:
            raise ValueError(f"unknown lanes: {', '.join(unknown_lane_ids)}")
        selected_lane_ids = set(args.lane)
        lanes = [lane for lane in lanes if lane["id"] in selected_lane_ids]
    if args.rounds is not None:
        workload["rounds"] = args.rounds
    if args.duration is not None:
        workload["duration_seconds"] = args.duration
    validate_configuration(workload, lanes)
    output = args.output.resolve()
    if output.exists():
        if not output.is_dir() or any(output.iterdir()):
            raise FileExistsError(
                f"benchmark output must be a new or empty directory: {output}"
            )
    facts = machine_facts()
    validate_machine(facts, workload)
    refresh_sudo_lease()
    raw_directory = output / "raw"
    raw_directory.mkdir(parents=True, exist_ok=True)
    original_governors = dict(facts["governor_before"])
    if not original_governors:
        raise RuntimeError("no CPU frequency governors were exposed")
    results: list[dict[str, Any]] = []
    complete = False
    write_campaign(output, workload, facts, lanes, results, complete)
    try:
        set_governor("performance")
        facts["governor_during"] = require_governors("performance")
        print(
            "initial cooldown "
            f"minimum_seconds={workload['cooldown_minimum_seconds']} "
            f"maximum_c={workload['cooldown_maximum_c']:.1f} "
            f"maximum_cpu_busy_percent="
            f"{workload['cooldown_maximum_cpu_busy_percent']:.1f}",
            flush=True,
        )
        (
            facts["temperature_after_initial_cooldown_c"],
            facts["cpu_busy_after_initial_cooldown_percent"],
        ) = wait_until_cool(
            workload["cooldown_maximum_c"],
            workload["cooldown_minimum_seconds"],
            workload["cooldown_maximum_cpu_busy_percent"],
            refresh_sudo_lease,
        )
        for round_number in range(1, workload["rounds"] + 1):
            round_lanes = list(lanes)
            random.Random(workload["random_seed"] + round_number).shuffle(round_lanes)
            for lane in round_lanes:
                print(f"measure round={round_number} lane={lane['id']}", flush=True)
                result = measure(root, lane, workload, round_number, raw_directory)
                print(
                    f"cooldown lane={lane['id']} minimum_seconds="
                    f"{workload['cooldown_minimum_seconds']} "
                    f"maximum_c={workload['cooldown_maximum_c']:.1f} "
                    f"maximum_cpu_busy_percent="
                    f"{workload['cooldown_maximum_cpu_busy_percent']:.1f}",
                    flush=True,
                )
                (
                    result["temperature_after_cooldown_c"],
                    result["cpu_busy_after_cooldown_percent"],
                ) = wait_until_cool(
                    workload["cooldown_maximum_c"],
                    workload["cooldown_minimum_seconds"],
                    workload["cooldown_maximum_cpu_busy_percent"],
                    refresh_sudo_lease,
                )
                results.append(result)
                write_campaign(output, workload, facts, lanes, results, False)
                print(
                    f"result lane={lane['id']} rps={result['requests_per_second']:.2f} "
                    f"mean_ms={result['request_time_mean_ms']:.3f} "
                    f"temp_c={result['temperature_after_c']:.1f} "
                    f"cooled_c={result['temperature_after_cooldown_c']:.1f} "
                    f"cooled_cpu_busy_percent="
                    f"{result['cpu_busy_after_cooldown_percent']:.1f}",
                    flush=True,
                )
        expected_measurements = len(lanes) * workload["rounds"]
        if len(results) != expected_measurements:
            raise RuntimeError(
                f"campaign retained {len(results)} measurements; "
                f"expected {expected_measurements}"
            )
        complete = True
    except BaseException as error:
        complete = False
        facts["campaign_error"] = f"{type(error).__name__}: {error}"
        raise
    finally:
        restore_error: Exception | None = None
        try:
            restore_governors(original_governors)
        except Exception as error:  # Preserve evidence before failing closed.
            restore_error = error
            complete = False
            facts["governor_restore_error"] = str(error)
        facts["governor_after"] = governors()
        facts["throttled_after"] = throttled()
        if facts["governor_after"] != original_governors:
            complete = False
            facts["governor_restore_error"] = (
                "CPU governor values differ after restoration"
            )
            if restore_error is None:
                restore_error = RuntimeError(facts["governor_restore_error"])
        if facts["throttled_after"] != "throttled=0x0":
            complete = False
            facts["final_throttle_error"] = facts["throttled_after"]
            if restore_error is None:
                restore_error = RuntimeError(
                    "final firmware throttle state is not clean"
                )
        write_campaign(output, workload, facts, lanes, results, complete)
        if restore_error is not None:
            raise RuntimeError("failed to restore CPU governors") from restore_error
    print(f"campaign={output / 'campaign.json'}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
