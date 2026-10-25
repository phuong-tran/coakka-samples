#!/usr/bin/env python3
"""Private PE release-metadata admission and narrowly profiled debug stripping.

Not a general PE rewriter or loader validator. The scanner is read-only; the
strip CLI requires an independently pinned, frozen producer input and writes a
new image. It never removes unfamiliar records or changes executable layout.
"""

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import stat
import struct
from typing import NamedTuple


MAX_HEADER = 256 * 1024
MAX_SECTIONS = 96
MAX_DEBUG_ENTRIES = 256
MAX_STRIP_IMAGE = 32 * 1024 * 1024
MAX_DEBUG_SECTION = 64 * 1024


class PEError(ValueError):
    pass


class Section(NamedTuple):
    name: bytes
    virtual_size: int
    rva: int
    raw_size: int
    offset: int
    flags: int
    auxiliary: bytes


class Layout(NamedTuple):
    machine: int
    magic: int
    file_flags: int
    entrypoint: int
    symbols: tuple
    checksum: int
    directories_offset: int
    directories: tuple
    sections: tuple


def _read(stream, size, offset, count):
    if offset < 0 or count < 0 or offset > size - count:
        raise PEError("PE span is outside the image")
    stream.seek(offset)
    value = stream.read(count)
    if len(value) != count:
        raise PEError("truncated PE span")
    return value


def _overlap(a, length, b, extent):
    return length > 0 and extent > 0 and a < b + extent and b < a + length


def _mapped(sections, rva, size):
    matches = [section for section in sections if size > 0 and
               section.rva <= rva and rva + size <= section.rva + section.raw_size]
    if len(matches) != 1:
        raise PEError("PE RVA has no unique file-backed section")
    section = matches[0]
    return section, section.offset + rva - section.rva


def read_layout(stream, size):
    """Bounded PE32/PE32+ file layout shared by the scanner and strip profile."""
    dos = _read(stream, size, 0, 64)
    nt = int.from_bytes(dos[60:64], "little")
    if dos[:2] != b"MZ" or not 64 <= nt <= MAX_HEADER - 24:
        raise PEError("invalid PE header offset")
    coff = _read(stream, size, nt, 24)
    if coff[:4] != b"PE\0\0":
        raise PEError("invalid PE signature")
    machine, count, _, symbol_offset, symbol_count, opt_size, file_flags = struct.unpack("<HHIIIHH", coff[4:])
    if not 1 <= count <= MAX_SECTIONS or not 96 <= opt_size <= 4096:
        raise PEError("unsupported PE section or optional-header count")
    end = nt + 24 + opt_size + count * 40
    if end > MAX_HEADER:
        raise PEError("PE headers exceed the byte bound")
    opt = _read(stream, size, nt + 24, opt_size)
    magic = int.from_bytes(opt[:2], "little")
    directory_start = {0x10b: 96, 0x20b: 112}.get(magic)
    if directory_start is None or opt_size < directory_start:
        raise PEError("unsupported PE optional header")
    directory_count = struct.unpack_from("<I", opt, directory_start - 4)[0]
    if not 7 <= directory_count <= 16 or directory_start + directory_count * 8 > opt_size:
        raise PEError("invalid PE data-directory count")
    image_size, header_size, checksum = struct.unpack_from("<III", opt, 56)
    if not end <= header_size <= min(size, MAX_HEADER) or image_size < header_size:
        raise PEError("invalid PE header/image bounds")
    directories = tuple(struct.unpack_from("<II", opt, directory_start + i * 8)
                        for i in range(directory_count))
    table = _read(stream, size, nt + 24 + opt_size, count * 40)
    sections: list[Section] = []
    for i in range(count):
        raw = table[i * 40:(i + 1) * 40]
        virtual_size, rva, raw_size, offset = struct.unpack_from("<IIII", raw, 8)
        extent = max(virtual_size, raw_size)
        if (raw_size and (offset < header_size or offset > size - raw_size)) or (
                extent and (rva < header_size or rva > image_size - extent)):
            raise PEError("invalid PE section bounds")
        section = Section(raw[:8].rstrip(b"\0"), virtual_size, rva, raw_size, offset,
                          struct.unpack_from("<I", raw, 36)[0], raw[24:36])
        for old in sections:
            if _overlap(offset, raw_size, old.offset, old.raw_size) or _overlap(
                    rva, extent, old.rva, max(old.virtual_size, old.raw_size)):
                raise PEError("overlapping PE sections")
        sections.append(section)
    return Layout(machine, magic, file_flags, struct.unpack_from("<I", opt, 16)[0],
                  (symbol_offset, symbol_count), checksum,
                  nt + 24 + directory_start, directories, tuple(sections))


def debug_records(stream, size, layout):
    rva, extent = layout.directories[6]
    if (rva, extent) == (0, 0):
        return ()
    if not rva or not extent or extent % 28 or extent > MAX_DEBUG_ENTRIES * 28:
        raise PEError("invalid PE debug-directory bounds")
    _, offset = _mapped(layout.sections, rva, extent)
    data = _read(stream, size, offset, extent)
    records = []
    for start in range(0, extent, 28):
        record = struct.unpack_from("<IIHHIIII", data, start)
        _, _, _, _, _, payload_size, payload_rva, payload_offset = record
        if payload_size:
            if payload_offset < 0 or payload_offset > size - payload_size:
                raise PEError("invalid PE debug payload span")
            if payload_rva and _mapped(layout.sections, payload_rva, payload_size)[1] != payload_offset:
                raise PEError("inconsistent PE debug payload mapping")
        elif payload_rva or payload_offset:
            # ILLink's writer emits the directory-end file offset even for
            # its empty Repro marker. Admit that exact zero-payload form only;
            # a nonzero RVA, other type/fields or arbitrary pointer still fails.
            empty_repro = (extent == 28 and record[:7] == (0, 0, 0, 0, 16, 0, 0)
                           and payload_offset == offset + extent)
            if not empty_repro:
                raise PEError("invalid empty PE debug payload")
        records.append(record)
    return tuple(records)


def check_release_metadata(stream, size):
    layout = read_layout(stream, size)
    if any(layout.symbols) or any(section.name.startswith((b".debug", b".zdebug"))
                                  for section in layout.sections):
        raise PEError("release PE retains symbol/debug storage")
    for record in debug_records(stream, size, layout):
        kind, extent = record[4:6]
        # Determinism and extended protection records are not debug symbols.
        # PE permits Repro's empty marker or a length-prefixed hash. Admit only
        # the qualified MSVC 32-byte hash shape, not arbitrary debug payloads.
        repro = kind == 16 and extent == 0
        if kind == 16 and extent == 36 and record[6]:
            payload = _read(stream, size, record[7], 36)
            repro = int.from_bytes(payload[:4], "little") == 32
        # Unfamiliar kinds/shapes fail closed; this never authorizes deletion.
        if (any(record[i] for i in (0, 2, 3)) or
                not (repro or (kind == 20 and extent == 4))):
            raise PEError(f"release PE retains debug metadata (type {kind})")


def stripped_image(value):
    """Accept only the known dedicated CodeView+Repro profile, preserving RVAs."""
    if not 0 < len(value) <= MAX_STRIP_IMAGE:
        raise PEError("strip image exceeds the byte bound")
    stream = io.BytesIO(value)
    layout = read_layout(stream, len(value))
    if (layout.machine, layout.magic, layout.file_flags) != (0x8664, 0x20b, 0x2022):
        raise PEError("strip profile requires AMD64 PE32+")
    if any(layout.symbols) or layout.checksum or any(layout.directories[4]):
        raise PEError("strip profile refuses symbols, checksum or signed input")
    records = debug_records(stream, len(value), layout)
    if len(records) != 2 or records[0][4] != 2 or records[1][4] != 16:
        raise PEError("unsupported PE debug record profile")
    cv, repro = records
    if any(cv[i] for i in (0, 2, 3)) or any(repro[i] for i in (0, 2, 3, 5, 6, 7)):
        raise PEError("unsupported PE debug record fields")
    rva, directory_size = layout.directories[6]
    section, offset = _mapped(layout.sections, rva, directory_size)
    if (section.name != b".buildid" or section.flags != 0x40000040 or any(section.auxiliary) or
            rva != section.rva or offset != section.offset or
            not 0 < section.raw_size <= MAX_DEBUG_SECTION or
            section.virtual_size != directory_size + cv[5] or
            section.virtual_size > section.raw_size or
            cv[6] != rva + directory_size or cv[7] != offset + directory_size):
        raise PEError("debug storage is not a dedicated read-only strip section")
    payload = value[cv[7]:cv[7] + cv[5]]
    if len(payload) < 25 or payload[:4] != b"RSDS" or payload[-1:] != b"\0" or b"\0" in payload[24:-1]:
        raise PEError("invalid CodeView locator profile")
    if any(value[offset + section.virtual_size:offset + section.raw_size]):
        raise PEError("debug section has unrelated nonzero padding")
    if section.rva <= layout.entrypoint < section.rva + section.raw_size:
        raise PEError("debug section contains the entrypoint")
    for i, (address, extent) in enumerate(layout.directories):
        if i != 6 and _overlap(address, extent, section.rva, section.raw_size):
            raise PEError("debug storage overlaps a live data directory")
    result = bytearray(value)
    ranges = ((layout.directories_offset + 6 * 8, 8), (offset, section.raw_size))
    for start, extent in ranges:
        result[start:start + extent] = bytes(extent)
    check_release_metadata(io.BytesIO(result), len(result))
    return result, ranges


def stripped_apphost_image(value):
    """Separate unsigned producer EXE profile; never erase the shared section."""
    if not 0 < len(value) <= MAX_STRIP_IMAGE:
        raise PEError("strip image exceeds the byte bound")
    stream = io.BytesIO(value)
    layout = read_layout(stream, len(value))
    if (layout.machine, layout.magic, layout.file_flags) != (0x8664, 0x20b, 0x22):
        raise PEError("apphost profile requires unsigned AMD64 PE32+ EXE")
    if any(layout.symbols) or layout.checksum or any(layout.directories[4]):
        raise PEError("apphost profile refuses symbols, checksum or signed input")
    if any(bool(address) != bool(extent) for address, extent in layout.directories):
        raise PEError("apphost profile refuses incomplete live-directory spans")
    records = debug_records(stream, len(value), layout)
    if len(records) != 3 or tuple(record[4] for record in records) != (2, 12, 13):
        raise PEError("unsupported apphost debug record profile")
    if any(any(record[i] for i in (0, 2, 3)) or record[1] != records[0][1] for record in records):
        raise PEError("unsupported apphost debug record fields")
    rva, extent = layout.directories[6]
    section, offset = _mapped(layout.sections, rva, extent)
    ranges = [(layout.directories_offset + 6 * 8, 8), (offset, extent)]
    virtual_spans = [(rva, extent)]
    for record in records:
        length, address, position = record[5:8]
        owner, mapped = _mapped(layout.sections, address, length)
        if (owner != section or mapped != position or section.name != b".rdata" or
                section.flags != 0x40000040 or any(section.auxiliary)):
            raise PEError("apphost debug data must be mapped read-only nonexecuting .rdata")
        if not 0 < length <= MAX_DEBUG_SECTION:
            raise PEError("apphost debug payload exceeds the byte bound")
        virtual_spans.append((address, length))
        ranges.append((position, length))
    if sum(length for _, length in ranges) > MAX_DEBUG_SECTION:
        raise PEError("apphost total debug data exceeds the byte bound")
    for index, (address, length) in enumerate(virtual_spans):
        if address <= layout.entrypoint < address + length:
            raise PEError("apphost debug data overlaps the entrypoint")
        if any(_overlap(address, length, old, size) for old, size in virtual_spans[:index]):
            raise PEError("overlapping apphost debug data")
        if any(index != 6 and _overlap(address, length, start, size)
               for index, (start, size) in enumerate(layout.directories)):
            raise PEError("apphost debug data overlaps a live directory")
    cv, feature, pogo = (_read(stream, len(value), record[7], record[5]) for record in records)
    if (len(cv) < 25 or cv[:4] != b"RSDS" or cv[-1:] != b"\0" or b"\0" in cv[24:-1]):
        raise PEError("invalid apphost CodeView locator")
    if len(feature) != 20 or struct.unpack_from("<I", feature)[0] != 0:
        raise PEError("invalid apphost compiler-feature profile")
    if len(pogo) < 16 or pogo[:4] != b"GCTL":
        raise PEError("invalid apphost section-group profile")
    cursor = 4
    groups = 0
    while cursor < len(pogo):
        if cursor > len(pogo) - 9 or groups >= MAX_DEBUG_ENTRIES:
            raise PEError("apphost section-group count or span exceeds bound")
        address, length = struct.unpack_from("<II", pogo, cursor)
        end = pogo.find(b"\0", cursor + 8, min(len(pogo), cursor + 137))
        if (end <= cursor + 8 or not length or
                not all(32 <= byte < 127 for byte in pogo[cursor + 8:end]) or
                not any(owner.rva <= address and address + length <= owner.rva +
                        max(owner.virtual_size, owner.raw_size) for owner in layout.sections)):
            raise PEError("invalid apphost section-group bounds or name")
        aligned = (end + 4) & ~3
        if aligned > len(pogo) or any(pogo[end:aligned]):
            raise PEError("invalid apphost section-group padding")
        cursor, groups = aligned, groups + 1
    result = bytearray(value)
    # Directory and payloads can share .rdata with live host constants. Clear
    # only the proved spans; all padding, imports, unwind and code remain exact.
    for position, length in ranges:
        result[position:position + length] = bytes(length)
    check_release_metadata(io.BytesIO(result), len(result))
    return result, tuple(ranges)


def _identity(value):
    return value.st_dev, value.st_ino, value.st_size, value.st_mtime_ns, value.st_ctime_ns


def finalize(source, output, expected_sha256, profile="amd64-core-v1"):
    if profile not in ("amd64-core-v1", "amd64-apphost-v1"):
        raise PEError("unknown release-metadata strip profile")
    if not re.fullmatch("[0-9a-f]{64}", expected_sha256):
        raise PEError("an independent lowercase SHA-256 input pin is required")
    initial = source.lstat()
    if not stat.S_ISREG(initial.st_mode):
        raise PEError("strip input must be a regular non-link file")
    descriptor = os.open(source, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_NONBLOCK", 0))
    try:
        before = os.fstat(descriptor)
        if _identity(initial) != _identity(before) or not stat.S_ISREG(before.st_mode):
            raise PEError("strip input changed before admission")
        if not 0 < before.st_size <= MAX_STRIP_IMAGE:
            raise PEError("strip image exceeds the byte bound")
        stream = os.fdopen(descriptor, "rb")
    except BaseException:
        os.close(descriptor)
        raise
    with stream:
        value = stream.read(MAX_STRIP_IMAGE + 1)
        if len(value) != before.st_size or _identity(before) != _identity(os.fstat(stream.fileno())):
            raise PEError("strip input changed during admission")
    if hashlib.sha256(value).hexdigest() != expected_sha256:
        raise PEError("strip input SHA-256 mismatch")
    result, ranges = (stripped_image(value) if profile == "amd64-core-v1" else stripped_apphost_image(value))
    # Only this invocation's exclusive, unpublished output may be retired.
    descriptor = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0), 0o755)
    try:
        output_stream = os.fdopen(descriptor, "wb")
    except BaseException:
        os.close(descriptor)
        output.unlink()
        raise
    try:
        with output_stream:
            if output_stream.write(result) != len(result):
                raise OSError("short strip output write")
            output_stream.flush()
            os.fsync(output_stream.fileno())
    except BaseException:
        output.unlink()
        raise
    return {"profile": ("amd64-codeview-repro-v1" if profile == "amd64-core-v1" else "amd64-apphost-codeview-vcfeature-pogo-v1"), "input_sha256": expected_sha256,
            "output_sha256": hashlib.sha256(result).hexdigest(), "bytes": len(result),
            "zeroed_file_spans": ranges, "all_other_bytes_unchanged": True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--input-sha256", required=True)
    parser.add_argument("--profile", choices=("amd64-core-v1", "amd64-apphost-v1"), default="amd64-core-v1")
    args = parser.parse_args()
    try:
        receipt = finalize(args.input, args.output, args.input_sha256, args.profile)
    except (OSError, ValueError) as error:
        parser.exit(1, f"[pe-release-metadata] {error}\n")
    print(json.dumps(receipt, sort_keys=True))


if __name__ == "__main__":
    main()
