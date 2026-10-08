#!/usr/bin/env python3
"""Exercise benchmark parsers and fail-closed evidence validation."""

from __future__ import annotations

import copy
import importlib.util
import json
import re
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


class HeadroomStudyTests(unittest.TestCase):
    """The opt-in study records refusals; strict capacity evidence stays closed."""

    def test_strict_still_refuses_interval_saturation(self):
        metrics = {"load_cpu_busy_percent": 70., "load_cpu_interval_peak_percent": 95.}
        with self.assertRaisesRegex(RuntimeError, "saturated"):
            RUNNER.classify_headroom(metrics, {"load_cpu_maximum_busy_percent": 90.})
        self.assertFalse(metrics["generator_headroom"]["passed"])

    def test_study_preserves_limit_and_classifies(self):
        for observed, expected in ((90., "no-generator-saturation-observed"), (95., "client-limited")):
            metrics = {"load_cpu_busy_percent": observed, "load_cpu_interval_peak_percent": observed}
            RUNNER.classify_headroom(metrics, {"load_cpu_maximum_busy_percent": 90.,
                                              "study_mode": "client-limited-loopback"})
            self.assertEqual(expected, metrics["capacity_classification"])
            self.assertEqual(90., metrics["generator_headroom"]["maximum_busy_percent"])

    def test_study_cannot_enter_strict_schema(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary)
            RUNNER.write_campaign(path, {"study_mode": "client-limited-loopback"}, {}, [], [], True)
            value = json.loads((path / "campaign.json").read_text())
            self.assertEqual(3, value["schema_version"])
            self.assertEqual("client-limited-loopback", value["evidence_kind"])


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
        "load_cpu_interval_samples": [
            {"start_seconds": index, "end_seconds": index + 1,
             "busy_by_cpu": {"cpu1": 40.0, "cpu2": 50.0, "cpu3": 40.0}}
            for index in range(14)],
        "load_cpu_interval_peak_percent": 50.0,
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
        "request_time_p50_ms": 1.0,
        "request_time_p95_ms": 1.8,
        "request_time_p99_ms": 2.0,
        "server_process_count": 1,
        "server_rss_kib": 1024,
        "server_rss_mean_kib": 1000,
        "server_rss_peak_kib": 1200,
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
            "cooldown_minimum_seconds": 30,
            "cooldown_consecutive_samples": 3,
            "cooldown_maximum_c": 50.0,
            "cooldown_maximum_cpu_busy_percent": 5.0,
            "load_cpu_maximum_busy_percent": 90.0,
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
def summarize_fixture(value: dict[str, Any], *, study: bool = False) -> Iterator[Path]:
    """Run the real summary entry point against disposable evidence."""
    with tempfile.TemporaryDirectory(prefix="coakka-http-benchmark-test-") as directory:
        root = Path(directory)
        source = root / "campaign.json"
        output = root / "RESULTS.md"
        source.write_text(json.dumps(value))
        original_arguments = sys.argv
        sys.argv = ["summarize.py", str(source), "--output", str(output)]
        if study:
            sys.argv.append("--study")
        try:
            SUMMARIZER.main()
            yield output
        finally:
            sys.argv = original_arguments


class BenchmarkToolsTest(unittest.TestCase):
    """Keep request parsing and release evidence rejection deterministic."""

    def test_mapped_package_identity_rejects_stale_cache_and_framework_leak(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "evidence/locks").mkdir(parents=True)
            package = root / "package"
            package.mkdir()
            library = package / "libcoakka_http_host.so.1"
            library.write_bytes(b"qualified-current-core")
            (root / "evidence/locks/package-paths.json").write_text(json.dumps({"go": str(package)}))
            installed = root / "old/libcoakka_http_host.so.1"
            installed.parent.mkdir()
            installed.write_bytes(b"obsolete-core")
            read_text = Path.read_text

            def mapped_text(path, *args, **kwargs):
                if str(path) == "/proc/123/maps":
                    return f"1000-2000 r-xp 00000000 00:00 1 {installed}\n"
                return read_text(path, *args, **kwargs)

            lane = {"id": "go-coakka", "role": "coakka", "ecosystem": "Go"}
            with mock.patch.object(RUNNER, "process_group_metrics", return_value={123: ()}), \
                    mock.patch.object(Path, "read_text", mapped_text):
                with self.assertRaisesRegex(ValueError, "mapped package identity mismatch"):
                    RUNNER.verify_loaded_package(root, lane, 123)
                installed.write_bytes(library.read_bytes())
                self.assertEqual(1, len(RUNNER.verify_loaded_package(root, lane, 123)))
                lane["role"] = "framework"
                with self.assertRaisesRegex(ValueError, "mapped package identity mismatch"):
                    RUNNER.verify_loaded_package(root, lane, 123)

    def test_mapped_runtime_must_be_present(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "evidence/locks").mkdir(parents=True)
            package = root / "package"
            package.mkdir()
            (package / "libcoakka_http_runtime.so.1").write_bytes(b"qualified-core")
            (root / "evidence/locks/package-paths.json").write_text(json.dumps({"native": str(package)}))
            lane = {"id": "c-coakka", "role": "coakka", "ecosystem": "C"}
            with mock.patch.object(RUNNER, "process_group_metrics", return_value={}):
                with self.assertRaisesRegex(ValueError, "not observed"):
                    RUNNER.verify_loaded_package(root, lane, 123)

    def test_study_report_preserves_saturation_and_refuses_false_classification(self):
        value = campaign()
        value.update(schema_version=3, evidence_kind="client-limited-loopback")
        value["workload"]["study_mode"] = "client-limited-loopback"
        for item in value["measurements"]:
            item["load_cpu_interval_samples"][-1]["busy_by_cpu"]["cpu2"] = 99.
            item["load_cpu_interval_peak_percent"] = 99.
            RUNNER.classify_headroom(item, value["workload"])
        with self.assertRaisesRegex(ValueError, "schema"):
            with summarize_fixture(value):
                pass
        tampered = copy.deepcopy(value)
        tampered["schema_version"] = 2
        with self.assertRaisesRegex(ValueError, "promoted"):
            with summarize_fixture(tampered):
                pass
        with summarize_fixture(value, study=True) as output:
            text = output.read_text()
            self.assertIn("Client-Limited Loopback Study", text)
            self.assertIn("not ranked (study)", text)
            self.assertIn("client-limited | 99.00 | 90.00", text)
        value["measurements"][0]["generator_headroom"]["passed"] = True
        with self.assertRaisesRegex(ValueError, "classification"):
            with summarize_fixture(value, study=True):
                pass

    def test_study_does_not_waive_http_or_interval_evidence(self):
        value = campaign()
        value.update(schema_version=3, evidence_kind="client-limited-loopback")
        value["workload"]["study_mode"] = "client-limited-loopback"
        for item in value["measurements"]:
            RUNNER.classify_headroom(item, value["workload"])
        broken = copy.deepcopy(value)
        broken["measurements"][0]["requests_failed"] = 1
        with self.assertRaisesRegex(ValueError, "accounting"):
            with summarize_fixture(broken, study=True):
                pass
        value["measurements"][0]["load_cpu_interval_samples"] = []
        with self.assertRaisesRegex(ValueError, "missing"):
            with summarize_fixture(value, study=True):
                pass

    def test_generator_intervals_reject_hidden_peak_and_missing_tail(self) -> None:
        value = measurement("native-coakka", 1000)
        cores = {"cpu1", "cpu2", "cpu3"}
        SUMMARIZER.validate_generator_intervals(value, cores, 90)
        value["load_cpu_interval_samples"][-1]["busy_by_cpu"]["cpu2"] = 99
        value["load_cpu_interval_peak_percent"] = 99
        with self.assertRaisesRegex(ValueError, "saturated"):
            SUMMARIZER.validate_generator_intervals(value, cores, 90)
        value = measurement("native-coakka", 1000)
        value["load_cpu_interval_samples"] = value["load_cpu_interval_samples"][:5]
        with self.assertRaisesRegex(ValueError, "cover the load window"):
            SUMMARIZER.validate_generator_intervals(value, cores, 90)
        value["load_cpu_interval_samples"] = []
        with self.assertRaisesRegex(ValueError, "missing"):
            SUMMARIZER.validate_generator_intervals(value, cores, 90)

    def test_rss_is_time_weighted_and_peak_is_sampled(self) -> None:
        values = [{"elapsed_seconds": 0, "server_rss_kib": 100},
                  {"elapsed_seconds": 1, "server_rss_kib": 200},
                  {"elapsed_seconds": 3, "server_rss_kib": 100}]
        result = RUNNER.rss_summary(values)
        self.assertEqual(150, result["server_rss_mean_kib"])
        self.assertEqual(200, result["server_rss_peak_kib"])
        self.assertEqual(3, result["resource_sample_count"])
        values[2]["elapsed_seconds"] = 1
        with self.assertRaises(ValueError):
            RUNNER.rss_summary(values)
        with self.assertRaises(ValueError):
            RUNNER.rss_summary(values[:1])

    def test_package_environment_excludes_private_and_unrelated_loaders(self) -> None:
        with tempfile.TemporaryDirectory(prefix="coakka-benchmark-env-") as directory:
            root = Path(directory)
            (root / "evidence/locks").mkdir(parents=True)
            (root / "evidence/locks/package-paths.json").write_text(json.dumps({
                "python": "/owned/python", "jvm": "/owned/jvm"}))
            injected = {"COAKKA_HTTP_HOST_PATH": "/wrong", "LD_LIBRARY_PATH": "/wrong",
                        "JAVA_TOOL_OPTIONS": "-Dwrong", "PYTHONPATH": "/wrong"}
            with mock.patch.dict(RUNNER.os.environ, injected, clear=True):
                framework = RUNNER.package_environment(root, {"role": "framework"}, campaign()["workload"])
                self.assertFalse(set(injected) & set(framework))
                python = RUNNER.package_environment(root, {"role": "coakka", "ecosystem": "Python"}, campaign()["workload"])
                self.assertEqual("/owned/python/python", python["PYTHONPATH"])
                self.assertEqual("single", python["COAKKA_BENCH_CPU_POLICY"])
                self.assertNotIn("COAKKA_HTTP_HOST_PATH", python)
                jvm = RUNNER.package_environment(root, {"role": "coakka", "ecosystem": "Kotlin/JVM",
                    "environment": {"JAVA_TOOL_OPTIONS": "-XX:ActiveProcessorCount=1"}}, campaign()["workload"])
                self.assertIn("-XX:ActiveProcessorCount=1", jvm["JAVA_TOOL_OPTIONS"])
                self.assertIn("/owned/jvm/native/libcoakka_http_host.so", jvm["JAVA_TOOL_OPTIONS"])
                self.assertNotIn("wrong", jvm["JAVA_TOOL_OPTIONS"])

    def test_shutdown_rejects_forced_or_premature_exit(self) -> None:
        process = mock.Mock(pid=123)
        process.poll.return_value = None
        process.wait.return_value = 0
        with mock.patch.object(RUNNER.os, "killpg") as kill:
            self.assertEqual(0, RUNNER.stop_server(process))
            kill.assert_called_once_with(123, RUNNER.signal.SIGTERM)
        process.wait.side_effect = [RUNNER.subprocess.TimeoutExpired("server", 15), -9]
        with mock.patch.object(RUNNER.os, "killpg") as kill:
            with self.assertRaisesRegex(RuntimeError, "forced shutdown"):
                RUNNER.stop_server(process)
            self.assertEqual(2, kill.call_count)
        process.poll.return_value = 1
        with self.assertRaisesRegex(RuntimeError, "before requested shutdown"):
            RUNNER.stop_server(process)

    def test_uvicorn_reraised_signal_requires_every_worker_shutdown(self) -> None:
        lane = {"id": "python-fastapi", "ecosystem": "Python", "role": "framework",
                "command": ["uvicorn", "--workers", "1"]}
        log = "Started server process [42]\nApplication shutdown complete.\nFinished server process [42]\n"
        RUNNER.validate_shutdown(lane, -15, log)
        for incomplete in ("", log.replace("Application shutdown complete.", ""),
                           log.replace("Finished server process [42]", "Finished server process [43]")):
            with self.assertRaises(RuntimeError):
                RUNNER.validate_shutdown(lane, -15, incomplete)
        with self.assertRaises(RuntimeError):
            RUNNER.validate_shutdown(lane, -9, log)
        lane["command"][-1] = "2"
        with self.assertRaises(RuntimeError):
            RUNNER.validate_shutdown(lane, 0, log)
        RUNNER.validate_shutdown(lane, 0, log + log.replace("42", "43"))

    def test_core_observation_accepts_actual_loop_count_not_fixed_tuning(self) -> None:
        record = {"cpu": {"selectedCpuIds": [0], "selectedCpuCount": 1},
                  "execution": {"observed": True, "configuredEventLoops": 2, "activeEventLoops": 2},
                  "requestNotificationBatchSize": 8, "terminalNotificationBatchSize": 8,
                  "limits": None, "ioUringEffective": False}
        workload = campaign()["workload"]
        log = "coakka-runtime-info=" + json.dumps(record)
        self.assertEqual(record, RUNNER.parse_core_observation(log, workload))
        for invalid_backend in (True, None, 0):
            invalid = {**record, "ioUringEffective": invalid_backend}
            with self.assertRaisesRegex(ValueError, "backend observation"):
                RUNNER.parse_core_observation("coakka-runtime-info=" + json.dumps(invalid), workload)
        with self.assertRaisesRegex(ValueError, "exactly one"):
            RUNNER.parse_core_observation(log + "\n" + log, workload)
        for field, value in (("observed", False), ("activeEventLoops", 0),
                             ("activeEventLoops", 3), ("configuredEventLoops", True)):
            invalid = copy.deepcopy(record)
            invalid["execution"][field] = value
            with self.assertRaisesRegex(ValueError, "execution observation"):
                RUNNER.parse_core_observation("coakka-runtime-info=" + json.dumps(invalid), workload)
        record["cpu"]["selectedCpuIds"] = [1]
        with self.assertRaisesRegex(ValueError, "CPU observation"):
            RUNNER.parse_core_observation("coakka-runtime-info=" + json.dumps(record), workload)

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
        self.assertEqual([mock.call(15), mock.call(5), mock.call(5)], sleep.call_args_list)
        self.assertEqual(3, keepalive.call_count)

    def test_cooldown_resets_streak_and_reports_worst_admitted_sample(self) -> None:
        with (
            mock.patch.object(RUNNER.time, "sleep"),
            mock.patch.object(RUNNER, "temperature_c", side_effect=[40, 40, 40, 43, 42, 41]) as temperature,
            mock.patch.object(RUNNER, "throttled", return_value="throttled=0x0"),
            mock.patch.object(RUNNER, "cpu_busy_percent", side_effect=[1, 2, 6, 2, 4, 1]),
        ):
            self.assertEqual((43, 4), RUNNER.wait_until_cool(50, 15, 5))
            self.assertEqual(6, temperature.call_count)

    def test_cooldown_times_out_without_three_clean_samples(self) -> None:
        with (
            mock.patch.object(RUNNER.time, "sleep"),
            mock.patch.object(RUNNER.time, "monotonic", side_effect=[0, 601]),
            mock.patch.object(RUNNER, "temperature_c", return_value=51),
            mock.patch.object(RUNNER, "throttled", return_value="throttled=0x0"),
            mock.patch.object(RUNNER, "cpu_busy_percent", return_value=2),
        ):
            with self.assertRaises(TimeoutError):
                RUNNER.wait_until_cool(50, 15, 5)

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

    def test_two_cpu_budget_keeps_load_isolated_and_summary_checks_cores(self) -> None:
        value = campaign()
        workload = value["workload"]
        workload.update(server_cpus="0-1", load_cpus="2-3", load_threads=2)
        value["machine"]["governor_before"] = {
            f"cpu{index}": "ondemand" for index in range(4)
        }
        value["machine"]["governor_after"] = dict(value["machine"]["governor_before"])
        value["machine"]["governor_during"] = {
            f"cpu{index}": "performance" for index in range(4)
        }
        for item in value["measurements"]:
            item["load_cpu_busy_percent_by_core"] = {"cpu2": 50.0, "cpu3": 40.0}
            for sample in item["load_cpu_interval_samples"]:
                del sample["busy_by_cpu"]["cpu1"]
        RUNNER.validate_machine(value["machine"], workload)
        with summarize_fixture(value) as output:
            self.assertIn("server 0-1; load generator 2-3 (2 threads)", output.read_text())
        value["measurements"][0]["load_cpu_busy_percent_by_core"]["cpu1"] = 1.0
        with self.assertRaisesRegex(ValueError, "load-core evidence"):
            with summarize_fixture(value):
                pass

    def test_native_stands_alone_and_framework_matrix_excludes_netty(self) -> None:
        """Both CPU profiles preserve the user-approved comparison boundary."""
        for name in ("lanes.json", "lanes-2cpu.json"):
            config = json.loads((SCRIPT_DIRECTORY.parent / "config" / name).read_text())
            RUNNER.validate_configuration(config["workload"], config["lanes"])
            lanes = config["lanes"]
            self.assertEqual(19, len(lanes))
            go_commands = [lane["command"][0] for lane in lanes if lane["ecosystem"] == "Go"]
            self.assertEqual(3, len(set(go_commands)))
            self.assertEqual({"kotlin-coakka", "kotlin-jetty", "kotlin-undertow", "kotlin-vertx",
                              "kotlin-webflux", "kotlin-mvc-tomcat"},
                             {lane["id"] for lane in lanes if lane["ecosystem"] == "Kotlin/JVM"})
            native = [lane for lane in lanes if lane["ecosystem"] in ("C", "C++")]
            self.assertEqual(["c-coakka"], [lane["id"] for lane in native])
            self.assertFalse(any("netty" in lane["id"] for lane in lanes))

    def test_two_cpu_lanes_use_the_declared_worker_budget(self) -> None:
        config = json.loads((SCRIPT_DIRECTORY.parent / "config/lanes-2cpu.json").read_text())
        RUNNER.validate_configuration(config["workload"], config["lanes"])
        lanes = {lane["id"]: lane for lane in config["lanes"]}
        self.assertEqual(19, len(lanes))
        self.assertNotIn("cpp-coakka", lanes)
        self.assertEqual("2", lanes["go-chi"]["environment"]["GOMAXPROCS"])
        self.assertEqual(
            "-XX:ActiveProcessorCount=2",
            lanes["kotlin-jetty"]["environment"]["JAVA_TOOL_OPTIONS"],
        )
        self.assertEqual("2", lanes["python-fastapi"]["command"][
            lanes["python-fastapi"]["command"].index("--workers") + 1
        ])

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
        cutoff = RUNNER.validate_load(
            "lane", workload, 0, raw.replace("100 started", "111 started")
        )
        self.assertEqual(11, cutoff["requests_started"] - cutoff["requests_done"])
        for started in (36, 82, 99, 164):
            boundary = RUNNER.validate_load(
                "lane", workload, 0, raw.replace("100 started", f"{started} started")
            )
            self.assertEqual(started - 100, boundary["requests_started"] - boundary["requests_done"])
        with self.assertRaisesRegex(RuntimeError, "timing interval"):
            RUNNER.validate_load("lane", workload, 0, raw.replace("15.00s", "3.00s"))
        with self.assertRaisesRegex(RuntimeError, "timing interval"):
            RUNNER.validate_load("lane", workload, 0, raw.replace(timing, ""))
        with self.assertRaisesRegex(RuntimeError, "one successful 2xx"):
            RUNNER.validate_load(
                "lane", workload, 0, raw.replace("10.00 req/s", "100.00 req/s")
            )
        for inconsistent in (
            raw.replace("100 started", "35 started"),
            raw.replace("100 started", "165 started"),
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
        with self.assertRaisesRegex(ValueError, "one load-generator thread per load CPU"):
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
            ("cooldown_minimum_seconds", 29),
            ("cooldown_minimum_seconds", 301),
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
        self.assertIn("| CoAkka event loops | Core-reported per run; no user loop override |", rendered)

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

    def test_bounded_timing_boundary_balance_is_disclosed(self) -> None:
        value = campaign()
        value["measurements"][0]["requests_started"] += 11
        with summarize_fixture(value) as output:
            self.assertIn(
                "| Starts minus completions, observed range | 0 to 11;",
                output.read_text(),
            )
        value["measurements"][0]["requests_started"] += 54
        with self.assertRaisesRegex(ValueError, "request accounting"):
            with summarize_fixture(value):
                pass

    def test_warmup_carry_is_bounded_and_never_a_negative_inflight_census(self) -> None:
        value = campaign()
        value["measurements"][0]["requests_started"] -= 18
        with summarize_fixture(value) as output:
            self.assertIn("| Starts minus completions, observed range | -18 to 0;", output.read_text())
            self.assertIn("not an exact in-flight census", output.read_text())
        value["measurements"][0]["requests_started"] -= 47
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


class CandidateIdentityTests(unittest.TestCase):
    """Keep the deployed candidate component bounded and shell/path-safe."""

    def test_deploy_candidate_pattern(self) -> None:
        source = (SCRIPT_DIRECTORY / "deploy-rpi5.sh").read_text()
        match = re.search(r"re\.fullmatch\(r'([^']+)', pin\['date'\]\)", source)
        self.assertIsNotNone(match)
        assert match is not None
        pattern = re.compile(match[1])
        for value in ("2026-10-08", "2026-10-08-r2", "2026-10-08-r999"):
            with self.subTest(value=value):
                self.assertIsNotNone(pattern.fullmatch(value))
        for value in ("../2026-10-08", "2026-10-08/extra", "2026-10-08-r0",
                      "2026-10-08-r01", "2026-10-08-r1000", "2026-10-08;id",
                      "2026-10-08\n", "2026-10-08-r2/../other"):
            with self.subTest(value=value):
                self.assertIsNone(pattern.fullmatch(value))


if __name__ == "__main__":
    unittest.main()
