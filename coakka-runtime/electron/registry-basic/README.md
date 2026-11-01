# Electron Runtime 3.0.0: First Run

Open a desktop window, submit application work and display its reply.

**You need:** Node.js 22 or later, npm, a desktop session and a
[Samples checkout](../../../docs/coakka-runtime/getting-started.md#get-the-examples).
The sample installs Electron 42 and the published Electron connector.

## Run

From the Samples repository root:

~~~sh
cd coakka-runtime/electron/registry-basic
npm install
npm start
~~~

A window shows the reply and the app then exits. No separate Core installation
is needed.

## Read the code

- [main.mjs](main.mjs) starts Runtime and owns the application handler.
- [preload.cjs](preload.cjs) exposes the window's supported request.
- [renderer.js](renderer.js) submits that request and displays the result.
- [lifecycle.mjs](lifecycle.mjs) coordinates closing the application.

Keep Runtime in the main process and use the window's supported request path.
The example keeps the window isolated while application work stays in the host.

[Choose a use case](../../../docs/coakka-runtime/guides/examples-and-use-cases.md) ·
[Integration guide](../../../docs/coakka-runtime/guides/runtime-integration-guide.md) ·
[Local-package evaluation](../../../docs/coakka-runtime/previews/README.md).
