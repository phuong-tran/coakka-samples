# Python Runtime 3.2.1 on PyPI

Released on 2026-11-05. Version `3.2.1` is the first Python connector
release on Core generation `3.2`, build `f9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45`, ABI 1.
Each wheel includes the matching Core and Python adapter.

## Install

Use GIL-enabled CPython 3.11 or later.

```sh
python -m pip install --only-binary=:all: coakka-v2-connector==3.2.1
```

The wheels cover macOS 13 or later on ARM64, Linux ARM64 with glibc
2.35 or later, Linux x64 with glibc 2.36 or later, and Windows ARM64
and x64. See the [Python first run](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/python/registry-basic)
and [2.x to 3.x migration guidance](../CHANGELOG.md#breaking-changes-and-migration).

## Registry verification

| Wheel platform tag | Bytes | SHA-256 |
| --- | ---: | --- |
| `macosx_13_0_arm64` | 722326 | `210178a92fcec3e2e0b16f80b04393a006ba408d65755a252aff527314a26d33` |
| `manylinux_2_35_aarch64` | 1028107 | `d5ebda4527ad5f87164f7ff9467c6b22ec2061176cd23e5a4cc1fcbfa9aac7c4` |
| `manylinux_2_36_x86_64` | 940664 | `3d28c0b5b8c9f474f8f1b156e18eae189179e76091242bef52b899910ca9ec6f` |
| `win_amd64` | 1050435 | `ba950155f7e20c026524a4d2c4114c5588a009eaf14ebb8b12168f038c406775` |
| `win_arm64` | 965265 | `f24f6261e6faad5945b29e94aaa55c0ceef298c19ff7af9ea38eed50fd99e9ce` |

All five public wheel downloads matched the qualified wheel bytes. Each
wheel had passed an installed test on its matching host. A fresh PyPI-only
install on macOS ARM64 with CPython 3.11 verified the loaded Core and
passed three request/reply and shutdown cycles, File and Stream lane
open and close, and the `accepted-hello` first run.

[Python Runtime 3.2.1 on PyPI](https://pypi.org/project/coakka-v2-connector/3.2.1/) ·
[Current package versions](../../common/current-packages.md)
