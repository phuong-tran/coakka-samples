# Tauri Intent Command

This is a separate Cargo app, not a packaged smoke and not a desktop WebView.
It consumes the exact private 2.5.3 aggregate and Core 2.5.1. See the
[candidate guide](../../docs/runtime-tauri-candidate.md) for artifact/tool/
offline-cache/fresh external work selections, then run from the repo root:

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

A GUI must run blocking work off UI and join accepted work and browser lifetime
under explicit application permissions. This CLI intentionally does not claim
that desktop gate. The candidate remains private and is not a published release.
