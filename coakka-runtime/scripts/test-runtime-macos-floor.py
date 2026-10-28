#!/usr/bin/env python3
"""Cold artifact fixtures only; these do not replace matching-host execution."""
import importlib.util
import os
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("macos_floor", Path(__file__).with_name("runtime-macos-floor.py"))
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def command(version=13 << 16, platform=1):
    return struct.pack("<6I", 0x32, 24, platform, version, 26 << 16, 0)


def image(commands=None, **changes):
    commands = [command()] if commands is None else commands
    values = dict(magic=0xFEEDFACF, cpu=0x0100000C, subtype=0, kind=2,
                  count=len(commands), extent=sum(map(len, commands)), flags=0, reserved=0)
    values.update(changes)
    return struct.pack("<8I", *values.values()) + b"".join(commands)


class DeploymentTests(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory(dir=os.environ["COAKKA_DOC_TEST_ROOT"], prefix="macos-floor-")
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name).resolve()

    def test_build_and_legacy_commands_preserve_exact_versions(self):
        self.assertEqual(MODULE.minimum(image()), (13, 0, 0))
        value = 13 << 16 | 7 << 8 | 4
        self.assertEqual(MODULE.minimum(image([command(value)])), (13, 7, 4))
        self.assertEqual(MODULE.minimum(image([struct.pack("<4I", 0x24, 16, 11 << 16, 13 << 16)])), (11, 0, 0))

    def test_identity_bounds_and_unknown_platform_refuse(self):
        for change in ({"magic": 0xCAFEBABE}, {"cpu": 0x01000007}, {"kind": 1},
                       {"count": 0}, {"count": 4097}, {"extent": 1 << 21}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                MODULE.minimum(image(**change))
        with self.assertRaises(ValueError):
            MODULE.minimum(image([command(platform=2)]))
        for data in (b"", b"x" * 31):
            with self.assertRaises(ValueError):
                MODULE.minimum(data)

    def test_truncation_alignment_and_command_count_refuse(self):
        for data in (image()[:-1], image(count=2), image(extent=25) + b"x",
                     image([struct.pack("<2I", 0x32, 0)]),
                     image([struct.pack("<2I", 0x32, 7)]),
                     image([struct.pack("<2I", 0x32, 40)])):
            with self.subTest(size=len(data)), self.assertRaises(ValueError):
                MODULE.minimum(data)

    def test_missing_ambiguous_or_malformed_versions_refuse(self):
        for data in (image([struct.pack("<2I", 0x1B, 8)]), image([command(), command()]),
                     image([struct.pack("<2I", 0x32, 8)]), image([command(0)]),
                     image([struct.pack("<6I", 0x32, 24, 1, 13 << 16, 0, 1)]),
                     image([struct.pack("<6I", 0x24, 24, 13 << 16, 0, 0, 0)])):
            with self.subTest(size=len(data)), self.assertRaises(ValueError):
                MODULE.minimum(data)

    def test_component_floor_refuses_host_sdk_accident_without_rewriting(self):
        path = self.root / "application"
        for version, accepted in ((11 << 16, True), (13 << 16, True),
                                  (13 << 16 | 1, False), (26 << 16, False)):
            data = image([command(version)])
            path.write_bytes(data)
            if accepted:
                self.assertEqual(MODULE.inspect(path)["file"], "application")
            else:
                with self.assertRaisesRegex(ValueError, "exceeds macOS 13"):
                    MODULE.inspect(path)
            self.assertEqual(path.read_bytes(), data)

    def test_regular_nonlinked_inputs_and_read_races_refuse(self):
        path = self.root / "application"
        path.write_bytes(image())
        alias = self.root / "alias"
        alias.symlink_to(path)
        with self.assertRaises(ValueError):
            MODULE.inspect(alias)
        parent = self.root / "parent"
        parent.symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(ValueError):
            MODULE.inspect(parent / path.name)
        with self.assertRaises(ValueError):
            MODULE.inspect(Path("relative"))
        with patch.object(MODULE.os.path, "samestat", return_value=False):
            with self.assertRaisesRegex(ValueError, "changed before"):
                MODULE.inspect(path)


if __name__ == "__main__":
    unittest.main()
