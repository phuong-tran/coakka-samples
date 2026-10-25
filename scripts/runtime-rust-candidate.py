#!/usr/bin/env python3
"""Cold, offline Rust sample build/install. Never loaded by the installed app."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import signal
import stat
import subprocess
import sys
import tarfile
import time

ARCHIVE_SHA = "df7093947077b9da46311d544e27495b992ee781f0db5d8355a4d9137235a038"
TOOL_SHA = "0a9ac6c65a95a929029a38515701eb5f6293b7f2d271d76a4b6d83cd3eaa707e"
TOOL_MEMBER = "coakka-runtime-rs-2.5.3/tools/package_tools.py"
SOURCES = ("Cargo.toml", "Cargo.lock", "src/main.rs")
TARGETS = {
    "macos-aarch64": "aarch64-apple-darwin",
    "linux-aarch64": "aarch64-unknown-linux-gnu",
    "windows-x86_64": "x86_64-pc-windows-msvc",
}


def directory(path: Path) -> None:
    if not path.is_absolute() or path.resolve(strict=True) != path or not path.is_dir():
        raise ValueError("existing absolute non-linked directory required")
    # resolve() detects symbolic links but not every Windows reparse tag.
    for parent in (path, *path.parents):
        if getattr(parent.lstat(), "st_file_attributes", 0) & 0x400:
            raise ValueError("reparse directory refused")


def regular(path: Path, maximum: int) -> bytes:
    directory(path.parent)
    before = path.lstat()
    if (not stat.S_ISREG(before.st_mode) or getattr(before, "st_file_attributes", 0) & 0x400
            or not 0 < before.st_size <= maximum):
        raise ValueError("bounded regular file required")
    with path.open("rb") as stream:
        opened = os.fstat(stream.fileno())
        content = stream.read(maximum + 1)
        after = os.fstat(stream.fileno())
    if (not os.path.samestat(before, opened) or not os.path.samestat(opened, after)
            or len(content) != before.st_size or after.st_size != before.st_size
            or after.st_mtime_ns != before.st_mtime_ns):
        raise ValueError("input changed during read")
    return content


def digest(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def archive_tool(content: bytes) -> bytes:
    if len(content) > 16 << 20 or digest(content) != ARCHIVE_SHA:
        raise ValueError("frozen Rust archive identity refused")
    # Only digest-admitted bytes reach tar parsing or Python compilation.
    # Full member/path/expansion admission stays in this exact package's
    # tested helper, rather than another parser copied into the sample runner.
    with tarfile.open(fileobj=io.BytesIO(content), mode="r:gz") as package:
        member = package.getmember(TOOL_MEMBER)
        if not member.isfile() or not 0 < member.size <= 65536:
            raise ValueError("package tool bound/type")
        stream = package.extractfile(member)
        if stream is None:
            raise ValueError("package tool missing")
        with stream:
            result = stream.read(65537)
    if digest(result) != TOOL_SHA:
        raise ValueError("package tool identity refused")
    return result


def write_new(path: Path, content: bytes) -> None:
    with path.open("xb") as output:
        if output.write(content) != len(content):
            raise OSError("short staging write")
        output.flush()
        os.fsync(output.fileno())


def child(command: list[str], cwd: Path, env: dict[str, str], log: Path, bound: int) -> dict:
    started = time.monotonic()
    with log.open("xb") as output:
        process = subprocess.Popen(command, cwd=cwd, env=env, stdout=output,
                                   stderr=subprocess.STDOUT, start_new_session=os.name != "nt")
        try:
            code = process.wait(timeout=bound)
        except BaseException:
            if sys.platform == "win32":
                subprocess.run([str(Path(env["SYSTEMROOT"]) / "System32/taskkill.exe"),
                                "/PID", str(process.pid), "/T", "/F"],
                               capture_output=True, timeout=10, check=False)
            else:
                os.killpg(process.pid, signal.SIGKILL)
            process.wait(timeout=10)
            raise
    result = {"command": command, "exit_code": code, "seconds": time.monotonic() - started,
              "log_sha256": digest(regular(log, 4 << 20))}
    if code:
        raise RuntimeError("Rust build failed; retained log: " + str(log))
    return result


def build(args: argparse.Namespace) -> Path:
    for path in (args.work.parent, args.cargo_home, args.source):
        directory(path)
    regular(args.cargo, 64 << 20)
    if (not args.work.is_absolute() or args.work.exists() or args.work.is_symlink()
            or any(args.work.is_relative_to(path) or path.is_relative_to(args.work)
                   for path in (args.source, args.cargo_home, args.archive))):
        raise ValueError("fresh non-overlapping work directory required")
    content = regular(args.archive, 16 << 20)
    tool = archive_tool(content)
    source = {name: regular(args.source / name, 65536) for name in SOURCES}
    linker = args.cargo.parent.parent / "lib/rustlib/x86_64-pc-windows-msvc/bin/rust-lld.exe"
    if args.target == "windows-x86_64":
        regular(linker, 128 << 20)
    args.work.mkdir()  # This exclusive reservation is the cleanup ownership fence.
    build_root = args.work / "build"
    build_root.mkdir()
    rows: list[dict] = []
    receipt = {"state": "failed", "archive_sha256": ARCHIVE_SHA, "tool_sha256": TOOL_SHA,
               "sources": {name: digest(data) for name, data in source.items()}, "commands": rows}
    try:
        helper = build_root / "package_tools.py"
        write_new(helper, tool)
        spec = importlib.util.spec_from_file_location("rust_package_tools", helper)
        if spec is None or spec.loader is None:
            raise ValueError("cannot load admitted build-only tool")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        # Re-snapshot admitted bytes: changing the input path after bootstrap
        # cannot select another archive between admission and helper unpack.
        snapshot = build_root / "candidate.tar.gz"
        write_new(snapshot, content)
        package = build_root / "RuntimeCandidate"
        module.unpack(snapshot, ARCHIVE_SHA, package)
        project = build_root / "basic"
        (project / "src").mkdir(parents=True)
        for name, data in source.items():
            write_new(project / name, data)
        temporary = build_root / "temp"
        temporary.mkdir()
        env = {key: value for key, value in os.environ.items()
               if not key.upper().startswith(("COAKKA_", "CARGO_", "RUST", "DYLD_", "LD_"))}
        env.update(PATH=str(args.cargo.parent) + os.pathsep + env.get("PATH", ""),
                   CARGO_HOME=str(args.cargo_home), CARGO_TARGET_DIR=str(build_root / "target"),
                   CARGO_NET_OFFLINE="true", TMPDIR=str(temporary), TMP=str(temporary), TEMP=str(temporary))
        flags = ["-Dwarnings", "--remap-path-prefix=" + str(args.work) + "=coakka-app",
                 "--remap-path-prefix=" + str(args.cargo_home) + "=coakka-language-cache"]
        if args.target == "windows-x86_64":
            # The default platform linker retains POGO metadata even with
            # strip=symbols. Select this toolchain's compatible bundled linker;
            # never patch the image or waive its release metadata gate.
            flags.extend(("-Ctarget-feature=+crt-static", "-Clinker=" + str(linker),
                          "-Clinker-flavor=lld-link"))
        env["CARGO_ENCODED_RUSTFLAGS"] = "\x1f".join(flags)
        triple = TARGETS[args.target]
        rows.append(child([str(args.cargo), "build", "--offline", "--locked", "--release", "--target", triple],
                          project, env, args.work / "build.log", 90))
        executable_name = "coakka-runtime-sample" + (".exe" if args.target == "windows-x86_64" else "")
        binary = build_root / "target" / triple / "release" / executable_name
        app = args.work / "installed"
        app.mkdir()
        installed = app / executable_name
        write_new(installed, regular(binary, 8 << 20))
        installed.chmod(0o755)
        module.deploy(package, installed, args.target)
        receipt.update(state="built", target=args.target, executable_sha256=digest(installed.read_bytes()))
        return installed
    except BaseException:
        app = args.work / "installed"
        if app.exists():
            shutil.rmtree(app)
        raise
    finally:
        # No running app uses the build tree. Keep installed resources and the
        # bounded log/receipt; extracted source/Cargo/temp state is reproducible.
        shutil.rmtree(build_root)
        write_new(args.work / "receipt.json", (json.dumps(receipt, indent=2) + "\n").encode())


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", required=True, type=Path)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--work", required=True, type=Path)
    parser.add_argument("--cargo", required=True, type=Path)
    parser.add_argument("--cargo-home", required=True, type=Path)
    parser.add_argument("--target", required=True, choices=TARGETS)
    args = parser.parse_args()
    if sys.version_info < (3, 11):
        parser.error("Python3.11 or later is a build-only requirement")
    print(build(args))


if __name__ == "__main__":
    main()
