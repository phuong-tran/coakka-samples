# Rust 3.2.1 archive sample

This guide uses the pinned 3.2.1 source archive with embedded Core 3.2.
Download the archive and checksum from the [3.2.1 release record](../../releases/2026-11-05-rust-tauri-runtime-3.2.1-source.md).
Select the archive named in the
[Samples pin](../../../../coakka-runtime/artifacts/runtime-rust-candidate.json).
The runner verifies the exact SHA-256 before opening it.

Rust application execution was checked on macOS ARM64, Pi 5 Linux ARM64,
WSL2 Linux x86-64, native Windows x64 and Windows ARM64 in UTM. The basic
sample runner covers macOS ARM64, Linux ARM64 and Windows x64.

Use Python 3.11 or later, an existing absolute Cargo executable and an
existing offline Cargo cache. The command smoke used Rust 1.95.0 on macOS ARM64.
Run from the Samples root with a fresh absolute work path.

```sh
export COAKKA_RUST_ARCHIVE="/absolute/coakka-runtime-rs-3.2.1.tar.gz"
export COAKKA_RUST_CARGO="/absolute/rust/bin/cargo"
export COAKKA_RUST_CARGO_HOME="/absolute/offline-cargo-cache"
export COAKKA_RUST_TARGET=macos-aarch64
export COAKKA_SAMPLE_WORK_ROOT="/absolute/existing-parent/rust-sample"
bash run.sh coakka-runtime rust basic
```

The app uses the verified Core image installed alongside the executable.
It reports its effective settings and a typed request outcome.
