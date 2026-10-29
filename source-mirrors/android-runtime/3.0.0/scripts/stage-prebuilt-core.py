#!/usr/bin/env python3
"""Reuse verified Android Core images while rebuilding only the owned JNI bridge."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import tempfile
import zipfile

MAX_AAR_BYTES = 128 * 1024 * 1024
MAX_SOURCE_BYTES = 8 * 1024 * 1024
ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
SOURCE_PATHS = (
    "v2/include", "v2/proto", "v2/CMakeLists.txt",
    "v2/cmake/GenerateCoakkaRuntimeExports.cmake",
    "v2/scripts/verify_native_binary_identity.py",
    "v2/scripts/pe_release_metadata.py",
)


def bounded_file(path: Path, limit: int) -> bytes:
    descriptor = os.open(path, os.O_RDONLY | os.O_NONBLOCK | os.O_NOFOLLOW)
    with os.fdopen(descriptor, "rb") as stream:
        info = os.fstat(stream.fileno())
        if not stat.S_ISREG(info.st_mode) or not 0 < info.st_size <= limit:
            raise ValueError("expected bounded regular input")
        value = stream.read(limit + 1)
    if not 0 < len(value) <= limit:
        raise ValueError("input grew beyond its bound")
    return value


def run(command: list[str]) -> bytes:
    result = subprocess.run(command, capture_output=True, timeout=60, check=False)
    if result.returncode:
        raise RuntimeError(result.stderr[-4096:].decode(errors="replace"))
    if len(result.stdout) > MAX_SOURCE_BYTES:
        raise ValueError("producer command output exceeds bound")
    return result.stdout


def guarded_destination(root: Path, path: Path) -> None:
    if not path.is_relative_to(root):
        raise ValueError("producer output escaped module")
    for parent in (path, *path.parents):
        if parent == root:
            break
        if parent.is_symlink():
            raise ValueError("producer output refuses symlink")


def pinned_sources(repository: Path, commit: str) -> dict[str, bytes]:
    rows = run(["git", "-C", str(repository), "ls-tree", "-rz", commit, "--", *SOURCE_PATHS])
    files: dict[str, bytes] = {}
    for row in rows.split(b"\0"):
        if not row:
            continue
        meta, name = row.split(b"\t", 1)
        mode, kind, _ = meta.split()
        if mode not in (b"100644", b"100755") or kind != b"blob":
            raise ValueError("pinned producer source is not a regular blob")
        text = name.decode()
        if len(files) >= 256:
            raise ValueError("pinned producer source count exceeds bound")
        files[text] = run(["git", "-C", str(repository), "show", commit + ":" + text])
    if not files or sum(map(len, files.values())) > MAX_SOURCE_BYTES:
        raise ValueError("pinned producer source extent exceeds bound")
    return files


def stage(root: Path, repository: Path, aar: Path, digest: str, commit: str, verify_only: bool) -> dict:
    if re.fullmatch(r"[0-9a-f]{64}", digest) is None:
        raise ValueError("a trusted exact AAR SHA-256 is required")
    if re.fullmatch(r"[0-9a-f]{40}", commit) is None:
        raise ValueError("exact Core commit is required")
    content = bounded_file(aar, MAX_AAR_BYTES)
    if hashlib.sha256(content).hexdigest() != digest:
        raise ValueError("prebuilt Core AAR digest differs")
    source = pinned_sources(repository, commit)
    source_root = root / ".native-source" / commit
    receipt_path = source_root / ".prebuilt-core-receipt.json"
    guarded_destination(root, receipt_path)
    outputs = {source_root / name: data for name, data in source.items()}
    # Existing generated source may be reused only when its exact bytes match.
    for path, data in outputs.items():
        guarded_destination(root, path)
        if path.exists() and bounded_file(path, MAX_SOURCE_BYTES) != data:
            raise ValueError("pinned producer source differs")
    for path, data in outputs.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        if not path.exists():
            path.write_bytes(data)
    marker = source_root / ".complete"
    guarded_destination(root, marker)
    marker.touch(exist_ok=True)
    export_map = root / ".native-build" / commit / "private-message-3" / ABIS[0] / "coakka_runtime_v2.exports.map"
    guarded_destination(root, export_map)
    export_map.parent.mkdir(parents=True, exist_ok=True)
    run(["cmake", "-DOUT=" + str(export_map),
         "-DHEADER_DIR=" + str(source_root / "v2/include/coakka/v2"),
         "-DFORMAT=ELF_VERSION_SCRIPT", "-P",
         str(source_root / "v2/cmake/GenerateCoakkaRuntimeExports.cmake")])
    temporary_root = Path(os.environ.get("COAKKA_ANDROID_VERIFY_TMP_ROOT", str(root / "build/tmp")))
    temporary_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="prebuilt-core-", dir=temporary_root) as temporary:
        snapshot = Path(temporary) / "verified-input.aar"
        snapshot.write_bytes(content)
        # Reuse the owning archive/metadata/export/dependency/architecture/privacy
        # gate. This input mode never weakens or replaces that release verifier.
        verified = run(["python3", "-B", str(root / "scripts/verify-release-aar.py"),
                        "--connector-root", str(root), "--aar", str(snapshot),
                        "--prebuilt-core-input-sha256", digest])
        images = {}
        with zipfile.ZipFile(snapshot) as archive:
            for abi in ABIS:
                name = f"jni/{abi}/libcoakka_runtime_v2.so"
                images[name] = archive.read(name)
        if hashlib.sha256(bounded_file(aar, MAX_AAR_BYTES)).hexdigest() != digest:
            raise ValueError("prebuilt Core input changed during verification")
        if not verify_only:
            destinations = {root / "src/main/jniLibs" / Path(name).relative_to("jni"): data
                            for name, data in images.items()}
            for path in destinations:
                guarded_destination(root, path)
            for path, data in destinations.items():
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
    receipt = {
        "mode": "verified-prebuilt-core", "core_commit": commit, "aar_sha256": digest,
        "native_core_rebuilt": False, "verify_only": verify_only,
        "source_files": {name: hashlib.sha256(data).hexdigest() for name, data in source.items()},
        "core_images": {name: hashlib.sha256(data).hexdigest() for name, data in images.items()},
        "release_verifier": json.loads(verified),
    }
    guarded_destination(root, receipt_path)
    receipt_path.write_text(json.dumps(receipt, indent=2) + "\n")
    return receipt


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--aar", type=Path, required=True)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    receipt = stage(args.root.resolve(strict=True), args.repository.resolve(strict=True),
                    args.aar, args.sha256, args.commit, args.verify_only)
    print(json.dumps(receipt, sort_keys=True))


if __name__ == "__main__":
    main()
