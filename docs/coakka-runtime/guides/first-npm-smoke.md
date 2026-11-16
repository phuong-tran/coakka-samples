# Node.js Customer Operation

Create a customer through a named application handler using published Runtime
3.2.1. This is a one-process business example you can run without cloning
Samples. The application owns the customer data and operation.

**You need:** Node.js 22 or later and npm.

## Install

Use a new working directory:

~~~sh
mkdir coakka-runtime-first-run
cd coakka-runtime-first-run
npm init -y
npm install coakka-v2-connector-node@3.2.1
~~~

## Write the application

Create runtime.mjs:

~~~js
import { Buffer } from "node:buffer";
import { localRoute, RuntimeHost } from "coakka-v2-connector-node";

const target = "samples.customer.store.create";
const store = new Map();
const runtime = RuntimeHost.start({
  systemName: "customer-app",
  nodeId: "customer-app-node-1",
  routes: [localRoute(target)],
});
try {
  runtime.registerPayloadHandler(target, (bytes) => {
    const draft = JSON.parse(Buffer.from(bytes).toString("utf8"));
    const customer = { id: draft.id, name: draft.name };
    store.set(customer.id, customer);
    return Buffer.from(JSON.stringify({ status: "created", customer, storedCount: store.size }));
  });
  const bytes = await runtime.askPayload("customer-api", target,
    Buffer.from(JSON.stringify({ id: "cust-001", name: "Ada Lovelace" })), 2000);
  console.log(JSON.parse(Buffer.from(bytes).toString("utf8")));
} finally {
  await runtime.close();
}
~~~

## Run and check

~~~sh
node runtime.mjs
~~~

Look for a reply with status created, the customer Ada Lovelace and storedCount 1:

~~~text
{
  status: 'created',
  customer: { id: 'cust-001', name: 'Ada Lovelace' },
  storedCount: 1
}
~~~

The app closes Runtime and exits. It does not start an HTTP server or require
Docker. The store is teaching data held in memory.

## How it fits an application

The handler owns the customer operation. A web controller could ask the same
named work and use the returned business result to build an HTTP response.

This example is local: it does not demonstrate a second process. Use
[examples and use cases](examples-and-use-cases.md#work-across-processes-and-languages)
to choose a larger topology and its exact package requirements.

<a id="runtime-replace-fake-backend-http"></a>
<a id="before"></a>
<a id="after"></a>

[The CoAkka story](../explanation/coakka-story.md) explains the application-owned
work model. [The integration guide](runtime-integration-guide.md) covers the
next step in your application.

## Logger Record

Logger is a separate product. Its
[one-record Node.js walkthrough](../../coakka-logger/first-node-record.md)
now lives with the Logger documentation.

## Next

[Run an example in another language](../getting-started.md), or
[choose a business example](examples-and-use-cases.md). Local-package
qualification wrappers are separate from this published-package walkthrough.
