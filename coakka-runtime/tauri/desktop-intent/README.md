# Tauri Desktop Intent

Download the archive and SHA-256 from the [3.2.1 release record](../../../docs/coakka-runtime/releases/2026-11-05-rust-tauri-runtime-3.2.1-source.md).

Foreground desktop sample for the Tauri Intents 3.2.1 aggregate with Core 3.2.
The WebView evidence for this version covers macOS ARM64 with Tauri 2.11.5.
The five bundled Core images describe the aggregate payload.

The local WebView sends an intent. Rust projects it through the intent adapter
to one local Core route and returns the typed result. The UI displays a cold
Core-issued configuration snapshot, not guessed defaults or live health.
It does not choose an engine, probe the OS, or own native handles.

## Build and launch

Use stable Rust 1.92 or newer with Clippy, Python 3.11+ at build time, prepared
native GUI/linker tools, and existing offline Cargo/build caches. The framework
graph is pinned independently of the Rust SDK's 1.77.2 minimum. Consult the
[candidate guide](../../../docs/coakka-runtime/previews/3.2.1/runtime-tauri-candidate.md) for archive identity
and the trust/cleanup contract. No registry or native rebuild is performed.

```sh
export COAKKA_TAURI_ARCHIVE=/absolute/candidate/coakka-runtime-tauri-intents-3.2.1-source.tar.gz
export COAKKA_SAMPLE_WORK_ROOT=/absolute/build-storage/desktop-new
export COAKKA_RUST_CARGO=/absolute/rust/bin/cargo
export COAKKA_RUST_CARGO_HOME=/absolute/offline-cargo-cache
export COAKKA_RUST_TARGET_CACHE=/absolute/desktop-build-cache
export COAKKA_RUST_TARGET=macos-aarch64
bash run.sh coakka-runtime tauri desktop-intent
```

Linux and Windows desktop execution, minimum OS validation and installer
signing require separate qualification for this aggregate.

The producer builds release code, runs strict Clippy and library tests, and
installs only the app, native resources and legal material. Move the complete
`installed/` tree. Mac has `CoAkka Tauri Intent.app`, declaring macOS 13.0 or
later to match its bundled runtime image. The image is under
`Contents/Frameworks/`; original component metadata and all legal texts are
under `Contents/Resources/`. Its local signature is for running the sample. The archive records publisher
signing as absent. No Python/Cargo/build tree or
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
