# Tauri Intent Command

Download the archive and SHA-256 from the [3.2.1 release record](../../../docs/coakka-runtime/releases/2026-11-05-rust-tauri-runtime-3.2.1-source.md).

This Cargo app consumes the Tauri Intents 3.2.1 aggregate and Core 3.2.
It exercises the adapter from the terminal on macOS ARM64. Select the
[3.2.1 archive and toolchain](../../../docs/coakka-runtime/previews/3.2.1/runtime-tauri-candidate.md),
then run from the Samples root:

```sh
bash run.sh coakka-runtime tauri intent-command
```

One handler echoes `hello-tauri-intent`; the app checks correlation and the
typed route-miss cause for `samples.tauri.intent.missing`. Both asks keep their
two-second deadline. It prints the result and causal error JSON, then actual
Core identity/effective facts: AUTO0/MULTIPLEX3, monitorfalse, delivery2/2 and
pending0. These facts are not reconstructed from submitted preferences.

There is no incidental queue, fixed listener port or native override. RAII
retires the last dispatcher clone on return/error/unwind, without `process::exit`
or fabricated serialization fallback. Move the installed directory as a whole;
it needs no Python/Cargo/source or separate native dependency installation.

The [desktop example](../desktop-intent/README.md) shows app-owned window
permissions, bounded blocking work and joined shutdown.
