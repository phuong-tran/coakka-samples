#!/usr/bin/env python3
"""Run a randomized, CPU-partitioned HTTP/1.1 campaign on one Raspberry Pi 5."""

from __future__ import annotations

import argparse
import datetime as dt
import http.client
import hashlib
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
        "--study-mode", choices=("strict", "client-limited-loopback"), default="strict",
        help="explicit observational loopback study; retains saturation, never certifies server capacity",
    )
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
        "firmware": command_output(["vcgencmd", "version"]),
        "cpu_frequency_khz": frequency_snapshot(),
        "memory_before": memory_snapshot(),
        "swap_devices": Path("/proc/swaps").read_text().splitlines(),
        "cooling": cooling_snapshot(),
        "ambient_temperature": {"value": None, "reason": "no ambient sensor measurement"},
        "network_topology": "HTTP/1.1 plaintext over IPv4 loopback; disjoint server/load CPU sets",
        "observer_affinity": sorted(os.sched_getaffinity(0)),
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


def frequency_snapshot() -> dict[str, dict[str, str]]:
    """Read observed frequency and limits; missing sysfs facts remain absent."""
    return {path.parts[-2]: {name: (path / name).read_text().strip()
            for name in ("scaling_cur_freq", "scaling_min_freq", "scaling_max_freq")
            if (path / name).is_file()}
            for path in sorted(Path("/sys/devices/system/cpu").glob("cpu[0-9]*/cpufreq"))}


def memory_snapshot() -> dict[str, int]:
    """Only non-sensitive aggregate RAM/swap counters, in kernel-reported KiB."""
    wanted = {"MemTotal", "MemAvailable", "SwapTotal", "SwapFree", "SwapCached"}
    return {parts[0].rstrip(":"): int(parts[1])
            for line in Path("/proc/meminfo").read_text().splitlines()
            if (parts := line.split())[0].rstrip(":") in wanted}


def cooling_snapshot() -> dict[str, str]:
    """Capture fan/cooling controls without changing the user's fan policy."""
    paths = [path for name in ("fan1_input", "pwm1", "pwm1_enable")
             for path in Path("/sys/class/hwmon").glob("hwmon*/" + name)]
    paths += [path for name in ("type", "cur_state", "max_state")
              for path in Path("/sys/class/thermal").glob("cooling_device*/" + name)]
    return {str(path): path.read_text().strip() for path in sorted(paths)}


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
    """Require three consecutive clean samples after the minimum idle interval.

    A transient cool/idle instant is insufficient. Any failed observation resets
    the streak. Return the worst temperature/utilization in the admitted streak
    so the evidence never presents a cherry-picked final observation.
    """
    deadline = time.monotonic() + 600
    time.sleep(minimum_seconds)
    clean_samples: list[tuple[float, float]] = []
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
            clean_samples.append((current_temperature, busy_percent))
            if len(clean_samples) == 3:
                return max(item[0] for item in clean_samples), max(item[1] for item in clean_samples)
        else:
            clean_samples.clear()
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
    """Count successful completions; bound carry across both timing boundaries."""
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
    if expected_requests > workload["max_measurement_requests"]:
        raise RuntimeError(f"{lane_id} exceeded the declared measurement request ceiling: "
                           f"{expected_requests} > {workload['max_measurement_requests']}")
    measured_rate = expected_requests / duration
    # h2load counts starts and completions by their current phase separately.
    # A request submitted during warmup can complete in MAIN_DURATION; one
    # started in MAIN_DURATION can remain unfinished at its end. With HTTP/1
    # and -m1 each boundary has at most one request per connection. Therefore
    # started-done is end carry minus warmup carry, not an in-flight census.
    warmup_carry_limit = workload["concurrency"] if warmup > 0 else 0
    if (
        expected_requests <= 0
        or not math.isfinite(metrics["requests_per_second"])
        or abs(metrics["requests_per_second"] - measured_rate)
        > measured_rate * 0.05
        or not max(0, expected_requests - warmup_carry_limit)
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


def process_group_metrics(process_group: int) -> dict[int, tuple[int, int, int, int]]:
    """Return total ticks, RSS KiB, user ticks and system ticks per process."""
    page_kib = os.sysconf("SC_PAGE_SIZE") // 1024
    metrics: dict[int, tuple[int, int, int, int]] = {}
    for stat_path in Path("/proc").glob("[0-9]*/stat"):
        try:
            fields = stat_path.read_text().rsplit(")", 1)[1].split()
            if int(fields[2]) != process_group:
                continue
            ticks = int(fields[11]) + int(fields[12])
            resident_pages = int((stat_path.parent / "statm").read_text().split()[1])
            metrics[int(stat_path.parent.name)] = (ticks, resident_pages * page_kib, int(fields[11]), int(fields[12]))
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
            if len(columns) < 3 or columns[1] != "200":
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


def rss_summary(samples: list[dict[str, Any]]) -> dict[str, Any]:
    """Time-weighted sampled RSS; never claim an unsampled instantaneous peak."""
    if len(samples) < 2:
        raise ValueError("RSS requires at least two observations")
    area = 0.0
    for before, after in zip(samples, samples[1:]):
        delta = after["elapsed_seconds"] - before["elapsed_seconds"]
        if delta <= 0 or min(before["server_rss_kib"], after["server_rss_kib"]) <= 0:
            raise ValueError("RSS observations are not positive and monotonic")
        area += delta * (before["server_rss_kib"] + after["server_rss_kib"]) / 2
    span = samples[-1]["elapsed_seconds"] - samples[0]["elapsed_seconds"]
    return {"server_rss_mean_kib": area / span,
            "server_rss_peak_kib": max(sample["server_rss_kib"] for sample in samples),
            "resource_sample_count": len(samples), "resource_sample_interval_seconds": .25,
            "resource_window": "load-process lifetime: warm-up plus measurement",
            "resource_samples": samples}


def monitored_load(command: list[str], root: Path, group: int, timeout: float,
                   raw_log: Path, *, generator_cpus: set[int] | None = None
                   ) -> tuple[subprocess.CompletedProcess[str], dict[str, Any]]:
    """Observe every server process at 250 ms cadence while h2load owns timing.

    CPU/RSS scope is explicitly the full warm-up plus measurement window. The
    collector does not pretend its wall clock is h2load's per-worker boundary.
    Membership changes fail closed, rather than omitting exited workers' usage.
    """
    started = time.monotonic()
    observer_started = time.process_time()
    samples: list[dict[str, Any]] = []
    members: set[int] | None = None
    # The caller isolates the observer on the generator CPUs. Explicit CPU
    # selection avoids treating a low whole-run mean as proof of headroom when
    # JVM startup masks a saturated measurement phase. Private diagnostics may
    # omit this argument; campaign admission must supply it and retain samples.
    load_names = {f"cpu{cpu}" for cpu in generator_cpus or set()}
    load_samples: list[dict[str, Any]] = []
    cpu_before = cpu_counters() if load_names else {}
    cpu_sample_started = started
    load = subprocess.Popen(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            text=True, start_new_session=True)
    load_rss: list[int] = []
    try:
        while True:
            now = time.monotonic()
            if load_names and now - cpu_sample_started >= 1:
                cpu_after = cpu_counters()
                _, by_cpu = busiest_cpu_busy_percent(
                    {cpu: cpu_before[cpu] for cpu in load_names},
                    {cpu: cpu_after[cpu] for cpu in load_names})
                load_samples.append({"start_seconds": cpu_sample_started - started,
                                     "end_seconds": now - started,
                                     "busy_by_cpu": by_cpu})
                cpu_before = cpu_after
                cpu_sample_started = now
            metrics = process_group_metrics(group)
            current_members = set(metrics)
            if members is not None and members != current_members:
                raise RuntimeError("server process membership changed during resource sampling")
            members = current_members
            samples.append({"elapsed_seconds": time.monotonic() - started,
                            "server_rss_kib": sum(item[1] for item in metrics.values())})
            try:
                load_rss.append(sum(item[1] for item in process_group_metrics(load.pid).values()))
            except RuntimeError:
                if load.poll() is None:
                    raise
            remaining = timeout - (time.monotonic() - started)
            if remaining <= 0:
                raise TimeoutError("load generator exceeded the declared deadline")
            try:
                stdout, stderr = load.communicate(timeout=min(.25, remaining))
                final_metrics = process_group_metrics(group)
                if set(final_metrics) != members:
                    raise RuntimeError("server process membership changed at load completion")
                samples.append({"elapsed_seconds": time.monotonic() - started,
                                "server_rss_kib": sum(item[1] for item in final_metrics.values())})
                raw_log.write_text(stdout + stderr)
                resources = rss_summary(samples)
                if load_names and not load_samples:
                    raise RuntimeError("generator CPU interval evidence is missing")
                resources["load_cpu_interval_samples"] = load_samples
                resources["load_cpu_interval_peak_percent"] = max(
                    (value for sample in load_samples for value in sample["busy_by_cpu"].values()),
                    default=None)
                resources["load_rss_sampled_peak_kib"] = max(load_rss) if load_rss else None
                resources["observer_cpu_seconds"] = time.process_time() - observer_started
                resources["context_switches"] = {"value": None,
                    "reason": "per-thread lifecycle accounting reserved for separate profiling; not inferred from process leader"}
                return subprocess.CompletedProcess(command, load.returncode, stdout, stderr), resources
            except subprocess.TimeoutExpired:
                continue
    except BaseException:
        if load.poll() is None:
            load.kill()
        stdout, stderr = load.communicate(timeout=5)
        raw_log.write_text(stdout + stderr)
        raise


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


def stop_server(process: subprocess.Popen[str]) -> int:
    """Clean up a server and reject a forced kill as benchmark evidence."""
    if process.poll() is not None:
        raise RuntimeError("benchmark server exited before requested shutdown")
    os.killpg(process.pid, signal.SIGTERM)
    try:
        return process.wait(timeout=15)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=5)
        raise RuntimeError("benchmark server required forced shutdown")


def package_environment(root: Path, lane: dict[str, Any], workload: dict[str, Any]) -> dict[str, str]:
    """Isolate each public package's documented loader; forbid inherited overrides.

    Package receipts are independently revalidated before changing the machine.
    A framework process gets no CoAkka loader configuration or Python imports.
    """
    environment = {key: value for key, value in os.environ.items()
                   if not key.startswith(("COAKKA_", "LD_", "DYLD_", "JAVA_", "JDK_", "PYTHON"))}
    environment.update(lane.get("environment", {}))
    environment["PYTHONDONTWRITEBYTECODE"] = "1"
    if lane["role"] != "coakka":
        return environment
    packages = json.loads((root / "evidence/locks/package-paths.json").read_text())
    environment["COAKKA_BENCH_CPU_POLICY"] = (
        "single" if len(cpu_set(workload["server_cpus"])) == 1 else "auto"
    )
    if lane["ecosystem"] == "Python":
        environment["PYTHONPATH"] = str(Path(packages["python"]) / "python")
    elif lane["ecosystem"] == "Kotlin/JVM":
        native = Path(packages["jvm"]) / "native"
        options = environment.get("JAVA_TOOL_OPTIONS", "")
        environment["JAVA_TOOL_OPTIONS"] = (
            f"{options} -Dcoakka.http.host.path={native}/libcoakka_http_host.so "
            f"-Dcoakka.http.bridge.path={native}/libcoakka_http_jvm.so"
        ).strip()
    return environment


def validate_shutdown(lane: dict[str, Any], exit_code: int, log: str) -> None:
    """Require each host's actual graceful completion convention, not exit alone."""
    if lane["ecosystem"] == "Python" and lane["role"] == "framework":
        # Pinned Uvicorn 0.52.4 restores and re-raises SIGTERM only after its
        # awaited shutdown. Its multiprocess supervisor may instead return 0.
        # A SIGTERM exit without complete worker lifespan evidence is a failure.
        started = re.findall(r"Started server process \[(\d+)\]", log)
        finished = re.findall(r"Finished server process \[(\d+)\]", log)
        workers = int(lane["command"][lane["command"].index("--workers") + 1])
        if (exit_code not in (0, -signal.SIGTERM)
                or len(started) != workers or len(set(started)) != workers
                or sorted(started) != sorted(finished)
                or log.count("Application shutdown complete.") != workers):
            raise RuntimeError(f"{lane['id']} lacks complete Uvicorn worker shutdown")
        return
    expected_exit = 143 if lane["ecosystem"] == "Kotlin/JVM" else 0
    if exit_code != expected_exit:
        raise RuntimeError(f"{lane['id']} shutdown exit {exit_code}, expected {expected_exit}")
    if "benchmark-shutdown=pass" not in log.splitlines():
        raise RuntimeError(f"{lane['id']} lacks graceful shutdown evidence")


def verify_package_inputs(root: Path) -> dict[str, Any]:
    """Recheck archives, extracted trees and compiled consumers before measuring."""
    receipt = json.loads((root / "evidence/locks/package-paths.json").read_text())
    if set(receipt) != {"native", "go", "jvm", "python", "javascript"}:
        raise ValueError("benchmark package receipt is incomplete")
    for lane, expected in receipt.items():
        result = subprocess.run(
            ["python3", str(root / "package-tools/resolve-package.py"),
             "--publish", str(root / "warehouse"), "--work", str(root / "packages"),
             lane, "linux-aarch64"], check=True, capture_output=True, text=True, timeout=60)
        if result.stdout.strip() != expected:
            raise ValueError("package receipt differs from independently pinned archive: " + lane)
    for manifest in ("source-files.sha256", "built-artifacts.sha256", "tool-artifacts.sha256"):
        subprocess.run(["sha256sum", "--check", "--strict", "evidence/locks/" + manifest],
                       cwd=root, check=True, capture_output=True, text=True, timeout=120)
    return json.loads((root / "package-tools/package-pins.json").read_text())


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
        # A constrained study must not enter the strict v2 report pipeline,
        # even when every individual measurement happens to have headroom.
        "schema_version": 3 if workload.get("study_mode") == "client-limited-loopback" else 2,
        "evidence_kind": workload.get("study_mode", "strict"),
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
    if "coakka_event_loop_threads" in workload:
        raise ValueError("event-loop counts are Core observations, not benchmark configuration")
    if workload.get("io_uring") is not False:
        raise ValueError("the framework campaign must keep io_uring disabled")
    load_threads = workload.get("load_threads")
    if isinstance(load_threads, bool) or load_threads not in {2, 3}:
        raise ValueError("the Pi campaign requires one load-generator thread per load CPU")
    server_cpus = cpu_set(workload["server_cpus"])
    load_cpus = cpu_set(workload["load_cpus"])
    if (server_cpus, load_cpus, load_threads) not in (
        ({0}, {1, 2, 3}, 3),
        ({0, 1}, {2, 3}, 2),
    ):
        raise ValueError("unsupported server/load CPU partition or load-generator thread count")
    if workload["warmup_seconds"] < 5:
        raise ValueError("warmup_seconds must be at least five seconds")
    cooldown_seconds = workload.get("cooldown_minimum_seconds")
    if workload.get("cooldown_consecutive_samples") != 3:
        raise ValueError("cooldown requires exactly three consecutive clean samples")
    if (
        isinstance(cooldown_seconds, bool)
        or not isinstance(cooldown_seconds, int)
        or cooldown_seconds < 30
        or cooldown_seconds > 300
    ):
        raise ValueError("cooldown_minimum_seconds must be between 30 and 300 seconds")
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
    if (server_cpus, load_cpus, workload["load_threads"]) not in (
        ({0}, {1, 2, 3}, 3),
        ({0, 1}, {2, 3}, 2),
    ):
        raise ValueError("server and load CPU sets must use a qualified Pi partition")
    if set(facts["governor_before"]) != {"cpu0", "cpu1", "cpu2", "cpu3"}:
        raise RuntimeError("CPU governor inventory does not cover all four CPUs")
    if facts["throttled_before"] != "throttled=0x0":
        raise RuntimeError("firmware reports prior power or thermal throttling")


def parse_core_observation(log: str, workload: dict[str, Any]) -> dict[str, Any]:
    """Check measured CPU ownership without guessing a connector's loop tuning.

    Preserve the complete public record, including language-specific enum
    representations. The driver validates shared observation fields only; it
    neither maps another ABI's enum values nor manufactures missing limits.
    """
    records = [line.removeprefix("coakka-runtime-info=") for line in log.splitlines()
               if line.startswith("coakka-runtime-info=")]
    if len(records) != 1:
        raise ValueError("exactly one Core startup observation is required")
    record = json.loads(records[0])
    if record.get("ioUringEffective") is not False:
        raise ValueError("Core backend observation does not confirm io_uring disabled")
    cpu = record.get("cpu", {})
    expected_ids = sorted(cpu_set(workload["server_cpus"]))
    if (cpu.get("selectedCpuIds") != expected_ids
            or type(cpu.get("selectedCpuCount")) is not int
            or cpu["selectedCpuCount"] != len(expected_ids)):
        raise ValueError("Core CPU observation differs from the measured partition")
    execution = record.get("execution", {})
    configured = execution.get("configuredEventLoops")
    active = execution.get("activeEventLoops")
    if (execution.get("observed") is not True
            or type(configured) is not int or type(active) is not int
            or not 0 < active <= configured):
        raise ValueError("Core execution observation is missing or incoherent")
    for field in ("requestNotificationBatchSize", "terminalNotificationBatchSize"):
        if type(record.get(field)) is not int or record[field] <= 0:
            raise ValueError("Core batch observation is missing or invalid")
    return record


def classify_headroom(metrics: dict[str, Any], workload: dict[str, Any]) -> None:
    """Retain observed limits without confusing execution success with capacity.

    Only the explicit loopback study may retain a headroom refusal. All other
    correctness, thermal and lifecycle failures remain fatal in the caller.
    The threshold is unchanged; absence of saturation is not proof of a server
    performance ceiling, especially on a machine shared with its load client.
    """
    observed = max(metrics["load_cpu_busy_percent"], metrics["load_cpu_interval_peak_percent"])
    if not math.isfinite(observed) or not 0 <= observed <= 100:
        raise ValueError("invalid load-generator CPU observation")
    limit = workload["load_cpu_maximum_busy_percent"]
    limited = observed > limit
    metrics["generator_headroom"] = {
        "observed_maximum_busy_percent": observed,
        "maximum_busy_percent": limit,
        "passed": not limited,
    }
    metrics["capacity_classification"] = (
        "client-limited" if limited else "no-generator-saturation-observed"
    )
    if limited and workload.get("study_mode", "strict") != "client-limited-loopback":
        raise RuntimeError(f"load generator saturated: {observed:.1f}% > {limit:.1f}%")


def verify_loaded_package(root: Path, lane: dict[str, Any], group: int) -> list[dict[str, Any]]:
    """Verify actual mapped runtime bytes, not merely the intended package pin.

    A reused consumer cache can retain an old RPATH despite a new package
    receipt. Node installs an exact package copy, so identity is checked by
    content against the canonical extracted package rather than guessed from
    directory names. This cold probe runs before timed load. It is benchmark
    provenance, not connector platform detection or runtime policy.
    """
    owners = {"C": "native", "Go": "go", "Python": "python",
              "Kotlin/JVM": "jvm", "Node.js": "javascript", "Bun": "javascript"}
    paths = json.loads((root / "evidence/locks/package-paths.json").read_text())
    expected = set()
    if lane["role"] == "coakka":
        package = Path(paths[owners[lane["ecosystem"]]])
        for path in package.rglob("*"):
            if path.is_file() and "coakka_http" in path.name and (".so" in path.name or path.suffix == ".node"):
                expected.add(hashlib.sha256(path.read_bytes()).hexdigest())
        if not expected:
            raise ValueError("no canonical native payload for loaded-package verification")
    observed = []
    for pid in process_group_metrics(group):
        mapped = set()
        for line in Path(f"/proc/{pid}/maps").read_text().splitlines():
            fields = line.split(maxsplit=5)
            if len(fields) != 6 or not fields[5].startswith("/"):
                continue
            path = Path(fields[5])
            if "coakka_http" in path.name and (".so" in path.name or path.suffix == ".node"):
                mapped.add(path)
        for path in sorted(mapped):
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            if digest not in expected:
                raise ValueError(f"mapped package identity mismatch: {lane['id']}: {path}")
            observed.append({"pid": pid, "path": str(path), "sha256": digest})
    if lane["role"] == "coakka":
        required = "libcoakka_http_runtime" if lane["ecosystem"] == "C" else "libcoakka_http_host"
        if not any(required in Path(row["path"]).name for row in observed):
            raise ValueError("expected runtime library was not observed in process maps")
    return observed


def measure(
    root: Path,
    lane: dict[str, Any],
    workload: dict[str, Any],
    round_number: int,
    raw_directory: Path,
) -> dict[str, Any]:
    port = free_port()
    command = [part.format(port=port) for part in lane["command"]]
    environment = package_environment(root, lane, workload)
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
            loaded_package = verify_loaded_package(root, lane, process.pid)
            core_info = None
            if lane["role"] == "coakka":
                observation_deadline = time.monotonic() + 5
                while "coakka-runtime-info=" not in server_log.read_text():
                    if process.poll() is not None or time.monotonic() >= observation_deadline:
                        raise RuntimeError("Core startup observation was not emitted")
                    time.sleep(.02)
                core_info = parse_core_observation(server_log.read_text(), workload)
            before_c = temperature_c()
            memory_before = memory_snapshot()
            frequencies_before = frequency_snapshot()
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
                load, resources = monitored_load(
                    h2load_command(workload, port, request_log),
                    root, process.pid,
                    (
                        workload["warmup_seconds"]
                        + workload["duration_seconds"]
                        + 30
                    ), raw_directory / f"round-{round_number:02d}-{lane['id']}-h2load.txt",
                    generator_cpus=cpu_set(workload["load_cpus"]),
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
            for process_id, (ticks_after, _rss_after, _user, _system) in process_metrics_after.items():
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
                    "loaded_package": loaded_package,
                    "ecosystem": lane["ecosystem"],
                    "implementation": lane["implementation"],
                    "role": lane["role"],
                    "round": round_number,
                    "core_runtime_info": core_info,
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
                    "server_user_cpu_seconds": sum(
                        item[2] - process_metrics_before[pid][2]
                        for pid, item in process_metrics_after.items()) / os.sysconf("SC_CLK_TCK"),
                    "server_system_cpu_seconds": sum(
                        item[3] - process_metrics_before[pid][3]
                        for pid, item in process_metrics_after.items()) / os.sysconf("SC_CLK_TCK"),
                    "temperature_before_c": before_c,
                    "temperature_after_c": after_c,
                    "memory_before": memory_before,
                    "memory_after": memory_snapshot(),
                    "cpu_frequency_before_khz": frequencies_before,
                    "cpu_frequency_after_khz": frequency_snapshot(),
                    "throttled": throttled(),
                }
            )
            metrics.update(resources)
            if metrics["throttled"] != "throttled=0x0":
                raise RuntimeError(
                    f"power or thermal throttling invalidated {lane['id']}: "
                    f"{metrics['throttled']}"
                )
            classify_headroom(metrics, workload)
            return metrics
        finally:
            exit_code = stop_server(process)
            validate_shutdown(lane, exit_code, server_log.read_text())


def main() -> int:
    args = arguments()
    root = Path(__file__).resolve().parent.parent
    os.chdir(root)
    config = json.loads(args.config.read_text())
    if config.get("schema_version") != 2:
        raise ValueError("benchmark configuration schema is not supported")
    workload = dict(config["workload"])
    workload["study_mode"] = args.study_mode
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
    package_pins = verify_package_inputs(root)
    output = args.output.resolve()
    if output.exists():
        if not output.is_dir() or any(output.iterdir()):
            raise FileExistsError(
                f"benchmark output must be a new or empty directory: {output}"
            )
    facts = machine_facts()
    facts["package_pins"] = package_pins
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
    observer_affinity_before = os.sched_getaffinity(0)
    try:
        # The observer shares only generator CPUs; it cannot steal scheduled
        # time from the server partition. Its cost remains in headroom evidence.
        os.sched_setaffinity(0, cpu_set(workload["load_cpus"]))
        facts["observer_affinity_during"] = sorted(os.sched_getaffinity(0))
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
            os.sched_setaffinity(0, observer_affinity_before)
        except Exception as error:
            restore_error = error
            complete = False
            facts["observer_affinity_restore_error"] = str(error)
        facts["observer_affinity_after"] = sorted(os.sched_getaffinity(0))
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
            raise RuntimeError("failed to restore benchmark host state") from restore_error
    print(f"campaign={output / 'campaign.json'}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
