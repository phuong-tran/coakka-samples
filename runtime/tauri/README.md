# Tauri Runtime Samples

These samples cover the published Tauri intent source connector lane.

- [Story](#story)
- [Before And After](#before-and-after)
- [Run](#run)
- [Samples](#samples)
- [Boundary](#boundary)

## Story

Tauri stands between WebView JavaScript and a Rust application host. The CoAkka
shape keeps that split explicit: JavaScript sends an intent, Rust validates and
executes it, and JavaScript receives a projected result.

The important rule is that WebView code does not own runtime transport details.
It should not construct runtime envelopes, hold native handles, or decide how a
capability is executed. Rust is the execution boundary.

Watch the Tauri intent walkthrough:

![CoAkka Runtime Tauri walkthrough](../../docs/assets/coakka-runtime-tauri.gif)

Full recording: [coakka-runtime-tauri.mp4](../../docs/assets/coakka-runtime-tauri.mp4)

## Before And After

Before:

```text
WebView JS -> fetch/app REST endpoint -> Rust or backend code
```

After:

```text
WebView JS intent -> Rust command -> CoAkka runtime target -> Rust handler -> projected result
```

The WebView still has a simple call site, but the app no longer needs a fake
HTTP edge just to move work across the desktop boundary.

## Run

```sh
bash run.sh runtime tauri intent-command
bash run.sh runtime tauri desktop-intent
```

## Samples

- `intent-command`: run the Rust command-level path a Tauri app would call.
- `desktop-intent`: compile and test a real Tauri v2 desktop app scaffold whose
  WebView calls one Rust intent command.

## Boundary

The frontend boundary is intentionally narrow:

- JavaScript sends an intent through Tauri `invoke`.
- Rust validates the intent.
- Rust owns `RuntimeIntentDispatcher`.
- Rust decides whether the work is local, runtime-routed, or later delegated to
  another executor.
- JavaScript receives only the projected result.
