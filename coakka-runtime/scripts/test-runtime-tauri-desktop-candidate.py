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
SOURCE = Path(__file__).resolve().parents[2] / "coakka-runtime/tauri/desktop-intent"


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
        self.assertEqual(info["LSMinimumSystemVersion"], "13.0")
        flags = DESKTOP.flags(self.args, self.args.work / "build")
        remaps = [flag.split("=", 1)[1].rsplit("=", 1) for flag in flags if flag.startswith("--remap-path-prefix=")]
        self.assertEqual(remaps[0][1], "coakka")
        for name in ("coakka-desktop", "coakka-runtime", "coakka-intents"):
            self.assertIn(name, [right for _, right in remaps])
        self.assertFalse(any("build" in right or "RuntimeCandidate" in right for _, right in remaps))

    def test_missing_bundle_signer_refuses_before_archive_or_work(self):
        if self.args.target != "macos-aarch64":
            self.skipTest("Mac bundle tool admission only")
        with patch.dict(DESKTOP.CARGO.MACOS_TOOLS, {"codesign": self.root / "missing-signer"}), \
                patch.object(DESKTOP.CARGO, "archive_tool", return_value=b"unit-helper") as archive:
            with self.assertRaises(FileNotFoundError):
                DESKTOP.admit(self.args)
            archive.assert_not_called()
        self.assertFalse(self.args.work.exists())

    def bundle_fixture(self):
        self.args.work.mkdir()
        app = self.args.work / "installed/Intent.app/Contents/MacOS"
        native = app.parent / "Frameworks/libcoakka_runtime_v2.dylib"
        native.parent.mkdir(parents=True)
        native.write_bytes(b"frozen-native-image")
        return app, native

    def test_bundle_code_and_component_provenance_have_distinct_layouts(self):
        app = self.args.work / "installed/Intent.app/Contents/MacOS"
        source = app / "native/macos-aarch64"
        source.mkdir(parents=True)
        legal = ("LICENSE", "NOTICE", "PACKAGE-LICENSE.md", "NATIVE-LICENSE.md")
        payload = {name: ("unit-only " + name).encode()
                   for name in (*legal, "native-manifest.json", "libcoakka_runtime_v2.dylib")}
        for name, data in payload.items():
            (source / name).write_bytes(data)
        receipt = DESKTOP.relocate_macos_payload(
            app, source / "libcoakka_runtime_v2.dylib", legal)
        self.assertEqual(len(receipt["files"]), 6)
        self.assertFalse((app / "native").exists())
        for name, data in payload.items():
            destination = app.parent / (
                "Frameworks/" + name if name.endswith(".dylib")
                else "Resources/native/macos-aarch64/" + name)
            self.assertEqual(destination.read_bytes(), data)
        self.assertIn("provenance", receipt["native_manifest_role"])

    def test_unexpected_bundle_component_refuses_before_moves(self):
        app = self.args.work / "installed/Intent.app/Contents/MacOS"
        source = app / "native/macos-aarch64"
        source.mkdir(parents=True)
        image = source / "libcoakka_runtime_v2.dylib"
        image.write_bytes(b"unit-only-image")
        (source / "unexpected").write_bytes(b"keep")
        with self.assertRaisesRegex(ValueError, "unexpected deployed"):
            DESKTOP.relocate_macos_payload(app, image, ())
        self.assertEqual(image.read_bytes(), b"unit-only-image")
        self.assertFalse((app.parent / "Frameworks").exists())

    def test_bundle_finalization_signs_only_bundle_and_keeps_native(self):
        app, native = self.bundle_fixture()
        with patch.object(DESKTOP.CARGO, "child", return_value={"unitOnly": True}) as child:
            rows = DESKTOP.finalize_macos_bundle(self.args, app, self.source, {})
        self.assertEqual(len(rows), 2)
        commands = [call.args[0] for call in child.call_args_list]
        self.assertEqual(commands[0][-1], str(app.parent.parent))
        self.assertIn("--timestamp=none", commands[0])
        self.assertEqual(commands[1][1:3], ["--verify", "--strict"])
        self.assertFalse(any("--deep" in command or str(native) in command for command in commands))
        self.assertEqual(native.read_bytes(), b"frozen-native-image")

    def test_bundle_finalization_failure_and_native_mutation_refuse(self):
        app, native = self.bundle_fixture()
        with patch.object(DESKTOP.CARGO, "child", side_effect=RuntimeError("signing refused")):
            with self.assertRaisesRegex(RuntimeError, "signing refused"):
                DESKTOP.finalize_macos_bundle(self.args, app, self.source, {})

        self.assertEqual(native.read_bytes(), b"frozen-native-image")

        def mutate_native(*args, **kwargs):
            native.write_bytes(b"changed")
            return {"unitOnly": True}

        with patch.object(DESKTOP.CARGO, "child", side_effect=mutate_native):
            with self.assertRaisesRegex(ValueError, "native image changed"):
                DESKTOP.finalize_macos_bundle(self.args, app, self.source, {})

    def test_build_places_complete_bundle_before_finalizer(self):
        if self.args.target != "macos-aarch64":
            self.skipTest("Mac bundle composition only")
        helper = b'''LEGAL = ("LICENSE", "NOTICE", "PACKAGE-LICENSE.md", "NATIVE-LICENSE.md")
def unpack(snapshot, digest, destination):
    destination.mkdir()
    (destination / "rust").mkdir()
    (destination / "rust/native-manifest.json").write_bytes(b"unit-original-component-provenance")
def deploy(package, executable, target):
    directory = executable.parent / "native" / target
    directory.mkdir(parents=True)
    for name in (*LEGAL, "native-manifest.json", "libcoakka_runtime_v2.dylib"):
        (directory / name).write_bytes(b"unit-only-placement-fixture")
    return directory / "libcoakka_runtime_v2.dylib"
'''
        files = {name: (self.source / name).read_bytes() for name in DESKTOP.SOURCES}
        binary = self.args.target_cache / DESKTOP.CARGO.TARGETS[self.args.target] / "release" / DESKTOP.BINARY

        def child(command, cwd, env, log, bound):
            if command[1] == "-vV":
                log.write_text("release: 1.92.0\nhost: " + DESKTOP.CARGO.TARGETS[self.args.target] + "\n")
            elif command[-1] == "-V":
                log.write_text("clippy unit-only\n")
            else:
                log.write_text("unit-only\n")
                if command[1] == "build":
                    binary.parent.mkdir(parents=True)
                    binary.write_bytes(b"unit-only-application-fixture")
            return {"unitOnly": True}

        def finalize(args, app, project, env):
            self.assertTrue((app.parent / "Info.plist").is_file())
            self.assertTrue((app.parent / "Frameworks/libcoakka_runtime_v2.dylib").is_file())
            self.assertTrue((app.parent / "Resources/native/macos-aarch64/NOTICE").is_file())
            self.assertFalse((app / "native").exists())
            return [{"unitOnly": True}]

        with patch.object(DESKTOP, "admit", return_value=(b"archive", helper, files)), \
                patch.object(DESKTOP.CARGO, "child", side_effect=child), \
                patch.object(DESKTOP, "finalize_macos_bundle", side_effect=finalize) as seal:
            DESKTOP.build(self.args)
        seal.assert_called_once()
        receipt = json.loads((self.args.work / "receipt.json").read_text())
        self.assertEqual(len(receipt["bundle_deployment"]["files"]), 6)
        self.assertEqual(receipt["bundle_deployment"]["files"]["rust/native-manifest.json"]["sha256"],
                         DESKTOP.CARGO.digest(b"unit-original-component-provenance"))
        self.assertFalse((self.args.work / "build").exists())

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
