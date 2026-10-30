# Python Runtime 3.0.0 on PyPI

Released on 2026-10-30: [coakka-v2-connector 3.0.0](https://pypi.org/project/coakka-v2-connector/3.0.0/).
This release covers the Python connector and bundled Core. C# 3.0.0 remains
published independently on NuGet; other Runtime language registries, HTTP
Runtime and Logger keep their own release status.

## Install and first run

Use GIL-enabled CPython 3.11 or newer, preferably in a virtual environment:

```sh
python -m pip install coakka-v2-connector==3.0.0
python coakka-runtime/python/registry-basic/main.py
```

Run the second command from the [Samples checkout](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/python/registry-basic).
Expected output is `accepted-hello`. The installer selects a wheel for the
machine; no separate Core installation or native override is needed.

## Compatibility

3.0.0 is a new API baseline: migrate 2.x applications before upgrading.
Each wheel contains Core `3.0.0+1522b4a9f30da1039ef8c7d198bbecfc749a2456` / ABI 1, its matching Python
adapter and 23 shrunk Python modules. There are zero runtime Python package
dependencies. The five 64-bit targets are macOS ARM64, Linux ARM64/x64 and
Windows ARM64/x64. Linux ARM64 wheels require GLIBC 2.35+, Linux x64 GLIBC 2.36+;
macOS wheels target 13+. PyPy and free-threaded Python are not qualified.

The normal CPU, connection and monitor defaults remain Core-owned.
Application handlers remain application-owned. No executable/runtime change
was made during publication.

## Verified public files

All five official PyPI filenames, sizes, SHA256 and downloaded bytes equal
the qualified wheel family. The complete original archives remain unchanged.

| Wheel | Bytes | SHA256 |
| --- | --- | --- |
| `coakka_v2_connector-3.0.0-cp311-abi3-macosx_13_0_arm64.whl` | 507301 | `45a4dfc5ed16b74fd98e05c37d2c86242fbb15f66cd6aeedd06035010943626a` |
| `coakka_v2_connector-3.0.0-cp311-abi3-manylinux_2_35_aarch64.whl` | 1029699 | `0c2e3182fe3bb4ffbb2175709b01b7d5d40f0b3db1cd3d98751942f842f88718` |
| `coakka_v2_connector-3.0.0-cp311-abi3-manylinux_2_36_x86_64.whl` | 942309 | `7e24407ff325c2f48bedcfa0a187e463f915f48c52236ebc51c0e1ab287aa842` |
| `coakka_v2_connector-3.0.0-cp311-abi3-win_amd64.whl` | 1054833 | `a61c3733bed7551340ea3cbc757bbd6ace234772f6faa304fdaf03c8fe2da00a` |
| `coakka_v2_connector-3.0.0-cp311-abi3-win_arm64.whl` | 969293 | `6d69606ec48835b1953602700e3b9250c146fa86add37fc99a9d9b0329255bda` |

A fresh CPython 3.11.15/macOS ARM64 virtual environment installed only from
PyPI with no cache, local feed, private SDK or native override. The pip report
identifies the expected official file/hash; all original installed members
match except the installer-maintained RECORD. The ordinary example prints
`accepted-hello`, and the public Runtime/File/Stream consumer passes.
Existing five-target qualification is retained by exact executable identity;
this is not a new post-publication execution claim on every OS/interpreter.

[Current coordinates](../../common/current-packages.md) ·
[Package/platform evidence](../reference/runtime-package-platform-evidence.md) ·
[Defaults and monitoring](../operations/runtime-monitoring.md).
