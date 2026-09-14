#!/usr/bin/env python3
"""Derive matched-round application or native-backend comparisons."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import shutil
import statistics
from collections import defaultdict
from pathlib import Path
from typing import Any


def median(values: list[float]) -> float | None:
    return statistics.median(values) if values else None


def mad(values: list[float]) -> float | None:
    center = median(values)
    return statistics.median(abs(value - center) for value in values) if center is not None else None


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def percentile(raw: dict[str, Any], name: str) -> float | None:
    values = raw.get("latencyPercentiles", {})
    value = values.get(name, values.get(name.removeprefix("p")))
    return float(value) * 1000 if value is not None else None


def percent_delta(coakka: float | None, comparator: float | None) -> float | None:
    if coakka is None or comparator in (None, 0):
        return None
    return (coakka / comparator - 1.0) * 100.0


def render(value: float | int | None, digits: int = 2) -> str:
    if value is None:
        return ""
    return f"{value:.{digits}f}" if isinstance(value, float) else str(value)


def sample_row(evidence: Path, sample: dict[str, Any]) -> dict[str, Any]:
    raw = json.loads((evidence / sample["measurement"]["raw"]).read_text())
    summary = raw["summary"]
    resources = sample.get("resources", {})
    measurement = sample["measurement"]
    return {
        "run_id": sample["run_id"],
        "lane": sample["lane"],
        "round": int(sample["round"]),
        "concurrency": int(sample["concurrency"]),
        "requests_per_second": float(summary["requestsPerSec"]),
        "p50_ms": percentile(raw, "p50"),
        "p99_ms": percentile(raw, "p99"),
        "latency_max_ms": float(summary["slowest"]) * 1000,
        "rss_peak_kib": resources.get("rss_peak_kib"),
        "threads_max": resources.get("threads_max"),
        "fds_max": resources.get("fds_max"),
        "server_cpu_seconds": float(resources.get("user_cpu_seconds", 0))
        + float(resources.get("system_cpu_seconds", 0)),
        "load_cpu_seconds": float(measurement.get("load_user_cpu_seconds", 0))
        + float(measurement.get("load_system_cpu_seconds", 0)),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", required=True, type=Path)
    args = parser.parse_args()
    evidence = args.evidence.resolve()
    campaign = json.loads((evidence / "campaign.json").read_text())
    family = campaign.get("campaign_family")
    if family not in {
        "application-pairs",
        "native-backend-pairs",
        "connector-backend-pairs",
    }:
        raise RuntimeError("pair summarizer received an unsupported campaign family")
    native_backends = family == "native-backend-pairs"
    backend_pairs = family in {"native-backend-pairs", "connector-backend-pairs"}

    failures: list[str] = []
    samples: dict[tuple[str, int, int], dict[str, Any]] = {}
    for path in sorted((evidence / "samples").glob("*.json")):
        sample = json.loads(path.read_text())
        if sample.get("validation") != "pass":
            failures.append(sample.get("run_id", path.stem))
            continue
        row = sample_row(evidence, sample)
        key = (row["lane"], row["round"], row["concurrency"])
        if key in samples:
            raise RuntimeError(f"duplicate sample for {key}")
        samples[key] = row

    lanes = {lane["id"]: lane for lane in campaign["lanes"]}
    matched_rows: list[dict[str, Any]] = []
    pair_groups: dict[tuple[str, int], list[dict[str, Any]]] = defaultdict(list)
    for pair in campaign["pairs"]:
        comparator_id = pair["baseline"] if backend_pairs else pair["comparator"]
        coakka_id = pair["candidate"] if backend_pairs else pair["coakka"]
        for concurrency in campaign["concurrencies"]:
            for round_number in range(1, campaign["rounds"] + 1):
                comparator = samples.get((comparator_id, round_number, concurrency))
                coakka = samples.get((coakka_id, round_number, concurrency))
                if comparator is None or coakka is None:
                    failures.append(f"{pair['id']}-r{round_number:02d}-c{concurrency:03d}")
                    continue
                row = {
                    "pair": pair["id"],
                    "kind": pair["kind"],
                    "round": round_number,
                    "concurrency": concurrency,
                    "comparator": comparator_id,
                    "coakka": coakka_id,
                    "comparator_requests_per_second": comparator["requests_per_second"],
                    "coakka_requests_per_second": coakka["requests_per_second"],
                    "coakka_throughput_delta_percent": percent_delta(
                        coakka["requests_per_second"], comparator["requests_per_second"]
                    ),
                    "comparator_p99_ms": comparator["p99_ms"],
                    "coakka_p99_ms": coakka["p99_ms"],
                    "coakka_p99_delta_percent": percent_delta(
                        coakka["p99_ms"], comparator["p99_ms"]
                    ),
                    "comparator_rss_peak_kib": comparator["rss_peak_kib"],
                    "coakka_rss_peak_kib": coakka["rss_peak_kib"],
                    "comparator_server_cpu_seconds": comparator["server_cpu_seconds"],
                    "coakka_server_cpu_seconds": coakka["server_cpu_seconds"],
                    "comparator_load_cpu_seconds": comparator["load_cpu_seconds"],
                    "coakka_load_cpu_seconds": coakka["load_cpu_seconds"],
                }
                matched_rows.append(row)
                pair_groups[(pair["id"], concurrency)].append(row)

    rows: list[dict[str, Any]] = []
    for (pair_id, concurrency), rounds in sorted(pair_groups.items()):
        pair = next(value for value in campaign["pairs"] if value["id"] == pair_id)
        comparator_id = pair["baseline"] if backend_pairs else pair["comparator"]
        coakka_id = pair["candidate"] if backend_pairs else pair["coakka"]
        row: dict[str, Any] = {
            "pair": pair_id,
            "kind": pair["kind"],
            "concurrency": concurrency,
            "matched_rounds": len(rounds),
            "comparator": comparator_id,
            "comparator_implementation": lanes[comparator_id]["implementation"],
            "coakka": coakka_id,
            "coakka_implementation": lanes[coakka_id]["implementation"],
        }
        for key in (
            "comparator_requests_per_second",
            "coakka_requests_per_second",
            "coakka_throughput_delta_percent",
            "comparator_p99_ms",
            "coakka_p99_ms",
            "coakka_p99_delta_percent",
            "comparator_rss_peak_kib",
            "coakka_rss_peak_kib",
            "comparator_server_cpu_seconds",
            "coakka_server_cpu_seconds",
            "comparator_load_cpu_seconds",
            "coakka_load_cpu_seconds",
        ):
            values = [float(round_row[key]) for round_row in rounds if round_row.get(key) is not None]
            row[f"{key}_median"] = median(values)
            row[f"{key}_mad"] = mad(values)
        rows.append(row)

    generator = Path(__file__).resolve()
    generator_copy = evidence / "pair-summary-generator.py"
    shutil.copy2(generator, generator_copy)
    value = {
        "schema_version": 1,
        "campaign_family": campaign["campaign_family"],
        "ecosystem": campaign["ecosystem"],
        "campaign_status": campaign["status"],
        "mode": campaign["mode"],
        "source_manifest_sha256": campaign.get("source_manifest_sha256"),
        "summary_generator_sha256": sha256(generator_copy),
        "pair_semantics": (
            "io_uring candidate relative to the same application on the platform baseline"
            if backend_pairs
            else "CoAkka application relative to ecosystem comparator"
        ),
        "failed_or_missing_samples": sorted(set(failures)),
        "matched_rounds": matched_rows,
        "pairs": rows,
    }
    (evidence / "pair-summary.json").write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")

    pair_fields = list(rows[0]) if rows else ["pair", "concurrency", "matched_rounds"]
    with (evidence / "pair-summary.csv").open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=pair_fields)
        writer.writeheader()
        writer.writerows(rows)
    round_fields = list(matched_rows[0]) if matched_rows else ["pair", "round"]
    with (evidence / "pair-rounds.csv").open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=round_fields)
        writer.writeheader()
        writer.writerows(matched_rows)

    report_title = (
        ("Native Backend Pair Evidence" if native_backends else f"{campaign['ecosystem'].title()} Connector Backend Pair Evidence")
        if backend_pairs
        else f"{campaign['ecosystem'].title()} Application Pair Evidence"
    )
    explanation = (
        "Every row compares the io_uring candidate with the platform-default "
        "backend under the same native HTTP/2 TLS workload. Positive throughput "
        "delta favors io_uring; negative p99 delta favors io_uring. Qualification "
        "numbers are not publishable."
        if backend_pairs
        else "Every row is an independent matched-round comparison with the "
        "ordinary CoAkka application service. Positive throughput delta favors "
        "CoAkka; negative p99 delta favors CoAkka. Qualification runs are "
        "functional checks and are not publishable results."
    )
    metric_header = (
        "| Pair | Kind | connections | n | Baseline req/s | io_uring req/s | io_uring delta | Baseline p99 ms | io_uring p99 ms | io_uring p99 delta |\n"
        if backend_pairs
        else "| Pair | Kind | c | n | Comparator req/s | CoAkka req/s | CoAkka delta | Comparator p99 ms | CoAkka p99 ms | CoAkka p99 delta |\n"
    )
    resource_header = (
        "| Pair | Baseline RSS KiB | io_uring RSS KiB | Baseline server CPU s | io_uring server CPU s | Baseline load CPU s | io_uring load CPU s |\n"
        if backend_pairs
        else "| Pair | Comparator RSS KiB | CoAkka RSS KiB | Comparator server CPU s | CoAkka server CPU s | Comparator load CPU s | CoAkka load CPU s |\n"
    )
    lines = [
        f"# {report_title}\n\n",
        f"- Campaign status: `{campaign['status']}`\n",
        f"- Mode: `{campaign['mode']}`\n",
        f"- HTTP version: `{campaign['workload'].get('http_version', '1.1')}`\n",
        f"- Parallel requests per connection: `{campaign['workload'].get('parallel_per_connection', 1)}`\n",
        f"- Measured at: `{campaign.get('started_at')}`\n",
        f"- Warmup per sample: `{campaign['warmup_seconds']}s`\n",
        f"- Measurement per sample: `{campaign['duration_seconds']}s`\n",
        f"- Thermal rest policy: `{campaign.get('cooldown')}`\n",
        f"- Source manifest: `{campaign.get('source_manifest_sha256')}`\n",
        f"- Failed or missing samples: `{len(set(failures))}`\n\n",
        f"{explanation}\n\n",
        "## Matched Comparisons\n\n",
        metric_header,
        "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n",
    ]
    for row in rows:
        lines.append(
            "| {pair} | {kind} | {concurrency} | {rounds} | {comparator_rps} | "
            "{coakka_rps} | {throughput_delta}% | {comparator_p99} | "
            "{coakka_p99} | {p99_delta}% |\n".format(
                pair=row["pair"],
                kind=row["kind"],
                concurrency=row["concurrency"],
                rounds=row["matched_rounds"],
                comparator_rps=render(row["comparator_requests_per_second_median"]),
                coakka_rps=render(row["coakka_requests_per_second_median"]),
                throughput_delta=render(row["coakka_throughput_delta_percent_median"]),
                comparator_p99=render(row["comparator_p99_ms_median"], 3),
                coakka_p99=render(row["coakka_p99_ms_median"], 3),
                p99_delta=render(row["coakka_p99_delta_percent_median"]),
            )
        )
    lines.extend([
        "\n## Resource Context\n\n",
        resource_header,
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: |\n",
    ])
    for row in rows:
        lines.append(
            "| {pair} | {comparator_rss} | {coakka_rss} | {comparator_cpu} | "
            "{coakka_cpu} | {comparator_load} | {coakka_load} |\n".format(
                pair=row["pair"],
                comparator_rss=render(row["comparator_rss_peak_kib_median"], 0),
                coakka_rss=render(row["coakka_rss_peak_kib_median"], 0),
                comparator_cpu=render(row["comparator_server_cpu_seconds_median"]),
                coakka_cpu=render(row["coakka_server_cpu_seconds_median"]),
                comparator_load=render(row["comparator_load_cpu_seconds_median"]),
                coakka_load=render(row["coakka_load_cpu_seconds_median"]),
            )
        )
    (evidence / "PAIR_REPORT.md").write_text("".join(lines))
    print(evidence / "PAIR_REPORT.md")
    return 1 if failures or campaign["status"] != "complete" else 0


if __name__ == "__main__":
    raise SystemExit(main())
