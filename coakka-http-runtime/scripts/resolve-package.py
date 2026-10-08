#!/usr/bin/env python3
"""Resolve a pinned offline candidate, never a private source checkout.

The sample repository pins complete archive identities independently of the
warehouse ledger. Extraction is bounded and confined. Reused trees are checked
against the archive on every invocation, so local damage fails closed.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import tarfile
import tempfile


def resolve(publish, work, lane, target):
    """Return an extracted package root after validating all retained files."""
    pins = json.loads(Path(__file__).with_name("package-pins.json").read_text())
    record = pins[lane]
    digest = record["sha256"][target]
    stem = f"coakka-http-{lane}-1.0.0-candidate-{target}"
    name = stem + "." + record["extension"]
    archive = publish / "coakka-http-runtime" / lane / "candidates" / record["date"] / name
    if archive.is_symlink() or not archive.is_file() or archive.stat().st_size > 64 * 1024 * 1024:
        raise ValueError("missing or invalid candidate archive: " + str(archive))
    if hashlib.sha256(archive.read_bytes()).hexdigest() != digest:
        raise ValueError("candidate archive checksum mismatch: " + name)
    work.mkdir(parents=True, exist_ok=True)
    destination = work / (stem + "-" + digest[:16])
    with tarfile.open(archive, "r:gz") as source:
        entries = source.getmembers()
        if len(entries) > 512 or sum(e.size for e in entries) > 128 * 1024 * 1024:
            raise ValueError("candidate inventory exceeds sample extraction bound")
        names = set()
        for entry in entries:
            path = PurePosixPath(entry.name)
            if path.is_absolute() or ".." in path.parts or "\\" in entry.name or entry.name in names:
                raise ValueError("unsafe or duplicated archive path")
            names.add(entry.name)
            if not (entry.isfile() or entry.isdir() or entry.issym()):
                raise ValueError("unsupported archive entry")
            if entry.issym():
                link = PurePosixPath(entry.linkname)
                if link.is_absolute() or ".." in link.parts or "\\" in entry.linkname:
                    raise ValueError("unsafe archive link")
        if not destination.exists():
            stage = Path(tempfile.mkdtemp(prefix="extract-", dir=work))
            try:
                # All bytes were pinned before opening; paths and resource bounds
                # are independently checked above. No executable install hooks.
                if hasattr(tarfile, "data_filter"):
                    source.extractall(stage, filter="data")
                else:
                    source.extractall(stage)
                stage.rename(destination)
            finally:
                if stage.exists():
                    shutil.rmtree(stage)
        if destination.is_symlink():
            raise ValueError("package cache root must not be a symlink")
        for entry in entries:
            path = destination / entry.name
            path.resolve().relative_to(destination.resolve())
            if entry.isfile():
                if path.is_symlink() or not path.is_file() or path.read_bytes() != source.extractfile(entry).read():
                    raise ValueError("extracted package was modified: " + entry.name)
            elif entry.issym() and (not path.is_symlink() or str(path.readlink()) != entry.linkname):
                raise ValueError("extracted package link was modified")
        files = {str(p.relative_to(destination)) for p in destination.rglob("*") if not p.is_dir() or p.is_symlink()}
        expected = {e.name for e in entries if not e.isdir()}
        if files != expected:
            raise ValueError("unexpected files in immutable package cache")
    return destination if lane == "native" else destination / ("package" if lane == "javascript" else stem)


def main():
    """Keep stdout machine-readable for shell runners; fail before compilation."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--publish", type=Path, required=True)
    parser.add_argument("--work", type=Path, required=True)
    parser.add_argument("lane", choices=("native", "go", "jvm", "javascript", "python"))
    parser.add_argument("target")
    args = parser.parse_args()
    try:
        print(resolve(args.publish.resolve(), args.work.resolve(), args.lane, args.target))
    except (OSError, ValueError, KeyError, tarfile.TarError) as error:
        parser.exit(1, f"sample package refused: {error}\n")


if __name__ == "__main__":
    main()
