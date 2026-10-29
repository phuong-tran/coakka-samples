"""Cold installer ownership/refusal tests; requires an explicit external work root."""
from __future__ import annotations

import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SPEC = importlib.util.spec_from_file_location("python_candidate", Path(__file__).with_name("runtime-python-candidate.py"))
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class CandidateTests(unittest.TestCase):
    def setUp(self):
        work = Path(os.environ["COAKKA_SAMPLE_WORK_ROOT"])
        MODULE.directory(work)
        fixture = tempfile.TemporaryDirectory(prefix="coakka-python-control-", dir=work)
        self.addCleanup(fixture.cleanup)
        self.root = Path(fixture.name)

    def fixture(self):
        name = "coakka_v2_connector-3.0.0-cp311-abi3-win_amd64.whl"
        path = self.root / name
        payloads = {"coakka_v2_connector/__init__.py": b"# fixture, never a native image\n"}
        with zipfile.ZipFile(path, "w") as output:
            for member, data in payloads.items():
                output.writestr(member, data)
        row = dict(filename=name, bytes=path.stat().st_size, sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                   members={name: dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest()) for name, data in payloads.items()})
        catalog = dict(schema=1, state="private-candidate", distribution="coakka-v2-connector",
                       version="3.0.0", minimumRuntime="3.11", coreCommit="f6b61768f60a08d81b1dc06625a211ffca8e89e2",
                       wheels={target: dict(row) for target in ("macos-aarch64", "linux-aarch64", "linux-x86_64", "windows-aarch64", "windows-x86_64")})
        return path, row, catalog

    def test_catalog_identity_unknown_duplicate_and_bounds(self):
        _, _, catalog = self.fixture()
        original = json.dumps(catalog).encode()
        MODULE.decode_catalog(original)
        for change in (
            lambda c: c.update(unknown=1), lambda c: c.update(state="published"),
            lambda c: c.update(schema=True), lambda c: c.update(wheels={}),
            lambda c: c.update(minimumRuntime="3.14"), lambda c: c.update(coreCommit="wrong"),
            lambda c: c.update(version="2.5.4.dev20261023"),
            lambda c: c["wheels"].pop("linux-x86_64"),
            lambda c: c["wheels"].pop("windows-aarch64"),
            lambda c: c["wheels"]["macos-aarch64"].update(bytes=(4 << 20)+1),
            lambda c: c["wheels"]["macos-aarch64"].update(sha256="wrong"),
            lambda c: c["wheels"]["macos-aarch64"].update(filename="coakka_v2_connector-3.0.0-py3-none-win_amd64.whl"),
            lambda c: c["wheels"]["macos-aarch64"].update(filename="coakka_v2_connector-3.0.0-cp310-abi3-win_amd64.whl"),
            lambda c: c["wheels"]["macos-aarch64"].update(filename="coakka_v2_connector-3.0.0-cp311-none-win_amd64.whl"),
            lambda c: c["wheels"]["macos-aarch64"].update(members={"../outside": {"bytes": 1, "sha256": "0"*64}}),
        ):
            copy = json.loads(original)
            change(copy)
            with self.assertRaises(ValueError):
                MODULE.decode_catalog(json.dumps(copy).encode())
        with self.assertRaises(ValueError):
            MODULE.decode_catalog(b'{"schema":1,"schema":1}')
        with self.assertRaises(ValueError):
            MODULE.decode_catalog(original+b" {}")

    def test_exact_wheel_and_regular_bounded_read(self):
        path, row, _ = self.fixture()
        self.assertEqual(MODULE.admit_wheel(path, row), path.read_bytes())
        with self.assertRaises(ValueError):
            MODULE.bounded_file(path, 1)
        with self.assertRaises(ValueError):
            MODULE.bounded_file(self.root, 1)
        wrong = dict(row, sha256="0"*64)
        with self.assertRaises(ValueError):
            MODULE.admit_wheel(path, wrong)
        wrong = dict(row, members={})
        with self.assertRaises(ValueError):
            MODULE.admit_wheel(path, wrong)
        wrong = dict(row, members={name: dict(member, sha256="0"*64) for name, member in row["members"].items()})
        with self.assertRaises(ValueError):
            MODULE.admit_wheel(path, wrong)

    def test_links_and_member_paths_refuse(self):
        if os.name == "nt":
            environment = dict(os.environ, COAKKA_CONTROL_LINK=str(self.root / "link"), COAKKA_CONTROL_TARGET=str(self.root))
            subprocess.run(["powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-Command",
                            "New-Item -ItemType Junction -Path $env:COAKKA_CONTROL_LINK -Target $env:COAKKA_CONTROL_TARGET | Out-Null"],
                           env=environment, check=True, capture_output=True, timeout=10)
        else:
            (self.root / "link").symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(ValueError):
            MODULE.directory(self.root / "link")
        for name in ("", "/absolute", "../outside", "a/../b", "a//b", "a\\b", "C:/data", "a/."):
            self.assertFalse(MODULE.valid_member(name), name)

    def test_owned_stage_success_failure_and_unconfirmed_reap(self):
        for fail in (False, True):
            stage = self.root / ("failure" if fail else "success")
            stage.mkdir()
            error = RuntimeError("injected body failure")

            def action():
                if fail:
                    raise error

            if fail:
                with self.assertRaises(RuntimeError) as caught:
                    MODULE.with_stage(stage, action)
                self.assertIs(caught.exception, error)
            else:
                MODULE.with_stage(stage, action)
            self.assertFalse(stage.exists())
        retained = self.root / "retained"
        retained.mkdir()

        def unconfirmed():
            raise MODULE.UnreapedChild("synthetic no real child")

        with self.assertRaises(MODULE.UnreapedChild):
            MODULE.with_stage(retained, unconfirmed)
        self.assertTrue(retained.exists())
        # Fake unconfirmed stop only; test cleanup owns this inert directory.

    def test_real_child_failure_deadline_and_exception_cleanup(self):
        environment = dict(os.environ)
        with self.assertRaises(subprocess.CalledProcessError) as caught:
            MODULE.run_command([sys.executable, "-I", "-B", "-c", "raise SystemExit(7)"], self.root, environment, 1)
        self.assertEqual(caught.exception.returncode, 7)
        with self.assertRaises(subprocess.TimeoutExpired):
            MODULE.run_command([sys.executable, "-I", "-B", "-c", "import time; time.sleep(10)"], self.root, environment, .05)
        # Inject only interruption at the Python wait seam; kill/reap is real.
        original = subprocess.Popen.wait
        first = True

        def interrupted(process, timeout=None):
            nonlocal first
            if first:
                first = False
                raise KeyboardInterrupt
            return original(process, timeout)

        with patch.object(subprocess.Popen, "wait", interrupted):
            with self.assertRaises(KeyboardInterrupt):
                MODULE.run_command([sys.executable, "-I", "-B", "-c", "import time; time.sleep(10)"], self.root, environment, 1)

    def test_unknown_selectors_and_overrides_before_stage(self):
        for sample in ("other", "../basic", "basic/../../outside"):
            with self.assertRaises(SystemExit):
                MODULE.entry(["--repo-root", str(self.root), sample])
        for name in ("COAKKA_RUNTIME_LIB", "COAKKA_FILE_LANE_RUNTIME_LIB", "PYTHONPATH", "LD_PRELOAD", "DYLD_INSERT_LIBRARIES"):
            with patch.dict(os.environ, {name: "must-refuse"}):
                with self.assertRaisesRegex(ValueError, "remove sample override"):
                    MODULE.entry(["--repo-root", str(self.root), "basic"])
        self.assertEqual(list(self.root.iterdir()), [])

    def test_bootstrap_utf8_safe_path_and_no_ambient_python_settings(self):
        ambient = dict(os.environ, PYTHONPATH="must-not-import", PYTHONHOME="must-not-load",
                       PYTHONUSERBASE="must-not-use", PIP_INDEX_URL="must-not-fetch", COAKKA_RUNTIME_LIB="must-not-load")
        environment = MODULE.child_environment(ambient, self.root)
        self.assertEqual({k: v for k, v in environment.items() if k.upper().startswith("PYTHON")},
                         {"PYTHONUTF8": "1", "PYTHONNOUSERSITE": "1", "PYTHONSAFEPATH": "1", "PYTHONDONTWRITEBYTECODE": "1"})
        self.assertEqual({k: v for k, v in environment.items() if k.upper().startswith("PIP_")}, {"PIP_CONFIG_FILE": os.devnull})
        self.assertFalse(any(k.upper().startswith("COAKKA_") for k in environment))
        code = ("import pathlib, site, sys; assert sys.flags.utf8_mode == 1; assert sys.flags.safe_path; "
                "assert not site.ENABLE_USER_SITE; assert '' not in sys.path; "
                "assert str(pathlib.Path.cwd()) not in sys.path; print('mã 空格')")
        result = subprocess.run([sys.executable, "-B", "-c", code], cwd=self.root, env=environment,
                                capture_output=True, check=True, timeout=1)
        self.assertEqual(result.stdout.decode("utf-8").strip(), "mã 空格")
        self.assertEqual(result.stderr, b"")


if __name__ == "__main__":
    unittest.main(verbosity=2)
