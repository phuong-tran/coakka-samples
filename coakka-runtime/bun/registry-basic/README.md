# Bun Runtime 3.2.1: First Run

Send one request to an application handler and print its reply.

**You need:** Bun 1.3.14 or later (Windows ARM64: 1.4.0 or later) and a
[Samples checkout](../../../docs/coakka-runtime/getting-started.md#get-the-examples).
This app uses the published Bun connector pinned in package.json.

## Run

From the Samples repository root:

~~~sh
cd coakka-runtime/bun/registry-basic
bun install
bun main.mjs
~~~

Expected output:

~~~text
accepted-hello
~~~

The app exits after closing Runtime. No Node.js/npm installation or separate
Core installation is needed for this example.

## Read the code

Open [main.mjs](main.mjs). It starts Runtime, registers one local handler,
sends a request and awaits close in a finally block. The handler is ordinary
application JavaScript running in Bun.

This is a one-process example. [Choose a use case](../../../docs/coakka-runtime/guides/examples-and-use-cases.md)
or [move the same steps into an application](../../../docs/coakka-runtime/guides/runtime-integration-guide.md).

[Runtime examples](../../README.md) ·
[Local-package evaluation](../../../docs/coakka-runtime/previews/README.md).
