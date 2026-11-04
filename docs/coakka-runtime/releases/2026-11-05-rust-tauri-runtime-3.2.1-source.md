# Rust and Tauri Intents Runtime 3.2.1 source packages

Published on 2026-11-05 through the `coakka-publish` source archive warehouse.
Both packages embed Runtime Core 3.2, ABI 1, build
`f9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45`.

| Package | Bytes | SHA-256 | Verification files |
| --- | ---: | --- | --- |
| [Rust 3.2.1](https://raw.githubusercontent.com/phuong-tran/coakka-publish/89e183de62618085a1223c69d8f5ecadf6e7cf7b/coakka-runtime/rust/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/coakka-runtime-rs-3.2.1.tar.gz) | 4,317,688 | `1e213fc11d5ff34bb7893a589f43766f5d24dadb07d4601d9733aaa86affaad2` | [Checksum](https://raw.githubusercontent.com/phuong-tran/coakka-publish/89e183de62618085a1223c69d8f5ecadf6e7cf7b/coakka-runtime/rust/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/SHA256SUMS) · [Manifest](https://raw.githubusercontent.com/phuong-tran/coakka-publish/89e183de62618085a1223c69d8f5ecadf6e7cf7b/coakka-runtime/rust/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/manifest.json) |
| [Tauri Intents 3.2.1](https://raw.githubusercontent.com/phuong-tran/coakka-publish/89e183de62618085a1223c69d8f5ecadf6e7cf7b/coakka-runtime/tauri/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/coakka-runtime-tauri-intents-3.2.1-source.tar.gz) | 4,353,274 | `81ccf9868d5b89f3c274c07be3de1845cf78048f78acca9bbae2565eb55150d4` | [Checksum](https://raw.githubusercontent.com/phuong-tran/coakka-publish/89e183de62618085a1223c69d8f5ecadf6e7cf7b/coakka-runtime/tauri/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/SHA256SUMS) · [Manifest](https://raw.githubusercontent.com/phuong-tran/coakka-publish/89e183de62618085a1223c69d8f5ecadf6e7cf7b/coakka-runtime/tauri/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/manifest.json) |

Anonymous downloads matched the sealed archive bytes, SHA-256 digests and
manifest files. Each archive contains five verified native images and its
license material. The manifests record publisher signing as absent.

Check the archive against its checksum and follow the included
`CONSUMING.md` for Cargo path dependencies and native resource placement.
The [Rust basic example](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/rust/basic/README.md)
and [Tauri intent example](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/tauri/intent-command/README.md)
pin these exact archives.

## Execution evidence

- Rust application execution covers macOS ARM64, Raspberry Pi 5 Linux ARM64,
  WSL2 Linux x86-64, native Windows x64 and Windows ARM64 in UTM. The
  Windows ARM64 run used a cross-built application. The sealed archive
  passed installed runtime smoke, transport smoke and 31 tests on macOS ARM64.
- Tauri Intents command and Tauri 2.11.5 WebView evidence covers macOS ARM64.
  The sealed archive passed the installed intent command and 18 tests.
  The earlier WebView run used identical executable inputs and exercised
  trusted and untrusted window IPC and closing with eight accepted requests
  in flight. Linux and Windows desktop execution and installer signing need
  separate qualification.
- The macOS archive smokes used Rust 1.95.0. TLS was outside their scope.
