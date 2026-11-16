# Electron Runtime 3.1.1 on npm

Released on 2026-11-04. This is Electron's first connector release on Core
generation 3.1. It depends exactly on Node connector 3.1.2, which includes
Core 3.1 r2, build `6789dd16210fc01f17cca4d2b66a592d1ac79121`, ABI 1.
Node.js and Bun remain at 3.1.2 on the same Core generation.

## Install

Use Electron 42 or later and install the connector in the app.

```sh
npm install coakka-v2-connector-electron@3.1.1
```

npm installs the exact Node dependency. Its native images cover macOS ARM64,
Linux ARM64/x64 and Windows ARM64/x64. Run the bridge in the Electron main
process. The package README shows how to authorize renderer requests and
expose the intent API from a sandboxed preload.

## Verification

The npm tarball SHA-256 is
`2310316a76a64408d4f1b7f876f0fadde08e87c5fe64919686746daf6f93ad1e`.
The public tarball matches the qualified archive, and an anonymous npm install
verified every Electron and Node member. Electron 42.0.0 on macOS ARM64 passed
sandboxed renderer request/reply and authorization, File, Stream and shutdown.
The README, logo, licensing links and offline licenses were checked in the
tarball.

[Electron on npm](https://www.npmjs.com/package/coakka-v2-connector-electron/v/3.1.1) ·
[Node.js and Bun 3.1.2](2026-11-04-npm-node-bun-runtime-3.1.2.md) ·
[Current packages](../../common/current-packages.md)
