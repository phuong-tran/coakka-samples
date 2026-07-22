# Electron Desktop Intent Runtime Sample

This sample installs the published Electron connector package into a
disposable Electron app, opens one hidden renderer window, sends an intent
through preload and IPC, and verifies that the Electron main process handles
the work through the CoAkka runtime host.

Run it from the repository root:

```sh
bash run.sh runtime electron desktop-intent
```

Expected output shape:

```text
coakka_electron_runtime_response payload={"handledBy":"electron-main","echo":{"message":"hello-electron-runtime"}} delivered=1 matchedResponses=1
```
