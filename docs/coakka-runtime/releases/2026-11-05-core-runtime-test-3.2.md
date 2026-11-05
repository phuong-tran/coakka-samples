# CoAkka Runtime Core 3.2 SDK and Runtime Test

Released 2026-11-05. The standalone Community SDK exposes the CoAkka Runtime
C interface, ABI 1. Choose the SDK to build a C or C++ application. Choose
a Runtime Test archive to try the same Core on your machine without a compiler.

**Publisher signing is absent for these archives.** Compare each download
with the SHA-256 value published below before extracting it.

| Download | SHA-256 |
| --- | --- |
| [Core 3.2 SDK for all five targets](https://raw.githubusercontent.com/phuong-tran/coakka-publish/main/runtime/native/releases/3.2%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/coakka-runtime-native-v2-3.2.tar.gz) | [SDK checksums](https://github.com/phuong-tran/coakka-publish/blob/main/runtime/native/releases/3.2%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/SHA256SUMS) |
| [Runtime Test for Linux ARM64 (including Raspberry Pi 5)](https://raw.githubusercontent.com/phuong-tran/coakka-publish/main/runtime/evidence/native/releases/3.2%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/coakka-runtime-native-evidence-v2-3.2-linux-aarch64.tar.gz) | [Runner checksums](https://github.com/phuong-tran/coakka-publish/blob/main/runtime/evidence/native/releases/3.2%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/SHA256SUMS) |
| [Runtime Test for Linux x86-64](https://raw.githubusercontent.com/phuong-tran/coakka-publish/main/runtime/evidence/native/releases/3.2%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/coakka-runtime-native-evidence-v2-3.2-linux-x86_64.tar.gz) | [Runner checksums](https://github.com/phuong-tran/coakka-publish/blob/main/runtime/evidence/native/releases/3.2%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/SHA256SUMS) |
| [Runtime Test for macOS ARM64](https://raw.githubusercontent.com/phuong-tran/coakka-publish/main/runtime/evidence/native/releases/3.2%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/coakka-runtime-native-evidence-v2-3.2-macos-aarch64.tar.gz) | [Runner checksums](https://github.com/phuong-tran/coakka-publish/blob/main/runtime/evidence/native/releases/3.2%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/SHA256SUMS) |
| [Runtime Test for Windows x86-64](https://raw.githubusercontent.com/phuong-tran/coakka-publish/main/runtime/evidence/native/releases/3.2%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/coakka-runtime-native-evidence-v2-3.2-windows-x86_64.zip) | [Runner checksums](https://github.com/phuong-tran/coakka-publish/blob/main/runtime/evidence/native/releases/3.2%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/SHA256SUMS) |
| [Runtime Test for Windows ARM64](https://raw.githubusercontent.com/phuong-tran/coakka-publish/main/runtime/evidence/native/releases/3.2%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/coakka-runtime-native-evidence-v2-3.2-windows-aarch64.zip) | [Runner checksums](https://github.com/phuong-tran/coakka-publish/blob/main/runtime/evidence/native/releases/3.2%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/SHA256SUMS) |

On Linux, run `sha256sum <archive>`. On macOS, run `shasum -a 256 <archive>`.
On Windows PowerShell, run `Get-FileHash <archive> -Algorithm SHA256`.
Match the output against the row for that filename in the checksum file.

- [Build against the SDK](https://github.com/phuong-tran/coakka-publish/blob/main/runtime/native/releases/3.2%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/CONSUMING.md)
- [Run the downloaded smoke test](https://github.com/phuong-tran/coakka-publish/blob/main/runtime/evidence/native/releases/3.2%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/README.md#run-the-smoke-test)
- [Inspect and build the public C tests](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-runtime/coakka-runtime-test)

The SDK and each runner carry the same Core library for the corresponding
target. Core 3.2 uses build `f9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45`.
The release was exercised on macOS ARM64, Raspberry Pi 5, WSL2 x86-64,
Windows x86-64, and Windows ARM64 in UTM. See the
[runner coverage](https://github.com/phuong-tran/coakka-publish/blob/main/runtime/evidence/native/releases/3.2%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/SYSTEMS-AUDIT.md) for the
scope of the packaged checks.
