# Tauri Logger Basic

This sample extracts the published `coakka-logger-tauri-intents` source package
and runs the bundled command-level smoke. It does not launch a WebView; it
proves the Rust command boundary that a Tauri app wraps with `#[tauri::command]`.

Run from this directory:

```sh
bash run.sh
```

Or from the repository root:

```sh
bash run.sh logger tauri basic
```

Expected output shape:

```text
coakka_tauri_logger_package_smoke ok accepted=true sequence=1
```
