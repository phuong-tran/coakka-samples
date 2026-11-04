# Tauri Intents 3.2.1 archive sample

This guide uses the pinned 3.2.1 source archive with embedded Core 3.2.
Download the archive and checksum from the [3.2.1 release record](../../releases/2026-11-05-rust-tauri-runtime-3.2.1-source.md).
Select the archive named in the
[Samples pin](../../../../coakka-runtime/artifacts/runtime-tauri-candidate.json).
The runner verifies the exact SHA-256 before opening it.

The intent command and Tauri 2.11.5 WebView execution were checked on macOS
ARM64. The archive has five Core images. This desktop evidence covers the
macOS app, including trusted and untrusted window IPC and closing with eight
requests in flight. Linux and Windows desktop execution and installer signing
need separate qualification.

Use Python 3.11 or later, an existing absolute Cargo executable and an
existing offline Cargo cache. The command smoke used Rust 1.95.0 on macOS ARM64.
Run from the Samples root with a fresh absolute work path.

```sh
export COAKKA_TAURI_ARCHIVE="/absolute/coakka-runtime-tauri-intents-3.2.1-source.tar.gz"
export COAKKA_RUST_CARGO="/absolute/rust/bin/cargo"
export COAKKA_RUST_CARGO_HOME="/absolute/offline-cargo-cache"
export COAKKA_RUST_TARGET=macos-aarch64
export COAKKA_SAMPLE_WORK_ROOT="/absolute/existing-parent/tauri-sample"
bash run.sh coakka-runtime tauri intent-command
```

The app uses the verified Core image installed alongside the executable.
It reports its effective settings and a typed request outcome.
