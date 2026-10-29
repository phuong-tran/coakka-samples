#!/usr/bin/env python3
"""Admit a private source candidate, then use ordinary SwiftPM; no runtime policy."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
from pathlib import Path, PurePosixPath
import shutil
import stat
import subprocess
import sys
import tempfile


def directory(path: Path) -> None:
    if not path.is_absolute() or path.resolve(strict=True) != path or not path.is_dir():
        raise ValueError("existing absolute non-linked directory required")


def member(name: object) -> bool:
    return (isinstance(name, str) and 0 < len(name) <= 256 and not PurePosixPath(name).is_absolute()
        and "\\" not in name and ":" not in name and all(part not in ("", ".", "..") for part in name.split("/")))


def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate catalog key")
        result[key] = value
    return result


def identity(row: object) -> None:
    if (not isinstance(row, dict) or row.keys() != {"bytes", "sha256"} or type(row["bytes"]) is not int
        or not 0 < row["bytes"] <= 64 << 20 or not isinstance(row["sha256"], str)
        or len(row["sha256"]) != 64 or any(part not in "0123456789abcdef" for part in row["sha256"])):
        raise ValueError("invalid artifact identity/bound")


def catalog(content: bytes) -> dict:
    if not 0 < len(content) <= 256 << 10:
        raise ValueError("catalog bound")
    value = json.loads(content, object_pairs_hook=unique)
    if (not isinstance(value, dict) or value.keys() != {"schema", "state", "version", "coreCommit", "files", "languageFiles"}
        or type(value["schema"]) is not int or value["schema"] != 1 or value["state"] != "private-candidate"
        or value["version"] != "3.0.0" or value["coreCommit"] != "1522b4a9f30da1039ef8c7d198bbecfc749a2456"
        or not isinstance(value["files"], dict) or len(value["files"]) != 57
        or not {"Package.swift", "coakka-runtime-package.json", "core-abi-manifest.json", "LICENSE", "NOTICE", "NATIVE-LICENSE.md", "PACKAGE-LICENSE.md"} <= value["files"].keys()
        or not isinstance(value["languageFiles"], dict) or value["languageFiles"].keys() != {
            "macos-aarch64", "linux-aarch64", "linux-x86_64", "windows-aarch64", "windows-x86_64"}):
        raise ValueError("invalid private Swift catalog")
    for rows in (value["files"], *value["languageFiles"].values()):
        if not isinstance(rows, dict) or len(rows) > 64:
            raise ValueError("artifact table bound")
        for name, row in rows.items():
            if not member(name):
                raise ValueError("artifact path refused")
            identity(row)
        if sum(row["bytes"] for row in rows.values()) > 192 << 20:
            raise ValueError("artifact total bound")
    return value


def regular(path: Path, maximum: int) -> bytes:
    before = path.lstat()
    if not stat.S_ISREG(before.st_mode) or path.resolve(strict=True) != path or not 0 < before.st_size <= maximum:
        raise ValueError("bounded regular file required")
    with path.open("rb") as source:
        opened = os.fstat(source.fileno())
        if not os.path.samestat(before, opened):
            raise ValueError("input changed before read")
        content = source.read(maximum + 1)
        after = os.fstat(source.fileno())
    if len(content) != before.st_size or opened.st_mtime_ns != after.st_mtime_ns or after.st_size != before.st_size:
        raise ValueError("input changed during read")
    return content


def copy_file(source: Path, target: Path, row: dict) -> None:
    identity(row)
    before = source.lstat()
    if not stat.S_ISREG(before.st_mode) or source.resolve(strict=True) != source or before.st_size != row["bytes"]:
        raise ValueError("artifact size/type refused")
    target.parent.mkdir(parents=True, exist_ok=True)
    digest = hashlib.sha256()
    copied = 0
    with source.open("rb") as original, target.open("xb") as output:
        opened = os.fstat(original.fileno())
        if not os.path.samestat(before, opened):
            raise ValueError("artifact changed before copy")
        while chunk := original.read(65536):
            copied += len(chunk)
            if copied > row["bytes"]:
                raise ValueError("artifact grew during copy")
            output.write(chunk)
            digest.update(chunk)
        after = os.fstat(original.fileno())
    if (copied != row["bytes"] or digest.hexdigest() != row["sha256"]
        or after.st_size != before.st_size or after.st_mtime_ns != opened.st_mtime_ns):
        raise ValueError("artifact integrity refused")


def copy_package(source: Path, target: Path, rows: dict) -> None:
    directory(source)
    found = set()
    for count, path in enumerate(source.rglob("*"), 1):
        if count > 128 or len(path.relative_to(source).parts) > 8 or path.is_symlink() or path.resolve() != path:
            raise ValueError("source tree bound/link refused")
        if path.is_file():
            found.add(path.relative_to(source).as_posix())
        elif not path.is_dir():
            raise ValueError("nonregular source member")
    if found != rows.keys():
        raise ValueError("source package member set differs")
    for name, row in rows.items():
        copy_file(source / name, target / name, row)


def run(command: list[str], cwd: Path, environment: dict[str, str], bound: int) -> None:
    child = subprocess.Popen(command, cwd=cwd, env=environment)
    try:
        code = child.wait(timeout=bound)
    except BaseException:
        if child.poll() is None:
            child.kill()
        child.wait(timeout=10)
        raise
    if code:
        raise subprocess.CalledProcessError(code, command)


def build_flags(host: str, platform_target: str) -> list[str]:
    """Select build-tool arguments; runtime behavior stays in the admitted package."""
    flags = ["-Xswiftc", "-warnings-as-errors", "-Xcc", "-Wall", "-Xcc", "-Wextra", "-Xcc", "-Werror", "-debug-info-format", "none",
        "-Xswiftc", "-Osize", "-Xswiftc", "-whole-module-optimization", "-Xswiftc", "-Xfrontend", "-Xswiftc", "-disable-reflection-metadata",
        "-Xswiftc", "-Xfrontend", "-Xswiftc", "-disable-reflection-names"]
    if host == "mac":
        flags += ["-Xlinker", "-dead_strip", "-Xlinker", "-S", "-Xlinker", "-x"]
    elif host == "pi":
        flags += ["-Xswiftc", "-use-ld=lld", "-Xswiftc", "-no-toolchain-stdlib-rpath", "-Xcc", "-fvisibility=hidden",
            "-Xlinker", "--gc-sections", "-Xlinker", "--strip-all", "-Xlinker", "--fatal-warnings", "-Xlinker", "-z", "-Xlinker", "relro",
            "-Xlinker", "-z", "-Xlinker", "now", "-Xlinker", "-rpath", "-Xlinker", "$ORIGIN/lib"]
    else:
        if platform_target == "windows-x86_64":
            # Swift6.0.3 Windows system modules own their builtin C headers.
            # Forward to Swift's importer only; native C warning flags stay exact.
            flags += ["-Xswiftc", "-Xcc", "-Xswiftc", "-Xclang",
                "-Xswiftc", "-Xcc", "-Xswiftc", "-fbuiltin-headers-in-system-modules"]
        else:
            flags += ["--build-system", "native"]
        flags += ["-Xlinker", "/WX", "-Xlinker", "/OPT:REF", "-Xlinker", "/OPT:ICF", "-Xlinker", "/INCREMENTAL:NO"]
    return flags


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, required=True)
    args = parser.parse_args()
    if sys.version_info < (3, 11):
        raise ValueError("optional candidate build helper requires Python3.11")
    repo = args.repo_root
    work = Path(os.environ.get("COAKKA_SAMPLE_WORK_ROOT", ""))
    candidate = Path(os.environ.get("COAKKA_SWIFT_CANDIDATE_HOME", ""))
    cache = Path(os.environ.get("COAKKA_SWIFT_CACHE_ROOT", ""))
    for path in (repo, work, candidate, cache):
        directory(path)
    for name in ("swift", "config", "security", "clang"):
        if (cache / name).exists():
            directory(cache / name)
    if work.is_relative_to(candidate) or candidate.is_relative_to(work) or cache.is_relative_to(candidate):
        raise ValueError("candidate inputs must not overlap work/cache")
    for key in ("COAKKA_RUNTIME_LIB", "COAKKA_RUNTIME_NATIVE_PATH", "COAKKA_FILE_LANE_RUNTIME_LIB", "COAKKA_STREAM_LANE_RUNTIME_LIB"):
        if os.environ.get(key):
            raise ValueError("candidate sample refuses native overrides")
    value = catalog(regular(repo / "coakka-runtime/artifacts/runtime-swift-candidate.json", 256 << 10))
    host = {"darwin": "mac", "linux": "pi", "win32": "windows"}.get(sys.platform)
    cpu = {"arm64": "aarch64", "aarch64": "aarch64", "amd64": "x86_64", "x86_64": "x86_64"}.get(platform.machine().lower())
    if host is None or cpu is None:
        raise ValueError("no admitted installer platform")
    platform_target = {"mac": "macos", "pi": "linux", "windows": "windows"}[host] + "-" + cpu
    if platform_target not in value["languageFiles"]:
        raise ValueError("no admitted installer platform")
    swift = shutil.which("swift")
    if swift is None:
        raise ValueError("configure the qualified Swift build toolchain")
    stage = Path(tempfile.mkdtemp(prefix="coakka-swift-basic-", dir=work))
    try:
        app, temporary = stage / "application", stage / "temp"
        app.mkdir()
        temporary.mkdir()
        copy_package(candidate, stage / "runtime-candidate", value["files"])
        language = stage / "language"
        if value["languageFiles"][platform_target]:
            runtime = Path(os.environ.get("COAKKA_SWIFT_LANGUAGE_HOME", ""))
            directory(runtime)
            for name, row in value["languageFiles"][platform_target].items():
                copy_file(runtime / name, language / name, row)
        for name in ("Package.swift", "Sample.swift", "AppScope.swift", "README.md", "run.sh", "tests/ScopeControls.swift"):
            content = regular(repo / "coakka-runtime/swift/basic" / name, 128 << 10)
            target = app / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
        environment = {key: val for key, val in os.environ.items() if not key.upper().startswith(("COAKKA_", "ASAN_", "LSAN_", "UBSAN_", "TSAN_", "DYLD_", "LD_"))}
        environment.update(TMPDIR=str(temporary), TMP=str(temporary), TEMP=str(temporary), CLANG_MODULE_CACHE_PATH=str(cache / "clang"))
        version = subprocess.run([swift, "--version"], env=environment, capture_output=True, text=True, timeout=15, check=True).stdout
        expected = "Swift version 6.4.1" if platform_target == "windows-aarch64" else (
            "Swift version 6.2" if host == "mac" else "Swift version 6.0.3")
        if expected not in version:
            raise ValueError("qualified candidate toolchain profile required")
        flags = build_flags(host, platform_target)
        run([swift, "build", "--package-path", str(app), "--scratch-path", str(stage / "build"), "--cache-path", str(cache / "swift"),
            "--config-path", str(cache / "config"), "--security-path", str(cache / "security"), "-j", "4", "-c", "release", *flags], stage, environment, 180)
        triple = "arm64-apple-macosx" if host == "mac" else (
            ("aarch64" if cpu == "aarch64" else "x86_64") + ("-unknown-linux-gnu" if host == "pi" else "-unknown-windows-msvc"))
        output = stage / "build" / triple / "release"
        installed = stage / "installed"
        installed.mkdir()
        executable = "CoAkkaRuntimeSample" + (".exe" if host == "windows" else "")
        module = {"mac": "libCoAkkaRuntime.dylib", "pi": "libCoAkkaRuntime.so", "windows": "CoAkkaRuntime.dll"}[host]
        bundle = "coakka-v2-runtime-swift_CoAkkaRuntime" + (".bundle" if host == "mac" else ".resources")
        for name in (executable, module):
            shutil.copy2(output / name, installed / name)
        shutil.copytree(output / bundle, installed / bundle)
        if value["languageFiles"][platform_target]:
            for name in value["languageFiles"][platform_target]:
                target = installed / name
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(language / name, target)
        environment.pop("SDKROOT", None)
        if host == "windows":
            environment.pop("Path", None)
            environment["PATH"] = "C:/Windows/System32;C:/Windows"
        elif host == "pi":
            environment["PATH"] = "/usr/bin:/bin"
        run([str(installed / executable)], installed, environment, 30)
        print(f"[swift-sample] installed application retained at {installed}")
    except BaseException as error:
        raise RuntimeError(f"candidate build failed; owned stage retained at {stage}") from error


if __name__ == "__main__":
    main()
