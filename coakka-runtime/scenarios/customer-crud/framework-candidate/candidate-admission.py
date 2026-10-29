#!/usr/bin/env python3
"""Admit a trusted local framework candidate before compiling or running samples."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import stat
import sys

CORE_COMMIT = "1522b4a9f30da1039ef8c7d198bbecfc749a2456"
VERSION = "3.0.0"
GROUP_PATH = "io/github/phuong-tran/coakka"
RUNTIME_LIBRARIES = {
    ("org.jetbrains", "annotations"): ("23.0.0", "org/jetbrains/annotations/23.0.0/annotations-23.0.0.jar"),
    ("org.jetbrains.kotlin", "kotlin-stdlib"): ("2.3.10", "org/jetbrains/kotlin/kotlin-stdlib/2.3.10/kotlin-stdlib-2.3.10.jar"),
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm"): ("1.10.2", "org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.10.2/kotlinx-coroutines-core-jvm-1.10.2.jar"),
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8"): ("1.10.2", "org/jetbrains/kotlinx/kotlinx-coroutines-jdk8/1.10.2/kotlinx-coroutines-jdk8-1.10.2.jar"),
}
EXPECTED_PATHS = {
    f"{GROUP_PATH}/{artifact}/{VERSION}/{artifact}-{VERSION}.{extension}"
    for artifact in ("runtime", "spring-boot-starter", "quarkus-extension")
    for extension in ("jar", "pom", "module")
} | {value[1] for value in RUNTIME_LIBRARIES.values()}
MAX_CATALOG_BYTES = 65_536
MAX_ARTIFACT_BYTES = 16 * 1024 * 1024
MAX_TOTAL_BYTES = 64 * 1024 * 1024


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate candidate catalog field")
        result[key] = value
    return result


def no_links(path: Path) -> None:
    if not path.is_absolute() or ".." in path.parts:
        raise ValueError("candidate paths must be absolute without parent traversal")
    cursor = Path(path.anchor)
    for part in path.parts[1:]:
        cursor /= part
        if cursor.is_symlink():
            raise ValueError("candidate paths must not traverse symbolic links")


def read_regular(path: Path, expected_size: int | None = None) -> bytes:
    no_links(path)
    limit = MAX_CATALOG_BYTES if expected_size is None else expected_size
    if not stat.S_ISREG(path.lstat().st_mode):
        raise ValueError("candidate member must be a regular file")
    # Never block opening an accidental FIFO/device before descriptor checks.
    descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0))
    try:
        metadata = os.fstat(descriptor)
        if not stat.S_ISREG(metadata.st_mode) or metadata.st_size < 1:
            raise ValueError("candidate member must be a nonempty regular file")
        if expected_size is not None and metadata.st_size != expected_size:
            raise ValueError("candidate member extent differs")
        if metadata.st_size > limit:
            raise ValueError("candidate member exceeds its byte bound")
        chunks = []
        total = 0
        while True:
            chunk = os.read(descriptor, min(65_536, limit + 1 - total))
            if not chunk:
                break
            total += len(chunk)
            if total > limit:
                raise ValueError("candidate member grew beyond its byte bound")
            chunks.append(chunk)
        if total != metadata.st_size:
            raise ValueError("candidate member changed during admission")
        return b"".join(chunks)
    finally:
        os.close(descriptor)


def admit(catalog: Path, home: Path) -> dict:
    # Catalog and repository are trusted, stable build inputs. This rejects
    # accidental traversal/link/tamper; hashes are not publisher signatures.
    no_links(home)
    if not home.is_dir():
        raise ValueError("candidate home must be an existing local Maven directory")
    value = json.loads(read_regular(catalog), object_pairs_hook=unique_object)
    keys = {"schema", "state", "runtimeVersion", "adapterVersion", "coreCommit", "artifacts"}
    if not isinstance(value, dict) or set(value) != keys:
        raise ValueError("unknown or missing framework candidate catalog fields")
    if (type(value["schema"]) is not int or value["schema"] != 1 or
            value["state"] != "private-candidate" or
            value["runtimeVersion"] != VERSION or value["adapterVersion"] != VERSION or
            value["coreCommit"] != CORE_COMMIT):
        raise ValueError("framework candidate identity differs")
    artifacts = value["artifacts"]
    if not isinstance(artifacts, list) or len(artifacts) != len(EXPECTED_PATHS):
        raise ValueError("framework candidate must declare exactly thirteen artifacts")
    total = 0
    seen = set()
    for row in artifacts:
        if not isinstance(row, dict) or set(row) != {"path", "bytes", "sha256"}:
            raise ValueError("unknown candidate artifact fields")
        name, extent, digest = row["path"], row["bytes"], row["sha256"]
        if not isinstance(name, str) or name not in EXPECTED_PATHS or name in seen:
            raise ValueError("duplicate, unsafe or unexpected candidate artifact path")
        if (type(extent) is not int or not 1 <= extent <= MAX_ARTIFACT_BYTES or
                not isinstance(digest, str) or len(digest) != 64 or
                any(letter not in "0123456789abcdef" for letter in digest)):
            raise ValueError("invalid candidate artifact identity")
        total += extent
        if total > MAX_TOTAL_BYTES:
            raise ValueError("candidate artifacts exceed their aggregate byte bound")
        seen.add(name)
    if seen != EXPECTED_PATHS:
        raise ValueError("framework candidate artifact set differs")
    for row in artifacts:
        data = read_regular(home / row["path"], row["bytes"])
        if hashlib.sha256(data).hexdigest() != row["sha256"]:
            raise ValueError(f"candidate artifact hash differs: {row['path']}")
    return value


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog", type=Path, required=True)
    parser.add_argument("--home", type=Path, required=True)
    args = parser.parse_args()
    try:
        value = admit(args.catalog, args.home)
    except (OSError, UnicodeError, ValueError, TypeError, KeyError) as error:
        print(f"[framework-candidate] {error}", file=sys.stderr)
        return 1
    print(json.dumps(value, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
