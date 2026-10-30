# Electron Runtime 3.0.0: First Run

Use Electron 42 and `coakka-v2-connector-electron@3.0.0`.
See the [current package ledger](../../../docs/common/current-packages.md)
for registry availability. Install the published package, or evaluate the exact
local archive below before publication; a2.x package cannot run this API.

From the Samples checkout:

```sh
cd coakka-runtime/electron/registry-basic
npm install
npm start
```

The app displays a reply in a real isolated, sandboxed renderer and then exits.
Its main process owns Runtime; the renderer sends a value-only intent through
preload. The files reuse the existing reviewed desktop-intent sample, including
bounded app IPC admission and orderly shutdown. Electron and esbuild are app
development tools; they are not bundled inside the connector.

CPU, connection and monitor defaults stay owned by Core.

For local archive evaluation, copy this app to a fresh external directory and
install the admitted tarball there with npm. Install both the exact Electron and Node connector tarballs together; supply the Electron runtime separately for offline testing.
Local installation is not proof of registry availability.

[Runtime examples](../../README.md) ·
[Integration guide](../../../docs/coakka-runtime/guides/runtime-integration-guide.md).
