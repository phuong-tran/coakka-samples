#!/usr/bin/env python3
"""Check the processed library's public ABI and implementation containment."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import struct
import subprocess
import zipfile

MAX_JAR = 16 << 20
MAX_CLASSES = 1024
MAX_CLASS = 2 << 20
MAX_EXPANDED = 32 << 20
FORBIDDEN = (
    b"coakka/v2/control/", b"coakka/v2/transport/",
    b"coakka.v2.control.", b"coakka.v2.transport.",
    b"com/google/protobuf", b"com.google.protobuf", b"GeneratedMessageLite",
    b"Protocol Buffer", b"Protobuf", b"protobuf", b"CodedOutputStream", b"ByteString",
    b"/Users/", b"/Volumes/", b"/home/", b"\\Users\\",
    b"SourceDebugExtension", b"LocalVariableTable", b"LocalVariableTypeTable",
    b"RuntimePipeTransport", b"RuntimeHostLanes", b"LaneCancellation",
    b"NativeStatus", b"StreamCallbackGuard",
)


def require(value: bool, message: str) -> None:
    if not value:
        raise ValueError(message)


def public_api(output: str, types: set[str]) -> list[str]:
    """Parse bounded javap declarations, not bytecode or runtime messages."""
    records = []
    owner = None
    final_owner = False
    declaration = None
    for raw in output.splitlines():
        line = raw.strip()
        if line.endswith("{"):
            match = re.search(r"\b(?:class|interface|enum) ([\w.$]+)", line)
            if match is None or match[1] not in types:
                raise ValueError("unexpected public class declaration")
            owner = match[1]
            final_owner = " final " in line
            records.append(owner + " | " + line)
        elif line.startswith(("public ", "protected ")) and line.endswith(";"):
            declaration = line
        elif line.startswith("descriptor:"):
            if owner is None or declaration is None:
                raise ValueError("orphan public descriptor")
            member = declaration.split("(", 1)[0].rsplit(" ", 1)[-1]
            # Kotlin internal members use the module-mangled name. Constructors
            # and public default-argument helpers are part of the consumer ABI.
            referenced = {name.replace("/", ".") for name in re.findall(r"L(coakka/v2/android/[\w$/]+);", line)}
            visible = not (referenced - types)
            if visible and ("$" not in member or member == owner or member.endswith("$default")):
                if final_owner and "(" in declaration:
                    declaration = declaration.replace(" final ", " ")
                records.append(owner + " | " + declaration + " | " + line)
            declaration = None
    require({r.split(" | ", 1)[0] for r in records} == types, "public type inventory incomplete")
    return sorted(records)


def inspect_jar(path: Path, public_types: set[str], source_names: tuple[bytes, ...] = ()) -> dict[str, int]:
    require(path.is_file() and not path.is_symlink() and 0 < path.stat().st_size <= MAX_JAR,
            "bounded regular managed JAR required")
    classes = internal = expanded = 0
    names = set()
    with zipfile.ZipFile(path) as archive:
        infos = archive.infolist()
        require(len(infos) <= MAX_CLASSES + 64, "managed member bound")
        require(len(infos) == len({item.filename for item in infos}), "unsafe managed member")
        for item in infos:
            name = item.filename
            relative = PurePosixPath(name)
            require(name not in names and not relative.is_absolute() and ".." not in relative.parts
                    and "\\" not in name and not item.flag_bits & 1
                    and not stat.S_ISLNK((item.external_attr >> 16) & 0xFFFF), "unsafe managed member")
            names.add(name)
            require(item.file_size <= MAX_CLASS, "managed entry bound")
            expanded += item.file_size
            require(expanded <= MAX_EXPANDED, "managed expansion bound")
            require(not any(word in name.lower() for word in ("mapping", "thirdparty", "third-party")),
                    "private mapping or dependency inventory in managed payload")
            if not name.endswith(".class"):
                require(name in ("META-INF/MANIFEST.MF", "META-INF/"), "unexpected managed resource")
                continue
            require(name.startswith("coakka/v2/android/"), "external implementation class in library")
            classes += 1
            require(classes <= MAX_CLASSES, "managed class bound")
            body = archive.read(item)
            require(len(body) >= 8 and body[:4] == b"\xca\xfe\xba\xbe", "invalid managed class")
            require(45 <= struct.unpack_from(">H", body, 6)[0] <= 55, "Android JVM11 class baseline exceeded")
            for marker in FORBIDDEN + source_names:
                require(marker not in body, "private/debug identity in " + name + ": " + marker.decode())
            if name.startswith("coakka/v2/android/internal/"):
                internal += 1
            if name[:-6].replace("/", ".") in public_types:
                require(b"kotlin/Metadata" in body, "public Kotlin metadata missing: " + name)
    require(all(name.replace(".", "/") + ".class" in names for name in public_types),
            "published type removed")
    require(internal > 0, "library implementation was not renamed")
    return {"classes": classes, "renamed_internal_classes": internal, "expanded_bytes": expanded}


def kotlin_api(classes: Path, root: Path, java: str) -> list[str]:
    classpath = os.environ.get("COAKKA_ANDROID_API_TOOL_CLASSPATH")
    require(bool(classpath), "Kotlin metadata build-tool classpath is required")
    command = [java, "-cp", str(classpath), str(root / "scripts/InspectKotlinVisibility.java"),
               str(classes), str(root / "release-api.txt")]
    process = subprocess.run(command, capture_output=True, text=True, timeout=30)
    require(process.returncode == 0 and len(process.stdout) <= 8 << 20,
            "Kotlin API inspection failed: " + process.stderr[-2000:])
    rows = []
    for line in process.stdout.splitlines():
        parts = line.split("\t")
        require(len(parts) == 5, "invalid Kotlin API projection")
        if parts[1] in ("PUBLIC", "PROTECTED"):
            rows.append(line)
    return sorted(rows)


def verify(classes: Path, root: Path, javap: str) -> dict:
    type_file = root / "release-api.txt"
    types = {line for line in type_file.read_text().splitlines() if line and not line.startswith("#")}
    require(0 < len(types) <= 256 and all(re.fullmatch(r"coakka\.v2\.android\.[\w$]+", name) for name in types),
            "invalid public type manifest")
    keep_types = set(re.findall(r"^-keep,allowoptimization (?:class|interface) (coakka\.v2\.android\.[\w$]+) \{",
                                (root / "library-release-rules.pro").read_text(), re.M))
    require(keep_types == types, "library keep rules differ from public API manifest")
    sources = tuple(sorted({source.name.encode() for source in (root / "src/main/java").rglob("*.kt")}))
    require(0 < len(sources) <= 128, "owned source inventory bound")
    result: dict[str, int | str] = dict(inspect_jar(classes, types, sources))
    command = [javap, "-public", "-s", "-constants", "-classpath", str(classes), *sorted(types)]
    process = subprocess.run(command, capture_output=True, text=True, timeout=30)
    require(process.returncode == 0 and len(process.stdout) <= 8 << 20,
            "public API inspection failed: " + process.stderr[-2000:])
    actual = public_api(process.stdout, types)
    expected = (root / "release-api.jvm").read_text().splitlines()
    missing = set(expected) - set(actual)
    extra = set(actual) - set(expected)
    # JVM-public Kotlin internals are renamed after R8 drops their internal
    # metadata. They are not a published source API. Fields and declared source
    # API remain exact, and every original JVM consumer entrypoint must survive.
    extra_fields = {row for row in extra if "(" not in row.split(" | ")[1]}
    require(not missing and not extra_fields,
            "public API changed: missing=" + str(sorted(missing)[:8])
            + " unexpected fields/types=" + str(sorted(extra_fields)[:8]))
    metadata = kotlin_api(classes, root, str(Path(javap).with_name("java.exe" if os.name == "nt" else "java")))
    require(metadata == (root / "release-api.kotlin").read_text().splitlines(), "public Kotlin metadata API changed")
    result.update(public_types=len(types), public_declarations=len(actual),
                  public_kotlin_declarations=len(metadata),
                  sha256=hashlib.sha256(classes.read_bytes()).hexdigest())
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classes", type=Path, required=True)
    parser.add_argument("--connector-root", type=Path, required=True)
    args = parser.parse_args()
    java_home = os.environ.get("JAVA_HOME")
    javap = str(Path(java_home) / "bin" / ("javap.exe" if os.name == "nt" else "javap")) if java_home else shutil.which("javap")
    if javap is None or not Path(javap).is_file():
        raise ValueError("qualified JDK javap required")
    print(json.dumps(verify(args.classes, args.connector_root, javap), sort_keys=True))


if __name__ == "__main__":
    main()
