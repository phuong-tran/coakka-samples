#!/usr/bin/env python3
"""Fail closed on Android release AAR identity, ABI, and native hardening."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import subprocess
import tempfile
import zipfile


ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
LIBRARIES = ("libcoakka_runtime_v2.so", "libcoakka_android_jni.so")
MAX_AAR_BYTES = 128 * 1024 * 1024
MAX_ENTRY_BYTES = 64 * 1024 * 1024
MAX_EXPANDED_BYTES = 256 * 1024 * 1024
MAX_ENTRIES = 4096
CORE_NEEDED = {"liblog.so", "libm.so", "libdl.so", "libc.so"}
JNI_NEEDED = CORE_NEEDED | {"libcoakka_runtime_v2.so"}
MACHINES = {
    "arm64-v8a": "AArch64",
    "armeabi-v7a": "ARM",
    "x86": "Intel 80386",
    "x86_64": "Advanced Micro Devices X86-64",
}


def require(condition: bool, detail: str) -> None:
    if not condition:
        raise RuntimeError(detail)


def output(command: list[str]) -> str:
    result = subprocess.run(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        timeout=30,
    )
    require(result.returncode == 0, f"command failed: {' '.join(command)}\n{result.stdout[-8000:]}")
    return result.stdout


def properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        require(bool(separator) and bool(key.strip()) and bool(value.strip()), f"invalid property line: {raw}")
        values[key.strip()] = value.strip()
    return values


def native_tools(connector_root: Path) -> tuple[Path, Path]:
    sdk_value = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk_value:
        local = connector_root / "local.properties"
        if local.is_file():
            sdk_value = properties(local).get("sdk.dir")
    require(bool(sdk_value), "ANDROID_SDK_ROOT, ANDROID_HOME, or local.properties sdk.dir is required")
    ndk_value = os.environ.get("ANDROID_NDK_HOME")
    ndk = Path(ndk_value) if ndk_value else Path(str(sdk_value)) / "ndk" / "29.0.14206865"
    prebuilt_root = ndk / "toolchains/llvm/prebuilt"
    require(prebuilt_root.is_dir(), f"Android NDK tool root is missing: {prebuilt_root}")
    prebuilt = [path for path in prebuilt_root.iterdir() if path.is_dir()]
    require(len(prebuilt) == 1, "Android NDK must expose exactly one host prebuilt tool root")
    nm = prebuilt[0] / "bin/llvm-nm"
    readelf = prebuilt[0] / "bin/llvm-readelf"
    require(nm.is_file() and readelf.is_file(), "qualified Android NDK inspection tools are missing")
    return nm, readelf


def map_symbols(path: Path, prefix: str) -> set[str]:
    symbols = set(re.findall(rf"^\s*({re.escape(prefix)}[A-Za-z0-9_]+);\s*$", path.read_text(), re.M))
    require(bool(symbols), f"empty export allow-list: {path}")
    return symbols


def dynamic_symbols(nm: Path, image: Path) -> set[str]:
    symbols: set[str] = set()
    for line in output([str(nm), "-D", "--defined-only", str(image)]).splitlines():
        columns = line.split()
        require(len(columns) >= 3, f"unexpected dynamic symbol row in {image}: {line}")
        symbols.add(columns[-1].split("@", 1)[0])
    return symbols


def needed_libraries(readelf: Path, image: Path) -> set[str]:
    values = set(re.findall(r"\(NEEDED\).*?\[(.+?)\]", output([str(readelf), "-d", str(image)])))
    require(bool(values), f"empty dynamic dependency closure: {image}")
    return values


def verify_image(
    nm: Path,
    readelf: Path,
    image: Path,
    abi: str,
    name: str,
    expected_symbols: set[str],
) -> None:
    require(image.stat().st_size <= MAX_ENTRY_BYTES, f"native image exceeds byte bound: {image}")
    actual_symbols = dynamic_symbols(nm, image)
    require(actual_symbols == expected_symbols,
            f"{abi}/{name} export mismatch: missing={sorted(expected_symbols - actual_symbols)} "
            f"unexpected={sorted(actual_symbols - expected_symbols)}")
    expected_needed = CORE_NEEDED if name == LIBRARIES[0] else JNI_NEEDED
    actual_needed = needed_libraries(readelf, image)
    require(actual_needed == expected_needed,
            f"{abi}/{name} dependency mismatch: {sorted(actual_needed)}")
    header = output([str(readelf), "-h", str(image)])
    require(f"Machine:                           {MACHINES[abi]}" in header,
            f"{abi}/{name} architecture mismatch")
    sections = output([str(readelf), "-S", str(image)])
    require(not re.search(r"\.(?:debug(?:_[A-Za-z0-9_.-]+)?|symtab|comment)(?:\s|$)", sections),
            f"{abi}/{name} retains debug, symbol-table, or compiler-comment sections")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--aar", type=Path, required=True)
    parser.add_argument("--connector-root", type=Path, required=True)
    parser.add_argument("--allow-dirty", action="store_true")
    parser.add_argument("--prebuilt-core-input-sha256")
    args = parser.parse_args()
    root = args.connector_root.resolve()
    aar = args.aar.resolve()
    require(aar.is_file() and 0 < aar.stat().st_size <= MAX_AAR_BYTES, "AAR is missing, empty, or oversized")
    input_mode = args.prebuilt_core_input_sha256 is not None
    if input_mode:
        require(not args.allow_dirty, "prebuilt Core input must be clean")
        require(re.fullmatch(r"[0-9a-f]{64}", args.prebuilt_core_input_sha256) is not None,
                "prebuilt Core input requires an exact trusted SHA-256")
        with aar.open("rb") as source:
            input_bytes = source.read(MAX_AAR_BYTES + 1)
        require(len(input_bytes) <= MAX_AAR_BYTES, "prebuilt Core input grew beyond bound")
        require(hashlib.sha256(input_bytes).hexdigest() == args.prebuilt_core_input_sha256,
                "prebuilt Core input digest mismatch")
    identity = properties(root / "release-identity.properties")
    commit = identity["core.commit"]
    require(re.fullmatch(r"[0-9a-f]{40}", commit) is not None, "Core commit identity is not exact")
    expected_entries = {
        f"jni/{abi}/{name}" for abi in ABIS for name in LIBRARIES
    }
    expected_legal = {"assets/LICENSE", "assets/NATIVE-LICENSE.md", "assets/PACKAGE-LICENSE.md", "assets/NOTICE"}
    nm, readelf = native_tools(root)
    core_map = root / ".native-build" / commit / "private-message-3" / ABIS[0] / "coakka_runtime_v2.exports.map"
    core_symbols = map_symbols(core_map, "coakka_v2_")
    # Historical producer input is a separately frozen contract. It never
    # weakens the current output map or copies the old JNI into a new AAR.
    jni_map = (root / "scripts/prebuilt-core-input-jni.exports.map" if input_mode
               else root / "src/main/cpp/coakka_android_jni.exports.map")
    jni_symbols = map_symbols(jni_map, "Java_coakka_v2_android_")
    require(len(core_symbols) == 160, f"expected 160 Core exports, got {len(core_symbols)}")
    expected_jni_count = 38 if input_mode else 43
    require(len(jni_symbols) == expected_jni_count,
            f"expected {expected_jni_count} JNI exports, got {len(jni_symbols)}")

    temp_parent = Path(os.environ.get("COAKKA_ANDROID_VERIFY_TMP_ROOT", str(root / "build/tmp")))
    temp_parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="coakka-aar-verify-", dir=temp_parent) as temporary:
        extracted = Path(temporary)
        with zipfile.ZipFile(aar) as archive:
            entries = archive.infolist()
            require(len(entries) <= MAX_ENTRIES, "AAR member-count bound exceeded")
            file_names = [entry.filename for entry in entries if not entry.is_dir()]
            require(len(file_names) == len(set(file_names)), "duplicate AAR entry refused")
            require(sum(entry.file_size for entry in entries) <= MAX_EXPANDED_BYTES,
                    "AAR expanded-size bound exceeded")
            names = set(file_names)
            actual_native = {name for name in names if name.endswith(".so")}
            require(actual_native == expected_entries,
                    f"AAR native entry mismatch: missing={sorted(expected_entries - actual_native)} "
                    f"unexpected={sorted(actual_native - expected_entries)}")
            require(expected_legal <= names, "AAR legal payload is incomplete")
            require(not any("thirdparty" in name.lower() or "third-party" in name.lower() for name in names),
                    "AAR must not contain a dependency inventory")
            required = expected_entries | {"assets/coakka/runtime-package.json"}
            if not input_mode:
                require({"classes.jar", "proguard.txt"} <= names, "managed release payload missing")
                require(not any("mapping" in name.lower() for name in names), "private mapping in AAR")
                require(archive.read("proguard.txt") == (root / "consumer-rules.pro").read_bytes(),
                        "consumer rules differ from the owned release boundary")
                required.add("classes.jar")
            for entry in entries:
                path = PurePosixPath(entry.filename)
                require(not path.is_absolute() and ".." not in path.parts and
                        "\\" not in entry.filename, "unsafe AAR entry path")
                mode = (entry.external_attr >> 16) & 0xFFFF
                require(not stat.S_ISLNK(mode), "symbolic-link AAR entry refused")
                require(not entry.flag_bits & 0x1, "encrypted AAR entry refused")
                require(entry.file_size <= MAX_ENTRY_BYTES, f"oversized AAR entry: {entry.filename}")
                if entry.filename in required:
                    archive.extract(entry, extracted)

        metadata = json.loads((extracted / "assets/coakka/runtime-package.json").read_text())
        require(metadata["connector_version"] == identity["connector.version"], "connector version mismatch")
        require(metadata["bundled_native_git_commit"] == commit, "bundled Core commit mismatch")
        require(metadata["core_source_git_commit"] == commit, "Core source commit mismatch")
        require(metadata["core_checkout_git_commit"] == commit, "Core checkout commit mismatch")
        require(metadata["native_source_verified"] is True, "native source was not verified")
        require(metadata["included_android_abis"] == list(ABIS), "Android ABI metadata mismatch")
        if not args.allow_dirty:
            require(metadata["source_tree_dirty"] is False and
                    metadata["connector_source_tree_dirty"] is False and
                    metadata["core_source_tree_dirty"] is False,
                    "release AAR records a dirty source tree")

        images: list[Path] = []
        for abi in ABIS:
            for name in LIBRARIES:
                image = extracted / f"jni/{abi}/{name}"
                expected = core_symbols if name == LIBRARIES[0] else jni_symbols
                verify_image(nm, readelf, image, abi, name, expected)
                images.append(image)
        scanner = root / ".native-source" / commit / "v2/scripts/verify_native_binary_identity.py"
        output(["python3", "-B", str(scanner), *map(str, images)])
        if not input_mode:
            output(["python3", "-B", str(root / "scripts/verify-library-managed.py"),
                    "--classes", str(extracted / "classes.jar"), "--connector-root", str(root)])

    print(json.dumps({
        "status": "PASS_ANDROID_AAR_HARDENING",
        "purpose": "frozen_core_input" if input_mode else "current_release_output",
        "aar": str(aar),
        "abis": list(ABIS),
        "core_exports": len(core_symbols),
        "jni_exports": len(jni_symbols),
        "dirty_metadata_allowed": args.allow_dirty,
    }, sort_keys=True))


if __name__ == "__main__":
    main()
