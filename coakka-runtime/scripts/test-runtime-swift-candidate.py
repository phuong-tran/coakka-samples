"""Cold source-package admission controls; no Core load or compiler graph."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("swift_candidate", Path(__file__).with_name("runtime-swift-candidate.py"))
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class AdmissionControls(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="swift-controls-", dir=os.environ.get("COAKKA_DOC_TEST_ROOT"))
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.package = self.root / "candidate"
        self.package.mkdir()
        names = ["Package.swift", "coakka-runtime-package.json", "core-abi-manifest.json", "LICENSE", "NOTICE", "NATIVE-LICENSE.md", "PACKAGE-LICENSE.md"]
        names += [f"Sources/owned-{index}.swift" for index in range(50)]
        self.rows = {}
        for name in names:
            content = name.encode()
            path = self.package / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
            self.rows[name] = dict(bytes=len(content), sha256=hashlib.sha256(content).hexdigest())
        self.value = dict(schema=1, state="private-candidate", version="3.0.0",
            coreCommit="1522b4a9f30da1039ef8c7d198bbecfc749a2456", files=self.rows,
            languageFiles={name: {} for name in ("macos-aarch64", "linux-aarch64", "linux-x86_64", "windows-aarch64", "windows-x86_64")})

    def test_exact_member_copy(self):
        actual = MODULE.catalog(json.dumps(self.value).encode())
        target = self.root / "copy"
        MODULE.copy_package(self.package, target, actual["files"])
        self.assertEqual({p.relative_to(target).as_posix() for p in target.rglob("*") if p.is_file()}, self.rows.keys())

    def test_catalog_duplicate_unknown_version_bounds(self):
        with self.assertRaises(ValueError):
            MODULE.catalog(b'{"schema":1,"schema":1}')
        for field, wrong in (("version", "2.5.3"), ("state", "published"), ("schema", True), ("coreCommit", "0" * 40)):
            value = dict(self.value, **{field: wrong})
            with self.subTest(field=field), self.assertRaises(ValueError):
                MODULE.catalog(json.dumps(value).encode())
        with self.assertRaises(ValueError):
            MODULE.catalog(b" " * ((256 << 10) + 1))

    def test_traversal_and_identity_refuse(self):
        for name in ("../escape", "/absolute", "a//b", "a/./b", "C:/drive", "a\\b", ""):
            self.assertFalse(MODULE.member(name), name)
        for row in ({"bytes": True, "sha256": "0" * 64}, {"bytes": 1 << 30, "sha256": "0" * 64},
                    {"bytes": 1, "sha256": "G" * 64}, {"bytes": 1, "sha256": "0" * 63}):
            with self.assertRaises(ValueError):
                MODULE.identity(row)

    def test_missing_extra_and_nonregular_refuse(self):
        (self.package / "foreign").write_text("reject")
        with self.assertRaises(ValueError):
            MODULE.copy_package(self.package, self.root / "bad1", self.rows)
        (self.package / "foreign").unlink()
        (self.package / "NOTICE").unlink()
        with self.assertRaises(ValueError):
            MODULE.copy_package(self.package, self.root / "bad2", self.rows)

    def test_size_and_hash_refuse(self):
        path = self.package / "LICENSE"
        for content in (b"short", b"LICENsE"):
            path.write_bytes(content)
            with self.assertRaises(ValueError):
                MODULE.copy_package(self.package, self.root / content.decode(), self.rows)

    def test_link_and_nonabsolute_root_refuse(self):
        linked = self.root / "link"
        try:
            linked.symlink_to(self.package, target_is_directory=True)
        except OSError:
            if sys.platform != "win32":
                raise
            # Ordinary NTFS junction creation needs no Developer Mode/admin grant.
            subprocess.run(["cmd.exe", "/d", "/c", "mklink", "/J", str(linked), str(self.package)],
                           capture_output=True, check=True, timeout=10)
        with self.assertRaises(ValueError):
            MODULE.directory(linked)
        with self.assertRaises(ValueError):
            MODULE.directory(Path("relative"))
        member = self.package / "Sources"
        outside = self.root / "owned-sources"
        member.rename(outside)
        if sys.platform == "win32":
            subprocess.run(["cmd.exe", "/d", "/c", "mklink", "/J", str(member), str(outside)],
                           capture_output=True, check=True, timeout=10)
        else:
            member.symlink_to(outside, target_is_directory=True)
        with self.assertRaises(ValueError):
            MODULE.copy_package(self.package, self.root / "bad", self.rows)

    def test_regular_file_read_bound(self):
        path = self.package / "NOTICE"
        self.assertEqual(MODULE.regular(path, 128), b"NOTICE")
        with self.assertRaises(ValueError):
            MODULE.regular(path, 1)
        with self.assertRaises(ValueError):
            MODULE.regular(self.package, 128)


class BuildProfileControls(unittest.TestCase):
    def test_windows_x64_importer_and_native_c_flags(self):
        flags = MODULE.build_flags("windows", "windows-x86_64")
        swift_args, c_args = [], []
        index = 0
        while index < len(flags):
            if flags[index] in ("-Xswiftc", "-Xcc"):
                (swift_args if flags[index] == "-Xswiftc" else c_args).append(flags[index + 1])
                index += 2
            else:
                index += 1
        self.assertEqual(c_args, ["-Wall", "-Wextra", "-Werror"])
        self.assertEqual(swift_args[-4:], ["-Xcc", "-Xclang", "-Xcc", "-fbuiltin-headers-in-system-modules"])
        self.assertEqual(flags.count("-fbuiltin-headers-in-system-modules"), 1)
        self.assertIn("/WX", flags)
        self.assertNotIn("--build-system", flags)

    def test_builtin_mode_is_only_windows_x64_and_no_overlay(self):
        profiles = [("mac", "macos-aarch64"), ("pi", "linux-aarch64"),
            ("pi", "linux-x86_64"), ("windows", "windows-aarch64"), ("windows", "windows-x86_64")]
        for host, target in profiles:
            with self.subTest(target=target):
                flags = MODULE.build_flags(host, target)
                self.assertEqual("-fbuiltin-headers-in-system-modules" in flags, target == "windows-x86_64")
                self.assertNotIn("-vfsoverlay", flags)
                self.assertNotIn("-ivfsoverlay", flags)
        arm = MODULE.build_flags("windows", "windows-aarch64")
        self.assertEqual(arm[arm.index("--build-system"):][:2], ["--build-system", "native"])

    def test_profile_lists_do_not_share_mutable_state(self):
        flags = MODULE.build_flags("windows", "windows-x86_64")
        flags.clear()
        self.assertIn("-warnings-as-errors", MODULE.build_flags("windows", "windows-x86_64"))


if __name__ == "__main__":
    unittest.main()
