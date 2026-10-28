# Tauri Desktop Intent

Private foreground desktop sample for the exact Tauri aggregate 2.5.3 over
Core 2.5.1. This is not a published, signed or production-support claim. The
old download/Tauri-CLI/debug-build recipe is not used.

The local WebView sends an intent. Rust projects it through the intent adapter
to one local Core route and returns the typed result. The UI displays a cold
Core-issued configuration snapshot, not guessed defaults or live health.
It does not choose an engine, probe the OS, or own native handles.

## Build and launch

Use stable Rust 1.92 or newer with Clippy, Python 3.11+ at build time, prepared
native GUI/linker tools, and existing offline Cargo/build caches. The framework
graph is pinned independently of the Rust SDK's 1.77.2 minimum. Consult the
[candidate guide](../../docs/runtime-tauri-candidate.md) for archive identity
and the trust/cleanup contract. No registry or native rebuild is performed.

```sh
export COAKKA_TAURI_ARCHIVE=/absolute/candidate/coakka-runtime-tauri-intents-2.5.3-source.tar.gz
export COAKKA_SAMPLE_WORK_ROOT=/absolute/build-storage/desktop-new
export COAKKA_RUST_CARGO=/absolute/rust/bin/cargo
export COAKKA_RUST_CARGO_HOME=/absolute/offline-cargo-cache
export COAKKA_RUST_TARGET_CACHE=/absolute/desktop-build-cache
export COAKKA_RUST_TARGET=macos-aarch64
bash run.sh coakka-runtime tauri desktop-intent
```

Choose `linux-aarch64` for Pi. Windows x64 uses native Python and a prepared
x64 compiler environment; it does not execute the app under WSL:

For a portable Linux artifact, build in the prepared ARM64 Debian 12/GLIBC 2.36
environment, inspect the final libc version requirements and test it on the
target. A successful local build on a newer Linux host is not ABI-baseline
evidence. The prebuilt Core resource is unchanged, not rebuilt by this app.

```powershell
python -B coakka-runtime/scripts/runtime-tauri-desktop-candidate.py --archive D:/candidate/coakka-runtime-tauri-intents-2.5.3-source.tar.gz --source D:/samples/coakka-runtime/tauri/desktop-intent --work D:/build-storage/desktop-new --cargo D:/rust/bin/cargo.exe --cargo-home D:/offline-cargo-cache --target-cache D:/desktop-build-cache --target windows-x86_64
& D:/build-storage/desktop-new/installed/coakka-sample-tauri-desktop-intent.exe
```

The producer builds release code, runs strict Clippy and library tests, and
installs only the app, native resources and legal material. Move the complete
`installed/` tree. Mac has `CoAkka Tauri Intent.app`, declaring macOS 13.0 or
later to match its bundled runtime image. The image is under
`Contents/Frameworks/`; original component metadata and all legal texts are
under `Contents/Resources/`. Its local private signature is not publisher
signing or qualification on every older OS. No Python/Cargo/build tree or
separate Core dependency installation is needed to run the app. Normal OS GUI
services remain platform prerequisites. A candidate signing warning must not
be bypassed by disabling protection.

The app normally uses its OS application cache for browser data. For controlled
storage, pass `--browser-data-dir /absolute/app-data` or set
`COAKKA_TAURI_BROWSER_DATA_DIR` when using `run.sh`. This app-only choice never
changes Core/native selection. Invalid/extra/relative arguments refuse; browser
initialization errors retire the Core owner and produce a nonzero app result.

## Boundary and lifetime

Only the local `main` window is granted the two declared application commands.
The app admits the exact local entrypoint, denies remote/other navigation and
new windows, and uses local-only CSP assets. No browser security-disable flag
or hidden/background scheduling override is added.

Asks execute off the UI thread with at most eight app-owned leases. Busy/closed
admission is an application refusal, not a Core queue/capability outcome. A
departed UI promise does not cancel accepted native work. Core alone owns
AUTO/MULTIPLEX, accepted effective settings and causal results; the sample
omits queue, CPU, connection and monitor tuning. Intent headers in this adapter
generation are reserved application metadata, not propagated context.

Closing seals admission, joins all accepted operations, drops the final
dispatcher and then completes application shutdown. The Windows browser event
subscription is a framework-specific UI resource: matching normal browser
retirement and Core-owner retirement must both be observed before normal exit.
It is not shared Core transport policy. There is no process polling or killing
inside the application/connector.

Functional and resource controls use real renderers. They do not establish
whole-browser/VM leak freedom, default background progress or native-equivalent
throughput. Keep this sample separate from the terminal intent example and
from an application-specific deployment/signing audit.
