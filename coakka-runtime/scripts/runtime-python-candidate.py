#!/usr/bin/env python3
"""Offline standard-wheel sample installer; no connector/runtime policy."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import platform
import shutil
import signal
import stat
import subprocess
import sys
import tempfile
from collections.abc import Callable
import zipfile


class UnreapedChild(RuntimeError):
    """The application stage must be retained until its child is known stopped."""


ISOLATED_FLAGS = ("-I", "-X", "utf8", "-B")


def child_environment(base: dict[str, str], temporary: Path) -> dict[str, str]:
    result = {k: v for k, v in base.items() if not k.upper().startswith(("PYTHON", "PIP_", "COAKKA_"))}
    result.update(TMPDIR=str(temporary), TMP=str(temporary), TEMP=str(temporary),
                  PIP_CONFIG_FILE=os.devnull,
                  PYTHONUTF8="1", PYTHONNOUSERSITE="1", PYTHONSAFEPATH="1", PYTHONDONTWRITEBYTECODE="1")
    return result


def directory(path: Path) -> None:
    metadata = path.lstat()
    if not path.is_absolute() or linked(metadata) or not stat.S_ISDIR(metadata.st_mode):
        raise ValueError("expected an absolute existing non-linked directory")


def linked(metadata: os.stat_result) -> bool:
    # On baseline Windows3.11, a junction is a reparse point, not is_symlink().
    # This is installer-owned file admission, never a runtime capability probe.
    if stat.S_ISLNK(metadata.st_mode):
        return True
    if sys.platform == "win32":
        return bool(metadata.st_file_attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT)
    return False


def bounded_file(path: Path, maximum: int) -> bytes:
    before = path.lstat()
    if linked(before) or not stat.S_ISREG(before.st_mode) or not 0 < before.st_size <= maximum:
        raise ValueError("expected bounded regular file")
    with path.open("rb") as source:
        opened = os.fstat(source.fileno())
        if not os.path.samestat(before, opened):
            raise ValueError("input changed before read")
        content = source.read(maximum + 1)
        after = os.fstat(source.fileno())
    if len(content) != before.st_size or after.st_size != before.st_size or after.st_mtime_ns != opened.st_mtime_ns:
        raise ValueError("input changed during read")
    return content


def unique_object(pairs: list[tuple[str, object]]) -> dict:
    result: dict = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate catalog key")
        result[key] = value
    return result


def digest(value: object) -> bool:
    return isinstance(value, str) and len(value) == 64 and all(part in "0123456789abcdef" for part in value)


def decode_catalog(content: bytes) -> dict:
    value = json.loads(content, object_pairs_hook=unique_object)
    fields = {"schema", "state", "distribution", "version", "minimumRuntime", "coreCommit", "wheels"}
    targets = {"macos-aarch64", "linux-aarch64", "linux-x86_64", "windows-aarch64", "windows-x86_64"}
    if not isinstance(value, dict) or value.keys() != fields:
        raise ValueError("unknown catalog shape")
    if (type(value["schema"]) is not int or value["schema"] != 1 or value["state"] != "private-candidate" or
            value["distribution"] != "coakka-v2-connector" or value["version"] != "3.0.0" or
            value["minimumRuntime"] != "3.11" or value["coreCommit"] != "1522b4a9f30da1039ef8c7d198bbecfc749a2456" or
            not isinstance(value["wheels"], dict) or value["wheels"].keys() != targets):
        raise ValueError("invalid private Python candidate")
    for row in value["wheels"].values():
        if not isinstance(row, dict) or row.keys() != {"filename", "bytes", "sha256", "members"}:
            raise ValueError("invalid wheel row")
        name = row["filename"]
        if (not isinstance(name, str) or Path(name).name != name or "/" in name or "\\" in name or ":" in name or
                not name.startswith("coakka_v2_connector-3.0.0-cp311-abi3-") or not name.endswith(".whl") or
                type(row["bytes"]) is not int or not 0 < row["bytes"] <= 4 << 20 or not digest(row["sha256"]) or
                not isinstance(row["members"], dict) or not 0 < len(row["members"]) <= 128):
            raise ValueError("invalid wheel identity/bounds")
        total = 0
        for path, identity in row["members"].items():
            if (not valid_member(path) or not isinstance(identity, dict) or identity.keys() != {"bytes", "sha256"} or
                    type(identity["bytes"]) is not int or not 0 < identity["bytes"] <= 8 << 20 or not digest(identity["sha256"])):
                raise ValueError("invalid wheel member")
            total += identity["bytes"]
        if total > 32 << 20:
            raise ValueError("wheel extent exceeds bound")
    return value


def valid_member(name: object) -> bool:
    return (isinstance(name, str) and 0 < len(name) <= 256 and not PurePosixPath(name).is_absolute() and
            "\\" not in name and ":" not in name and all(part not in ("", ".", "..") for part in name.split("/")))


def admit_wheel(path: Path, row: dict) -> bytes:
    content = bounded_file(path, 4 << 20)
    if path.name != row["filename"] or len(content) != row["bytes"] or hashlib.sha256(content).hexdigest() != row["sha256"]:
        raise ValueError("wheel identity differs from trusted catalog")
    # Parse the already pinned bytes, not a second mutable filesystem read.
    from io import BytesIO

    with zipfile.ZipFile(BytesIO(content)) as source:
        entries = source.infolist()
        if not 0 < len(entries) <= 128 or len({item.filename for item in entries}) != len(entries):
            raise ValueError("wheel member count/duplicate refused")
        if {item.filename for item in entries} != row["members"].keys():
            raise ValueError("wheel members differ")
        for item in entries:
            expected = row["members"][item.filename]
            mode = stat.S_IFMT(item.external_attr >> 16)
            if (not valid_member(item.filename) or item.is_dir() or mode not in (0, stat.S_IFREG) or item.flag_bits & 1 or
                    item.file_size != expected["bytes"] or not 0 < item.file_size <= 8 << 20):
                raise ValueError("wheel non-regular/extent refused")
            with source.open(item) as stream:
                data = stream.read((8 << 20) + 1)
            if len(data) != item.file_size or hashlib.sha256(data).hexdigest() != expected["sha256"]:
                raise ValueError("wheel member integrity differs")
    return content


def run_command(command: list[str], cwd: Path, environment: dict[str, str], bound: float) -> None:
    process = subprocess.Popen(command, cwd=cwd, env=environment)
    try:
        status = process.wait(timeout=bound)
    except BaseException as cause:
        if process.poll() is None:
            try:
                process.kill()
            except OSError:
                if process.poll() is None:
                    raise UnreapedChild(f"cannot stop direct child pid={process.pid}") from cause
        try:
            process.wait(timeout=1)
        except subprocess.TimeoutExpired as error:
            raise UnreapedChild(f"cannot reap direct child pid={process.pid}") from error
        raise
    if status != 0:
        raise subprocess.CalledProcessError(status, command)


def with_stage(stage: Path, action: Callable[[], None]) -> None:
    try:
        action()
    except UnreapedChild as error:
        raise UnreapedChild(f"unconfirmed child stop; stage retained at {stage}") from error
    except BaseException as error:
        try:
            shutil.rmtree(stage)
        except OSError as cleanup:
            raise BaseExceptionGroup("sample and owned-stage cleanup failed", [error, cleanup]) from None
        raise
    else:
        shutil.rmtree(stage)


def entry(arguments: list[str]) -> None:
    flags = argparse.ArgumentParser(description=__doc__)
    flags.add_argument("--repo-root", required=True, type=Path)
    flags.add_argument("sample", choices=("basic", "deadletter", "hot-reload"))
    args = flags.parse_args(arguments)
    directory(args.repo_root)
    if sys.implementation.name != "cpython" or sys.version_info < (3, 11):
        raise ValueError("candidate requires CPython3.11 or later")
    for key, value in os.environ.items():
        upper = key.upper()
        if value and ((upper.startswith("COAKKA_") and upper not in ("COAKKA_SAMPLE_WORK_ROOT", "COAKKA_PYTHON_CANDIDATE_WHEEL")) or
                      upper.startswith(("LD_", "DYLD_")) or upper in ("PYTHONPATH", "PYTHONHOME", "PYTHONUSERBASE")):
            raise ValueError(f"remove sample override: {key}")
    work = Path(os.environ.get("COAKKA_SAMPLE_WORK_ROOT", ""))
    wheel = Path(os.environ.get("COAKKA_PYTHON_CANDIDATE_WHEEL", ""))
    directory(work)
    if not wheel.is_absolute():
        raise ValueError("set an absolute COAKKA_PYTHON_CANDIDATE_WHEEL")
    catalog = decode_catalog(bounded_file(args.repo_root / "coakka-runtime/artifacts/runtime-python-candidate.json", 256 << 10))
    # Installer artifact selection, never a connector capability/platform probe.
    target = {("darwin", "arm64"): "macos-aarch64", ("linux", "aarch64"): "linux-aarch64",
              ("linux", "x86_64"): "linux-x86_64", ("win32", "arm64"): "windows-aarch64",
              ("win32", "amd64"): "windows-x86_64"}.get((sys.platform, platform.machine().lower()))
    if target is None:
        raise ValueError("no qualified candidate for this installer target")
    row = catalog["wheels"][target]
    content = admit_wheel(wheel, row)
    app = bounded_file(args.repo_root / "coakka-runtime/python" / args.sample / "main.py", 128 << 10)
    stage = Path(tempfile.mkdtemp(prefix="coakka-python-sample-", dir=work))

    def install_and_run() -> None:
        archives, temporary, application = (stage / name for name in ("archives", "temp", "application"))
        for path in (archives, temporary, application):
            path.mkdir()
        installed_wheel = archives / row["filename"]
        installed_wheel.write_bytes(content)
        script = application / "main.py"
        script.write_bytes(app)
        environment = child_environment(dict(os.environ), temporary)
        venv = stage / "environment"
        run_command([sys.executable, *ISOLATED_FLAGS, "-m", "venv", "--without-pip", str(venv)], stage, environment, 180)
        python = venv / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
        # Baseline ensurepip forwards -I but not -X to its nested pip process.
        # Its one bootstrap phase uses safe-path/no-user-site plus only our four
        # allowlisted Python settings so UTF-8 also reaches that nested child.
        # Ordinary install and app execution keep full interpreter isolation.
        run_command([str(python), "-X", "utf8", "-s", "-P", "-B", "-m", "ensurepip", "--default-pip"], stage, environment, 180)
        run_command([str(python), *ISOLATED_FLAGS, "-m", "pip", "--isolated", "--disable-pip-version-check", "install",
                     "--no-index", "--no-deps", "--no-compile", "--no-cache-dir", "--prefix", str(venv), str(installed_wheel)],
                    stage, environment, 180)
        archives.rename(stage / "archives-retired")
        print(f"coakka_sample_package runtime=python version={catalog['version']} wheel={row['sha256']}", flush=True)
        run_command([str(python), *ISOLATED_FLAGS, "-Werror", str(script)], stage, environment, 30)

    with_stage(stage, install_and_run)


def interrupted(_signum: int, _frame: object) -> None:
    raise KeyboardInterrupt


def main() -> int:
    original = {kind: signal.signal(kind, interrupted) for kind in (signal.SIGINT, signal.SIGTERM)}
    try:
        entry(sys.argv[1:])
    except KeyboardInterrupt:
        print("[runtime-python-sample] interrupted after direct-child cleanup", file=sys.stderr)
        return 130
    except Exception as error:
        print(f"[runtime-python-sample] {error}", file=sys.stderr)
        return 1
    finally:
        for kind, handler in original.items():
            signal.signal(kind, handler)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
