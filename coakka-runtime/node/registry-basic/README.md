# Node.js Runtime 3.2.1: First Run

Send one request to an application handler and print its reply.

**You need:** Node.js 22 or later, npm and a [Samples checkout](../../../docs/coakka-runtime/getting-started.md#get-the-examples).
This app uses the published npm package pinned in package.json.

## Run

From the Samples repository root:

~~~sh
cd coakka-runtime/node/registry-basic
npm install
node main.mjs
~~~

Expected output:

~~~text
accepted-hello
~~~

The app exits after closing Runtime. No separate service or Core installation
is needed for this local example.

## Read the code

Open [main.mjs](main.mjs). It starts Runtime, names one local target, registers
a handler that adds accepted- to the message, asks that target and awaits close
in a finally block. Your application owns the handler.

This is a one-process example. Next, try the
[customer operation](../../../docs/coakka-runtime/guides/first-npm-smoke.md) or
[choose another use case](../../../docs/coakka-runtime/guides/examples-and-use-cases.md).

[Add Runtime to an application](../../../docs/coakka-runtime/guides/runtime-integration-guide.md) ·
[Local-package evaluation](../../../docs/coakka-runtime/previews/README.md).
