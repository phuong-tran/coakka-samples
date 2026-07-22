# Electron Runtime Samples

Electron runtime samples document the `coakka-v2-connector-electron` package
shape. The renderer sends one intent through preload and IPC; the Electron main
process owns the CoAkka runtime host and returns the projected result.

Watch the Electron runtime walkthrough:

![CoAkka Runtime Electron walkthrough](../../docs/assets/coakka-runtime-electron.gif)

Full recording: [coakka-runtime-electron.mp4](../../docs/assets/coakka-runtime-electron.mp4)

## Run

```sh
bash run.sh runtime electron basic
```

Renderer code does not import the runtime connector and does not own runtime
lifecycle. It only calls the preload API exposed by the app:

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
