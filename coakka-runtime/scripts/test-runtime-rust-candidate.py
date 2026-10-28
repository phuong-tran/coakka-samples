#!/usr/bin/env python3
"""Cold admission and failure cleanup; no native fixture substitutes for Core."""
import argparse
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("cargo_candidate", Path(__file__).with_name("runtime-cargo-candidate.py"))
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class AdmissionTests(unittest.TestCase):
    def setUp(self):
        fixture = tempfile.TemporaryDirectory(prefix="rust-admission-", dir=os.environ.get("COAKKA_DOC_TEST_ROOT"))
        self.addCleanup(fixture.cleanup)
        self.root = Path(fixture.name).resolve()
        self.source = self.root / "source"
        (self.source / "src").mkdir(parents=True)
        for name in MODULE.SOURCES:
            (self.source / name).write_bytes(b"fixture")
        self.cargo = self.root / "cargo"
        self.cargo.write_bytes(b"fixture, never executed")
        self.archive = self.root / "candidate.tar.gz"
        self.archive.write_bytes(b"untrusted archive")
        self.cache = self.root / "cache"
        self.cache.mkdir()
        self.work = self.root / "work"
        self.args = argparse.Namespace(source=self.source, work=self.work, cargo=self.cargo,
                                       archive=self.archive, cargo_home=self.cache, target="macos-aarch64")

    def test_changed_or_oversized_archive_never_reaches_tar(self):
        for content in (b"not an archive", b"x" * ((16 << 20) + 1)):
            with patch.object(MODULE.tarfile, "open") as opened:
                with self.assertRaises(ValueError):
                    MODULE.archive_tool(content)
                opened.assert_not_called()
        with self.assertRaises(ValueError):
            MODULE.build(self.args)
        self.assertFalse(self.work.exists())

    def test_existing_work_and_overlap_are_preserved(self):
        self.work.mkdir()
        sentinel = self.work / "keep"
        sentinel.write_bytes(b"user data")
        with self.assertRaises(ValueError):
            MODULE.build(self.args)
        self.assertEqual(sentinel.read_bytes(), b"user data")
        self.args.work = self.source / "nested"
        with self.assertRaises(ValueError):
            MODULE.build(self.args)
        self.assertFalse(self.args.work.exists())

    def test_regular_bounds_and_relative_path(self):
        for value in (b"", b"x" * 17):
            self.cargo.write_bytes(value)
            with self.assertRaises(ValueError):
                MODULE.regular(self.cargo, 16)
        with self.assertRaises(ValueError):
            MODULE.directory(Path("."))
        with self.assertRaises(FileNotFoundError):
            MODULE.regular(self.root / "missing", 16)

    def test_linked_parent_refused_without_outside_write(self):
        outside = self.root / "outside"
        outside.mkdir()
        link = self.root / "linked"
        if os.name == "nt":
            result = subprocess.run(["cmd.exe", "/d", "/c", "mklink", "/J", str(link), str(outside)],
                                    capture_output=True, timeout=10)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.addCleanup(link.rmdir)
        else:
            link.symlink_to(outside, target_is_directory=True)
            self.addCleanup(link.unlink)
        with self.assertRaises(ValueError):
            MODULE.directory(link)
        self.args.work = link / "work"
        with self.assertRaises(ValueError):
            MODULE.build(self.args)
        self.assertEqual(list(outside.iterdir()), [])

    def test_exclusive_write_sync_and_partial_failure(self):
        output = self.root / "output"
        MODULE.write_new(output, b"keep")
        with self.assertRaises(FileExistsError):
            MODULE.write_new(output, b"replace")
        self.assertEqual(output.read_bytes(), b"keep")
        with patch.object(MODULE.os, "fsync", side_effect=OSError("injected sync")):
            with self.assertRaisesRegex(OSError, "injected sync"):
                MODULE.write_new(self.root / "partial", b"partial")

    def test_transaction_interrupt_removes_only_owned_build_tree(self):
        # The trusted helper is replaced only in this cold transaction fixture.
        # No fake native function or Core result enters runtime evidence.
        with patch.object(MODULE, "archive_tool", return_value=b"raise KeyboardInterrupt('injected')"):
            with self.assertRaisesRegex(KeyboardInterrupt, "injected"):
                MODULE.build(self.args)
        self.assertFalse((self.work / "build").exists())
        self.assertFalse((self.work / "installed").exists())
        self.assertIn('"state": "failed"', (self.work / "receipt.json").read_text())
        self.assertTrue(self.source.is_dir())
        self.assertTrue(self.cache.is_dir())

    def test_source_admission_precedes_output(self):
        for name in MODULE.SOURCES:
            path = self.source / name
            before = path.read_bytes()
            path.write_bytes(b"x" * 65537)
            with patch.object(MODULE, "archive_tool", return_value=b"unused"):
                with self.assertRaises(ValueError):
                    MODULE.build(self.args)
            path.write_bytes(before)
            self.assertFalse(self.work.exists())

    def test_fixed_artifact_profiles_refuse_cross_identity_before_parsing(self):
        self.assertNotEqual(MODULE.RUST.archive_sha, MODULE.TAURI.archive_sha)
        self.assertFalse(MODULE.RUST.nested_rust)
        self.assertTrue(MODULE.TAURI.nested_rust)
        for profile in (MODULE.RUST, MODULE.TAURI):
            with patch.object(MODULE.tarfile, "open") as opened:
                with self.assertRaises(ValueError):
                    MODULE.archive_tool(b"wrong candidate", profile)
                opened.assert_not_called()


if __name__ == "__main__":
    unittest.main()
