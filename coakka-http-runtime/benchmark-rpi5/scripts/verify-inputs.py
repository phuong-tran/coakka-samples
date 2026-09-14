#!/usr/bin/env python3
"""Reject missing or altered release artifacts and benchmark tools."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify(root: Path, lock_path: Path, base: Path, key: str) -> int:
    lock = json.loads(lock_path.read_text())
    verified = 0
    for item in lock[key]:
        relative = item["destination"]
        path = base / relative
        if not path.is_file():
            raise FileNotFoundError(f"missing locked input: {path}")
        if sha256(path) != item["sha256"]:
            raise RuntimeError(f"locked input digest mismatch: {item['id']}")
        verified += 1
    return verified


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    artifacts = verify(
        root,
        root / "config/artifacts.lock.json",
        root / "artifacts",
        "artifacts",
    )
    tools = verify(
        root,
        root / "config/tools.lock.json",
        root / "tools/downloads",
        "tools",
    )
    print(f"verified {artifacts} candidate inputs and {tools} tools")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
