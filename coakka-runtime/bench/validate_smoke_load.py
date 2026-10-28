#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


REQUIRED_METADATA = (
    "repo_commit",
    "os",
    "os_release",
    "machine",
    "cpu_model",
    "strict_no_drop",
    "warmup_runs",
    "measured_runs",
    "aggregation",
    "production_claim",
)


class ValidationError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValidationError(message)


def load_report(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise ValidationError(f"{path}: invalid JSON: {exc}") from exc
    require(isinstance(data, dict), f"{path}: report must be a JSON object")
    return data


def require_number(mapping: dict[str, Any], key: str) -> float:
    value = mapping.get(key)
    require(isinstance(value, (int, float)), f"missing numeric aggregate field: {key}")
    return float(value)


def validate_common(report: dict[str, Any]) -> None:
    require(report.get("schema") == "coakka.samples.smoke_load.v1", "unexpected schema")
    require(report.get("production_claim") is None, "production_claim must live under metadata")
    metadata = report.get("metadata")
    require(isinstance(metadata, dict), "metadata must be an object")
    for key in REQUIRED_METADATA:
        require(key in metadata, f"missing metadata field: {key}")
    require(metadata["production_claim"] is False, "smoke-load output must not be a production claim")
    require(metadata["strict_no_drop"] is True, "strict_no_drop must be true for public smoke-load profiles")
    require(isinstance(report.get("runs"), list) and report["runs"], "runs must be a non-empty list")
    for run in report["runs"]:
        require(run.get("exit_code") == 0, f"run {run.get('index')} failed")
        require(isinstance(run.get("metrics"), dict), f"run {run.get('index')} has no metrics")
        require(isinstance(run.get("stdout"), str) and run["stdout"], f"run {run.get('index')} has no stdout")


def validate_native_pressure(report: dict[str, Any]) -> None:
    aggregate = report["aggregate"]
    attempts = require_number(aggregate, "attempts")
    delivered = require_number(aggregate, "delivered")
    rejected = require_number(aggregate, "rejected")
    queue_rejected = require_number(aggregate, "queue_rejected")
    deadletters = require_number(aggregate, "deadletters")
    capacity = require_number(aggregate, "queue_capacity")
    high_watermark = require_number(aggregate, "high_watermark")
    require(attempts > 0, "native pressure attempts must be positive")
    require(delivered > 0, "native pressure must deliver at least one request")
    require(rejected > 0, "native pressure must reject under constrained queue capacity")
    require(rejected == queue_rejected == deadletters, "rejection, queue rejection, and deadletter counts must match")
    require(delivered + rejected == attempts, "delivered + rejected must equal attempts")
    require(capacity > 0, "queue capacity must be positive")
    require(0 <= high_watermark <= capacity, "high watermark must stay within queue capacity")
    require_number(aggregate, "generation")
    require_number(aggregate, "routes")


def validate_runtime_basic(report: dict[str, Any]) -> None:
    aggregate = report["aggregate"]
    delivered = require_number(aggregate, "delivered")
    matched = require_number(aggregate, "matched_responses")
    require(delivered >= 1, "runtime basic must deliver at least one request")
    require(delivered == matched, "delivered and matched response counts must match")
    require(require_number(aggregate, "generation") >= 1, "generation must be applied")
    require(require_number(aggregate, "routes") >= 1, "route count must be positive")


def validate_hot_reload(report: dict[str, Any]) -> None:
    aggregate = report["aggregate"]
    require(require_number(aggregate, "generation") == 2, "hot reload must finish on generation 2")
    require(require_number(aggregate, "routes") == 1, "hot reload must finish with one active route")
    route_misses = require_number(aggregate, "route_misses")
    deadletters = require_number(aggregate, "deadletters")
    matched = require_number(aggregate, "matched_deadletters")
    require(route_misses == deadletters == matched == 3, "hot reload deadletter diagnostics must match")


def validate_report(path: Path) -> None:
    report = load_report(path)
    validate_common(report)
    profile = report.get("profile")
    if profile == "runtime-native-pressure":
        validate_native_pressure(report)
    elif profile == "runtime-jvm-basic":
        validate_runtime_basic(report)
    elif profile == "runtime-python-hot-reload":
        validate_hot_reload(report)
    else:
        raise ValidationError(f"unsupported profile: {profile}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate CoAkka smoke-load JSON artifacts.")
    parser.add_argument("paths", nargs="+", type=Path)
    args = parser.parse_args()

    failed = False
    for path in args.paths:
        try:
            validate_report(path)
        except ValidationError as exc:
            print(f"[validate-smoke-load] {path}: {exc}", file=sys.stderr)
            failed = True
        else:
            print(f"[validate-smoke-load] ok: {path}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
