#!/usr/bin/env python3
"""Shared process, load, cooling, and evidence helpers for pair campaigns."""

from __future__ import annotations

import hashlib
import http.client
import json
import os
import platform
import resource
import selectors
import signal
import ssl
import statistics
import subprocess
import threading
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")
    temporary.replace(path)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def capture(command: list[str], cwd: Path | None = None) -> dict[str, Any]:
    try:
        completed = subprocess.run(
            command, cwd=cwd, text=True, capture_output=True, timeout=30, check=False
        )
        return {
            "command": command,
            "exit_code": completed.returncode,
            "stdout": completed.stdout,
            "stderr": completed.stderr,
        }
    except (OSError, subprocess.TimeoutExpired) as error:
        return {"command": command, "error": str(error)}


def resolve(root: Path, value: str) -> str:
    candidate = root / value
    return str(candidate) if "/" in value and candidate.exists() else value


def read_text(path: Path) -> str | None:
    try:
        return path.read_text(errors="replace")
    except OSError:
        return None


def redacted_cpuinfo() -> str | None:
    value = read_text(Path("/proc/cpuinfo"))
    if value is None:
        return None
    return "\n".join(
        "Serial\t\t: rpi5-benchmark-01" if line.startswith("Serial") else line
        for line in value.splitlines()
    ) + "\n"


def capture_environment(root: Path, output: Path) -> dict[str, Any]:
    commands = {
        "uname": ["uname", "-a"],
        "lscpu": ["lscpu", "--json"],
        "gcc": ["gcc", "--version"],
        "gxx": ["g++", "--version"],
        "java": ["java", "-version"],
        "python": ["python3", "--version"],
        "node": ["node", "--version"],
        "bun": ["bun", "--version"],
        "go": ["/home/pi5/lab/go1.26.3/bin/go", "version"],
        "oha": [str(root / "tools/downloads/oha-1.14.0-linux-arm64"), "--version"],
        "services": [
            "systemctl",
            "list-units",
            "--state=running",
            "--no-pager",
            "--plain",
        ],
        "timers": ["systemctl", "list-timers", "--all", "--no-pager"],
        "network": ["ip", "-brief", "address"],
        "mounts": ["findmnt", "--json"],
        "storage": [
            "lsblk",
            "--json",
            "--output",
            "NAME,MODEL,SIZE,ROTA,TRAN,FSTYPE,MOUNTPOINTS",
        ],
        "firmware": ["vcgencmd", "version"],
        "firmware_config": ["vcgencmd", "get_config", "int"],
        "core_voltage": ["vcgencmd", "measure_volts", "core"],
        "eeprom": ["rpi-eeprom-update"],
        "throttled": ["vcgencmd", "get_throttled"],
    }
    files = {
        "/etc/os-release": read_text(Path("/etc/os-release")),
        "/proc/cpuinfo": redacted_cpuinfo(),
        "/proc/cmdline": read_text(Path("/proc/cmdline")),
        "/proc/uptime": read_text(Path("/proc/uptime")),
        "/sys/firmware/devicetree/base/model": read_text(
            Path("/sys/firmware/devicetree/base/model")
        ),
    }
    digests: dict[str, str] = {}
    digest_directories = (
        root / "artifacts",
        root / "tools/downloads",
        root / "build/application-native",
        root / "build/bin",
        root / "build/jvm",
        root / "build/jvm-application",
        root / "build/javascript",
        root / "build/javascript-http2-package",
        root / "build/python-application-package",
        root / "build/python-package",
        root / "build/typescript",
    )
    for directory in digest_directories:
        if not directory.is_dir():
            continue
        for path in sorted(directory.rglob("*")):
            if path.is_file() and not path.is_symlink():
                digests[str(path.relative_to(root))] = sha256(path)
    native_evidence_files = (
        root / "build/native-io-uring/coakka_http_native_connector_server",
        root / "build/native-io-uring/coakka_http_native_poller_tests",
        root / "build/native-io-uring/coakka_http_runtime_http2_public_fixture",
        root / "build/native-io-uring/CMakeCache.txt",
        root / "build/native-io-uring/dependency-sources.sha256",
        root / "build/native-io-uring/startup/platform-default.pb",
        root / "build/native-io-uring/startup/io-uring.pb",
        root / "build/native-io-uring/tls/ca.pem",
    )
    for path in native_evidence_files:
        if path.is_file() and not path.is_symlink():
            digests[str(path.relative_to(root))] = sha256(path)
    source_manifest = root / "build/source.sha256"
    if source_manifest.is_file():
        target = output / "source.sha256"
        target.write_bytes(source_manifest.read_bytes())
        digests["build/source.sha256"] = sha256(source_manifest)
    environment = {
        "captured_at": utc_now(),
        "host_label": "rpi5-benchmark-01",
        "hostname": platform.node(),
        "machine": platform.machine(),
        "commands": {name: capture(command, root) for name, command in commands.items()},
        "files": files,
        "digests": digests,
        "sample_repository": capture(
            ["git", "status", "--short", "--untracked-files=all"], root
        ),
        "sample_commit": capture(["git", "rev-parse", "HEAD"], root),
        "go_build_info": {
            path.name: capture(
                ["/home/pi5/lab/go1.26.3/bin/go", "version", "-m", str(path)], root
            )
            for path in sorted((root / "build/bin").glob("fixed-*"))
        },
    }
    write_json(output / "environment.json", environment)
    return environment


def read_governors() -> dict[str, str]:
    return {
        path.parts[-3]: path.read_text().strip()
        for path in sorted(
            Path("/sys/devices/system/cpu").glob("cpu[0-9]*/cpufreq/scaling_governor")
        )
    }


def read_host_observation(*, include_throttled: bool = True) -> dict[str, Any]:
    temperature = read_text(Path("/sys/class/thermal/thermal_zone0/temp"))
    frequencies: dict[str, int] = {}
    for path in sorted(
        Path("/sys/devices/system/cpu").glob("cpu[0-9]*/cpufreq/scaling_cur_freq")
    ):
        try:
            frequencies[path.parts[-3]] = int(path.read_text().strip())
        except (OSError, ValueError):
            pass
    result = {
        "monotonic_ns": time.monotonic_ns(),
        "temperature_millicelsius": int(temperature.strip()) if temperature else None,
        "frequencies_khz": frequencies,
    }
    if include_throttled:
        throttled = capture(["vcgencmd", "get_throttled"])
        result["throttled"] = throttled.get("stdout", "").strip()
    return result


def wait_for_cooldown(
    output: Path,
    run_id: str,
    policy: dict[str, Any],
) -> dict[str, Any]:
    minimum_idle = float(policy.get("minimum_idle_seconds", 0))
    target_millicelsius = int(float(policy.get("target_temperature_c", 0)) * 1_000)
    maximum_wait = float(policy.get("maximum_wait_seconds", 0))
    poll_interval = float(policy.get("poll_interval_seconds", 0))
    if minimum_idle < 0 or target_millicelsius <= 0:
        raise RuntimeError("cooldown minimum and target temperature are invalid")
    if maximum_wait < minimum_idle or maximum_wait <= 0:
        raise RuntimeError("cooldown deadline must cover the minimum idle interval")
    if poll_interval <= 0 or poll_interval > maximum_wait:
        raise RuntimeError("cooldown poll interval is invalid")

    path = output / "cooldowns" / f"{run_id}.json"
    started = time.monotonic()
    record: dict[str, Any] = {
        "run_id": run_id,
        "status": "cooling",
        "started_at": utc_now(),
        "minimum_idle_seconds": minimum_idle,
        "target_temperature_millicelsius": target_millicelsius,
        "maximum_wait_seconds": maximum_wait,
        "poll_interval_seconds": poll_interval,
        "observations": [],
    }
    while True:
        elapsed = time.monotonic() - started
        observation = read_host_observation()
        observation["elapsed_seconds"] = elapsed
        record["observations"].append(observation)
        temperature = observation.get("temperature_millicelsius")
        throttle_clean = observation.get("throttled") == "throttled=0x0"
        cool_enough = temperature is not None and temperature <= target_millicelsius
        if elapsed >= minimum_idle and cool_enough and throttle_clean:
            record["status"] = "pass"
            record["finished_at"] = utc_now()
            record["elapsed_seconds"] = elapsed
            record["final_temperature_millicelsius"] = temperature
            write_json(path, record)
            return {
                "status": "pass",
                "elapsed_seconds": elapsed,
                "initial_temperature_millicelsius": record["observations"][0].get(
                    "temperature_millicelsius"
                ),
                "final_temperature_millicelsius": temperature,
                "observations": len(record["observations"]),
                "raw": str(path.relative_to(output)),
            }
        if elapsed >= maximum_wait:
            record["status"] = "fail"
            record["finished_at"] = utc_now()
            record["elapsed_seconds"] = elapsed
            record["failure"] = (
                "host did not reach the cooling and throttle gate before deadline"
            )
            write_json(path, record)
            raise RuntimeError(
                f"cooldown failed for {run_id}: temperature={temperature}, "
                f"throttled={observation.get('throttled')}"
            )
        write_json(path, record)
        time.sleep(min(poll_interval, maximum_wait - elapsed))


def read_system_cpu() -> dict[str, list[int]]:
    result: dict[str, list[int]] = {}
    with Path("/proc/stat").open() as stream:
        for line in stream:
            fields = line.split()
            if not fields or not fields[0].startswith("cpu"):
                break
            result[fields[0]] = [int(value) for value in fields[1:]]
    return result


def read_process(pid: int) -> dict[str, Any] | None:
    process = Path("/proc") / str(pid)
    try:
        status: dict[str, str] = {}
        for line in (process / "status").read_text().splitlines():
            if ":" in line:
                key, value = line.split(":", 1)
                status[key] = value.strip()
        raw_stat = (process / "stat").read_text()
        fields = raw_stat[raw_stat.rfind(")") + 2 :].split()
        return {
            "monotonic_ns": time.monotonic_ns(),
            "rss_kib": int(status.get("VmRSS", "0 kB").split()[0]),
            "rss_peak_kib": int(status.get("VmHWM", "0 kB").split()[0]),
            "threads": int(status.get("Threads", "0")),
            "fds": sum(1 for _ in (process / "fd").iterdir()),
            "voluntary_context_switches": int(
                status.get("voluntary_ctxt_switches", "0")
            ),
            "involuntary_context_switches": int(
                status.get("nonvoluntary_ctxt_switches", "0")
            ),
            "user_ticks": int(fields[11]),
            "system_ticks": int(fields[12]),
        }
    except (FileNotFoundError, PermissionError, ProcessLookupError, ValueError):
        return None


class ResourceSampler:
    def __init__(self, pid: int) -> None:
        self.pid = pid
        self.records: list[dict[str, Any]] = []
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._run, name="resource-sampler")

    def start(self) -> None:
        self._thread.start()

    def stop(self) -> list[dict[str, Any]]:
        self._stop.set()
        self._thread.join(timeout=5)
        return self.records

    def _run(self) -> None:
        next_host = 0.0
        while not self._stop.is_set():
            record = read_process(self.pid)
            if record is not None:
                now = time.monotonic()
                if now >= next_host:
                    host = read_host_observation(include_throttled=False)
                    record["host"] = host
                    next_host = now + 1.0
                self.records.append(record)
            self._stop.wait(0.25)


class ServerProcess:
    def __init__(self, root: Path, lane: dict[str, Any], cpu_set: str, run_id: str, output: Path):
        self.root = root
        self.lane = lane
        self.run_id = run_id
        self.output = output
        (output / "logs").mkdir(parents=True, exist_ok=True)
        self.stderr_stream = (output / "logs" / f"{run_id}-server.stderr.log").open("w")
        environment = os.environ.copy()
        for key, value in lane.get("environment", {}).items():
            environment[key] = resolve(root, value)
        command = [resolve(root, value) for value in lane["command"]]
        started = time.monotonic_ns()
        self.process = subprocess.Popen(
            ["taskset", "-c", cpu_set, *command],
            cwd=root,
            env=environment,
            text=True,
            stdout=subprocess.PIPE,
            stderr=self.stderr_stream,
            start_new_session=True,
        )
        assert self.process.stdout is not None
        selector = selectors.DefaultSelector()
        selector.register(self.process.stdout, selectors.EVENT_READ)
        events = selector.select(timeout=30)
        if not events:
            self.force_stop()
            raise RuntimeError(f"{lane['id']} did not report ready within 30 seconds")
        ready_line = self.process.stdout.readline()
        try:
            self.ready = json.loads(ready_line)
        except json.JSONDecodeError as error:
            exit_code = self.process.poll()
            self.force_stop()
            raise RuntimeError(
                f"{lane['id']} emitted a non-JSON ready record "
                f"(exit={exit_code}, value={ready_line!r})"
            ) from error
        if self.ready.get("ready") is not True or not isinstance(
            self.ready.get("bound_port"), int
        ):
            self.force_stop()
            raise RuntimeError(f"{lane['id']} emitted an invalid ready record")
        for key, expected in lane.get("ready_expect", {}).items():
            if self.ready.get(key) != expected:
                self.force_stop()
                raise RuntimeError(
                    f"{lane['id']} ready field {key!r} did not equal {expected!r}"
                )
        self.startup_proof = self._capture_startup_proof()
        self.port = self.ready["bound_port"]
        self.startup_ms = (time.monotonic_ns() - started) / 1_000_000
        self.stdout_lines = [ready_line]

    def _capture_startup_proof(self) -> dict[str, Any] | None:
        proof = self.lane.get("startup_proof")
        if proof is None:
            return None
        command = [resolve(self.root, value) for value in proof["command"]]
        input_path = Path(resolve(self.root, proof["stdin_file"]))
        if not input_path.is_file():
            self.force_stop()
            raise RuntimeError(
                f"{self.lane['id']} startup proof input is missing: {input_path}"
            )
        completed = subprocess.run(
            command,
            cwd=self.root,
            input=input_path.read_bytes(),
            capture_output=True,
            timeout=30,
            check=False,
        )
        stdout = completed.stdout.decode(errors="replace")
        stderr = completed.stderr.decode(errors="replace")
        proof_path = self.output / "proofs" / f"{self.run_id}.json"
        record = {
            "command": command,
            "exit_code": completed.returncode,
            "stdin": str(input_path),
            "stdin_sha256": sha256(input_path),
            "stdout": stdout,
            "stderr": stderr,
        }
        write_json(proof_path, record)
        expected = proof.get("stdout_contains", [])
        if completed.returncode != 0 or any(value not in stdout for value in expected):
            self.force_stop()
            raise RuntimeError(f"{self.lane['id']} startup proof did not match")
        return {
            "status": "pass",
            "raw": str(proof_path.relative_to(self.output)),
            "stdin_sha256": record["stdin_sha256"],
        }

    def stop(self) -> dict[str, Any]:
        started = time.monotonic_ns()
        os.killpg(self.process.pid, signal.SIGTERM)
        try:
            stdout, _ = self.process.communicate(timeout=20)
        except subprocess.TimeoutExpired:
            self.force_stop()
            raise RuntimeError(f"{self.lane['id']} did not stop within 20 seconds")
        self.stderr_stream.close()
        self.stdout_lines.append(stdout)
        stdout_path = self.output / "logs" / f"{self.run_id}-server.stdout.log"
        stdout_path.write_text("".join(self.stdout_lines))
        records = [
            json.loads(line)
            for line in stdout.splitlines()
            if line.strip().startswith("{")
        ]
        stopped = records[-1] if records else {}
        allowed_exit_codes = set(self.lane.get("allowed_exit_codes", [0]))
        if self.process.returncode not in allowed_exit_codes or stopped.get("stopped") is not True:
            raise RuntimeError(
                f"{self.lane['id']} stopped incorrectly: exit={self.process.returncode}"
            )
        if any(
            int(stopped.get(key, 0)) != 0
            for key in ("response_failures", "handler_errors")
        ):
            raise RuntimeError(f"{self.lane['id']} reported handler failures")
        return {
            "shutdown_ms": (time.monotonic_ns() - started) / 1_000_000,
            "exit_code": self.process.returncode,
            "record": stopped,
        }

    def force_stop(self) -> None:
        if self.process.poll() is None:
            os.killpg(self.process.pid, signal.SIGKILL)
            self.process.wait(timeout=5)
        self.stderr_stream.close()


def smoke(root: Path, port: int, workload: dict[str, Any]) -> dict[str, Any]:
    started = time.monotonic_ns()
    scheme = workload.get("scheme", "http")
    http_version = str(workload.get("http_version", "1.1"))
    request_body = workload.get("request_body_utf8")
    expected = workload["body_utf8"].encode()
    if http_version == "2":
        # Python's standard client is HTTP/1.1-only. Require an exact HTTP/2
        # protocol and body proof from curl before accepting the sample.
        url = f"{scheme}://127.0.0.1:{port}{workload['path']}"
        marker = "\nCOAKKA_HTTP_SMOKE_PROTOCOL="
        command = [
            "curl",
            "--silent",
            "--show-error",
            "--fail-with-body",
            "--http2",
            "--cacert",
            resolve(root, workload["tls_ca_file"]),
            "--request",
            workload["method"],
        ]
        if workload.get("content_type"):
            command.extend(["--header", f"Content-Type: {workload['content_type']}"])
        if request_body is not None:
            command.extend(["--data-binary", request_body])
        command.extend(["--write-out", f"{marker}%{{http_version}}", url])
        completed = subprocess.run(
            command, cwd=root, text=True, capture_output=True, timeout=10, check=False
        )
        expected_output = workload["body_utf8"] + marker + "2"
        if completed.returncode != 0 or completed.stdout != expected_output:
            raise RuntimeError("HTTP/2 smoke protocol or response mismatch")
        return {
            "status": workload["status"],
            "body_sha256": hashlib.sha256(expected).hexdigest(),
            "body_bytes": len(expected),
            "http_version": http_version,
            "elapsed_ms": (time.monotonic_ns() - started) / 1_000_000,
        }

    headers = {}
    if workload.get("content_type"):
        headers["Content-Type"] = workload["content_type"]
    if scheme == "https":
        context = ssl.create_default_context(
            cafile=resolve(root, workload["tls_ca_file"])
        )
        connection: http.client.HTTPConnection = http.client.HTTPSConnection(
            "127.0.0.1", port, timeout=5, context=context
        )
    else:
        connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
    try:
        connection.request(
            workload["method"], workload["path"], body=request_body, headers=headers
        )
        response = connection.getresponse()
        body = response.read()
    finally:
        connection.close()
    if response.status != workload["status"] or body != expected:
        raise RuntimeError(
            f"response mismatch: status={response.status}, body_size={len(body)}"
        )
    return {
        "status": response.status,
        "body_sha256": hashlib.sha256(body).hexdigest(),
        "body_bytes": len(body),
        "http_version": http_version,
        "elapsed_ms": (time.monotonic_ns() - started) / 1_000_000,
    }


def run_oha(
    root: Path,
    output: Path,
    run_id: str,
    phase: str,
    port: int,
    concurrency: int,
    duration: int,
    cpu_set: str,
    workload: dict[str, Any],
) -> dict[str, Any]:
    raw_path = output / "raw" / f"{run_id}-{phase}-oha.json"
    stderr_path = output / "raw" / f"{run_id}-{phase}-oha.stderr.log"
    raw_path.parent.mkdir(parents=True, exist_ok=True)
    oha = root / "tools/downloads/oha-1.14.0-linux-arm64"
    http_version = str(workload.get("http_version", "1.1"))
    scheme = workload.get("scheme", "http")
    command = [
        "taskset",
        "-c",
        cpu_set,
        str(oha),
        "--no-tui",
        "--no-color",
        "--output-format",
        "json",
        "--http-version",
        http_version,
        "--wait-ongoing-requests-after-deadline",
        "-m",
        workload["method"],
        "-c",
        str(concurrency),
    ]
    parallel = int(workload.get("parallel_per_connection", 1))
    if http_version == "2" and parallel > 1:
        command.extend(["-p", str(parallel)])
    if workload.get("tls_ca_file"):
        command.extend(["--cacert", resolve(root, workload["tls_ca_file"])])
    if workload.get("content_type"):
        command.extend(["-T", workload["content_type"]])
    if workload.get("request_body_utf8") is not None:
        command.extend(["-d", workload["request_body_utf8"]])
    command.extend(
        [
            "-z",
            f"{duration}s",
            f"{scheme}://127.0.0.1:{port}{workload['path']}",
        ]
    )
    environment = os.environ.copy()
    environment["NO_COLOR"] = "true"
    usage_before = resource.getrusage(resource.RUSAGE_CHILDREN)
    started = time.monotonic_ns()
    completed = subprocess.run(
        command,
        cwd=root,
        env=environment,
        text=True,
        capture_output=True,
        timeout=duration + 45,
        check=False,
    )
    usage_after = resource.getrusage(resource.RUSAGE_CHILDREN)
    raw_path.write_text(completed.stdout)
    stderr_path.write_text(completed.stderr)
    if completed.returncode != 0:
        raise RuntimeError(f"oha {phase} failed with exit {completed.returncode}")
    try:
        value = json.loads(completed.stdout)
    except json.JSONDecodeError as error:
        raise RuntimeError(f"oha {phase} emitted invalid JSON") from error
    errors = value.get("errorDistribution", {})
    statuses = value.get("statusCodeDistribution", {})
    expected_status = str(workload["status"])
    if errors or set(statuses) != {expected_status}:
        raise RuntimeError(
            f"oha {phase} observed errors or non-{expected_status} responses"
        )
    return {
        "command": command,
        "elapsed_ms": (time.monotonic_ns() - started) / 1_000_000,
        "load_user_cpu_seconds": usage_after.ru_utime - usage_before.ru_utime,
        "load_system_cpu_seconds": usage_after.ru_stime - usage_before.ru_stime,
        "load_voluntary_context_switches": usage_after.ru_nvcsw - usage_before.ru_nvcsw,
        "load_involuntary_context_switches": usage_after.ru_nivcsw - usage_before.ru_nivcsw,
        "raw": str(raw_path.relative_to(output)),
        "stderr": str(stderr_path.relative_to(output)),
        "http_version": http_version,
        "parallel_per_connection": parallel,
    }


def summarize_resources(records: list[dict[str, Any]]) -> dict[str, Any]:
    if not records:
        return {}
    ticks = os.sysconf("SC_CLK_TCK")
    hosts = [record["host"] for record in records if "host" in record]
    result = {
        "samples": len(records),
        "rss_peak_kib": max(record["rss_peak_kib"] for record in records),
        "rss_median_kib": statistics.median(record["rss_kib"] for record in records),
        "threads_max": max(record["threads"] for record in records),
        "fds_max": max(record["fds"] for record in records),
        "user_cpu_seconds": (records[-1]["user_ticks"] - records[0]["user_ticks"]) / ticks,
        "system_cpu_seconds": (
            records[-1]["system_ticks"] - records[0]["system_ticks"]
        )
        / ticks,
        "voluntary_context_switches": records[-1]["voluntary_context_switches"]
        - records[0]["voluntary_context_switches"],
        "involuntary_context_switches": records[-1][
            "involuntary_context_switches"
        ]
        - records[0]["involuntary_context_switches"],
    }
    temperatures = [
        host["temperature_millicelsius"]
        for host in hosts
        if host.get("temperature_millicelsius") is not None
    ]
    frequencies = [
        value for host in hosts for value in host.get("frequencies_khz", {}).values()
    ]
    if temperatures:
        result["temperature_min_millicelsius"] = min(temperatures)
        result["temperature_max_millicelsius"] = max(temperatures)
    if frequencies:
        result["frequency_min_khz"] = min(frequencies)
        result["frequency_max_khz"] = max(frequencies)
    return result


def run_sample(
    root: Path,
    output: Path,
    lane: dict[str, Any],
    workload: dict[str, Any],
    server_cpu_set: str,
    load_cpu_set: str,
    round_number: int,
    concurrency: int,
    warmup: int,
    duration: int,
    cooldown_before: dict[str, Any],
) -> dict[str, Any]:
    run_id = f"r{round_number:02d}-{lane['id']}-c{concurrency:03d}"
    sidecar = output / "samples" / f"{run_id}.json"
    record: dict[str, Any] = {
        "run_id": run_id,
        "lane": lane["id"],
        "language": lane["language"],
        "round": round_number,
        "concurrency": concurrency,
        "warmup_seconds": warmup,
        "duration_seconds": duration,
        "cooldown_before": cooldown_before,
        "started_at": utc_now(),
        "validation": "pending",
    }
    server: ServerProcess | None = None
    try:
        server = ServerProcess(root, lane, server_cpu_set, run_id, output)
        record["server_pid"] = server.process.pid
        record["startup_ms"] = server.startup_ms
        if server.startup_proof is not None:
            record["startup_proof"] = server.startup_proof
        record["smoke_before_warmup"] = smoke(root, server.port, workload)
        record["warmup"] = run_oha(
            root,
            output,
            run_id,
            "warmup",
            server.port,
            concurrency,
            warmup,
            load_cpu_set,
            workload,
        )
        record["host_before"] = read_host_observation()
        system_before = read_system_cpu()
        sampler = ResourceSampler(server.process.pid)
        sampler.start()
        try:
            record["measurement"] = run_oha(
                root,
                output,
                run_id,
                "measurement",
                server.port,
                concurrency,
                duration,
                load_cpu_set,
                workload,
            )
        finally:
            resources = sampler.stop()
        system_after = read_system_cpu()
        resources_path = output / "resources" / f"{run_id}.jsonl"
        resources_path.parent.mkdir(parents=True, exist_ok=True)
        resources_path.write_text(
            "".join(json.dumps(value, sort_keys=True) + "\n" for value in resources)
        )
        record["resources"] = summarize_resources(resources)
        record["resources"]["raw"] = str(resources_path.relative_to(output))
        record["system_cpu_ticks_before"] = system_before
        record["system_cpu_ticks_after"] = system_after
        record["host_after"] = read_host_observation()
        record["smoke_after_measurement"] = smoke(root, server.port, workload)
        record["shutdown"] = server.stop()
        server = None
        if record["host_before"]["throttled"] != "throttled=0x0" or record[
            "host_after"
        ]["throttled"] != "throttled=0x0":
            raise RuntimeError("firmware reported throttling during the sample")
        record["validation"] = "pass"
        record["finished_at"] = utc_now()
        write_json(sidecar, record)
        return record
    except BaseException as error:
        record["validation"] = "fail"
        record["error"] = f"{type(error).__name__}: {error}"
        record["finished_at"] = utc_now()
        write_json(sidecar, record)
        raise
    finally:
        if server is not None:
            server.force_stop()
