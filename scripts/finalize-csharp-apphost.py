#!/usr/bin/env python3
"""Build-only pinned apphost promotion; PE policy is the exact Core utility."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import stat
import sys
import uuid

MAX_IMAGE = 8 << 20


def directory(path: Path) -> None:
    if not path.is_absolute() or path.resolve(strict=True) != path or not path.is_dir():
        raise ValueError('existing absolute non-linked build directory required')


def pin(value: str) -> None:
    if len(value) != 64 or any(part not in '0123456789abcdef' for part in value):
        raise ValueError('trusted lowercase artifact digest required')


def digest(path: Path, size: int) -> str:
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or path.resolve(strict=True) != path or info.st_size != size:
        raise ValueError('regular bounded artifact size/type required')
    value = hashlib.sha256()
    copied = 0
    with path.open('rb') as stream:
        opened = os.fstat(stream.fileno())
        if not os.path.samestat(info, opened):
            raise ValueError('artifact changed before admission')
        while chunk := stream.read(65536):
            copied += len(chunk)
            if copied > size:
                raise ValueError('artifact grew during admission')
            value.update(chunk)
        after = os.fstat(stream.fileno())
    if copied != size or (opened.st_size, opened.st_mtime_ns, opened.st_ctime_ns) != (after.st_size, after.st_mtime_ns, after.st_ctime_ns):
        raise ValueError('artifact changed during admission')
    return value.hexdigest()


def utility(path: Path, expected: str):
    size = path.lstat().st_size
    if not 0 < size <= 65536 or digest(path, size) != expected:
        raise ValueError('owning artifact utility integrity refused')
    spec = importlib.util.spec_from_file_location('_coakka_build_metadata', path)
    if spec is None or spec.loader is None:
        raise ValueError('owning artifact utility cannot be loaded')
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def backup(source: Path, target: Path, size: int, expected: str) -> None:
    if target.exists() or target.is_symlink():
        if digest(target, size) != expected:
            raise ValueError('foreign private original is not overwritten')
        return
    owned = False
    try:
        with target.open('xb') as output:
            owned = True
            copied = 0
            with source.open('rb') as original:
                while chunk := original.read(65536):
                    copied += len(chunk)
                    if copied > size:
                        raise ValueError('original grew during backup')
                    if output.write(chunk) != len(chunk):
                        raise OSError('short private-original write')
            output.flush()
            os.fsync(output.fileno())
        if copied != size or digest(target, size) != expected:
            raise ValueError('private original integrity refused')
    except BaseException:
        if owned:
            target.unlink(missing_ok=True)
        raise


def finalize(source: Path, work: Path, size: int, raw: str, final: str, tool: Path, tool_digest: str) -> dict:
    if sys.version_info < (3, 11):
        raise ValueError('candidate build finalizer requires Python3.11')
    if not 0 < size <= MAX_IMAGE:
        raise ValueError('candidate apphost byte bound refused')
    for value in (raw, final, tool_digest):
        pin(value)
    if raw == final:
        raise ValueError('raw and finalized identities must differ')
    directory(work)
    directory(source.parent)
    if not source.is_absolute():
        raise ValueError('absolute published apphost required')
    module = utility(tool, tool_digest)
    for index, entry in enumerate(source.parent.iterdir()):
        if index >= 512:
            raise ValueError('candidate publish tree exceeds entry bound')
        if entry.name.startswith('.coakka-apphost-'):
            raise FileExistsError('unadmitted apphost stage requires explicit inspection')
    current = digest(source, size)
    if current == final:
        return dict(status='verified-already-finalized', sha256=final, bytes=size)
    if current != raw:
        raise ValueError('unqualified SDK/apphost input refused')
    private = work / 'csharp/apphost-private'
    if private.is_relative_to(source.parent):
        raise ValueError('private original must remain outside publish directory')
    for path in (work / 'csharp', private):
        if not path.exists() and not path.is_symlink():
            path.mkdir()
        directory(path)
    before = source.stat()
    backup(source, private / (raw + '.exe'), size, raw)
    staged = source.parent / ('.coakka-apphost-' + uuid.uuid4().hex + '.exe')
    owned = False
    try:
        receipt = module.finalize(source, staged, raw, profile='amd64-apphost-v1')
        owned = True
        if receipt['output_sha256'] != final or digest(staged, size) != final:
            raise ValueError('finalized apphost differs from trusted candidate')
        after = source.stat()
        if (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns, before.st_ctime_ns) != (
                after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns) or digest(source, size) != raw:
            raise ValueError('published apphost changed before promotion')
        # Same-directory replace is atomic; private debug originals never enter
        # the consumer tree. A failed/canceled publish cannot promote a stage.
        os.replace(staged, source)
        owned = False
        return dict(status='finalized', sha256=final, bytes=size, metadata=receipt)
    finally:
        if owned:
            staged.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--work-root', type=Path, required=True)
    parser.add_argument('--bytes', type=int, required=True)
    parser.add_argument('--raw-sha256', required=True)
    parser.add_argument('--final-sha256', required=True)
    parser.add_argument('--tool-sha256', required=True)
    args = parser.parse_args()
    result = finalize(args.input, args.work_root, args.bytes, args.raw_sha256, args.final_sha256,
        Path(__file__).resolve().with_name('pe_release_metadata.py'), args.tool_sha256)
    print('[apphost] ' + json.dumps(result, sort_keys=True))


if __name__ == '__main__':
    main()
