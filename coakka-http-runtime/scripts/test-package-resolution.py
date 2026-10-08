#!/usr/bin/env python3
"""Artifact-backed resolver regressions; all disposable fixtures are external."""
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("resolver", Path(__file__).with_name("resolve-package.py"))
resolver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(resolver)


class ResolutionTests(unittest.TestCase):
    def setUp(self):
        root = Path(os.environ["COAKKA_HTTP_SAMPLE_WORK_ROOT"]) / "resolver-tests"
        root.mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(dir=root)
        self.addCleanup(self.temporary.cleanup)
        self.work = Path(self.temporary.name)
        self.publish = Path(os.environ["COAKKA_PUBLISH_ROOT"])

    def test_all_platform_inventories(self):
        pins = json.loads(Path(__file__).with_name("package-pins.json").read_text())
        for lane, record in pins.items():
            for target in record["sha256"]:
                with self.subTest(lane=lane, target=target):
                    package = resolver.resolve(self.publish, self.work, lane, target)
                    self.assertTrue(package.is_dir())
                    self.assertEqual(package, resolver.resolve(self.publish, self.work, lane, target))

    def test_corrupt_archive_is_rejected_before_extraction(self):
        # A corrupt candidate must exist at the active pin, not an obsolete
        # date that would exercise only the missing-file branch.
        pins = json.loads(Path(__file__).with_name("package-pins.json").read_text())
        folder = self.work / "publish/coakka-http-runtime/python/candidates" / pins["python"]["date"]
        folder.mkdir(parents=True)
        (folder / "coakka-http-python-1.0.0-candidate-macos-aarch64.tar.gz").write_bytes(b"not a candidate")
        with self.assertRaisesRegex(ValueError, "checksum"):
            resolver.resolve(self.work / "publish", self.work / "cache", "python", "macos-aarch64")
        self.assertFalse((self.work / "cache").exists())

    def test_mutated_cache_and_extra_file_are_rejected(self):
        package = resolver.resolve(self.publish, self.work, "python", "macos-aarch64")
        extra = package / "unapproved.py"
        extra.write_text("raise RuntimeError('unexpected')")
        with self.assertRaisesRegex(ValueError, "unexpected"):
            resolver.resolve(self.publish, self.work, "python", "macos-aarch64")
        extra.unlink()
        module = package / "python/coakka_http/__init__.py"
        module.write_text("modified")
        with self.assertRaisesRegex(ValueError, "modified"):
            resolver.resolve(self.publish, self.work, "python", "macos-aarch64")

    def test_missing_and_unknown_target(self):
        with self.assertRaises(ValueError):
            resolver.resolve(self.work, self.work / "cache", "native", "macos-aarch64")
        with self.assertRaises(KeyError):
            resolver.resolve(self.publish, self.work / "cache", "native", "unknown")


if __name__ == "__main__":
    unittest.main()
