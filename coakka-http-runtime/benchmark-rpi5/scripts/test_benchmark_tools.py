#!/usr/bin/env python3
"""Exercise benchmark parsers and fail-closed evidence validation."""

from __future__ import annotations

import copy
import importlib.util
import json
import sys
import tempfile
import unittest
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator


SCRIPT_DIRECTORY = Path(__file__).resolve().parent


def load_script(name: str, filename: str) -> Any:
    """Load one adjacent script without changing its public command-line shape."""
    spec = importlib.util.spec_from_file_location(name, SCRIPT_DIRECTORY / filename)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"could not load {filename}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


RUNNER = load_script("benchmark_runner", "run-rpi5.py")
SUMMARIZER = load_script("benchmark_summarizer", "summarize.py")


def measurement(lane_id: str, rps: float) -> dict[str, Any]:
    """Return one internally consistent reduced lane measurement."""
    return {
        "lane_id": lane_id,
        "round": 1,
        "throttled": "throttled=0x0",
        "calibration_temperature_after_cooldown_c": 40.0,
        "calibration_cpu_busy_after_cooldown_percent": 1.0,
        "temperature_after_cooldown_c": 41.0,
        "cpu_busy_after_cooldown_percent": 2.0,
        "measurement_requests": 100,
        "requests_total": 100,
        "requests_started": 100,
        "requests_done": 100,
        "requests_succeeded": 100,
        "requests_failed": 0,
        "requests_errored": 0,
        "requests_timed_out": 0,
        "requests_per_second": rps,
        "request_time_mean_ms": 1.0,
        "request_time_p99_ms": 2.0,
        "server_process_count": 1,
        "server_rss_kib": 1024,
        "server_cpu_percent": 90.0,
        "load_cpu_busy_percent": 50.0,
    }


def campaign() -> dict[str, Any]:
    """Return the smallest complete campaign accepted by the summarizer."""
    return {
        "schema_version": 1,
        "complete": True,
        "workload": {
            "method": "GET",
            "path": "/fixed",
            "status": 200,
            "body": "0123456789abcdef0123456789abcdef",
            "concurrency": 64,
            "calibration_requests": 20_000,
            "duration_seconds": 10,
            "rounds": 1,
            "random_seed": 20260930,
            "cooldown_minimum_seconds": 15,
            "cooldown_maximum_c": 50.0,
            "cooldown_maximum_cpu_busy_percent": 5.0,
            "load_cpu_maximum_busy_percent": 90.0,
            "coakka_event_loop_threads": 1,
            "io_uring": False,
            "server_cpus": "0-2",
            "load_cpu": "3",
        },
        "machine": {
            "throttled_before": "throttled=0x0",
            "throttled_after": "throttled=0x0",
            "governor_before": {"cpu0": "ondemand"},
            "governor_during": {"cpu0": "performance"},
            "governor_after": {"cpu0": "ondemand"},
            "temperature_after_initial_cooldown_c": 39.0,
            "cpu_busy_after_initial_cooldown_percent": 1.0,
            "model": "Raspberry Pi 5 Model B Rev 1.0",
            "architecture": "aarch64",
            "cpu_model": "Cortex-A76",
            "cpu_count": 4,
            "memory_kib": 8 * 1024 * 1024,
            "operating_system": "Debian GNU/Linux",
            "operating_system_codename": "trixie",
            "kernel": "6.12-test",
            "source_manifest_sha256": "a" * 64,
        },
        "lanes": [
            {
                "id": "coakka",
                "ecosystem": "Go",
                "implementation": "CoAkka host-inlined",
                "role": "coakka",
            },
            {
                "id": "framework",
                "ecosystem": "Go",
                "implementation": "Framework",
                "role": "framework",
            },
        ],
        "measurements": [measurement("coakka", 1000.0), measurement("framework", 800.0)],
    }


@contextmanager
def summarize_fixture(value: dict[str, Any]) -> Iterator[Path]:
    """Run the real summary entry point against disposable evidence."""
    with tempfile.TemporaryDirectory(prefix="coakka-http-benchmark-test-") as directory:
        root = Path(directory)
        source = root / "campaign.json"
        output = root / "RESULTS.md"
        source.write_text(json.dumps(value))
        original_arguments = sys.argv
        sys.argv = ["summarize.py", str(source), "--output", str(output)]
        try:
            SUMMARIZER.main()
            yield output
        finally:
            sys.argv = original_arguments


class BenchmarkToolsTest(unittest.TestCase):
    """Keep request parsing and release evidence rejection deterministic."""

    def test_machine_validation_requires_trixie(self) -> None:
        value = campaign()
        facts = value["machine"]
        facts["governor_before"] = {
            f"cpu{index}": "ondemand" for index in range(4)
        }
        RUNNER.validate_machine(facts, value["workload"])
        facts["operating_system_codename"] = "bookworm"
        with self.assertRaisesRegex(RuntimeError, "Trixie"):
            RUNNER.validate_machine(facts, value["workload"])

    def test_h2load_parser_requires_exact_accounting(self) -> None:
        raw = (
            "finished in 1.00s, 100.00 req/s\n"
            "time for request: 100us 3ms 1ms 200us\n"
            "requests: 100 total, 100 started, 100 done, 100 succeeded, "
            "0 failed, 0 errored, 0 timeout\n"
        )
        parsed = RUNNER.validate_load("lane", 100, 0, raw)
        self.assertEqual(100, parsed["requests_succeeded"])
        with self.assertRaises(RuntimeError):
            RUNNER.validate_load("lane", 101, 0, raw)

    def test_calibration_and_measurement_use_the_same_request_logging(self) -> None:
        value = campaign()
        log = Path("calibration-requests.tsv")
        command = RUNNER.h2load_command(value["workload"], 100, 8080, log)
        self.assertEqual("--log-file", command[-3])
        self.assertEqual(str(log), command[-2])

    def test_load_cpu_accounting_requires_headroom(self) -> None:
        self.assertEqual(90.0, RUNNER.busy_percent_between((100, 50), (200, 60)))
        with self.assertRaisesRegex(RuntimeError, "not monotonic"):
            RUNNER.busy_percent_between((200, 60), (100, 50))
        value = campaign()
        value["measurements"][0]["load_cpu_busy_percent"] = 95.0
        with self.assertRaisesRegex(ValueError, "load generator was saturated"):
            with summarize_fixture(value):
                pass

    def test_checked_in_configuration_is_valid(self) -> None:
        config = json.loads((SCRIPT_DIRECTORY.parent / "config/lanes.json").read_text())
        RUNNER.validate_configuration(config["workload"], config["lanes"])

    def test_framework_campaign_rejects_io_uring_or_cpu_budget_drift(self) -> None:
        config = json.loads((SCRIPT_DIRECTORY.parent / "config/lanes.json").read_text())
        io_uring = copy.deepcopy(config)
        io_uring["workload"]["io_uring"] = True
        with self.assertRaisesRegex(ValueError, "io_uring"):
            RUNNER.validate_configuration(io_uring["workload"], io_uring["lanes"])
        event_loops = copy.deepcopy(config)
        event_loops["workload"]["coakka_event_loop_threads"] = 2
        with self.assertRaisesRegex(ValueError, "event-loop"):
            RUNNER.validate_configuration(
                event_loops["workload"], event_loops["lanes"]
            )

    def test_cooldown_and_load_headroom_cannot_be_weakened(self) -> None:
        config = json.loads((SCRIPT_DIRECTORY.parent / "config/lanes.json").read_text())
        for field, weakened in (
            ("cooldown_minimum_seconds", 14),
            ("cooldown_maximum_c", 51.0),
            ("cooldown_maximum_cpu_busy_percent", 6.0),
            ("load_cpu_maximum_busy_percent", 91.0),
        ):
            with self.subTest(field=field):
                workload = dict(config["workload"])
                workload[field] = weakened
                with self.assertRaisesRegex(ValueError, field):
                    RUNNER.validate_configuration(workload, config["lanes"])

    def test_complete_campaign_renders_a_relative_table(self) -> None:
        with summarize_fixture(campaign()) as output:
            rendered = output.read_text()
        self.assertIn("| CoAkka host-inlined | 1 | 1,000 | 1,000-1,000 | 1.00x |", rendered)
        self.assertIn("| Framework | 1 | 800 | 800-800 | 0.80x |", rendered)
        self.assertIn("| Model | Raspberry Pi 5 Model B Rev 1.0 |", rendered)
        self.assertIn("| CoAkka event loops | 1 |", rendered)

    def test_incomplete_campaign_is_rejected(self) -> None:
        value = campaign()
        value["complete"] = False
        with self.assertRaisesRegex(ValueError, "incomplete"):
            with summarize_fixture(value):
                pass

    def test_request_accounting_mismatch_is_rejected(self) -> None:
        value = campaign()
        value["measurements"][0]["requests_succeeded"] = 99
        with self.assertRaisesRegex(ValueError, "request accounting"):
            with summarize_fixture(value):
                pass

    def test_busy_cooldown_is_rejected(self) -> None:
        value = campaign()
        value["measurements"][0]["cpu_busy_after_cooldown_percent"] = 6.0
        with self.assertRaisesRegex(ValueError, "CPU-idle gate"):
            with summarize_fixture(value):
                pass

    def test_non_performance_governor_is_rejected(self) -> None:
        value = campaign()
        value["machine"]["governor_during"]["cpu0"] = "ondemand"
        with self.assertRaisesRegex(ValueError, "performance governor"):
            with summarize_fixture(value):
                pass

    def test_non_finite_measurement_is_rejected(self) -> None:
        value = campaign()
        value["measurements"][0]["requests_per_second"] = float("nan")
        with self.assertRaisesRegex(ValueError, "numeric evidence"):
            with summarize_fixture(value):
                pass

    def test_missing_round_is_rejected(self) -> None:
        value = campaign()
        value["workload"]["rounds"] = 2
        with self.assertRaisesRegex(ValueError, "one result per round"):
            with summarize_fixture(value):
                pass


if __name__ == "__main__":
    unittest.main()
