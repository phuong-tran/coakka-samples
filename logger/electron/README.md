# Electron Logger Samples

Electron samples consume `coakka-logger-electron@1.2.3` from npm.

- [Story](#story)
- [Before And After](#before-and-after)
- [Run](#run)
- [Samples](#samples)
- [Boundary](#boundary)

## Story

Renderer JavaScript should not own logger lifecycle or native loading. In this
lane the renderer sends a log intent through preload/IPC. The Electron main
process owns the logger bridge, bounded queue behavior, drain path, and
counters.

## Before And After

Before:

```text
renderer JS -> console/fetch/log endpoint -> unclear delivery state
```

After:

```text
renderer log intent -> preload/IPC -> main-process logger bridge -> projected counters
```

The renderer still has a small API, but delivery behavior belongs to the main
process.

## Run

```sh
bash run.sh logger electron basic
```

## Samples

Current samples:

- `basic`: install the published package into a temporary Electron app, send
  one renderer log intent through preload/IPC, drain it in the main process,
  and print counters

## Boundary

- Renderer JavaScript sends log intent only.
- Preload exposes `coakkaLogger.log(...)`.
- The Electron main process owns `ElectronLoggerIntentBridge`.
- The main process returns accepted/sequence/record/stats projections.
