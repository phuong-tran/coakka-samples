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
from types import SimpleNamespace
from typing import Any, Iterator
from unittest import mock


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
    request_count = int(rps * 10)
    return {
        "lane_id": lane_id,
        "round": 1,
        "throttled": "throttled=0x0",
        "temperature_after_cooldown_c": 41.0,
        "cpu_busy_after_cooldown_percent": 2.0,
        "benchmark_duration_ms": 15000.0,
        "load_wall_seconds": 15.0,
        "measurement_requests": request_count,
        "requests_total": request_count,
        "requests_started": request_count,
        "requests_done": request_count,
        "requests_succeeded": request_count,
        "requests_failed": 0,
        "requests_errored": 0,
        "requests_timed_out": 0,
        "responses_2xx": request_count,
        "responses_3xx": 0,
        "responses_4xx": 0,
        "responses_5xx": 0,
        "requests_per_second": rps,
        "request_time_mean_ms": 1.0,
        "request_time_p99_ms": 2.0,
        "server_process_count": 1,
        "server_rss_kib": 1024,
        "server_cpu_percent": 90.0,
        "load_cpu_busy_percent": 50.0,
        "load_cpu_busy_percent_by_core": {
            "cpu1": 30.0,
            "cpu2": 50.0,
            "cpu3": 40.0,
        },
    }


def campaign() -> dict[str, Any]:
    """Return the smallest complete campaign accepted by the summarizer."""
    return {
        "schema_version": 2,
        "complete": True,
        "workload": {
            "method": "GET",
            "path": "/fixed",
            "status": 200,
            "body": "0123456789abcdef0123456789abcdef",
            "concurrency": 64,
            "warmup_seconds": 5,
            "max_measurement_requests": 2_000_000,
            "duration_seconds": 10,
            "rounds": 1,
            "random_seed": 20260930,
            "cooldown_minimum_seconds": 15,
            "cooldown_maximum_c": 50.0,
            "cooldown_maximum_cpu_busy_percent": 5.0,
            "load_cpu_maximum_busy_percent": 90.0,
            "coakka_event_loop_threads": 1,
            "io_uring": False,
            "server_cpus": "0",
            "load_cpus": "1-3",
            "load_threads": 3,
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

    def test_sudo_lease_refresh_is_noninteractive_and_fails_closed(self) -> None:
        with mock.patch.object(RUNNER.subprocess, "run") as run:
            run.return_value.returncode = 0
            RUNNER.refresh_sudo_lease()
            run.assert_called_once_with(
                ["sudo", "-n", "-v"],
                check=False,
                capture_output=True,
                text=True,
            )
            run.return_value.returncode = 1
            with self.assertRaisesRegex(RuntimeError, "not authenticated"):
                RUNNER.refresh_sudo_lease()

    def test_cooldown_refreshes_lease_outside_measured_work(self) -> None:
        keepalive = mock.Mock()
        with (
            mock.patch.object(RUNNER.time, "sleep") as sleep,
            mock.patch.object(RUNNER, "temperature_c", return_value=40.0),
            mock.patch.object(RUNNER, "throttled", return_value="throttled=0x0"),
            mock.patch.object(RUNNER, "cpu_busy_percent", return_value=2.0),
        ):
            self.assertEqual((40.0, 2.0), RUNNER.wait_until_cool(50.0, 15, 5.0, keepalive))
        sleep.assert_called_once_with(15)
        keepalive.assert_called_once_with()

    def test_governor_commands_never_prompt_for_sudo(self) -> None:
        with (
            mock.patch.object(RUNNER.Path, "glob", return_value=[]),
            mock.patch.object(RUNNER.subprocess, "run") as run,
        ):
            RUNNER.set_governor("performance")
            RUNNER.restore_governors({})
        self.assertEqual(2, run.call_count)
        for call in run.call_args_list:
            self.assertEqual(["sudo", "-n"], call.args[0][:2])

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
        timing = "".join(
            f"spawning thread #{index}: Timing-based test with 5s of warm-up "
            "time and 10s of main duration\n"
            f"Main benchmark duration is started for thread #{index}\n"
            f"Main benchmark duration is over for thread #{index}\n"
            for index in range(3)
        )
        raw = timing + (
            "finished in 15.00s, 10.00 req/s\n"
            "time for request: 100us 3ms 1ms 200us\n"
            "requests: 100 total, 100 started, 100 done, 100 succeeded, "
            "0 failed, 0 errored, 0 timeout\n"
            "status codes: 100 2xx, 0 3xx, 0 4xx, 0 5xx\n"
        )
        workload = campaign()["workload"]
        parsed = RUNNER.validate_load("lane", workload, 0, raw)
        self.assertEqual(100, parsed["requests_succeeded"])
        self.assertEqual(100, parsed["responses_2xx"])
        with self.assertRaisesRegex(RuntimeError, "timing interval"):
            RUNNER.validate_load("lane", workload, 0, raw.replace("15.00s", "3.00s"))
        with self.assertRaisesRegex(RuntimeError, "timing interval"):
            RUNNER.validate_load("lane", workload, 0, raw.replace(timing, ""))
        with self.assertRaisesRegex(RuntimeError, "one successful 2xx"):
            RUNNER.validate_load(
                "lane", workload, 0, raw.replace("10.00 req/s", "100.00 req/s")
            )
        for inconsistent in (
            raw.replace("100 2xx", "101 2xx"),
            raw.replace("100 2xx", "99 2xx"),
            raw.replace("0 4xx", "1 4xx"),
        ):
            with self.subTest(inconsistent=inconsistent):
                with self.assertRaisesRegex(RuntimeError, "one successful 2xx"):
                    RUNNER.validate_load("lane", workload, 0, inconsistent)
        with self.assertRaisesRegex(ValueError, "could not parse"):
            RUNNER.validate_load("lane", workload, 0, raw.split("status codes:")[0])

    def test_lane_must_reuse_one_http1_connection(self) -> None:
        connection = mock.MagicMock()
        connection.sock = object()
        response = mock.MagicMock()
        response.status = 200
        response.getheader.return_value = "application/octet-stream"
        response.read.return_value = RUNNER.BODY
        response.will_close = False
        connection.getresponse.return_value = response
        with mock.patch.object(RUNNER.http.client, "HTTPConnection", return_value=connection):
            RUNNER.require_persistent_http1(8080, "framework")
            self.assertEqual(2, connection.request.call_count)
            response.will_close = True
            with self.assertRaisesRegex(RuntimeError, "persistent HTTP/1.1"):
                RUNNER.require_persistent_http1(8080, "framework")

    def test_warmup_and_measurement_share_connections_and_request_logging(self) -> None:
        value = campaign()
        log = Path("measured-requests.tsv")
        command = RUNNER.h2load_command(value["workload"], 8080, log)
        self.assertEqual("--log-file", command[-3])
        self.assertEqual(str(log), command[-2])
        self.assertEqual(["taskset", "-c", "1-3", "h2load"], command[:4])
        self.assertEqual("3", command[command.index("-t") + 1])
        self.assertIn("--duration=10s", command)
        self.assertIn("--warm-up-time=5s", command)
        self.assertNotIn("-n", command)

    def test_request_timing_log_requires_bounded_tmpfs(self) -> None:
        enough = SimpleNamespace(f_bavail=1024, f_frsize=1024 * 1024)
        too_small = SimpleNamespace(f_bavail=1, f_frsize=1024 * 1024)
        with (
            mock.patch.object(
                RUNNER.Path, "read_text", return_value="tmpfs /dev/shm tmpfs rw 0 0\n"
            ),
            mock.patch.object(RUNNER.os, "statvfs", return_value=enough),
        ):
            self.assertEqual(Path("/dev/shm"), RUNNER.require_request_log_tmpfs(2_000_000))
        with (
            mock.patch.object(
                RUNNER.Path, "read_text", return_value="/dev/sda2 /dev/shm ext4 rw 0 0\n"
            ),
            mock.patch.object(RUNNER.os, "statvfs", return_value=enough),
        ):
            with self.assertRaisesRegex(RuntimeError, "tmpfs"):
                RUNNER.require_request_log_tmpfs(2_000_000)
        with (
            mock.patch.object(
                RUNNER.Path, "read_text", return_value="tmpfs /dev/shm tmpfs rw 0 0\n"
            ),
            mock.patch.object(RUNNER.os, "statvfs", return_value=too_small),
        ):
            with self.assertRaisesRegex(RuntimeError, "requires at least"):
                RUNNER.require_request_log_tmpfs(2_000_000)

    def test_load_cpu_accounting_requires_headroom(self) -> None:
        self.assertEqual(90.0, RUNNER.busy_percent_between((100, 50), (200, 60)))
        with self.assertRaisesRegex(RuntimeError, "not monotonic"):
            RUNNER.busy_percent_between((200, 60), (100, 50))
        busiest, by_core = RUNNER.busiest_cpu_busy_percent(
            {"cpu2": (100, 50), "cpu3": (100, 50)},
            {"cpu2": (200, 55), "cpu3": (200, 100)},
        )
        self.assertEqual(95.0, busiest)
        self.assertEqual({"cpu2": 95.0, "cpu3": 50.0}, by_core)
        with self.assertRaisesRegex(RuntimeError, "inventory changed"):
            RUNNER.busiest_cpu_busy_percent(
                {"cpu2": (100, 50), "cpu3": (100, 50)},
                {"cpu2": (200, 55)},
            )
        value = campaign()
        value["measurements"][0]["load_cpu_busy_percent"] = 95.0
        value["measurements"][0]["load_cpu_busy_percent_by_core"]["cpu2"] = 95.0
        with self.assertRaisesRegex(ValueError, "load generator was saturated"):
            with summarize_fixture(value):
                pass

    def test_summary_rejects_hidden_saturated_load_core(self) -> None:
        value = campaign()
        value["measurements"][0]["load_cpu_busy_percent_by_core"]["cpu2"] = 95.0
        with self.assertRaisesRegex(ValueError, "inconsistent load-core evidence"):
            with summarize_fixture(value):
                pass

    def test_checked_in_configuration_is_valid(self) -> None:
        config = json.loads((SCRIPT_DIRECTORY.parent / "config/lanes.json").read_text())
        RUNNER.validate_configuration(config["workload"], config["lanes"])

    def test_framework_lanes_cannot_fall_back_to_system_toolchains(self) -> None:
        config = json.loads((SCRIPT_DIRECTORY.parent / "config/lanes.json").read_text())
        for lane_id, replacement, message in (
            ("node-coakka", "node", "Node.js lane must use"),
            ("bun-elysia", "bun", "Bun lane must use"),
        ):
            with self.subTest(lane_id=lane_id):
                changed = copy.deepcopy(config)
                lane = next(item for item in changed["lanes"] if item["id"] == lane_id)
                lane["command"][0] = replacement
                with self.assertRaisesRegex(ValueError, message):
                    RUNNER.validate_configuration(changed["workload"], changed["lanes"])
        changed = copy.deepcopy(config)
        lane = next(item for item in changed["lanes"] if item["id"] == "kotlin-coakka")
        lane["environment"]["JAVA_HOME"] = "/usr/lib/jvm/java-17-openjdk-arm64"
        with self.assertRaisesRegex(ValueError, "Trixie JDK 21"):
            RUNNER.validate_configuration(changed["workload"], changed["lanes"])

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
        threads = copy.deepcopy(config)
        threads["workload"]["load_threads"] = 1
        with self.assertRaisesRegex(ValueError, "three load-generator threads"):
            RUNNER.validate_configuration(threads["workload"], threads["lanes"])
        facts = campaign()["machine"]
        facts["governor_before"] = {
            f"cpu{index}": "ondemand" for index in range(4)
        }
        one_load_cpu = copy.deepcopy(config["workload"])
        one_load_cpu["load_cpus"] = "3"
        with self.assertRaisesRegex(ValueError, "partition"):
            RUNNER.validate_machine(facts, one_load_cpu)

    def test_cooldown_and_load_headroom_cannot_be_weakened(self) -> None:
        config = json.loads((SCRIPT_DIRECTORY.parent / "config/lanes.json").read_text())
        for field, weakened in (
            ("warmup_seconds", 4),
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

    def test_superseded_campaign_schema_is_rejected(self) -> None:
        value = campaign()
        value["schema_version"] = 1
        with self.assertRaisesRegex(ValueError, "schema"):
            with summarize_fixture(value):
                pass

    def test_request_accounting_mismatch_is_rejected(self) -> None:
        value = campaign()
        value["measurements"][0]["requests_succeeded"] = 99
        with self.assertRaisesRegex(ValueError, "request accounting"):
            with summarize_fixture(value):
                pass

    def test_response_status_accounting_mismatch_is_rejected(self) -> None:
        value = campaign()
        value["measurements"][0]["responses_2xx"] = 101
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

    def test_short_duration_is_rejected_by_summary(self) -> None:
        value = campaign()
        value["measurements"][0]["benchmark_duration_ms"] = 3000.0
        with self.assertRaisesRegex(ValueError, "measurement duration"):
            with summarize_fixture(value):
                pass


if __name__ == "__main__":
    unittest.main()
