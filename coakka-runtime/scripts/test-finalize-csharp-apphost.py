"""Cold metadata-promotion controls; synthetic PE files are never executed."""
import hashlib
import importlib.util
import os
from pathlib import Path
import struct
import tempfile
import types
import unittest
from unittest import mock

SPEC = importlib.util.spec_from_file_location('apphost_promotion', Path(__file__).with_name('finalize-csharp-apphost.py'))
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
TOOL = Path(__file__).with_name('pe_release_metadata.py')
TOOL_SHA = hashlib.sha256(TOOL.read_bytes()).hexdigest()


def fixture():
    data = bytearray(2048)
    data[:2] = b'MZ'
    struct.pack_into('<I', data, 60, 64)
    data[64:68] = b'PE\0\0'
    struct.pack_into('<HHIIIHH', data, 68, 0x8664, 3, 0, 0, 0, 240, 0x22)
    struct.pack_into('<H', data, 88, 0x20b)
    struct.pack_into('<I', data, 104, 0x1000)
    struct.pack_into('<III', data, 144, 0x4000, 512, 0)
    struct.pack_into('<I', data, 196, 16)
    struct.pack_into('<II', data, 248, 0x2000, 84)
    for index, name in enumerate((b'.text', b'.rdata', b'.pdata')):
        offset = 328 + index * 40
        data[offset:offset + 8] = name.ljust(8, b'\0')
        struct.pack_into('<IIII', data, offset + 8, 512, (index + 1) * 4096, 512, (index + 1) * 512)
        struct.pack_into('<I', data, offset + 36, 0x60000020 if index == 0 else 0x40000040)
    data[512:1024] = bytes(range(256)) * 2
    payloads = ((2, 1140, b'RSDS' + bytes(20) + b'apphost.pdb\0'),
        (12, 1200, bytes(20)), (13, 1240, b'GCTL' + struct.pack('<II', 0x1000, 512) + b'.text\0\0\0'))
    for index, (kind, position, payload) in enumerate(payloads):
        struct.pack_into('<IIHHIIII', data, 1024 + index * 28, 0, 1, 0, 0, kind, len(payload), position + 0x1c00, position)
        data[position:position + len(payload)] = payload
    return data


class PromotionTests(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory(prefix='apphost-promotion-', dir=os.environ.get('COAKKA_DOC_TEST_ROOT'))
        self.addCleanup(folder.cleanup)
        self.root = Path(folder.name).resolve()
        self.publish, self.work = self.root / 'published', self.root / 'work'
        self.publish.mkdir()
        self.work.mkdir()
        self.image = self.publish / 'app.exe'
        self.original = fixture()
        self.image.write_bytes(self.original)
        self.raw = hashlib.sha256(self.original).hexdigest()
        owner = MODULE.utility(TOOL, TOOL_SHA)
        self.final = hashlib.sha256(owner.stripped_apphost_image(self.original)[0]).hexdigest()

    def run_finalizer(self, **overrides):
        arguments = dict(source=self.image, work=self.work, size=len(self.original), raw=self.raw,
            final=self.final, tool=TOOL, tool_digest=TOOL_SHA)
        arguments.update(overrides)
        return MODULE.finalize(**arguments)

    def test_exact_promotion_private_original_and_idempotence(self):
        self.assertEqual(self.run_finalizer()['status'], 'finalized')
        self.assertEqual(hashlib.sha256(self.image.read_bytes()).hexdigest(), self.final)
        private = self.work / 'csharp/apphost-private' / (self.raw + '.exe')
        self.assertEqual(private.read_bytes(), self.original)
        stamp = self.image.stat().st_mtime_ns
        self.assertEqual(self.run_finalizer()['status'], 'verified-already-finalized')
        self.assertEqual(stamp, self.image.stat().st_mtime_ns)
        self.assertEqual(list(self.publish.iterdir()), [self.image])

    def test_foreign_size_pins_and_utility_refuse_before_promotion(self):
        for arguments in (dict(size=0), dict(size=len(self.original) + 1), dict(raw='0' * 64),
            dict(raw=self.raw.upper()), dict(tool_digest='0' * 64), dict(final=self.raw)):
            with self.subTest(arguments=arguments), self.assertRaises((OSError, ValueError)):
                self.run_finalizer(**arguments)
            self.assertEqual(self.image.read_bytes(), self.original)
            self.assertEqual(list(self.publish.iterdir()), [self.image])

    def test_paths_and_private_publish_overlap_refuse(self):
        for arguments in (dict(work=Path('relative')), dict(source=Path('relative.exe')), dict(work=self.publish)):
            with self.subTest(arguments=arguments), self.assertRaises((OSError, ValueError)):
                self.run_finalizer(**arguments)
            self.assertEqual(self.image.read_bytes(), self.original)

    def test_foreign_private_original_is_not_overwritten(self):
        private = self.work / 'csharp/apphost-private'
        private.mkdir(parents=True)
        target = private / (self.raw + '.exe')
        target.write_bytes(b'foreign')
        with self.assertRaises(ValueError):
            self.run_finalizer()
        self.assertEqual(target.read_bytes(), b'foreign')
        self.assertEqual(self.image.read_bytes(), self.original)

    def test_wrong_final_pin_cleans_only_owned_stage(self):
        with self.assertRaises(ValueError):
            self.run_finalizer(final='0' * 64)
        self.assertEqual(list(self.publish.iterdir()), [self.image])
        self.assertEqual(self.image.read_bytes(), self.original)

    def test_stage_collision_never_removes_foreign_file(self):
        collision = self.publish / '.coakka-apphost-collision.exe'
        collision.write_bytes(b'foreign')
        with mock.patch.object(MODULE.uuid, 'uuid4', return_value=types.SimpleNamespace(hex='collision')):
            with self.assertRaises(FileExistsError):
                self.run_finalizer()
        self.assertEqual(collision.read_bytes(), b'foreign')
        self.assertEqual(self.image.read_bytes(), self.original)

    def test_promotion_failure_and_interruption_preserve_raw(self):
        for failure in (OSError('replace'), KeyboardInterrupt()):
            with mock.patch.object(MODULE.os, 'replace', side_effect=failure):
                with self.assertRaises(type(failure)):
                    self.run_finalizer()
            self.assertEqual(self.image.read_bytes(), self.original)
            self.assertEqual(list(self.publish.iterdir()), [self.image])

    def test_backup_sync_failure_retires_partial_not_input(self):
        with mock.patch.object(MODULE.os, 'fsync', side_effect=OSError('sync')):
            with self.assertRaises(OSError):
                self.run_finalizer()
        self.assertEqual(list((self.work / 'csharp/apphost-private').iterdir()), [])
        self.assertEqual(self.image.read_bytes(), self.original)


if __name__ == '__main__':
    unittest.main()
