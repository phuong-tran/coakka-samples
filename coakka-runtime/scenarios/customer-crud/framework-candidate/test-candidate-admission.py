#!/usr/bin/env python3
"""Negative controls for the framework sample's actual artifact admission."""
from __future__ import annotations

import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("framework_admission", Path(__file__).with_name("candidate-admission.py"))
assert spec is not None and spec.loader is not None
admission = importlib.util.module_from_spec(spec)
spec.loader.exec_module(admission)


class AdmissionControls(unittest.TestCase):
    def setUp(self):
        fixture_root = Path(os.environ["COAKKA_FRAMEWORK_TEST_ROOT"])
        self.assertTrue(fixture_root.is_absolute() and fixture_root.is_dir())
        temporary = tempfile.TemporaryDirectory(prefix="admission-", dir=fixture_root)
        self.addCleanup(temporary.cleanup)
        self.work = Path(temporary.name)
        self.home = self.work / "repository"
        self.home.mkdir()
        artifacts = []
        for index, name in enumerate(sorted(admission.EXPECTED_PATHS)):
            path = self.home / name
            path.parent.mkdir(parents=True, exist_ok=True)
            data = f"fixture-artifact-{index}\n".encode()
            path.write_bytes(data)
            artifacts.append({"path": name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})
        self.value = {"schema": 1, "state": "private-candidate", "runtimeVersion": "3.0.0",
                      "adapterVersion": "3.0.0", "coreCommit": admission.CORE_COMMIT, "artifacts": artifacts}
        self.catalog = self.work / "catalog.json"
        self.save()

    def save(self):
        self.catalog.write_text(json.dumps(self.value))

    def check(self):
        return admission.admit(self.catalog, self.home)

    def test_exact_complete_candidate_is_admitted(self):
        self.assertEqual(self.check(), self.value)

    def test_changed_bytes_with_equal_extent_are_refused(self):
        row = self.value["artifacts"][0]
        path = self.home / row["path"]
        path.write_bytes(b"x" * row["bytes"])
        with self.assertRaisesRegex(ValueError, "hash differs"):
            self.check()

    def test_short_member_is_refused(self):
        (self.home / self.value["artifacts"][0]["path"]).write_bytes(b"x")
        with self.assertRaisesRegex(ValueError, "extent differs"):
            self.check()

    def test_missing_member_is_refused(self):
        (self.home / self.value["artifacts"][0]["path"]).unlink()
        with self.assertRaises(OSError):
            self.check()

    def test_final_member_link_is_refused(self):
        path = self.home / self.value["artifacts"][0]["path"]
        target = self.work / "foreign"
        target.write_bytes(path.read_bytes())
        path.unlink()
        path.symlink_to(target)
        with self.assertRaisesRegex(ValueError, "symbolic links"):
            self.check()

    def test_parent_directory_link_is_refused(self):
        renamed = self.work / "moved-repository"
        self.home.rename(renamed)
        self.home.symlink_to(renamed, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "symbolic links"):
            self.check()

    @unittest.skipUnless(hasattr(os, "mkfifo"), "FIFO creation is unavailable on this host")
    def test_fifo_member_is_refused_without_blocking_open(self):
        path = self.home / self.value["artifacts"][0]["path"]
        path.unlink()
        os.mkfifo(path)
        result = subprocess.run([sys.executable, "-I", "-B", str(Path(__file__).with_name("candidate-admission.py")),
                                 "--catalog", str(self.catalog), "--home", str(self.home)],
                                capture_output=True, text=True, timeout=2)
        self.assertEqual(result.returncode, 1)
        self.assertIn("regular file", result.stderr)

    def test_wrong_candidate_identity_or_unknown_field_is_refused(self):
        for key, wrong in (("schema", True), ("state", "published"), ("runtimeVersion", "2.5.3"),
                           ("adapterVersion", "2.5.3"), ("coreCommit", "0" * 40), ("unknown", "field")):
            value = copy.deepcopy(self.value)
            value[key] = wrong
            self.catalog.write_text(json.dumps(value))
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.check()

    def test_duplicate_json_fields_are_refused(self):
        text = json.dumps(self.value)
        self.catalog.write_text(text.replace('"schema": 1', '"schema": 1, "schema": 1'))
        with self.assertRaisesRegex(ValueError, "duplicate"):
            self.check()

    def test_duplicate_or_unexpected_artifact_is_refused(self):
        for name in (self.value["artifacts"][1]["path"], "../outside.jar",
                     "io/github/phuong-tran/coakka/other/3.0.0/other-3.0.0.jar"):
            value = copy.deepcopy(self.value)
            value["artifacts"][0]["path"] = name
            self.catalog.write_text(json.dumps(value))
            with self.subTest(name=name), self.assertRaises(ValueError):
                self.check()

    def test_noninteger_or_excessive_extent_is_refused(self):
        for size in (True, 0, admission.MAX_ARTIFACT_BYTES + 1):
            self.value["artifacts"][0]["bytes"] = size
            self.save()
            with self.subTest(size=size), self.assertRaisesRegex(ValueError, "artifact identity"):
                self.check()

    def test_excessive_catalog_is_refused_before_parsing(self):
        self.catalog.write_bytes(b" " * (admission.MAX_CATALOG_BYTES + 1))
        with self.assertRaisesRegex(ValueError, "byte bound"):
            self.check()

    def test_missing_catalog_fields_and_artifact_fields_are_refused(self):
        for field in ("state", "artifacts"):
            value = copy.deepcopy(self.value)
            del value[field]
            self.catalog.write_text(json.dumps(value))
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.check()
        value = copy.deepcopy(self.value)
        value["artifacts"][0]["extra"] = "unreviewed"
        self.catalog.write_text(json.dumps(value))
        with self.assertRaises(ValueError):
            self.check()


if __name__ == "__main__":
    unittest.main()
