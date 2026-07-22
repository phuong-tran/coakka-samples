# Tauri Logger Samples

Tauri samples consume the published `coakka-logger-tauri-intents` source
package from `coakka-publish`.

- [Story](#story)
- [Before And After](#before-and-after)
- [Run](#run)
- [Samples](#samples)
- [Boundary](#boundary)

## Story

The logger follows the same Tauri boundary as runtime intents. WebView
JavaScript emits a log intent. Rust validates it, owns the logger handle,
applies bounded queue behavior, and returns counters or a drained record.

This is not a console helper for renderer code. It is a Rust-owned logging
boundary that WebView code can request.

## Before And After

Before:

```text
WebView JS -> console/fetch/log endpoint -> unclear queue and drop behavior
```

After:

```text
WebView log intent -> Rust command -> bounded logger -> projected counters
```

The WebView does not own native logger lifecycle or pressure policy.

## Run

```sh
bash run.sh logger tauri basic
```

## Samples

Current samples:

- `basic`: extract the source package, run its command-level smoke, and prove
  one log intent through the Rust-side Tauri-shaped command boundary

## Boundary

- JavaScript sends a log intent through Tauri `invoke`.
- Rust validates category, level, and message.
- Rust owns the logger bridge and serializes access to the logger handle.
- Rust returns accepted/sequence/record/stats projections.
