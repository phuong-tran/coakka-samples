#!/usr/bin/env python3
"""Small white-box build transaction controls, not native/runtime evidence."""
import argparse
import importlib.util
import json
from pathlib import Path
import plistlib
import sys
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location(
    "desktop_candidate", Path(__file__).with_name("runtime-tauri-desktop-candidate.py")
)
assert SPEC is not None and SPEC.loader is not None
DESKTOP = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(DESKTOP)
SOURCE = Path(__file__).resolve().parent.parent / "runtime/tauri/desktop-intent"


class Admission(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="coakka-desktop-admission-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.source = self.root / "source"
        for name in DESKTOP.SOURCES:
            path = self.source / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes((SOURCE / name).read_bytes())
        for name in ("cargo-cache", "target-cache", "tools"):
            (self.root / name).mkdir()
        target = next(name for name, platform in DESKTOP.PLATFORMS.items() if platform == sys.platform)
        suffix = ".exe" if sys.platform == "win32" else ""
        self.cargo = self.root / ("tools/cargo" + suffix)
        self.cargo.write_bytes(b"unit-cargo")
        self.cargo.with_name("rustc" + suffix).write_bytes(b"unit-rustc")
        self.cargo.with_name("cargo-clippy" + suffix).write_bytes(b"unit-clippy")
        self.cargo.with_name("clippy-driver" + suffix).write_bytes(b"unit-clippy-driver")
        if sys.platform == "win32":
            linker = self.root / "lib/rustlib/x86_64-pc-windows-msvc/bin/rust-lld.exe"
            linker.parent.mkdir(parents=True)
            linker.write_bytes(b"unit-linker")
        self.archive = self.root / "candidate.tar.gz"
        self.archive.write_bytes(b"unit-archive")
        self.args = argparse.Namespace(work=self.root / "work", source=self.source,
            cargo_home=self.root / "cargo-cache", target_cache=self.root / "target-cache",
            archive=self.archive, cargo=self.cargo, target=target)

    def test_closed_projection_does_not_reserve_work(self):
        with patch.object(DESKTOP.CARGO, "archive_tool", return_value=b"unit-helper"):
            content, helper, source = DESKTOP.admit(self.args)
        self.assertEqual((content, helper), (b"unit-archive", b"unit-helper"))
        self.assertEqual(set(source), set(DESKTOP.SOURCES))
        self.assertFalse(self.args.work.exists())

    def test_existing_and_overlapping_work_refuse_before_archive(self):
        self.args.work.mkdir()
        marker = self.args.work / "caller"
        marker.write_bytes(b"keep")
        with patch.object(DESKTOP.CARGO, "archive_tool") as tool:
            with self.assertRaises(ValueError):
                DESKTOP.admit(self.args)
            tool.assert_not_called()
        self.assertEqual(marker.read_bytes(), b"keep")
        for parent in (self.source, self.args.cargo_home, self.args.target_cache):
            self.args.work = parent / "fresh-child"
            with self.assertRaises(ValueError):
                DESKTOP.admit(self.args)
            self.assertFalse(self.args.work.exists())

    def test_wrong_archive_is_refused_before_tar(self):
        with patch.object(DESKTOP.CARGO.tarfile, "open", side_effect=AssertionError("tar reached")):
            with self.assertRaisesRegex(ValueError, "identity refused"):
                DESKTOP.admit(self.args)
        self.assertFalse(self.args.work.exists())

    def test_missing_and_oversized_source_refused(self):
        path = self.source / "dist/main.js"
        path.unlink()
        with patch.object(DESKTOP.CARGO, "archive_tool", return_value=b"unit-helper"):
            with self.assertRaises(FileNotFoundError):
                DESKTOP.admit(self.args)
            path.write_bytes(b"x" * ((256 << 10) + 1))
            with self.assertRaisesRegex(ValueError, "bounded regular"):
                DESKTOP.admit(self.args)
        self.assertFalse(self.args.work.exists())

    def test_unshrunk_profile_and_foreign_host_refuse(self):
        path = self.source / "src-tauri/Cargo.toml"
        path.write_text(path.read_text().replace('strip = "symbols"', 'strip = "none"'))
        with patch.object(DESKTOP.CARGO, "archive_tool", return_value=b"unit-helper"):
            with self.assertRaisesRegex(ValueError, "release contract"):
                DESKTOP.admit(self.args)
        self.args.target = next(name for name, platform in DESKTOP.PLATFORMS.items() if platform != sys.platform)
        with self.assertRaisesRegex(ValueError, "matching native"):
            DESKTOP.admit(self.args)

    def test_bundle_and_product_specific_remaps(self):
        info = plistlib.loads(DESKTOP.bundle_info())
        self.assertEqual(info["CFBundleExecutable"], DESKTOP.BINARY)
        self.assertEqual(info["CFBundlePackageType"], "APPL")
        flags = DESKTOP.flags(self.args, self.args.work / "build")
        remaps = [flag.split("=", 1)[1].rsplit("=", 1) for flag in flags if flag.startswith("--remap-path-prefix=")]
        self.assertEqual(remaps[0][1], "coakka")
        for name in ("coakka-desktop", "coakka-runtime", "coakka-intents"):
            self.assertIn(name, [right for _, right in remaps])
        self.assertFalse(any("build" in right or "RuntimeCandidate" in right for _, right in remaps))

    def transaction(self, fail_build, expect_cleanup=True):
        helper = b'''def unpack(snapshot, digest, destination):
    destination.mkdir()
    (destination / "rust").mkdir()
def deploy(package, executable, target):
    (executable.parent / "partial-native").write_bytes(b"partial")
    raise RuntimeError("injected deployment failure")
'''
        files = {name: (self.source / name).read_bytes() for name in DESKTOP.SOURCES}
        binary = self.args.target_cache / DESKTOP.CARGO.TARGETS[self.args.target] / "release" / (
            DESKTOP.BINARY + (".exe" if sys.platform == "win32" else ""))

        def child(command, cwd, env, log, bound):
            if command[1] == "-vV":
                log.write_text("release: 1.92.0\nhost: " + DESKTOP.CARGO.TARGETS[self.args.target] + "\n")
            elif command[-1] == "-V":
                log.write_text("clippy 0.1.92 (unit-only)\n")
            elif fail_build:
                raise RuntimeError("injected compiler failure")
            else:
                log.write_text("unit-only\n")
                if command[1] == "build":
                    binary.parent.mkdir(parents=True)
                    binary.write_bytes(b"unit-only-not-a-native-image")
            return {"unitOnly": True, "exit_code": 0}

        with patch.object(DESKTOP, "admit", return_value=(b"archive", helper, files)), \
                patch.object(DESKTOP.CARGO, "child", side_effect=child):
            with self.assertRaisesRegex(RuntimeError,
                    "injected compiler failure" if fail_build else "injected deployment failure"):
                DESKTOP.build(self.args)
        self.assertEqual((self.args.work / "build").exists(), not expect_cleanup)
        self.assertFalse((self.args.work / "installed").exists())
        self.assertTrue((self.args.work / "receipt.json").is_file())
        self.assertTrue(self.args.target_cache.is_dir())
        return binary

    def test_compiler_failure_retires_only_reserved_staging(self):
        binary = self.transaction(True)
        self.assertFalse(binary.exists())
        self.assertTrue(self.archive.is_file())

    def test_partial_promotion_is_removed_but_caller_cache_retained(self):
        binary = self.transaction(False)
        self.assertEqual(binary.read_bytes(), b"unit-only-not-a-native-image")

    def test_cleanup_failure_retains_receipt_and_original_compiler_failure(self):
        original = DESKTOP.shutil.rmtree

        def fail_owned_root(path, *args, **kwargs):
            if path == self.args.work / "build":
                raise PermissionError("injected cleanup refusal")
            return original(path, *args, **kwargs)

        # transaction's original failure must propagate, not become a false
        # success or disappear behind a cleanup exception with no receipt.
        with patch.object(DESKTOP.shutil, "rmtree", side_effect=fail_owned_root):
            self.transaction(True, expect_cleanup=False)
        receipt = json.loads((self.args.work / "receipt.json").read_text())
        self.assertEqual(receipt["state"], "failed-cleanup")
        self.assertEqual(receipt["cleanup_failure"], "injected cleanup refusal")
        self.assertTrue((self.args.work / "build").is_dir())


if __name__ == "__main__":
    unittest.main()
