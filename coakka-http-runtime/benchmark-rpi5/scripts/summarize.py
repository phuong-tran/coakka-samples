#!/usr/bin/env python3
"""Render median benchmark measurements as Markdown tables."""

from __future__ import annotations

import argparse
import json
import math
import statistics
from collections import defaultdict
from pathlib import Path
from typing import Any


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("campaign", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    campaign = json.loads(args.campaign.read_text())
    if campaign.get("schema_version") != 2:
        raise ValueError("benchmark campaign schema is not supported")
    if campaign.get("complete") is not True:
        raise ValueError("benchmark campaign is incomplete")
    machine = campaign["machine"]
    workload = campaign["workload"]
    if (
        workload.get("warmup_seconds", 0) < 5
        or workload.get("duration_seconds", 0) <= 0
        or workload.get("max_measurement_requests", 0) <= 0
    ):
        raise ValueError("benchmark timing or request ceiling is invalid")
    if workload.get("server_cpus") != "0" or workload.get("load_cpus") != "1-3":
        raise ValueError("benchmark CPU placement differs from the qualified campaign")
    if workload.get("load_threads") != 3:
        raise ValueError("benchmark load-generator thread count differs")
    finite_machine_fields = (
        "temperature_after_initial_cooldown_c",
        "cpu_busy_after_initial_cooldown_percent",
    )
    if any(
        isinstance(machine[field], bool)
        or not isinstance(machine[field], (int, float))
        or not math.isfinite(machine[field])
        for field in finite_machine_fields
    ):
        raise ValueError("benchmark machine has invalid initial idle evidence")
    if (
        machine["throttled_before"] != "throttled=0x0"
        or machine["throttled_after"] != "throttled=0x0"
    ):
        raise ValueError("benchmark machine has an unclean throttle record")
    if machine["governor_before"] != machine["governor_after"]:
        raise ValueError("benchmark machine governors were not restored")
    if set(machine.get("governor_during", {}).values()) != {"performance"}:
        raise ValueError("benchmark machine did not use the performance governor")
    if set(machine["governor_during"]) != set(machine["governor_before"]):
        raise ValueError("benchmark governor inventory changed during the campaign")
    if (
        machine["temperature_after_initial_cooldown_c"]
        > workload["cooldown_maximum_c"]
        or machine["cpu_busy_after_initial_cooldown_percent"]
        > workload["cooldown_maximum_cpu_busy_percent"]
    ):
        raise ValueError("benchmark machine did not pass the initial idle gate")
    rounds = workload["rounds"]
    expected_rounds = set(range(1, rounds + 1))
    configured_lanes = {lane["id"] for lane in campaign["lanes"]}
    grouped: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for measurement in campaign["measurements"]:
        if measurement["lane_id"] not in configured_lanes:
            raise ValueError(f"measurement references unknown lane {measurement['lane_id']}")
        if measurement["throttled"] != "throttled=0x0":
            raise ValueError(f"measurement was throttled: {measurement['lane_id']}")
        numeric_fields = (
            "temperature_after_cooldown_c",
            "cpu_busy_after_cooldown_percent",
            "benchmark_duration_ms",
            "load_wall_seconds",
            "requests_per_second",
            "request_time_mean_ms",
            "request_time_p99_ms",
            "server_rss_kib",
            "server_cpu_percent",
            "load_cpu_busy_percent",
        )
        if any(
            isinstance(measurement[field], bool)
            or not isinstance(measurement[field], (int, float))
            or not math.isfinite(measurement[field])
            for field in numeric_fields
        ):
            raise ValueError(f"measurement has invalid numeric evidence: {measurement['lane_id']}")
        if (
            measurement["requests_per_second"] <= 0
            or measurement["benchmark_duration_ms"] <= 0
            or measurement["load_wall_seconds"] <= 0
            or measurement["request_time_mean_ms"] < 0
            or measurement["request_time_p99_ms"] < 0
            or measurement["server_rss_kib"] <= 0
            or measurement["server_cpu_percent"] < 0
            or not 0 <= measurement["load_cpu_busy_percent"] <= 100
        ):
            raise ValueError(f"measurement has impossible values: {measurement['lane_id']}")
        by_core = measurement.get("load_cpu_busy_percent_by_core")
        if (
            not isinstance(by_core, dict)
            or set(by_core) != {"cpu1", "cpu2", "cpu3"}
            or any(
                isinstance(value, bool)
                or not isinstance(value, (int, float))
                or not math.isfinite(value)
                or not 0 <= value <= 100
                for value in by_core.values()
            )
            or abs(max(by_core.values()) - measurement["load_cpu_busy_percent"])
            > 1e-6
        ):
            raise ValueError(
                f"measurement has inconsistent load-core evidence: "
                f"{measurement['lane_id']}"
            )
        if (
            isinstance(measurement["server_process_count"], bool)
            or not isinstance(measurement["server_process_count"], int)
            or measurement["server_process_count"] <= 0
        ):
            raise ValueError(f"measurement has invalid process count: {measurement['lane_id']}")
        maximum_busy = workload["cooldown_maximum_cpu_busy_percent"]
        if measurement["cpu_busy_after_cooldown_percent"] > maximum_busy:
            raise ValueError(
                f"measurement did not pass the CPU-idle gate: "
                f"{measurement['lane_id']}"
            )
        if (
            measurement["load_cpu_busy_percent"]
            > workload["load_cpu_maximum_busy_percent"]
        ):
            raise ValueError(
                f"load generator was saturated: {measurement['lane_id']}"
            )
        if (
            measurement["temperature_after_cooldown_c"]
            > workload["cooldown_maximum_c"]
        ):
            raise ValueError(
                f"measurement did not pass the temperature gate: "
                f"{measurement['lane_id']}"
            )
        expected = measurement["measurement_requests"]
        accounting_fields = (
            "measurement_requests",
            "requests_total",
            "requests_started",
            "requests_done",
            "requests_succeeded",
            "requests_failed",
            "requests_errored",
            "requests_timed_out",
            "responses_2xx",
            "responses_3xx",
            "responses_4xx",
            "responses_5xx",
        )
        if any(
            isinstance(measurement[field], bool)
            or not isinstance(measurement[field], int)
            or measurement[field] < 0
            for field in accounting_fields
        ) or expected == 0:
            raise ValueError(
                f"measurement request accounting is invalid: {measurement['lane_id']}"
            )
        total_seconds = workload["warmup_seconds"] + workload["duration_seconds"]
        measured_rate = expected / workload["duration_seconds"]
        if (
            not (total_seconds - 0.5) * 1000
            <= measurement["benchmark_duration_ms"]
            <= (total_seconds + 3) * 1000
            or abs(measurement["requests_per_second"] - measured_rate)
            > measured_rate * 0.05
            or measurement["load_wall_seconds"] < total_seconds - 0.5
        ):
            raise ValueError(
                f"measurement duration is invalid: {measurement['lane_id']}"
            )
        if (
            measurement["requests_total"] != expected
            or measurement["requests_started"] != expected
            or measurement["requests_done"] != expected
            or measurement["requests_succeeded"] != expected
            or measurement["requests_failed"] != 0
            or measurement["requests_errored"] != 0
            or measurement["requests_timed_out"] != 0
            or measurement["responses_2xx"] != expected
            or measurement["responses_3xx"] != 0
            or measurement["responses_4xx"] != 0
            or measurement["responses_5xx"] != 0
        ):
            raise ValueError(
                f"measurement request accounting failed: {measurement['lane_id']}"
            )
        grouped[measurement["lane_id"]].append(measurement)
    if set(grouped) != configured_lanes:
        raise ValueError("benchmark campaign does not contain every configured lane")
    for lane_id, values in grouped.items():
        observed_rounds = [item["round"] for item in values]
        if len(values) != rounds or set(observed_rounds) != expected_rounds:
            raise ValueError(f"lane {lane_id} does not contain one result per round")
    lanes = {lane["id"]: lane for lane in campaign["lanes"]}
    if len(lanes) != len(campaign["lanes"]):
        raise ValueError("benchmark configuration contains duplicate lane identifiers")
    medians: dict[str, dict[str, float]] = {}
    for lane_id, values in grouped.items():
        medians[lane_id] = {
            "rps": statistics.median(item["requests_per_second"] for item in values),
            "rps_min": min(item["requests_per_second"] for item in values),
            "rps_max": max(item["requests_per_second"] for item in values),
            "mean_ms": statistics.median(item["request_time_mean_ms"] for item in values),
            "p99_ms": statistics.median(item["request_time_p99_ms"] for item in values),
            "rss_mib": statistics.median(item["server_rss_kib"] for item in values) / 1024,
            "cpu_percent": statistics.median(item["server_cpu_percent"] for item in values),
            "load_cpu_percent": statistics.median(
                item["load_cpu_busy_percent"] for item in values
            ),
            "processes": statistics.median(item["server_process_count"] for item in values),
        }
    lines = [
        "# Raspberry Pi 5 Benchmark Results",
        "",
        "All values are medians across matched rounds. `Relative throughput` is",
        "the lane throughput divided by the CoAkka host-inlined lane in the same",
        "ecosystem. It is a comparison aid, not a cross-ecosystem ranking.",
        "The request rate and latency exclude warm-up; CPU utilization covers",
        "the warm-up and measured interval together.",
        "",
    ]
    ecosystems = []
    for lane in campaign["lanes"]:
        if lane["ecosystem"] not in ecosystems:
            ecosystems.append(lane["ecosystem"])
    for ecosystem in ecosystems:
        selected = [
            lane for lane in campaign["lanes"] if lane["ecosystem"] == ecosystem
        ]
        coakka_lanes = [lane for lane in selected if lane["role"] == "coakka"]
        if len(coakka_lanes) != 1:
            raise ValueError(f"ecosystem {ecosystem} must contain one CoAkka lane")
        coakka = coakka_lanes[0]
        baseline = medians[coakka["id"]]["rps"]
        if baseline <= 0:
            raise ValueError(f"ecosystem {ecosystem} has non-positive throughput")
        lines.extend(
            [
                f"## {ecosystem}",
                "",
                "| Implementation | Processes | Requests/s median | RPS range | "
                "Relative throughput | Mean | p99 | Server CPU | Busiest load CPU | Server RSS |",
                "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
            ]
        )
        for lane in selected:
            value = medians[lane["id"]]
            lines.append(
                f"| {lane['implementation']} | {value['processes']:.0f} | "
                f"{value['rps']:,.0f} | "
                f"{value['rps_min']:,.0f}-{value['rps_max']:,.0f} | "
                f"{value['rps'] / baseline:.2f}x | {value['mean_ms']:.3f} ms | "
                f"{value['p99_ms']:.3f} ms | {value['cpu_percent']:.1f}% | "
                f"{value['load_cpu_percent']:.1f}% | "
                f"{value['rss_mib']:.1f} MiB |"
            )
        lines.append("")
    lines.extend(
        [
            "## Measurement Protocol",
            "",
            "| Field | Value |",
            "| --- | --- |",
            (
                f"| Request | {workload['method']} {workload['path']} -> "
                f"{workload['status']}, "
                f"{len(workload['body'].encode('ascii'))} bytes |"
            ),
            (
                f"| Concurrency | {workload['concurrency']} clients, one request "
                "in flight per connection |"
            ),
            (
                f"| Same-connection warm-up | {workload['warmup_seconds']} "
                "seconds before each measured interval |"
            ),
            f"| Measured duration | {workload['duration_seconds']} seconds per lane |",
            (
                f"| Measured request ceiling | "
                f"{workload['max_measurement_requests']:,} per lane |"
            ),
            f"| Matched rounds | {workload['rounds']} |",
            f"| Random seed | {workload['random_seed']} |",
            (
                f"| CPU placement | server {workload['server_cpus']}; load "
                f"generator {workload['load_cpus']} ({workload['load_threads']} threads) |"
            ),
            f"| CoAkka event loops | {workload['coakka_event_loop_threads']} |",
            f"| io_uring | {'enabled' if workload['io_uring'] else 'disabled'} |",
            (
                f"| Cooldown gate | at least "
                f"{workload['cooldown_minimum_seconds']} s; <= "
                f"{workload['cooldown_maximum_c']:.1f} C; busiest CPU <= "
                f"{workload['cooldown_maximum_cpu_busy_percent']:.1f}% |"
            ),
            (
                f"| Busiest load-generator CPU ceiling | "
                f"{workload['load_cpu_maximum_busy_percent']:.1f}% busy |"
            ),
            "",
            "## Measured Machine",
            "",
            "| Field | Value |",
            "| --- | --- |",
            f"| Model | {machine['model']} |",
            f"| Architecture | {machine['architecture']} |",
            f"| CPU | {machine['cpu_model']} ({machine['cpu_count']} logical CPUs) |",
            f"| Memory | {machine['memory_kib'] / 1024 / 1024:.1f} GiB |",
            f"| OS | {machine['operating_system']} |",
            f"| Kernel | {machine['kernel']} |",
            (
                f"| Throttling before / after | {machine['throttled_before']} / "
                f"{machine['throttled_after']} |"
            ),
            "| Measurement governor | performance on every CPU |",
            f"| Source manifest SHA-256 | `{machine['source_manifest_sha256']}` |",
            "",
        ]
    )
    args.output.write_text("\n".join(lines) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
