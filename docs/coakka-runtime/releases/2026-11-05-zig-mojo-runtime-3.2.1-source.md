# Zig and Mojo Runtime 3.2.1 source packages

Published on 2026-11-05 through the `coakka-publish` source archive warehouse.
Both packages embed Core generation 3.2, ABI 1, build
`f9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45`. The final digit in `3.2.1` is the connector revision.

| Package | Bytes | SHA-256 | Verification files |
| --- | ---: | --- | --- |
| [Zig 3.2.1](https://raw.githubusercontent.com/phuong-tran/coakka-publish/c426326567f57dc01ca0449c347779802833b66b/coakka-runtime/zig/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/coakka-runtime-zig-3.2.1-source.tar.gz) | 4,264,372 | `7190852f0988df959260cfa4ed7bedb9cf0a5cb3bc4cab3ae82a07059b0948ae` | [Checksum](https://raw.githubusercontent.com/phuong-tran/coakka-publish/c426326567f57dc01ca0449c347779802833b66b/coakka-runtime/zig/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/SHA256SUMS) · [Manifest](https://raw.githubusercontent.com/phuong-tran/coakka-publish/c426326567f57dc01ca0449c347779802833b66b/coakka-runtime/zig/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/manifest.json) |
| [Mojo 3.2.1 preview](https://raw.githubusercontent.com/phuong-tran/coakka-publish/c426326567f57dc01ca0449c347779802833b66b/coakka-runtime/mojo/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/coakka-runtime-mojo-3.2.1-source.tar.gz) | 4,249,191 | `6d7ecea56ecf0df60cfe87368627ed55a9428ef250beebd81b8f9bd7c8b61134` | [Checksum](https://raw.githubusercontent.com/phuong-tran/coakka-publish/c426326567f57dc01ca0449c347779802833b66b/coakka-runtime/mojo/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/SHA256SUMS) · [Manifest](https://raw.githubusercontent.com/phuong-tran/coakka-publish/c426326567f57dc01ca0449c347779802833b66b/coakka-runtime/mojo/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/manifest.json) |

Both archives, checksum files and manifests were downloaded anonymously.
The archive bytes and SHA-256 digests matched the qualified inputs.
The manifests record the five bundled Core files and absent publisher signing.

Use the checksum alongside the downloaded archive and follow the included
`CONSUMING.md`. Samples pins the same bytes in the
[Zig](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/zig/basic/README.md)
and [Mojo preview](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/mojo/basic/README.md)
examples.

## Execution evidence

- Zig 0.16.0 ran on macOS ARM64, Raspberry Pi 5 Linux ARM64, WSL2 Linux x86-64,
  native Windows x86-64 and Windows ARM64 in UTM. The Windows applications
  were built on macOS and executed on the matching Windows targets.
- Mojo 1.0.0b2.dev2026052115 is a **preview** on macOS ARM64 and Raspberry Pi 5
  Linux ARM64. Its executable uses the packaged C boundary. The preview
  scope covers those two ARM64 hosts.
- The final archives passed request/reply, cancellation and route-miss
  deadletter on macOS ARM64. The Mojo archive also passed File and Stream
  checks there. Other host results use identical connector and native bytes.
  TLS was unexercised in the 3.2.1 smoke runs.
