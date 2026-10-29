#!/usr/bin/env python3
"""Mutation controls against the real processed library, without loading code."""
from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path
import tempfile
import warnings
import zipfile


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classes", type=Path, required=True)
    parser.add_argument("--connector-root", type=Path, required=True)
    args = parser.parse_args()
    root = args.connector_root.resolve()
    spec = importlib.util.spec_from_file_location("managed_guard", root / "scripts/verify-library-managed.py")
    assert spec is not None and spec.loader is not None
    guard = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(guard)
    types = {line for line in (root / "release-api.txt").read_text().splitlines()
             if line and not line.startswith("#")}
    sources = tuple(sorted({source.name.encode() for source in (root / "src/main/java").rglob("*.kt")}))
    guard.inspect_jar(args.classes, types, sources)
    with zipfile.ZipFile(args.classes) as archive:
        entries = [(item, archive.read(item)) for item in archive.infolist()]
    public_entry = sorted(types)[0].replace(".", "/") + ".class"
    controls = []
    with tempfile.TemporaryDirectory(prefix="coakka-managed-negative-") as temporary:
        work = Path(temporary)

        def reject(name, needle, transform, extra=None):
            target = work / (name + ".jar")
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                with zipfile.ZipFile(target, "w") as output:
                    for item, body in entries:
                        changed = transform(item.filename, body)
                        if changed is not None:
                            output.writestr(item, changed)
                    if extra is not None:
                        output.writestr(*extra)
            try:
                guard.inspect_jar(target, types, sources)
            except ValueError as error:
                if needle not in str(error):
                    raise AssertionError(name + ": unexpected refusal " + str(error)) from error
            else:
                raise AssertionError(name + ": mutation accepted")
            controls.append(name)

        def same(_name, body):
            return body
        reject("duplicate", "unsafe managed member", same, (entries[0][0], entries[0][1]))
        reject("traversal", "unsafe managed member", same, ("../private.class", b"x"))
        link = zipfile.ZipInfo("private-link")
        link.external_attr = 0o120777 << 16
        reject("symlink", "unsafe managed member", same, (link, b"private"))
        reject("mapping", "private mapping", same, ("mapping.txt", b"private"))
        reject("resource", "unexpected managed resource", same, ("private-data", b"private"))
        reject("external_class", "external implementation class", same,
               ("private/Original.class", entries[0][1]))
        reject("entry_bound", "managed entry bound", same, ("huge", bytes(guard.MAX_CLASS + 1)))
        reject("missing_api", "published type removed",
               lambda name, body: None if name == public_entry else body)
        for label, marker in (("private_schema", b"coakka/v2/control/Envelope"),
                              ("debug_smap", b"SourceDebugExtension"),
                              ("developer_path", b"/Users/developer/private"),
                              ("source_file", sources[0])):
            reject(label, "private/debug identity",
                   lambda name, body, token=marker: body + token if name == public_entry else body)
        reject("jvm_baseline", "Android JVM11",
               lambda name, body: body[:6] + b"\x00\x3d" + body[8:] if name == public_entry else body)
        reject("metadata", "public Kotlin metadata missing",
               lambda name, body: body.replace(b"kotlin/Metadata", b"kotlin/Metadatz") if name == public_entry else body)
    print(json.dumps({"status": "PASS_MANAGED_MUTATION_CONTROLS", "controls": controls}, sort_keys=True))


if __name__ == "__main__":
    main()
