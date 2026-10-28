#!/usr/bin/env python3
"""Real child-process controls for the sample launcher's stop classification."""
from __future__ import annotations

import importlib.util
import os
from pathlib import Path
import selectors
import subprocess
import sys
import unittest

spec = importlib.util.spec_from_file_location("sample_runner", Path(__file__).with_name("sample-runner.py"))
assert spec is not None and spec.loader is not None
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class ProcessControls(unittest.TestCase):
    @staticmethod
    def reap(process):
        if process.poll() is None:
            process.kill()
        process.wait(timeout=5)
        if process.stdout:
            process.stdout.close()

    def test_early_crash_is_not_a_successful_stop(self):
        process = subprocess.Popen([sys.executable, "-I", "-B", "-c", "raise SystemExit(7)"])
        self.addCleanup(self.reap, process)
        process.wait(timeout=5)
        result, _ = runner.stop_owned(process)
        self.assertEqual(result, "already-exited")
        self.assertEqual(process.returncode, 7)

    @unittest.skipUnless(os.name == "posix", "POSIX owned termination control")
    def test_live_child_is_terminated_and_reaped(self):
        process = subprocess.Popen([sys.executable, "-I", "-B", "-c", "import time; time.sleep(60)"],
                                   start_new_session=True)
        self.addCleanup(self.reap, process)
        result, _ = runner.stop_owned(process)
        self.assertEqual(result, "terminated")
        self.assertIsNotNone(process.poll())

    @unittest.skipUnless(os.name == "posix", "POSIX signal result control")
    def test_crash_during_requested_stop_is_not_success(self):
        script = ("import os,signal,time; "
                  "signal.signal(signal.SIGTERM,lambda *_:os._exit(7)); "
                  "print('ready',flush=True); time.sleep(60)")
        process = subprocess.Popen([sys.executable, "-I", "-B", "-c", script],
                                   stdout=subprocess.PIPE, text=True, start_new_session=True)
        self.addCleanup(self.reap, process)
        assert process.stdout is not None
        with selectors.DefaultSelector() as readiness:
            readiness.register(process.stdout, selectors.EVENT_READ)
            self.assertTrue(readiness.select(timeout=2), "control child did not become ready")
            self.assertEqual(process.stdout.readline().strip(), "ready")
        result, _ = runner.stop_owned(process)
        self.assertEqual(result, "unexpected-exit")
        self.assertEqual(process.returncode, 7)

    def test_stop_command_does_not_stop_an_unrelated_process(self):
        process = subprocess.Popen([sys.executable, "-I", "-B", "-c", "import time; time.sleep(60)"])
        self.addCleanup(self.reap, process)
        environment = {key: value for key, value in os.environ.items()
                       if not key.startswith("COAKKA_") and key != "GRADLE_USER_HOME"}
        result = subprocess.run([sys.executable, "-I", "-B", str(Path(__file__).with_name("sample-runner.py")),
                                 "spring", "stop"], env=environment, capture_output=True, text=True, timeout=5)
        self.assertEqual(result.returncode, 0)
        self.assertIn("No background process", result.stdout)
        self.assertIsNone(process.poll())


if __name__ == "__main__":
    unittest.main()
