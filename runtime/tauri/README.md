# Tauri Runtime Samples

These samples cover the published Tauri intent source connector lane.

The frontend boundary is intentionally narrow: JavaScript sends an intent to a
Rust command, Rust executes the intent, and JavaScript receives a projected
result. The samples do not expose CoAkka runtime envelopes to WebView code.

Watch the Tauri intent walkthrough:

![CoAkka Runtime Tauri walkthrough](../../docs/assets/coakka-runtime-tauri.gif)

Full recording: [coakka-runtime-tauri.mp4](../../docs/assets/coakka-runtime-tauri.mp4)

## Samples

- `intent-command`: run the Rust command-level path a Tauri app would call.
- `desktop-intent`: compile and test a real Tauri v2 desktop app scaffold whose
  WebView calls one Rust intent command.
