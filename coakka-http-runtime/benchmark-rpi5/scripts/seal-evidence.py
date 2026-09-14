#!/usr/bin/env python3
"""Seal a completed and restored campaign with a deterministic digest list."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--evidence", type=Path, required=True)
    args = parser.parse_args()
    evidence = args.evidence.resolve()
    campaign = json.loads((evidence / "campaign.json").read_text())
    if campaign.get("status") != "complete":
        raise RuntimeError("only a complete campaign can be sealed")
    if not (evidence / "host-control/restored-at.txt").is_file():
        raise RuntimeError("host restoration evidence is missing")

    generator_copy = evidence / "seal-generator.py"
    shutil.copy2(Path(__file__).resolve(), generator_copy)
    manifest = evidence / "EVIDENCE-SHA256SUMS"
    digest_file = evidence / "EVIDENCE-DIGEST"
    excluded = {manifest, digest_file}
    paths = sorted(
        path for path in evidence.rglob("*") if path.is_file() and path not in excluded
    )
    manifest.write_text(
        "".join(f"{sha256(path)}  {path.relative_to(evidence)}\n" for path in paths),
        encoding="ascii",
    )
    digest_file.write_text(f"sha256:{sha256(manifest)}\n", encoding="ascii")
    print(digest_file.read_text().strip())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
