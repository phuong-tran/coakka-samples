"""Sample app failure seams over the actual installed public package API."""
from __future__ import annotations

from pathlib import Path
import runpy
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from coakka_v2_connector import CoakkaStatus, CoreCallError, Deadletter, Message

ROOT = Path(__file__).resolve().parent


class SampleTests(unittest.TestCase):
    def load(self, name):
        return runpy.run_path(str(ROOT / name / "main.py"), run_name="sample_test_only")

    def test_echo_business_response_not_assumed(self):
        check = self.load("basic")["check_response"]
        check({"echo": "hello-runtime-python"})
        for value in (None, [], {"echo": "wrong"}, {"echo": "hello-runtime-python", "extra": True}):
            with self.assertRaises(RuntimeError):
                check(value)

    def test_nullable_original_and_reason_target_mismatch(self):
        check = self.load("deadletter")["check_deadletter"]
        check(Deadletter(reason=2, original_envelope=Message(target="missing")), "missing")
        for value in (Deadletter(reason=2), Deadletter(reason=999, original_envelope=Message(target="missing")),
                      Deadletter(reason=2, original_envelope=Message(target="other"))):
            with self.assertRaises(RuntimeError):
                check(value, "missing")

    def test_control_refusal_requires_core_status_not_diagnostic_text(self):
        check = self.load("hot-reload")["expect_control_rejected"]
        expected = CoakkaStatus.ERR_STALE_GENERATION
        for error, accepted in ((CoreCallError(expected, "apply_control_envelope"), True),
                                (CoreCallError(CoakkaStatus.ERR_IO, "apply_control_envelope"), False),
                                (RuntimeError("apply_control_envelope failed rc=-4"), False),
                                (ValueError("wrong boundary"), False)):
            def refused():
                raise error
            if accepted:
                check("test", refused, expected)
            else:
                with self.assertRaises(type(error)) as caught:
                    check("test", refused, expected)
                self.assertIs(caught.exception, error)
        with self.assertRaises(RuntimeError):
            check("unexpected-success", lambda: None, expected)

    def test_host_close_runs_after_body_or_observer_failure(self):
        injected = RuntimeError("injected application boundary")
        for name, background in ((name, background) for name in ("basic", "deadletter", "hot-reload") for background in (False, True)):
            with self.subTest(sample=name, background=background):
                calls = []
                worker_error = RuntimeError("injected worker diagnostic") if background else None

                class Host:
                    def __enter__(self):
                        return self

                    def __exit__(self, *_):
                        calls.append("host-close")

                    def runtime_info(self):
                        raise injected

                    def deadletters(self, buffer_capacity):
                        self_capacity = buffer_capacity
                        if self_capacity != 1:
                            raise AssertionError("observation capacity changed")
                        return SimpleNamespace(close=lambda: calls.append("observer-close"))

                    def ask_json(self, **_):
                        raise injected

                    def take_background_error(self):
                        calls.append("diagnostic")
                        return worker_error

                main = self.load(name)["main"]
                with patch.dict(main.__globals__, RuntimeHost=SimpleNamespace(start=lambda **_: Host())):
                    with self.assertRaises(RuntimeError) as caught:
                        main()
                self.assertIs(caught.exception, worker_error if background else injected)
                if background:
                    self.assertIs(caught.exception.__context__, injected)
                self.assertIn("host-close", calls)
                if name == "deadletter":
                    self.assertEqual(calls[:2], ["observer-close", "host-close"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
