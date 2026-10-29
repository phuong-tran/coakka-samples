#!/usr/bin/env python3
"""Cold artifact controls only; these fixtures never replace Core runtime tests."""
from __future__ import annotations

import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import tarfile
import tempfile
import time
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("source_candidate", Path(__file__).with_name("runtime-source-candidate.py"))
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class SourceAdmissionTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="source-admission-", dir=os.environ["COAKKA_DOC_TEST_ROOT"])
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name).resolve()
        self.archive = self.root / "candidate.tar.gz"
        self.catalog = self.root / "catalog.json"
        self.work = self.root / "fresh work-é"
        self.prefix = "coakka-runtime-mojo-3.0.0-source"
        self.metadata = dict(artifact_name="coakka-runtime-mojo", artifact_version="3.0.0",
                             bundled_native_git_commit="f" * 40, supported_platforms=list(MODULE.NATIVE_FILES))

    def normal_members(self):
        result = [(self.prefix + "/coakka-runtime-package.json", json.dumps(self.metadata).encode(), tarfile.REGTYPE)]
        result += [(self.prefix + "/native/" + target + "/" + name, b"fixture-not-a-runtime", tarfile.REGTYPE)
                   for target, name in MODULE.NATIVE_FILES.items()]
        result.append((self.prefix + "/scripts/smoke.sh", b"exit 0\n", tarfile.REGTYPE))
        return result

    def fixture(self, items=None):
        data = io.BytesIO()
        with tarfile.open(fileobj=data, mode="w:gz") as bundle:
            for name, content, kind in self.normal_members() if items is None else items:
                item = tarfile.TarInfo(name)
                item.type = kind
                item.mode = 0o755
                item.linkname = "outside"
                item.size = len(content) if kind == tarfile.REGTYPE else 0
                bundle.addfile(item, io.BytesIO(content) if kind == tarfile.REGTYPE else None)
        body = data.getvalue()
        self.archive.write_bytes(body)
        value = dict(schema=1, state="private-candidate", language="mojo", version="3.0.0",
                     coreCommit="f" * 40, archive=dict(file=self.prefix + ".tar.gz", prefix=self.prefix,
                     bytes=len(body), sha256=hashlib.sha256(body).hexdigest()))
        self.catalog.write_text(json.dumps(value))
        return value

    def admit(self):
        return MODULE.admit("mojo", self.archive, self.work, self.catalog)

    def refused(self):
        with self.assertRaises((ValueError, OSError, tarfile.TarError)):
            self.admit()
        self.assertFalse(self.work.exists())

    def test_exact_package_reserves_fresh_work_and_records_identity(self):
        value = self.fixture()
        result = self.admit()
        self.assertEqual(result["archiveSha256"], value["archive"]["sha256"])
        self.assertEqual(Path(result["packageRoot"]), self.work / "package" / self.prefix)
        self.assertEqual(json.loads((self.work / "admission.json").read_text()), result)
        self.assertEqual((Path(result["packageRoot"]) / "scripts/smoke.sh").read_bytes(), b"exit 0\n")

    def test_wrong_bytes_or_extent_refuse_before_tar_and_output(self):
        value = self.fixture()
        for key, wrong in (("sha256", "0" * 64), ("bytes", value["archive"]["bytes"] + 1)):
            changed = json.loads(json.dumps(value))
            changed["archive"][key] = wrong
            self.catalog.write_text(json.dumps(changed))
            with patch.object(MODULE.tarfile, "open") as opened:
                self.refused()
                opened.assert_not_called()

    def test_product_version_and_source_archive_suffix_are_distinct_facts(self):
        value = self.fixture()
        selected = MODULE.profile(self.catalog, "mojo")
        self.assertEqual(selected["version"], "3.0.0")
        self.assertEqual(selected["archive"]["prefix"], self.prefix)
        for wrong in ("3.0.0-source", "03.0.0", "3.00.0", "3.0", "3.0.0+build"):
            changed = dict(value, version=wrong)
            self.catalog.write_text(json.dumps(changed))
            self.refused()
        self.metadata["artifact_version"] = "3.0.1"
        self.fixture()
        with self.assertRaisesRegex(ValueError, "metadata differs from catalog"):
            self.admit()
        self.assertFalse(self.work.exists())

    def test_decompression_is_bounded_before_extension_header_parsing(self):
        self.fixture()
        with patch.object(MODULE, "MAX_TAR_BYTES", 1024), patch.object(MODULE.tarfile, "open") as opened:
            self.refused()
            opened.assert_not_called()

    def test_catalog_identity_types_unknowns_and_duplicate_keys_refuse(self):
        value = self.fixture()
        for key, wrong in (("schema", True), ("state", "published"), ("language", "zig"),
                           ("version", "../3.0"), ("coreCommit", "f" * 41), ("extra", 1)):
            changed = dict(value)
            changed[key] = wrong
            self.catalog.write_text(json.dumps(changed))
            self.refused()
        self.catalog.write_text('{"schema":1,"schema":1}')
        self.refused()

    def test_archive_descriptor_types_and_bounds_refuse(self):
        value = self.fixture()
        for key, wrong in (("bytes", True), ("bytes", MODULE.MAX_ARCHIVE + 1),
                           ("sha256", "X" * 64), ("prefix", "other"), ("file", "other.tar.gz"),
                           ("extra", "ignored")):
            changed = json.loads(json.dumps(value))
            changed["archive"][key] = wrong
            self.catalog.write_text(json.dumps(changed))
            self.refused()

    def test_existing_work_is_preserved(self):
        self.fixture()
        self.work.mkdir()
        sentinel = self.work / "user-data"
        sentinel.write_bytes(b"preserve")
        with self.assertRaises(ValueError):
            self.admit()
        self.assertEqual(sentinel.read_bytes(), b"preserve")

    def test_linked_archive_and_parent_refuse(self):
        self.fixture()
        original = self.archive.read_bytes()
        saved = self.root / "original"
        self.archive.rename(saved)
        self.archive.symlink_to(saved)
        self.refused()
        self.archive.unlink()
        self.archive.write_bytes(original)
        outside = self.root / "outside"
        outside.mkdir()
        linked = self.root / "linked"
        linked.symlink_to(outside, target_is_directory=True)
        self.work = linked / "work"
        self.refused()
        self.assertEqual(list(outside.iterdir()), [])

    @unittest.skipUnless(hasattr(os, "mkfifo"), "POSIX FIFO fixture")
    def test_fifo_input_refuses_without_waiting(self):
        self.fixture()
        self.archive.unlink()
        os.mkfifo(self.archive)
        start = time.monotonic()
        self.refused()
        self.assertLess(time.monotonic() - start, 0.5)

    def test_unsafe_paths_refuse_before_output(self):
        for suffix in ("/../outside", "//duplicate", "/./dot", "/a\\escape", "/a:stream", "/bad\nname"):
            self.fixture(self.normal_members() + [(self.prefix + suffix, b"x", tarfile.REGTYPE)])
            self.refused()
        self.fixture(self.normal_members() + [("/absolute", b"x", tarfile.REGTYPE)])
        self.refused()

    def test_links_special_and_duplicate_members_refuse(self):
        for kind in (tarfile.SYMTYPE, tarfile.LNKTYPE, tarfile.FIFOTYPE, tarfile.CHRTYPE):
            self.fixture(self.normal_members() + [(self.prefix + "/special", b"", kind)])
            self.refused()
        items = self.normal_members()
        self.fixture(items + [items[-1]])
        self.refused()

    def test_file_parent_conflict_refuses_before_reservation(self):
        self.fixture(self.normal_members() + [(self.prefix + "/file", b"x", tarfile.REGTYPE),
                                             (self.prefix + "/file/child", b"x", tarfile.REGTYPE)])
        self.refused()

    def test_unexpected_pax_metadata_refuses_before_output(self):
        data = io.BytesIO()
        with tarfile.open(fileobj=data, mode="w:gz") as bundle:
            item = tarfile.TarInfo(self.prefix + "/private")
            item.pax_headers = {"LIBARCHIVE.xattr.com.apple.provenance": "private"}
            item.size = 1
            bundle.addfile(item, io.BytesIO(b"x"))
        value = self.fixture()
        body = data.getvalue()
        value["archive"].update(bytes=len(body), sha256=hashlib.sha256(body).hexdigest())
        self.archive.write_bytes(body)
        self.catalog.write_text(json.dumps(value))
        self.refused()

    def test_member_expansion_and_count_limits(self):
        self.fixture()
        for name, bound in (("MAX_MEMBERS", 2), ("MAX_FILE", 2), ("MAX_EXPANDED", 2)):
            with patch.object(MODULE, name, bound):
                self.refused()

    def test_metadata_and_native_inventory_are_actual_archive_facts(self):
        for key, wrong in (("artifact_name", "other"), ("artifact_version", "2.5.2-source"),
                           ("bundled_native_git_commit", "0" * 40), ("supported_platforms", ["macos-aarch64"])):
            original = dict(self.metadata)
            self.metadata[key] = wrong
            self.fixture()
            self.refused()
            self.metadata = original
        self.fixture(self.normal_members()[1:])
        self.refused()
        self.fixture([row for row in self.normal_members() if "/linux-x86_64/" not in row[0]])
        self.refused()

    def test_extraction_failure_and_interruption_retire_only_reserved_work(self):
        self.fixture()
        for error in (OSError("injected write"), KeyboardInterrupt("injected interrupt")):
            with patch.object(Path, "chmod", side_effect=error):
                with self.assertRaises(type(error)):
                    self.admit()
            self.assertFalse(self.work.exists())
            self.assertTrue(self.archive.is_file())
            self.assertTrue(self.catalog.is_file())

    def test_oversized_catalog_and_relative_work_refuse(self):
        self.fixture()
        self.catalog.write_bytes(b"x" * (MODULE.MAX_JSON + 1))
        self.refused()
        self.fixture()
        self.work = Path("relative-work")
        self.refused()


if __name__ == "__main__":
    unittest.main()
