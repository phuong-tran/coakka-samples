# Electron Basic Runtime Sample

This sample runs one renderer intent through the Electron preload and IPC
boundary. The Electron main process owns the CoAkka runtime host and returns a
projected result to the renderer.

Run it from the repository root:

```sh
bash run.sh runtime electron basic
```

Expected output shape:

```text
coakka_electron_runtime_response payload={"handledBy":"electron-main","echo":{"message":"hello-electron-runtime"}} delivered=1 matchedResponses=1
```
