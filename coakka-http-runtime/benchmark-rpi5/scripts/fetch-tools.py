#!/usr/bin/env python3
"""Fetch and verify the benchmark's pinned external command-line tools."""

from __future__ import annotations

import hashlib
import json
import os
import tempfile
import urllib.request
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    destination_root = root / "tools/downloads"
    destination_root.mkdir(parents=True, exist_ok=True)
    lock = json.loads((root / "config/tools.lock.json").read_text())
    records: list[dict[str, str]] = []
    for tool in lock["tools"]:
        destination = destination_root / tool["destination"]
        destination.parent.mkdir(parents=True, exist_ok=True)
        if not destination.is_file() or sha256(destination) != tool["sha256"]:
            with urllib.request.urlopen(tool["url"], timeout=120) as response:
                with tempfile.NamedTemporaryFile(
                    dir=destination.parent, prefix=f".{tool['id']}.", delete=False
                ) as temporary:
                    while chunk := response.read(1 << 20):
                        temporary.write(chunk)
                    temporary_path = Path(temporary.name)
            try:
                if sha256(temporary_path) != tool["sha256"]:
                    raise RuntimeError(f"download digest mismatch: {tool['id']}")
                os.replace(temporary_path, destination)
            finally:
                temporary_path.unlink(missing_ok=True)
        if tool.get("executable"):
            destination.chmod(destination.stat().st_mode | 0o111)
        records.append(
            {
                "id": tool["id"],
                "version": tool["version"],
                "path": str(destination),
                "sha256": sha256(destination),
            }
        )
    print(json.dumps({"tools": records}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
