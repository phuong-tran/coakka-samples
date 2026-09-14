#!/usr/bin/env python3
"""Stage the JavaScript HTTP/2 candidate and rewrite its integrity manifest."""

from __future__ import annotations

import hashlib
import json
import shutil
import sys
from pathlib import Path


def entry(path: Path) -> dict[str, object]:
    payload = path.read_bytes()
    return {
        "file": path.name,
        "size": len(payload),
        "sha256": hashlib.sha256(payload).hexdigest(),
    }


def main() -> int:
    if len(sys.argv) != 5:
        raise SystemExit(
            "usage: stage-javascript-http2-package.py SOURCE_PACKAGE OUTPUT_PACKAGE ADDON CORE"
        )
    source, output, addon_source, core_source = map(Path, sys.argv[1:])
    if output.exists():
        shutil.rmtree(output)
    shutil.copytree(source, output)
    native = output / "prebuilds" / "linux-arm64"
    native.mkdir(parents=True, exist_ok=True)
    addon = native / "coakka_http_javascript.node"
    core = native / "libcoakka_http_runtime.so.1"
    shutil.copy2(addon_source, addon)
    shutil.copy2(core_source, core)
    manifest = {
        "format": 1,
        "platform": "linux",
        "architecture": "arm64",
        "addon": entry(addon),
        "core": entry(core),
    }
    (native / "native.json").write_text(
        json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
