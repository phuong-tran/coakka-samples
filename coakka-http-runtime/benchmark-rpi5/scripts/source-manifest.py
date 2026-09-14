#!/usr/bin/env python3
"""Write deterministic digests for every human-authored benchmark input."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path


IGNORED_PARTS = {
    ".gradle",
    "__pycache__",
    "artifacts",
    "build",
    "downloads",
    "evidence",
    "node_modules",
}
IGNORED_GENERATED_NAMES = {
    "coakka-http-rpi5-benchmark",
    "coakka-http-rpi5-go-core",
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=root / "build/source.sha256")
    args = parser.parse_args()
    paths = [
        path
        for path in root.rglob("*")
        if path.is_file()
        and not any(part in IGNORED_PARTS for part in path.relative_to(root).parts)
        and path.name not in IGNORED_GENERATED_NAMES
        and path != args.output
    ]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    lines = [f"{sha256(path)}  {path.relative_to(root)}\n" for path in sorted(paths)]
    args.output.write_text("".join(lines), encoding="ascii")
    print(args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
