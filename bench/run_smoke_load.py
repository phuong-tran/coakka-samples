#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import platform
import re
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from statistics import median
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[1]
NATIVE_PACKAGE_RE = re.compile(r"runtime/native/releases/([^/]+)/")


PROFILES: dict[str, dict[str, Any]] = {
    "runtime-native-pressure": {
        "command": ["bash", "run.sh", "runtime", "native", "pressure"],
        "strict_no_drop": True,
        "parser": "native_pressure",
    },
    "runtime-jvm-basic": {
        "command": ["bash", "run.sh", "runtime", "jvm", "basic"],
        "strict_no_drop": True,
        "parser": "runtime_basic",
    },
    "runtime-python-hot-reload": {
        "command": ["bash", "run.sh", "runtime", "python", "hot-reload"],
        "strict_no_drop": True,
        "parser": "hot_reload",
    },
}


def run_text(command: list[str], *, cwd: Path) -> str:
    return subprocess.check_output(command, cwd=cwd, text=True).strip()


def try_run_text(command: list[str], *, cwd: Path) -> str | None:
    try:
        return run_text(command, cwd=cwd)
    except Exception:
        return None


def cpu_model() -> str:
    if platform.system() == "Darwin":
        value = try_run_text(["sysctl", "-n", "machdep.cpu.brand_string"], cwd=REPO_ROOT)
        if value:
            return value
    cpuinfo = Path("/proc/cpuinfo")
    if cpuinfo.exists():
        for line in cpuinfo.read_text(encoding="utf-8", errors="replace").splitlines():
            if line.lower().startswith("model name"):
                return line.split(":", 1)[1].strip()
    return platform.processor() or platform.machine()


def publish_root() -> Path:
    return Path(os.environ.get("COAKKA_PUBLISH_ROOT", REPO_ROOT.parent / "coakka-publish-public"))


def native_package_version() -> str | None:
    manifest = publish_root() / "artifacts" / "public-artifacts.tsv"
    if not manifest.exists():
        return None
    for line in manifest.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        columns = line.split("\t")
        if len(columns) >= 3 and columns[1] == "runtime Native package":
            match = NATIVE_PACKAGE_RE.search(columns[2])
            return match.group(1) if match else None
    return None


def parse_metrics(parser: str, stdout: str) -> dict[str, Any]:
    if parser == "native_pressure":
        pressure = re.search(
            r"coakka_runtime_pressure attempts=(\d+) delivered=(\d+) rejected=(\d+) capacity=(\d+) highWatermark=(\d+)",
            stdout,
        )
        stats = re.search(
            r"coakka_runtime_stats generation=(\d+) routes=(\d+) queueRejected=(\d+) deadletters=(\d+)",
            stdout,
        )
        return {
            "attempts": int(pressure.group(1)) if pressure else None,
            "delivered": int(pressure.group(2)) if pressure else None,
            "rejected": int(pressure.group(3)) if pressure else None,
            "queue_capacity": int(pressure.group(4)) if pressure else None,
            "high_watermark": int(pressure.group(5)) if pressure else None,
            "generation": int(stats.group(1)) if stats else None,
            "routes": int(stats.group(2)) if stats else None,
            "queue_rejected": int(stats.group(3)) if stats else None,
            "deadletters": int(stats.group(4)) if stats else None,
        }
    if parser == "runtime_basic":
        stats = re.search(
            r"coakka_runtime_stats generation=(\d+) routes=(\d+) delivered=(\d+) matchedResponses=(\d+)",
            stdout,
        )
        return {
            "generation": int(stats.group(1)) if stats else None,
            "routes": int(stats.group(2)) if stats else None,
            "delivered": int(stats.group(3)) if stats else None,
            "matched_responses": int(stats.group(4)) if stats else None,
        }
    if parser == "hot_reload":
        final = re.search(
            r"coakka_runtime_hot_reload_final generation=(\d+) routes=(\d+) routeMisses=(\d+) deadletters=(\d+) matchedDeadletters=(\d+)",
            stdout,
        )
        return {
            "generation": int(final.group(1)) if final else None,
            "routes": int(final.group(2)) if final else None,
            "route_misses": int(final.group(3)) if final else None,
            "deadletters": int(final.group(4)) if final else None,
            "matched_deadletters": int(final.group(5)) if final else None,
        }
    return {}


def run_profile(profile: dict[str, Any]) -> dict[str, Any]:
    started = time.perf_counter()
    process = subprocess.run(
        profile["command"],
        cwd=REPO_ROOT,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    elapsed_ms = round((time.perf_counter() - started) * 1000, 3)
    return {
        "elapsed_ms": elapsed_ms,
        "exit_code": process.returncode,
        "metrics": parse_metrics(profile["parser"], process.stdout),
        "stdout": process.stdout,
    }


def aggregate(runs: list[dict[str, Any]]) -> dict[str, Any]:
    ok_runs = [run for run in runs if run["exit_code"] == 0]
    values: dict[str, list[float]] = {"elapsed_ms": [run["elapsed_ms"] for run in ok_runs]}
    for run in ok_runs:
        for key, value in run["metrics"].items():
            if isinstance(value, (int, float)):
                values.setdefault(key, []).append(float(value))
    return {
        key: median(items)
        for key, items in values.items()
        if items
    }


def build_report(args: argparse.Namespace) -> dict[str, Any]:
    profile = PROFILES[args.profile]
    warmups = []
    for _ in range(args.warmup_runs):
        warmup = run_profile(profile)
        warmups.append(warmup)
        if warmup["exit_code"] != 0:
            raise SystemExit(warmup["stdout"])

    runs = []
    for index in range(args.repetitions):
        run = run_profile(profile)
        run["index"] = index + 1
        runs.append(run)
        if run["exit_code"] != 0:
            break

    aggregate_data = aggregate(runs)
    warmup_seconds = round(sum(run["elapsed_ms"] for run in warmups) / 1000, 3)
    measured_seconds = round(sum(run["elapsed_ms"] for run in runs) / 1000, 3)

    return {
        "schema": "coakka.samples.smoke_load.v1",
        "result_class": args.result_class,
        "profile": args.profile,
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "metadata": {
            "repo_commit": try_run_text(["git", "rev-parse", "HEAD"], cwd=REPO_ROOT),
            "artifact_manifest_commit": try_run_text(["git", "rev-parse", "HEAD"], cwd=publish_root()),
            "publish_root": str(publish_root()),
            "publish_raw_base": os.environ.get("COAKKA_PUBLISH_RAW_BASE"),
            "native_runtime_package": native_package_version(),
            "os": platform.system(),
            "os_release": platform.release(),
            "os_version": platform.version(),
            "machine": platform.machine(),
            "cpu_model": cpu_model(),
            "queue_capacity": aggregate_data.get("queue_capacity"),
            "strict_no_drop": profile["strict_no_drop"],
            "worker_count": args.worker_count,
            "warmup_runs": args.warmup_runs,
            "warmup_seconds": warmup_seconds,
            "measured_runs": args.repetitions,
            "measured_seconds": measured_seconds,
            "aggregation": "median of successful runs",
            "production_claim": False,
        },
        "command": profile["command"],
        "aggregate": aggregate_data,
        "runs": runs,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Run CoAkka reference smoke-load profiles and emit JSON.")
    parser.add_argument("--profile", choices=sorted(PROFILES), default="runtime-native-pressure")
    parser.add_argument("--result-class", default="macos-smoke")
    parser.add_argument("--warmup-runs", type=int, default=1)
    parser.add_argument("--repetitions", type=int, default=3)
    parser.add_argument("--worker-count", type=int, default=1)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    if args.warmup_runs < 0 or args.repetitions < 1 or args.worker_count < 1:
        parser.error("warmup-runs must be >= 0; repetitions and worker-count must be >= 1")

    report = build_report(args)
    encoded = json.dumps(report, indent=2, sort_keys=True)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded + "\n", encoding="utf-8")
    else:
        print(encoded)
    return 0 if all(run["exit_code"] == 0 for run in report["runs"]) else 1


if __name__ == "__main__":
    sys.exit(main())
