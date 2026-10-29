#!/usr/bin/env python3
"""Shrink framework glue with real ProGuard; retain its public library contract."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import zipfile

MAX_JAR_BYTES = 64 * 1024 * 1024
MAX_ENTRIES = 4096
MAX_EXPANDED = 128 * 1024 * 1024
MAX_SOURCE_BYTES = 1024 * 1024


def regular(path: Path, limit: int) -> Path:
    if path.is_symlink() or not path.is_file() or path.stat().st_size > limit:
        raise ValueError(f"expected bounded regular input: {path}")
    return path.resolve(strict=True)


def quote(path: Path) -> str:
    value = str(path.resolve()).replace("\\", "/")
    if any(mark in value for mark in ('"', "\n", "\r", "\0")):
        raise ValueError("unsupported configuration path")
    return '"' + value + '"'


def inventory(root: Path) -> list[str]:
    names = []
    files = sorted(root.rglob("*.java"))
    if not files or len(files) > 1024:
        raise ValueError("invalid public Java source inventory")
    for path in files:
        text = regular(path, MAX_SOURCE_BYTES).read_text()
        package = re.search(r"^package\s+([\w.]+);", text, re.M)
        declaration = re.search(
            r"^public\s+(?:(?:final|abstract|sealed|non-sealed)\s+)*"
            r"(?:class|interface|@interface|enum|record)\s+([A-Za-z_]\w*)", text, re.M)
        if package is None:
            raise ValueError("framework source must declare its package")
        if declaration is None:
            # Package-private framework helpers remain shrink/rename candidates.
            continue
        names.append(package[1] + "." + declaration[1])
    if not names:
        raise ValueError("public framework inventory is empty")
    if len(names) != len(set(names)):
        raise ValueError("duplicate public framework type")
    return names


def archive(path: Path) -> dict[str, bytes]:
    regular(path, MAX_JAR_BYTES)
    with zipfile.ZipFile(path) as jar:
        entries = jar.infolist()
        if len(entries) > MAX_ENTRIES or sum(row.file_size for row in entries) > MAX_EXPANDED:
            raise ValueError("framework archive exceeds bounds")
        names = [row.filename for row in entries]
        if len(names) != len(set(names)):
            raise ValueError("duplicate archive member")
        for row in entries:
            if (row.filename.startswith("/") or "\\" in row.filename or
                    ".." in row.filename.split("/") or (row.external_attr >> 16) & 0o170000 == 0o120000):
                raise ValueError("unsafe framework archive member")
        return {row.filename: jar.read(row) for row in entries if not row.is_dir()}


def signatures(java_home: Path, jar: Path, api: list[str]) -> str:
    result = subprocess.run([str(java_home / "bin/javap"), "-public", "-s",
                             "-classpath", str(jar), *api],
                            text=True, capture_output=True, check=True, timeout=30)
    return "\n".join(line for line in result.stdout.splitlines()
                     if not line.startswith("Compiled from "))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ("jar", "output", "source-root", "consumer-rules", "java-home",
                   "proguard-jar", "evidence"):
        parser.add_argument("--" + option, type=Path, required=True)
    parser.add_argument("--library", type=Path, action="append", default=[])
    args = parser.parse_args()
    regular(args.proguard_jar, MAX_JAR_BYTES)
    regular(args.consumer_rules, MAX_SOURCE_BYTES)
    if not (args.java_home / "jmods/java.base.jmod").is_file():
        raise ValueError("actual Java17 JDK modules are required")
    release = regular(args.java_home / "release", MAX_SOURCE_BYTES).read_text()
    if re.search(r'^JAVA_VERSION="17(?:[.\"+])', release, re.M) is None:
        raise ValueError("framework packaging requires the actual Java17 API baseline")
    api = inventory(args.source_root)
    original = archive(args.jar)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.evidence.mkdir(parents=True, exist_ok=True)
    # ProGuard chooses directory output when the final suffix is not .jar.
    temporary = args.output.with_name(args.output.stem + ".shrinking.jar")
    # The JVM distribution owner supplies deterministic ZIP/debug normalization.
    helper_path = Path(__file__).with_name("shrink-runtime.py")
    spec = importlib.util.spec_from_file_location("runtime_distribution", helper_path)
    assert spec is not None and spec.loader is not None
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    rules = [
        "-injars " + quote(args.jar), "-outjars " + quote(temporary),
        "-dontoptimize",
        "-keepattributes Exceptions,InnerClasses,Signature,*Annotation*,EnclosingMethod,Record,MethodParameters",
        "-printmapping " + quote(args.evidence / "mapping.txt"),
        "-printusage " + quote(args.evidence / "usage.txt"),
        "-include " + quote(args.consumer_rules),
    ]
    for module in sorted((args.java_home / "jmods").glob("*.jmod")):
        rules.append("-libraryjars " + quote(module) + "(!**.jar;!module-info.class)")
    for library in args.library:
        regular(library, MAX_JAR_BYTES)
        rules.append("-libraryjars " + quote(library))
    # A distributed library retains its deliberately named public types/methods.
    # Private implementation names and unused code remain eligible for removal.
    for name in api:
        rules.append("-keep,includedescriptorclasses class " + name + " { public protected *; }")
    config = args.evidence / "distribution.pro"
    config.write_text("\n".join(rules) + "\n")
    command = [str(args.java_home / "bin/java"), "-Xmx768m", "-jar",
               str(args.proguard_jar), "@" + str(config)]
    try:
        with (args.evidence / "proguard.log").open("w") as log:
            subprocess.run(command, stdout=log, stderr=subprocess.STDOUT,
                           check=True, timeout=180)
        helper.strip_jar_source_debug_extensions(temporary)
        output = archive(temporary)
        for name, value in original.items():
            if not name.endswith(".class") and output.get(name) != value:
                raise ValueError("non-class framework resource changed: " + name)
        for name in api:
            if name.replace(".", "/") + ".class" not in output:
                raise ValueError("public type removed")
        for name, value in output.items():
            helper.verify_contained_entry(name, value if name.endswith(".class") else b"")
            if "thirdparty" in name.lower():
                raise ValueError("dependency inventories are not consumer resources")
            if name.endswith((".so", ".dll", ".dylib")):
                raise ValueError("framework package must not embed a native library")
            if name.endswith(".class"):
                if value[:4] != b"\xca\xfe\xba\xbe" or int.from_bytes(value[6:8], "big") != 61:
                    raise ValueError("framework bytecode is not Java17")
                if any(marker in value for marker in (b"/Users/", b"/Volumes/", b"/home/", b"SMAP\n")):
                    raise ValueError("private build/source information remains")
                if any(marker in value for marker in (
                    b"SourceFile", b"SourceDebugExtension", b"LineNumberTable",
                    b"LocalVariableTable", b"LocalVariableTypeTable",
                )):
                    raise ValueError("framework debug metadata remains")
        before = signatures(args.java_home, args.jar, api)
        after = signatures(args.java_home, temporary, api)
        if before != after:
            raise ValueError("framework public JVM signatures changed")
        (args.evidence / "public-api.txt").write_text(after + "\n")
        temporary.replace(args.output)
        receipt = dict(command=command,public_types=api,classes=sum(n.endswith(".class") for n in output),
                       sha256=hashlib.sha256(args.output.read_bytes()).hexdigest(),
                       resource_identity=True,public_api_identity=True,
                       qualification="real shrink only; exact packaged framework consumers required")
        (args.evidence / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
        print(json.dumps(receipt, sort_keys=True))
    finally:
        if temporary.exists():
            temporary.unlink()


if __name__ == "__main__":
    main()
