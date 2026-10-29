#!/usr/bin/env python3
"""Focused producer-input controls; real native verification runs separately."""
from __future__ import annotations

import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import unittest
from unittest.mock import patch
import zipfile

SPEC = importlib.util.spec_from_file_location("prebuilt_core", Path(__file__).with_name("stage-prebuilt-core.py"))
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
COMMIT = "f" * 40


class ProducerInputTests(unittest.TestCase):
    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory(
            prefix="android-producer-control-", dir=os.environ["COAKKA_ANDROID_TEST_ROOT"],
        )
        self.addCleanup(temporary.cleanup)
        self.work = Path(temporary.name)
        self.root = self.work / "module"
        self.root.mkdir()
        self.input = self.work / "input.aar"
        with zipfile.ZipFile(self.input, "w") as archive:
            for abi in MODULE.ABIS:
                archive.writestr(f"jni/{abi}/libcoakka_runtime_v2.so", abi.encode())
        self.digest = hashlib.sha256(self.input.read_bytes()).hexdigest()
        self.source = {"v2/include/test.h": b"owned source"}

    def stage(self, verify_only: bool = False) -> dict:
        return MODULE.stage(self.root, self.work, self.input, self.digest, COMMIT, verify_only)

    def no_images(self) -> None:
        self.assertFalse((self.root / "src/main/jniLibs").exists())

    def test_missing_trusted_digest_refuses_before_file_access(self) -> None:
        with self.assertRaisesRegex(ValueError, "trusted exact"):
            MODULE.stage(self.root, self.work, self.work / "absent", "", COMMIT, False)
        self.assertEqual(list(self.root.iterdir()), [])

    def test_wrong_digest_refuses_before_source_or_outputs(self) -> None:
        self.input.write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "digest differs"):
            self.stage()
        self.assertEqual(list(self.root.iterdir()), [])

    def test_invalid_commit_refuses_before_source_or_outputs(self) -> None:
        with self.assertRaisesRegex(ValueError, "exact Core commit"):
            MODULE.stage(self.root, self.work, self.input, self.digest, "not-a-commit", False)
        self.assertEqual(list(self.root.iterdir()), [])

    def test_linked_regular_input_refuses(self) -> None:
        link = self.work / "linked.aar"
        link.symlink_to(self.input)
        with self.assertRaises(OSError):
            MODULE.bounded_file(link, 1024)

    def test_fifo_input_refuses_without_waiting(self) -> None:
        fifo = self.work / "fifo"
        os.mkfifo(fifo)
        start = time.monotonic()
        with self.assertRaisesRegex(ValueError, "bounded regular"):
            MODULE.bounded_file(fifo, 1024)
        self.assertLess(time.monotonic() - start, 0.5)

    def test_input_extent_is_bounded(self) -> None:
        with self.assertRaisesRegex(ValueError, "bounded regular"):
            MODULE.bounded_file(self.input, 1)

    def test_output_ancestor_link_refuses(self) -> None:
        outside = self.work / "outside"
        outside.mkdir()
        (self.root / "child").symlink_to(outside)
        with self.assertRaisesRegex(ValueError, "symlink"):
            MODULE.guarded_destination(self.root, self.root / "child/file")
        self.assertEqual(list(outside.iterdir()), [])

    def test_foreign_pinned_source_refuses(self) -> None:
        source = self.root / ".native-source" / COMMIT / "v2/include/test.h"
        source.parent.mkdir(parents=True)
        source.write_bytes(b"foreign")
        with patch.object(MODULE, "pinned_sources", return_value=self.source):
            with self.assertRaisesRegex(ValueError, "source differs"):
                self.stage()
        self.assertEqual(source.read_bytes(), b"foreign")
        self.no_images()

    def test_receipt_link_refuses_before_image_output(self) -> None:
        receipt = self.root / ".native-source" / COMMIT / ".prebuilt-core-receipt.json"
        receipt.parent.mkdir(parents=True)
        outside = self.work / "outside"
        outside.write_bytes(b"foreign")
        receipt.symlink_to(outside)
        with patch.object(MODULE, "pinned_sources", return_value=self.source):
            with self.assertRaisesRegex(ValueError, "symlink"):
                self.stage()
        self.assertEqual(outside.read_bytes(), b"foreign")
        self.no_images()

    def test_later_image_link_refuses_before_any_image_write(self) -> None:
        image = self.root / "src/main/jniLibs/x86_64/libcoakka_runtime_v2.so"
        image.parent.mkdir(parents=True)
        outside = self.work / "outside"
        outside.write_bytes(b"foreign")
        image.symlink_to(outside)
        with patch.object(MODULE, "pinned_sources", return_value=self.source), patch.object(MODULE, "run", return_value=b'{"status":"test-only-accepted"}'):
            with self.assertRaisesRegex(ValueError, "symlink"):
                self.stage()
        self.assertFalse((self.root / "src/main/jniLibs/arm64-v8a/libcoakka_runtime_v2.so").exists())
        self.assertEqual(outside.read_bytes(), b"foreign")

    def test_failed_real_verifier_handoff_never_stages_images(self) -> None:
        def command(argv: list[str]) -> bytes:
            if argv[0] == "cmake":
                return b""
            raise RuntimeError("owned release verifier refused")
        with patch.object(MODULE, "pinned_sources", return_value=self.source), patch.object(MODULE, "run", side_effect=command):
            with self.assertRaisesRegex(RuntimeError, "release verifier refused"):
                self.stage()
        self.no_images()
        temporary_root = Path(os.environ["COAKKA_ANDROID_VERIFY_TMP_ROOT"])
        self.assertEqual(list(temporary_root.glob("prebuilt-core-*")), [])

    def test_only_verified_core_bytes_are_staged(self) -> None:
        original = self.input.read_bytes()
        with patch.object(MODULE, "pinned_sources", return_value=self.source), patch.object(MODULE, "run", return_value=b'{"status":"test-only-accepted"}'):
            result = self.stage()
        self.assertFalse(result["native_core_rebuilt"])
        for abi in MODULE.ABIS:
            self.assertEqual((self.root / f"src/main/jniLibs/{abi}/libcoakka_runtime_v2.so").read_bytes(), abi.encode())
        self.assertEqual(self.input.read_bytes(), original)

    def test_verify_only_preserves_consumer_output_absence(self) -> None:
        with patch.object(MODULE, "pinned_sources", return_value=self.source), patch.object(MODULE, "run", return_value=b'{"status":"test-only-accepted"}'):
            result = self.stage(verify_only=True)
        self.assertTrue(result["verify_only"])
        self.no_images()

    def test_input_mutation_after_snapshot_refuses_before_outputs(self) -> None:
        def command(argv: list[str]) -> bytes:
            if argv[0] != "cmake":
                self.input.write_bytes(b"mutated while validation ran")
            return json.dumps({"status": "test-only-accepted"}).encode()
        with patch.object(MODULE, "pinned_sources", return_value=self.source), patch.object(MODULE, "run", side_effect=command):
            with self.assertRaisesRegex(ValueError, "changed during verification"):
                self.stage()
        self.no_images()

    def test_bash_entrypoint_handles_build_and_verify_modes(self) -> None:
        scripts = self.root / "scripts"
        scripts.mkdir()
        owner = Path(__file__).resolve().parent
        for name in ("build-native-runtime.sh", "load-release-identity.sh"):
            shutil.copyfile(owner / name, scripts / name)
        shutil.copyfile(owner.parent / "release-identity.properties", self.root / "release-identity.properties")
        tools = self.work / "tools"
        tools.mkdir()
        # This seam tests shell argument construction only. Real exact-source
        # and eight-image verification have separate non-mocked evidence.
        (tools / "git").write_text("#!/bin/sh\nexit 0\n")
        (tools / "python3").write_text('#!/bin/sh\nprintf "%s\\n" "$@" > "$COAKKA_TEST_CAPTURE"\n')
        for path in tools.iterdir():
            path.chmod(0o700)
        capture = self.work / "arguments"
        env = os.environ.copy()
        env.update(PATH=str(tools) + os.pathsep + env["PATH"], COAKKA_CORE_REPO=str(self.work),
                   COAKKA_ANDROID_PREBUILT_CORE_AAR=str(self.input),
                   COAKKA_ANDROID_PREBUILT_CORE_SHA256=self.digest,
                   COAKKA_TEST_CAPTURE=str(capture))
        for mode in ("--build", "--verify-source"):
            result = subprocess.run(["/bin/bash", str(scripts / "build-native-runtime.sh"), mode],
                                    env=env, capture_output=True, text=True, timeout=5)
            self.assertEqual(result.returncode, 0, result.stderr)
            arguments = capture.read_text().splitlines()
            self.assertEqual(arguments.count("--verify-only"), int(mode == "--verify-source"))
            self.assertIn(str(self.input), arguments)


if __name__ == "__main__":
    unittest.main()
