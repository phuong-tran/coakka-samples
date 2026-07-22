# Tauri Intent Command

This source-adjacent sample runs the command-level path that a Tauri app would
use:

```text
WebView JS intent -> Rust command bridge -> RuntimeIntentDispatcher -> Rust handler
```

It does not start a desktop WebView yet. The goal is to prove the boundary
before adding Tauri app scaffolding: the caller submits an intent, Rust handles
it through the native runtime, and the result is projected back as JSON.

Run from this directory:

```sh
bash run.sh
```

Or from the repository root:

```sh
bash run.sh runtime tauri intent-command
```
