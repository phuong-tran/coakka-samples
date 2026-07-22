# Tauri Runtime Samples

These samples cover the source-adjacent Tauri intent connector lane.

The frontend boundary is intentionally narrow: JavaScript sends an intent to a
Rust command, Rust executes the intent, and JavaScript receives a projected
result. The samples do not expose CoAkka runtime envelopes to WebView code.

## Samples

- `intent-command`: run the Rust command-level path a Tauri app would call.
- `desktop-intent`: compile and test a real Tauri v2 desktop app scaffold whose
  WebView calls one Rust intent command.
