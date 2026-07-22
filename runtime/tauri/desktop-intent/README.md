# Tauri Desktop Intent

This sample is a real Tauri v2 desktop app scaffold for the CoAkka intent
boundary.

The WebView UI sends one intent through Tauri `invoke`. Rust owns the
`RuntimeIntentDispatcher`, converts the intent to a runtime ask, handles the
target locally, and returns an intent result projection to the WebView.

Run the non-UI smoke from this directory:

```sh
bash run.sh
```

Or from the repository root:

```sh
bash run.sh runtime tauri desktop-intent
```

To launch the desktop app manually after installing the Tauri CLI:

```sh
cd runtime/tauri/desktop-intent/src-tauri
cargo tauri dev --no-dev-server
```
