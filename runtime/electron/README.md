# Electron Runtime Samples

Electron runtime samples document the `coakka-v2-connector-electron` package
shape. The renderer sends one intent through preload and IPC; the Electron main
process owns the CoAkka runtime host and returns the projected result.

The desktop sample requires Node.js 22 and Electron 42 or newer. Set
`COAKKA_ELECTRON_VERSION` to exercise another supported Electron release.

- [Story](#story)
- [Before And After](#before-and-after)
- [Run](#run)
- [Renderer](#renderer)
- [Main Process](#main-process)
- [Boundary](#boundary)

## Story

Electron apps often let renderer code call application endpoints directly. That
works for a browser edge, but it is a weak desktop boundary. In this sample the
renderer only emits intent. The main process owns runtime lifecycle, handler
registration, timeout behavior, and result projection.

Watch the Electron runtime walkthrough:

![CoAkka Runtime Electron walkthrough](../../docs/assets/coakka-runtime-electron.gif)

Full recording: [coakka-runtime-electron.mp4](../../docs/assets/coakka-runtime-electron.mp4)

## Before And After

Before:

```text
renderer JS -> fetch/fake local REST -> main/backend code
```

After:

```text
renderer intent -> preload/IPC -> Electron main process -> CoAkka runtime target -> projected result
```

The renderer remains easy to write, but it no longer owns the runtime path.

## Run

```sh
bash run.sh runtime electron basic
```

Renderer code does not import the runtime connector and does not own runtime
lifecycle. It only calls the preload API exposed by the app:

## Renderer

```js
const result = await window.coakka.intent({
  intentId: "intent-1",
  source: "electron-renderer",
  target: "samples.electron.intent.echo",
  operation: "echo",
  payload: { message: "hello-electron-runtime" },
  payloadIdentity: {
    messageType: "samples.electron.intent.echo.request.v1",
    payloadSchemaVersion: 1,
    payloadFormat: "json",
  },
});
```

The main process registers the IPC handler and the runtime target:

## Main Process

```js
const bridge = ElectronRuntimeIntentBridge.start({
  systemName: "electron-runtime-sample",
  nodeId: "electron-runtime-sample-main",
  defaultTarget: "samples.electron.intent.echo",
});

bridge.registerJsonIntentHandler("samples.electron.intent.echo", async (intent) => ({
  handledBy: "electron-main",
  echo: intent.payload,
}));

registerElectronIntentIpcHandler(ipcMain, bridge);
```

Keep HTTP at a real browser/API or legacy edge. This sample is a desktop app
boundary: renderer intent in, main-process runtime execution out.

## Boundary

- Renderer JavaScript sends intent only.
- Preload exposes the smallest IPC surface.
- The Electron main process starts and closes the runtime host.
- The main process registers the handler and projects the result.
- Runtime envelopes stay out of renderer code.

## Continue Integrating

Use this lane's runnable sample as the source for package imports and the
renderer-to-main boundary. Before generating connection strategy, TLS/mTLS,
File Lane, or Stream Lane code, follow
[AI-Assisted Integration](../../docs/ai-assisted-integration.md).

Connection policy and native lanes belong to Electron main, never the renderer.
The current `2.5.3` package train includes typed replica-owner File and Stream
Lane grants; keep both lane lifecycles in the main process.
