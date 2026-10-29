#!/usr/bin/env python3
"""Admit a fixed private source package before a language sample builds it."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import signal
import stat
import sys
import tarfile
import zlib

MAX_ARCHIVE = 16 << 20
MAX_FILE = 16 << 20
MAX_EXPANDED = 64 << 20
MAX_MEMBERS = 512
MAX_TAR_BYTES = MAX_EXPANDED + MAX_MEMBERS * 1024 + 10240
MAX_JSON = 65536
LANGUAGES = ("mojo", "zig")
# Build-time payload inventory, not a probe or a language execution claim.
NATIVE_FILES = {
    "macos-aarch64": "libcoakka_runtime_v2.dylib",
    "linux-aarch64": "libcoakka_runtime_v2.so",
    "linux-x86_64": "libcoakka_runtime_v2.so",
    "windows-aarch64": "libcoakka_runtime_v2.dll",
    "windows-x86_64": "libcoakka_runtime_v2.dll",
}


def directory(path: Path) -> None:
    if not path.is_absolute() or path.resolve(strict=True) != path or not path.is_dir():
        raise ValueError("existing absolute non-linked directory required")
    for parent in (path, *path.parents):
        if getattr(parent.lstat(), "st_file_attributes", 0) & 0x400:
            raise ValueError("reparse directory refused")


def regular(path: Path, maximum: int) -> bytes:
    directory(path.parent)
    before = path.lstat()
    if (not stat.S_ISREG(before.st_mode) or getattr(before, "st_file_attributes", 0) & 0x400
            or not 0 < before.st_size <= maximum):
        raise ValueError("bounded regular input required")
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    flags |= getattr(os, "O_NONBLOCK", 0)
    # Nonblocking open also refuses a file-to-FIFO race without waiting for a writer.
    with os.fdopen(os.open(path, flags), "rb") as stream:
        opened = os.fstat(stream.fileno())
        if not stat.S_ISREG(opened.st_mode) or not os.path.samestat(before, opened):
            raise ValueError("input changed before read")
        content = stream.read(maximum + 1)
        after = os.fstat(stream.fileno())
    if (not os.path.samestat(opened, after) or len(content) != before.st_size
            or after.st_size != before.st_size or after.st_mtime_ns != before.st_mtime_ns):
        raise ValueError("input changed during read")
    return content


def unique_object(pairs: list[tuple[str, object]]) -> dict:
    result: dict = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate JSON member")
        result[key] = value
    return result


def json_object(content: bytes) -> dict:
    if not 0 < len(content) <= MAX_JSON:
        raise ValueError("bounded JSON required")
    result = json.loads(content, object_pairs_hook=unique_object)
    if not isinstance(result, dict):
        raise ValueError("JSON object required")
    return result


def profile(path: Path, language: str) -> dict:
    if language not in LANGUAGES:
        raise ValueError("unknown source-package language")
    value = json_object(regular(path, MAX_JSON))
    if (set(value) != {"schema", "state", "language", "version", "coreCommit", "archive"}
            or type(value["schema"]) is not int or value["schema"] != 1
            or value["state"] != "private-candidate" or value["language"] != language
            or not isinstance(value["version"], str)
            or re.fullmatch(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)", value["version"]) is None
            or not isinstance(value["coreCommit"], str)
            or re.fullmatch(r"[0-9a-f]{40}", value["coreCommit"]) is None):
        raise ValueError("private source-package catalog identity refused")
    archive = value["archive"]
    # Product metadata owns the version. The fixed source-package suffix
    # belongs to archive naming and must never be folded into that version.
    prefix = "coakka-runtime-" + language + "-" + value["version"] + "-source"
    if (not isinstance(archive, dict) or set(archive) != {"file", "prefix", "bytes", "sha256"}
            or archive["prefix"] != prefix or archive["file"] != prefix + ".tar.gz"
            or type(archive["bytes"]) is not int or not 0 < archive["bytes"] <= MAX_ARCHIVE
            or not isinstance(archive["sha256"], str)
            or re.fullmatch(r"[0-9a-f]{64}", archive["sha256"]) is None):
        raise ValueError("bounded source archive identity required")
    return value


def members(content: bytes, selected: dict) -> list[tuple[PurePosixPath, bytes | None, bool]]:
    identity = selected["archive"]
    if len(content) != identity["bytes"] or hashlib.sha256(content).hexdigest() != identity["sha256"]:
        raise ValueError("source archive identity refused")
    prefix = identity["prefix"]
    result: list[tuple[PurePosixPath, bytes | None, bool]] = []
    seen: set[str] = set()
    directories: set[str] = set()
    bodies: dict[str, bytes] = {}
    expanded = 0
    # Bound the whole decompressed stream before tarfile can consume extension
    # headers internally. Per-member checks alone cannot bound those headers.
    try:
        with gzip.GzipFile(fileobj=io.BytesIO(content)) as compressed:
            raw = compressed.read(MAX_TAR_BYTES + 1)
    except (EOFError, zlib.error) as error:
        raise ValueError("invalid compressed source archive") from error
    if len(raw) > MAX_TAR_BYTES:
        raise ValueError("source archive decompression bound")
    # Both byte snapshots are owned; changing an input path cannot replace them.
    with tarfile.open(fileobj=io.BytesIO(raw), mode="r:") as package:
        for item in package:
            if len(result) >= MAX_MEMBERS:
                raise ValueError("archive member bound")
            name = item.name
            path = PurePosixPath(name)
            canonical = str(path)
            if (not name or len(name.encode("utf-8")) > 512 or len(path.parts) > 24
                    or path.is_absolute() or not path.parts or path.parts[0] != prefix
                    or ".." in path.parts or "\\" in name or ":" in name
                    or any(ord(char) < 32 for char in name)
                    or name not in (canonical, canonical + "/")
                    or canonical in seen or not (item.isdir() or item.isfile())
                    or item.issparse() or item.pax_headers):
                raise ValueError("unsafe or duplicate source archive member")
            seen.add(canonical)
            if item.isdir():
                if item.size:
                    raise ValueError("nonempty archive directory")
                directories.add(canonical)
                result.append((path, None, False))
                continue
            expanded += item.size
            if not 0 <= item.size <= MAX_FILE or expanded > MAX_EXPANDED:
                raise ValueError("archive expansion bound")
            stream = package.extractfile(item)
            if stream is None:
                raise ValueError("archive member body missing")
            with stream:
                body = stream.read(item.size + 1)
            if len(body) != item.size:
                raise ValueError("archive member extent changed")
            bodies[canonical] = body
            result.append((path, body, bool(item.mode & 0o111)))
    for path, _, _ in result:
        for parent in path.parents:
            if str(parent) in seen and str(parent) not in directories:
                raise ValueError("archive parent is not a directory")
    metadata = json_object(bodies.get(prefix + "/coakka-runtime-package.json", b""))
    platforms = metadata.get("supported_platforms")
    if (metadata.get("artifact_name") != "coakka-runtime-" + selected["language"]
            or metadata.get("artifact_version") != selected["version"]
            or metadata.get("bundled_native_git_commit") != selected["coreCommit"]
            or not isinstance(platforms, list) or len(platforms) != len(NATIVE_FILES)
            or any(not isinstance(target, str) for target in platforms)
            or set(platforms) != set(NATIVE_FILES)):
        raise ValueError("source package metadata differs from catalog")
    for target, filename in NATIVE_FILES.items():
        if not bodies.get(prefix + "/native/" + target + "/" + filename):
            raise ValueError("source package native inventory incomplete")
    return result


def admit(language: str, archive: Path, work: Path, catalog: Path) -> dict:
    directory(work.parent)
    if not work.is_absolute() or work.exists() or work.is_symlink():
        raise ValueError("fresh absolute work directory required")
    selected = profile(catalog, language)
    entries = members(regular(archive, MAX_ARCHIVE), selected)
    # Every input and member is checked before this exclusive ownership fence.
    work.mkdir(mode=0o700)
    try:
        package = work / "package"
        package.mkdir()
        for relative, content, executable in entries:
            target = package.joinpath(*relative.parts)
            if content is None:
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with target.open("xb") as stream:
                    if stream.write(content) != len(content):
                        raise OSError("short source extraction write")
                target.chmod(0o755 if executable else 0o644)
        result = dict(state="admitted", language=language, version=selected["version"],
                      coreCommit=selected["coreCommit"], archiveSha256=selected["archive"]["sha256"],
                      packageRoot=str(package / selected["archive"]["prefix"]), workRoot=str(work))
        with (work / "admission.json").open("x") as receipt:
            json.dump(result, receipt, indent=2)
            receipt.write("\n")
        return result
    except BaseException:
        # Only the directory reserved above belongs to this invocation. Existing
        # work was refused; SIGINT/SIGTERM unwind through this same cleanup.
        shutil.rmtree(work)
        raise


def terminate(_signal: int, _frame: object) -> None:
    raise SystemExit(143)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--language", required=True, choices=LANGUAGES)
    parser.add_argument("--archive", required=True, type=Path)
    parser.add_argument("--work", required=True, type=Path)
    args = parser.parse_args()
    if sys.version_info < (3, 11):
        parser.error("Python 3.11 or later is a build-only requirement")
    signal.signal(signal.SIGTERM, terminate)
    catalog = Path(__file__).resolve().parents[1] / "artifacts" / ("runtime-" + args.language + "-candidate.json")
    try:
        print(json.dumps(admit(args.language, args.archive, args.work, catalog), sort_keys=True))
    except (OSError, ValueError, RecursionError, tarfile.TarError) as error:
        parser.exit(1, "[source-candidate] " + str(error) + "\n")


if __name__ == "__main__":
    main()
