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


def table_value(value: Any) -> str:
    """Keep observed structured facts readable without breaking Markdown tables."""
    if value is None:
        return "N/A — not captured"
    text = value if isinstance(value, str) else json.dumps(value, sort_keys=True, ensure_ascii=False)
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("|", "\\|").replace("\n", "<br>")


def validate_generator_intervals(measurement: dict[str, Any], cores: set[str], ceiling: float,
                                 *, allow_limited: bool = False) -> None:
    """Reject saturation hidden by a low whole-run CPU average.

    Sampling spans startup/warm-up and measurement; these are interval averages,
    not instantaneous CPU peaks. Retain every interval and its CPU identities.
    """
    samples = measurement.get("load_cpu_interval_samples")
    if not isinstance(samples, list) or not samples:
        raise ValueError("generator CPU interval evidence is missing")
    previous = 0.0
    peak = 0.0
    for sample in samples:
        start, end = sample.get("start_seconds"), sample.get("end_seconds")
        values = sample.get("busy_by_cpu")
        if (any(isinstance(value, bool) or not isinstance(value, (int, float))
                or not math.isfinite(value) for value in (start, end))
                or abs(start - previous) > 1e-6 or not 1 <= end - start <= 2
                or not isinstance(values, dict) or set(values) != cores
                or any(isinstance(value, bool) or not isinstance(value, (int, float))
                       or not math.isfinite(value) or not 0 <= value <= 100
                       for value in values.values())):
            raise ValueError("invalid generator CPU interval evidence")
        previous = end
        peak = max(peak, *values.values())
    if not 0 <= measurement["load_wall_seconds"] - previous <= 2:
        raise ValueError("generator CPU intervals do not cover the load window")
    if measurement.get("load_cpu_interval_peak_percent") != peak:
        raise ValueError("inconsistent generator CPU interval peak")
    if peak > ceiling and not allow_limited:
        raise ValueError("load generator was saturated within a sampled interval")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("campaign", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--study", action="store_true",
                        help="render only explicitly labelled client-limited loopback evidence")
    args = parser.parse_args()
    campaign = json.loads(args.campaign.read_text())
    if campaign.get("schema_version") != (3 if args.study else 2):
        raise ValueError("benchmark campaign schema is not supported")
    if not args.study and (campaign.get("evidence_kind", "strict") != "strict"
                           or campaign.get("workload", {}).get("study_mode", "strict") != "strict"):
        raise ValueError("study evidence cannot be promoted by changing its schema")
    if args.study and (campaign.get("evidence_kind") != "client-limited-loopback"
                       or campaign.get("workload", {}).get("study_mode") != "client-limited-loopback"):
        raise ValueError("study identity is missing")
    if campaign.get("complete") is not True:
        raise ValueError("benchmark campaign is incomplete")
    machine = campaign["machine"]
    workload = campaign["workload"]
    if workload.get("cooldown_consecutive_samples") != 3:
        raise ValueError("campaign lacks the three-sample cooldown contract")
    if (
        workload.get("warmup_seconds", 0) < 5
        or workload.get("duration_seconds", 0) <= 0
        or workload.get("max_measurement_requests", 0) <= 0
    ):
        raise ValueError("benchmark timing or request ceiling is invalid")
    placement = (
        workload.get("server_cpus"),
        workload.get("load_cpus"),
        workload.get("load_threads"),
    )
    if placement not in (("0", "1-3", 3), ("0-1", "2-3", 2)):
        raise ValueError("benchmark CPU placement or load-thread count is unqualified")
    expected_load_cores = {
        f"cpu{index}" for index in ((1, 2, 3) if placement[0] == "0" else (2, 3))
    }
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
            "request_time_p50_ms",
            "request_time_p95_ms",
            "request_time_p99_ms",
            "server_rss_kib",
            "server_rss_mean_kib",
            "server_rss_peak_kib",
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
            or not 0 < measurement["server_rss_mean_kib"] <= measurement["server_rss_peak_kib"]
            or not 0 <= measurement["request_time_p50_ms"] <= measurement["request_time_p95_ms"] <= measurement["request_time_p99_ms"]
            or measurement["server_cpu_percent"] < 0
            or not 0 <= measurement["load_cpu_busy_percent"] <= 100
        ):
            raise ValueError(f"measurement has impossible values: {measurement['lane_id']}")
        by_core = measurement.get("load_cpu_busy_percent_by_core")
        if (
            not isinstance(by_core, dict)
            or set(by_core) != expected_load_cores
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
        ) and not args.study:
            raise ValueError(
                f"load generator was saturated: {measurement['lane_id']}"
            )
        validate_generator_intervals(measurement, expected_load_cores,
                                     workload["load_cpu_maximum_busy_percent"], allow_limited=args.study)
        if args.study:
            observed = max(measurement["load_cpu_busy_percent"], measurement["load_cpu_interval_peak_percent"])
            limited = observed > workload["load_cpu_maximum_busy_percent"]
            expected_class = "client-limited" if limited else "no-generator-saturation-observed"
            if (measurement.get("capacity_classification") != expected_class
                    or measurement.get("generator_headroom") != {
                        "observed_maximum_busy_percent": observed,
                        "maximum_busy_percent": workload["load_cpu_maximum_busy_percent"],
                        "passed": not limited}):
                raise ValueError("study headroom classification differs from raw observations")
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
            # Independently recheck the same two-boundary accounting law:
            # starts-done can be negative when warmup work finishes in-window.
            or not max(0, expected - workload["concurrency"])
            <= measurement["requests_started"]
            <= expected + workload["concurrency"]
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
    boundary_balances = [
        item["requests_started"] - item["requests_done"]
        for values in grouped.values()
        for item in values
    ]
    for lane_id, values in grouped.items():
        medians[lane_id] = {
            "rps": statistics.median(item["requests_per_second"] for item in values),
            "rps_min": min(item["requests_per_second"] for item in values),
            "rps_max": max(item["requests_per_second"] for item in values),
            "mean_ms": statistics.median(item["request_time_mean_ms"] for item in values),
            "p99_ms": statistics.median(item["request_time_p99_ms"] for item in values),
            "p50_ms": statistics.median(item["request_time_p50_ms"] for item in values),
            "p95_ms": statistics.median(item["request_time_p95_ms"] for item in values),
            "rss_mib": statistics.median(item["server_rss_mean_kib"] for item in values) / 1024,
            "rss_peak_mib": statistics.median(item["server_rss_peak_kib"] for item in values) / 1024,
            "cpu_percent": statistics.median(item["server_cpu_percent"] for item in values),
            "load_cpu_percent": statistics.median(
                item["load_cpu_busy_percent"] for item in values
            ),
            "processes": statistics.median(item["server_process_count"] for item in values),
        }
    lines = [
        "# Raspberry Pi 5 Benchmark Results",
        "",
        ("Diagnostic only: fewer than three matched rounds; not publication evidence."
         if rounds < 3 else "Repeated-run report; publication still requires the complete campaign review."),
        "",
        "All values are medians across matched rounds. `Relative throughput` is",
        "the lane throughput divided by the CoAkka host-inlined lane in the same",
        "ecosystem. It is a comparison aid, not a cross-ecosystem ranking.",
        "Request rate and latency cover completions in the measured interval;",
        "at most one warm-up request per connection may complete in that window.",
        "CPU and RSS cover warm-up and measurement together. RSS uses 250 ms",
        "sampling: time-weighted mean and observed peak, not instantaneous peak.",
        "Process RSS is summed and may double-count shared pages. Latency columns",
        "are medians of per-run percentiles, not pooled-request percentiles.",
        "",
    ]
    ecosystems = []
    if args.study:
        # Do not print ratios that suggest capacity rankings when the load
        # machine shares the board and some rows have saturated generation.
        lines[0] = "# Raspberry Pi 5 Client-Limited Loopback Study"
        lines[4:8] = [
            "Observational localhost workload, not an unconstrained server-capacity comparison.",
            "Generator-limited rows do not establish performance ceilings or winners.",
            "No saturation observed is only a headroom observation, not capacity proof.",
            "Values are medians; every per-run classification is retained below.",
            "Request rate and latency cover completions in the measured interval;",
        ]
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
                "Relative throughput | Mean | p50 | p95 | p99 | Server CPU | Busiest load CPU | RSS mean | RSS sampled peak |",
                "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
            ]
        )
        for lane in selected:
            value = medians[lane["id"]]
            relative = "not ranked (study)" if args.study else f"{value['rps'] / baseline:.2f}x"
            lines.append(
                f"| {lane['implementation']} | {value['processes']:.0f} | "
                f"{value['rps']:,.0f} | "
                f"{value['rps_min']:,.0f}-{value['rps_max']:,.0f} | "
                f"{relative} | {value['mean_ms']:.3f} ms | "
                f"{value['p50_ms']:.3f} ms | {value['p95_ms']:.3f} ms | "
                f"{value['p99_ms']:.3f} ms | {value['cpu_percent']:.1f}% | "
                f"{value['load_cpu_percent']:.1f}% | "
                f"{value['rss_mib']:.1f} MiB | {value['rss_peak_mib']:.1f} MiB |"
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
                f"| Starts minus completions, observed range | "
                f"{min(boundary_balances)} to {max(boundary_balances)}; "
                "end carry minus warm-up carry, each bounded by concurrency; "
                "not an exact in-flight census |"
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
            "| CoAkka event loops | Core-reported per run; no user loop override |",
            f"| io_uring | {'enabled' if workload['io_uring'] else 'disabled'} |",
            (
                f"| Cooldown gate | at least "
                f"{workload['cooldown_minimum_seconds']} s; <= "
                f"{workload['cooldown_maximum_c']:.1f} C; busiest CPU <= "
                f"{workload['cooldown_maximum_cpu_busy_percent']:.1f}%; "
                "three consecutive clean observations; 5 s wait between 1 s samples |"
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
    # Machine facts and raw per-run accounting accompany the median tables.
    # Optional unavailable fields stay explicit, never filled from old runs.
    for label, key in (("Firmware", "firmware"), ("Frequency snapshot (kHz)", "cpu_frequency_khz"),
                       ("RAM/swap before (KiB)", "memory_before"), ("Swap devices", "swap_devices"),
                       ("Boot filesystem", "root_filesystem"), ("Cooling controls", "cooling"),
                       ("Ambient temperature", "ambient_temperature"), ("Network", "network_topology"),
                       ("Observer CPU set", "observer_affinity_during"), ("Tool versions", "tool_versions")):
        lines.insert(-1, f"| {label} | {table_value(machine.get(key))} |")
    lines.extend(["## Candidate Identities", "", "| Package | Linux AArch64 archive SHA-256 |",
                  "| --- | --- |"])
    for name, pin in sorted(machine.get("package_pins", {}).items()):
        lines.append(f"| {name} | `{pin['sha256']['linux-aarch64']}` |")
    lines.extend(["", "## Core Observations", "",
                  "Facts below are the actual startup pull, not values inferred from CPU intent.",
                  "A null native limits table means that public surface does not expose it.", ""])
    for lane in campaign["lanes"]:
        if lane["role"] != "coakka":
            continue
        records = [value.get("core_runtime_info") for value in grouped[lane["id"]]]
        unique = {json.dumps(record, sort_keys=True) for record in records}
        lines.extend([f"### {lane['id']}", ""])
        for record in sorted(unique):
            lines.extend(["```json", json.dumps(json.loads(record), indent=2), "```", ""])
    lines.extend(["## Per-Run Resource Accounting", "",
                  "CPU seconds and sampled memory use the warm-up plus measurement window.",
                  "N/A fields are not zero. Full observations and request outcomes remain in campaign.json.", "",
                  "Generator CPU interval peak is the maximum of roughly one-second per-core averages, not an instantaneous peak.", "",
                  "| Lane / round | User CPU s | System CPU s | Generator peak RSS KiB | Observer CPU s | Generator CPU interval peak % |",
                  "| --- | ---: | ---: | ---: | ---: | ---: |"])
    for measurement in campaign["measurements"]:
        cells = [table_value(measurement.get(key)) for key in (
            "server_user_cpu_seconds", "server_system_cpu_seconds", "load_rss_sampled_peak_kib", "observer_cpu_seconds",
            "load_cpu_interval_peak_percent")]
        lines.append(f"| {measurement['lane_id']} / {measurement['round']} | " + " | ".join(cells) + " |")
    if args.study:
        lines.extend(["", "## Per-Run Generator Limitations", "",
                      "| Lane / round | Classification | Observed busiest CPU % | Limit % |",
                      "| --- | --- | ---: | ---: |"])
        for measurement in campaign["measurements"]:
            headroom = measurement["generator_headroom"]
            lines.append(f"| {measurement['lane_id']} / {measurement['round']} | "
                         f"{measurement['capacity_classification']} | "
                         f"{headroom['observed_maximum_busy_percent']:.2f} | "
                         f"{headroom['maximum_busy_percent']:.2f} |")
    lines.extend(["", "## Observed Loaded Runtime Files", "",
                  "Recorded before timed load; filenames and content hashes, not inferred from package intent.",
                  "Observed paths and process IDs remain in the private measurement receipt.", "",
                  "| Lane / round | Mapped file | SHA-256 |", "| --- | --- | --- |"])
    for measurement in campaign["measurements"]:
        records = measurement.get("loaded_package")
        if records is None:
            lines.append(f"| {measurement['lane_id']} / {measurement['round']} | N/A — not captured | N/A |")
        elif not records:
            lines.append(f"| {measurement['lane_id']} / {measurement['round']} | No CoAkka mapping | — |")
        else:
            for record in records:
                lines.append(f"| {measurement['lane_id']} / {measurement['round']} | "
                             f"{table_value(Path(record['path']).name)} | `{record['sha256']}` |")
    args.output.write_text("\n".join(lines) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
