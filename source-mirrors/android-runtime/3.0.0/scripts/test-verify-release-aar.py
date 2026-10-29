#!/usr/bin/env python3
"""Negative controls for the release AAR hardening verifier."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess
import tempfile
import warnings
import zipfile


METADATA = "assets/coakka/runtime-package.json"


def rewrite(source: Path, target: Path, mutate) -> None:
    with zipfile.ZipFile(source) as reader, zipfile.ZipFile(target, "w") as writer:
        for entry in reader.infolist():
            payload = reader.read(entry.filename)
            writer.writestr(entry, mutate(entry.filename, payload))


def expect_refusal(command: list[str], needle: str) -> None:
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    if result.returncode == 0 or needle not in result.stdout:
        raise RuntimeError(
            f"negative control did not fail closed for {needle!r}:\n{result.stdout[-8000:]}"
        )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--aar", type=Path, required=True)
    parser.add_argument("--connector-root", type=Path, required=True)
    args = parser.parse_args()
    aar = args.aar.resolve()
    root = args.connector_root.resolve()
    verifier = root / "scripts/verify-release-aar.py"
    base = ["python3", "-B", str(verifier), "--connector-root", str(root)]

    with tempfile.TemporaryDirectory(prefix="coakka-aar-negative-") as temporary:
        work = Path(temporary)
        unexpected = work / "unexpected-native.aar"
        rewrite(aar, unexpected, lambda _name, payload: payload)
        with zipfile.ZipFile(unexpected, "a") as writer:
            writer.writestr("jni/x86_64/libunexpected.so", b"refused")
        expect_refusal(
            [*base, "--aar", str(unexpected), "--allow-dirty"],
            "AAR native entry mismatch",
        )

        duplicate = work / "duplicate-entry.aar"
        rewrite(aar, duplicate, lambda _name, payload: payload)
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(duplicate, "a") as writer:
                writer.writestr(METADATA, b"{}")
        expect_refusal(
            [*base, "--aar", str(duplicate), "--allow-dirty"],
            "duplicate AAR entry refused",
        )

        dirty = work / "dirty-metadata.aar"

        def mark_dirty(name: str, payload: bytes) -> bytes:
            if name != METADATA:
                return payload
            metadata = json.loads(payload)
            metadata["source_tree_dirty"] = True
            metadata["connector_source_tree_dirty"] = True
            return (json.dumps(metadata, indent=2) + "\n").encode()

        rewrite(aar, dirty, mark_dirty)
        expect_refusal([*base, "--aar", str(dirty)], "release AAR records a dirty source tree")

    print(json.dumps({
        "status": "PASS_ANDROID_AAR_NEGATIVE_CONTROLS",
        "controls": [
            "unexpected_native_entry",
            "duplicate_entry",
            "dirty_source_metadata",
        ],
    }, sort_keys=True))


if __name__ == "__main__":
    main()
