#!/usr/bin/env python3
"""Check build-time component deployment floors; never rewrite native images."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import stat
import struct

MAX_BINARY = 64 << 20
MAX_COMMANDS = 4096
MAX_COMMAND_BYTES = 1 << 20
DEPLOYMENT = (13, 0, 0)


def minimum(data: bytes) -> tuple[int, int, int]:
    if not 32 <= len(data) <= MAX_BINARY:
        raise ValueError("bounded Mach-O component required")
    magic, cpu, _subtype, kind, count, extent, _flags, _reserved = struct.unpack_from("<8I", data)
    if (magic != 0xFEEDFACF or cpu != 0x0100000C or kind not in (2, 6)
            or not 0 < count <= MAX_COMMANDS or not 0 < extent <= MAX_COMMAND_BYTES
            or 32 + extent > len(data)):
        raise ValueError("expected a bounded thin ARM64 executable or library")
    offset, end = 32, 32 + extent
    found = None
    for _ in range(count):
        if offset + 8 > end:
            raise ValueError("truncated Mach-O load command")
        command, size = struct.unpack_from("<2I", data, offset)
        if size < 8 or size % 8 or offset + size > end:
            raise ValueError("invalid Mach-O load-command extent")
        if command in (0x32, 0x24):
            if found is not None:
                raise ValueError("ambiguous Mach-O deployment commands")
            if command == 0x32:
                if size < 24:
                    raise ValueError("truncated build-version command")
                platform, encoded, _sdk, tools = struct.unpack_from("<4I", data, offset + 8)
                if platform != 1 or 24 + tools * 8 != size:
                    raise ValueError("invalid macOS build-version command")
            else:
                if size != 16:
                    raise ValueError("invalid legacy macOS version command")
                encoded = struct.unpack_from("<I", data, offset + 8)[0]
            found = (encoded >> 16, (encoded >> 8) & 255, encoded & 255)
        offset += size
    if offset != end or found is None or found[0] == 0:
        raise ValueError("missing or inconsistent macOS deployment minimum")
    return found


def inspect(path: Path) -> dict:
    if not path.is_absolute() or path.parent.resolve(strict=True) != path.parent:
        raise ValueError("absolute non-linked component parent required")
    before = path.lstat()
    if not stat.S_ISREG(before.st_mode) or not 32 <= before.st_size <= MAX_BINARY:
        raise ValueError("bounded regular component required")
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0)
    with os.fdopen(os.open(path, flags), "rb") as stream:
        opened = os.fstat(stream.fileno())
        if not stat.S_ISREG(opened.st_mode) or not os.path.samestat(before, opened):
            raise ValueError("component changed before inspection")
        data = stream.read(MAX_BINARY + 1)
        after = os.fstat(stream.fileno())
    if (not os.path.samestat(before, opened) or not os.path.samestat(opened, after)
            or len(data) != before.st_size or after.st_size != before.st_size
            or after.st_mtime_ns != before.st_mtime_ns):
        raise ValueError("component changed during inspection")
    value = minimum(data)
    if value > DEPLOYMENT:
        raise ValueError("component exceeds macOS 13 deployment contract: " + path.name)
    return dict(file=path.name, minimum=".".join(map(str, value)),
                sha256=hashlib.sha256(data).hexdigest())


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("binary", type=Path, nargs="+")
    args = parser.parse_args()
    if len(args.binary) > 16:
        parser.error("at most 16 components per deployment check")
    try:
        print(json.dumps(dict(deployment="13.0", components=[inspect(p) for p in args.binary]), sort_keys=True))
    except (OSError, ValueError, struct.error) as error:
        parser.exit(1, "[macos-floor] " + str(error) + "\n")


if __name__ == "__main__":
    main()
