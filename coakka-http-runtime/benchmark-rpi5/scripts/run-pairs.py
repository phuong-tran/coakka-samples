#!/usr/bin/env python3
"""Run a matched application or native-backend comparison campaign."""

from __future__ import annotations

import argparse
import json
import platform
import shutil
import sys
from pathlib import Path
from typing import Any

import run as common


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--mode", choices=("qualify", "measure"), default="qualify")
    parser.add_argument("--rounds", type=int)
    parser.add_argument("--duration", type=int)
    parser.add_argument("--warmup", type=int)
    parser.add_argument("--concurrency", type=int, action="append")
    parser.add_argument("--max-start-uptime", type=int, default=900)
    return parser.parse_args()


def validate_config(config: dict[str, Any]) -> None:
    family = config.get("campaign_family")
    if family not in {
        "application-pairs",
        "native-backend-pairs",
        "connector-backend-pairs",
    }:
        raise RuntimeError("pair runner received an unsupported campaign family")
    if not config.get("ecosystem"):
        raise RuntimeError("pair runner requires an ecosystem")
    lanes = config.get("lanes", [])
    lane_ids = [lane.get("id") for lane in lanes]
    if not lane_ids or len(lane_ids) != len(set(lane_ids)) or None in lane_ids:
        raise RuntimeError("lane identifiers must be present and unique")
    pairs = config.get("pairs", [])
    pair_ids = [pair.get("id") for pair in pairs]
    if not pair_ids or len(pair_ids) != len(set(pair_ids)) or None in pair_ids:
        raise RuntimeError("pair identifiers must be present and unique")
    for pair in pairs:
        left_key, right_key = (
            ("comparator", "coakka")
            if family == "application-pairs"
            else ("baseline", "candidate")
        )
        left = pair.get(left_key)
        right = pair.get(right_key)
        if left not in lane_ids or right not in lane_ids:
            raise RuntimeError(f"pair {pair['id']} references an unknown lane")
        if left == right:
            raise RuntimeError(f"pair {pair['id']} compares a lane with itself")
        roles = {lane["id"]: lane.get("role") for lane in lanes}
        if family == "application-pairs":
            if roles[right] != "coakka-application":
                raise RuntimeError(f"pair {pair['id']} has an invalid CoAkka side")
            if roles[left] not in {"direct-http", "framework"}:
                raise RuntimeError(f"pair {pair['id']} has an invalid comparator role")
        elif family == "native-backend-pairs":
            if roles[left] != "native-backend" or roles[right] != "native-backend":
                raise RuntimeError(f"pair {pair['id']} must compare native backends")
        elif roles[left] != "connector-backend" or roles[right] != "connector-backend":
            raise RuntimeError(f"pair {pair['id']} must compare connector backends")
    if family == "application-pairs":
        coakka_lanes = [
            lane for lane in lanes if lane.get("role") == "coakka-application"
        ]
        if len(coakka_lanes) != 1:
            raise RuntimeError("exactly one CoAkka application lane is required")
        ready_expect = coakka_lanes[0].get("ready_expect", {})
        application_path = ready_expect.get("application_path")
        if application_path not in {"normal", "host-inline"}:
            raise RuntimeError(
                "the CoAkka lane must prove a normal or host-inline application path"
            )
        if application_path == "host-inline" and not ready_expect.get(
            "connector_surface"
        ):
            raise RuntimeError(
                "a host-inline CoAkka lane must identify its connector surface"
            )
        minimum_rps = config.get("qualification", {}).get(
            "coakka_min_requests_per_second"
        )
    elif family == "native-backend-pairs":
        if any(lane.get("role") != "native-backend" for lane in lanes):
            raise RuntimeError("native campaigns accept only native-backend lanes")
        if any("startup_proof" not in lane for lane in lanes):
            raise RuntimeError("every native backend lane requires startup proof")
        minimum_rps = config.get("qualification", {}).get(
            "minimum_requests_per_second"
        )
    else:
        if any(lane.get("role") != "connector-backend" for lane in lanes):
            raise RuntimeError("connector backend campaigns accept only connector-backend lanes")
        for pair in pairs:
            baseline = next(lane for lane in lanes if lane["id"] == pair["baseline"])
            candidate = next(lane for lane in lanes if lane["id"] == pair["candidate"])
            if baseline.get("ready_expect", {}).get("io_uring_active") is not False:
                raise RuntimeError("connector backend baseline must prove io_uring is inactive")
            if candidate.get("ready_expect", {}).get("io_uring_active") is not True:
                raise RuntimeError("connector backend candidate must prove io_uring is active")
        minimum_rps = config.get("qualification", {}).get(
            "minimum_requests_per_second"
        )
    if not isinstance(minimum_rps, (int, float)) or minimum_rps <= 0:
        raise RuntimeError("a positive CoAkka qualification throughput floor is required")
    cooldown = config.get("cooldown")
    if not isinstance(cooldown, dict):
        raise RuntimeError("a bounded cooldown policy is required")


def snapshot_sources(root: Path, output: Path) -> None:
    manifest = root / "build/source.sha256"
    if not manifest.is_file():
        raise RuntimeError("source manifest is missing; run prepare-rpi5.sh first")
    destination_root = output / "source"
    if destination_root.exists():
        raise RuntimeError(f"refusing to overwrite source snapshot: {destination_root}")
    for line in manifest.read_text(encoding="ascii").splitlines():
        expected, relative = line.split("  ", 1)
        source = root / relative
        if not source.is_file() or common.sha256(source) != expected:
            raise RuntimeError(f"source changed after preparation: {relative}")
        destination = destination_root / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)

    artifact_lock = json.loads((root / "config/artifacts.lock.json").read_text())
    archive_root = output / "source-archives"
    for artifact in artifact_lock["artifacts"]:
        if artifact.get("type") != "git-archive":
            continue
        source = root / "artifacts" / artifact["destination"]
        if not source.is_file() or common.sha256(source) != artifact["sha256"]:
            raise RuntimeError(f"locked source archive changed: {artifact['id']}")
        destination = archive_root / Path(artifact["destination"]).name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)


def qualification_throughput(output: Path, record: dict[str, Any]) -> float:
    raw_path = output / record["measurement"]["raw"]
    raw = json.loads(raw_path.read_text())
    return float(raw["summary"]["requestsPerSec"])


def main() -> int:
    args = parse_args()
    root = Path(__file__).resolve().parent.parent
    config_path = args.config.resolve()
    config = json.loads(config_path.read_text())
    validate_config(config)

    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    campaign_path = output / "campaign.json"
    if campaign_path.exists():
        raise RuntimeError(f"refusing to overwrite campaign: {campaign_path}")

    rounds = args.rounds or (1 if args.mode == "qualify" else 9)
    duration = args.duration or (2 if args.mode == "qualify" else 30)
    warmup = args.warmup or (1 if args.mode == "qualify" else 30)
    concurrencies = args.concurrency or [8]
    lanes = config["lanes"]
    if rounds < 1 or duration < 1 or warmup < 1:
        raise RuntimeError("campaign dimensions must be positive")
    if any(value < 1 or value > 256 for value in concurrencies):
        raise RuntimeError("concurrency must be in [1, 256]")

    uptime = float(Path("/proc/uptime").read_text().split()[0])
    governors = common.read_governors()
    if platform.machine() != "aarch64":
        raise RuntimeError("campaign requires aarch64")
    if args.mode == "measure":
        if uptime > args.max_start_uptime:
            raise RuntimeError(
                f"host uptime {uptime:.0f}s exceeds {args.max_start_uptime}s; reboot first"
            )
        if not governors or set(governors.values()) != {"performance"}:
            raise RuntimeError(f"performance governor is not active: {governors}")
        if not (output / "host-control/quiesced").is_file():
            raise RuntimeError("measure mode requires recorded host quiescence")

    snapshot_sources(root, output)
    environment = common.capture_environment(root, output)
    campaign: dict[str, Any] = {
        "schema_version": 1,
        "campaign_family": config["campaign_family"],
        "ecosystem": config["ecosystem"],
        "status": "running",
        "mode": args.mode,
        "started_at": common.utc_now(),
        "host_uptime_seconds_at_start": uptime,
        "governors": governors,
        "rounds": rounds,
        "duration_seconds": duration,
        "warmup_seconds": warmup,
        "concurrencies": concurrencies,
        "server_cpu_set": config["server_cpu_set"],
        "load_cpu_set": config["load_cpu_set"],
        "workload": config["workload"],
        "lanes": lanes,
        "pairs": config["pairs"],
        "cooldown": config["cooldown"],
        "config": str(config_path.relative_to(root)),
        "source_manifest_sha256": environment["digests"].get("build/source.sha256"),
        "body_validation_scope": "exact response before warmup and after measurement",
        "qualification_checks": [],
        "samples": [],
    }
    common.write_json(campaign_path, campaign)
    try:
        for concurrency in concurrencies:
            for round_number in range(1, rounds + 1):
                offset = (round_number - 1) % len(lanes)
                order = lanes[offset:] + lanes[:offset]
                for lane in order:
                    run_id = (
                        f"r{round_number:02d}-{lane['id']}-c{concurrency:03d}"
                    )
                    cooldown = common.wait_for_cooldown(
                        output,
                        run_id,
                        config["cooldown"],
                    )
                    print(
                        f"[{common.utc_now()}] {lane['id']} round={round_number} "
                        f"concurrency={concurrency} "
                        f"cooled={cooldown['elapsed_seconds']:.1f}s "
                        f"temperature={cooldown['final_temperature_millicelsius'] / 1000:.1f}C",
                        flush=True,
                    )
                    record = common.run_sample(
                        root,
                        output,
                        lane,
                        config["workload"],
                        config["server_cpu_set"],
                        config["load_cpu_set"],
                        round_number,
                        concurrency,
                        warmup,
                        duration,
                        cooldown,
                    )
                    campaign["samples"].append(record["run_id"])
                    qualifies = lane.get("role") in {
                        "coakka-application",
                        "native-backend",
                        "connector-backend",
                    }
                    if args.mode == "qualify" and qualifies:
                        actual_rps = qualification_throughput(output, record)
                        floor_key = (
                            "coakka_min_requests_per_second"
                            if config["campaign_family"] == "application-pairs"
                            else "minimum_requests_per_second"
                        )
                        minimum_rps = float(config["qualification"][floor_key])
                        check = {
                            "lane": lane["id"],
                            "round": round_number,
                            "concurrency": concurrency,
                            "minimum_requests_per_second": minimum_rps,
                            "actual_requests_per_second": actual_rps,
                            "status": "pass" if actual_rps >= minimum_rps else "fail",
                        }
                        campaign["qualification_checks"].append(check)
                        if actual_rps < minimum_rps:
                            raise RuntimeError(
                                f"{lane['id']} qualification throughput {actual_rps:.2f} "
                                f"is below the {minimum_rps:.2f} req/s sanity floor"
                            )
                    common.write_json(campaign_path, campaign)
        campaign["status"] = "complete"
        campaign["finished_at"] = common.utc_now()
        common.write_json(campaign_path, campaign)
        return 0
    except BaseException as error:
        campaign["status"] = "failed"
        campaign["error"] = f"{type(error).__name__}: {error}"
        campaign["finished_at"] = common.utc_now()
        common.write_json(campaign_path, campaign)
        raise


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("campaign interrupted", file=sys.stderr)
        raise SystemExit(130)
