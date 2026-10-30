# Node.js Runtime Samples

`coakka-v2-connector-node@3.0.0` is published on npm.
Start with [the ordinary3.0.0 first run](registry-basic/README.md):

```sh
cd coakka-runtime/node/registry-basic
npm install
node main.mjs
```

Expected output: `accepted-hello`. The package carries its matching Core and
host adapters. The basic/deadletter wrappers instead perform
[exact local-archive qualification](../../docs/coakka-runtime/previews/3.0.0/runtime-js-candidate.md).
Use the [package ledger](../../docs/common/current-packages.md) for versions.

## Historical 2.x walkthrough

The recordings and integration snippets below describe npm 2.5.3 and Core
2.5.1+26f7944d. Use the [complete frozen 2.x sample](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime/node)
with that version's runner and API. These are historical context, not 3.0.0
installation instructions or release evidence.

## Contents

- [Run](#run)
- [Integration Recipe](#integration-recipe)
- [Before: Fake Backend HTTP](#before-fake-backend-http)
- [After: Runtime Target](#after-runtime-target)
- [Production Notes](#production-notes)
- [Continue Integrating](#continue-integrating)

## Run

```sh
bash run.sh coakka-runtime node basic
bash run.sh coakka-runtime node deadletter
```

Watch the Node.js runtime walkthrough:

![CoAkka Runtime Node.js walkthrough](../../docs/coakka-runtime/assets/coakka-runtime-node.gif)

Full recording: [coakka-runtime-node.mp4](../../docs/coakka-runtime/assets/coakka-runtime-node.mp4)

## Integration Recipe

Install the package through your normal package management path. The samples
resolve the public package into a temporary workspace for each run.

Start one `RuntimeHost` per process:

```js
const startSpec = {
  systemName: "customer-store",
  nodeId: "customer-store-node-1",
  queueCapacity: 128,
  strictNoDrop: true,
  generation: 1,
  routes: [localRoute("samples.customer.store", 19102)],
};

const runtime = RuntimeHost.start(startSpec);
```

Name roles in this snippet:

- `systemName` says this process belongs to the logical `customer-store`
  runtime participant.
- `nodeId` says this concrete process is `customer-store-node-1`.
- `target` says which capability the runtime can route to:
  `samples.customer.store`.

`target` is not `systemName` and not `nodeId`. A process can own multiple
targets; the route table maps those target names to endpoints.

`samples.customer.store` is the sample's capability target name. In your app,
choose your own target name, then use the same value in the route table, the
process-owned `registerHandler(...)`, and every caller `target`. For example,
`billing.invoice.create` is valid if that is the capability contract you want
to expose.

The same string appears again in the reply helper as `source`: the response is
coming from the target handler that produced it.

This sample registers one handler to stay small. A real app-host can register
multiple handlers if it owns multiple targets, such as
`samples.customer.create`, `samples.customer.update`, and
`samples.customer.list`. The route table and caller target must use the same
names.

Register handlers only for targets this process owns:

```js
runtime.registerHandler("samples.customer.store", (request) =>
  NodeRuntimeClient.makeJsonReplyFromRequestIdentity(
    request,
    "samples.customer.store",
    { status: "ACCEPTED" },
  ),
);
```

`askJson(...)` below is a convenience helper for JSON samples. It is not the
runtime saying that only JSON is supported. The payload identity carries the
message type, schema version, and `PayloadFormat.JSON`; other payload formats
are still runtime envelopes with a different payload format and byte encoding,
exposed through the connector API surface for that host language.

Send typed requests with explicit timeout and operation metadata:

```js
const response = await runtime.askJson(
  "customer-web",
  "samples.customer.store",
  { id: "cust-001" },
  new PayloadIdentity("samples.customer.create.request.v1", 1, PayloadFormat.JSON),
  5000,
  "create_customer",
  DeliveryHint.ROUTER_DEFAULT,
);
```

Close the runtime host on process shutdown:

```js
process.once("SIGTERM", () => {
  void runtime.close().catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
});
```

## Before: Fake Backend HTTP

The public API route can be real HTTP. The problem starts when the store also
becomes a backend Express/Fastify endpoint created only so work owned by the
same app or team has an address:

```js
app.post("/backend/customers", async (req, res) => {
  const customer = await store.create(req.body);
  res.json(customer);
});
```

The web/API side then forwards the same customer command through that private
HTTP surface:

```js
app.post("/api/customers", async (req, res) => {
  const reply = await fetch("http://customer-store/backend/customers", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(req.body),
  });
  res.json(await reply.json());
});
```

## After: Runtime Target

Read the address change like this:

```text
Before fake backend HTTP:
  POST /api/customers -> fetch("http://customer-store/backend/customers")

After CoAkka:
  POST /api/customers -> target "samples.customer.store" -> registered handler
```

The target replaces the private backend URL. It is runtime routing vocabulary,
not another HTTP endpoint.

With CoAkka, the store is a runtime target:

```js
runtime.registerHandler("samples.customer.store", (request) =>
  NodeRuntimeClient.makeJsonReplyFromRequestIdentity(
    request,
    "samples.customer.store",
    store.create(JSON.parse(new TextDecoder().decode(request.payload))),
  ),
);
```

`askJson(...)` below is used because the sample wants readable payloads in logs
and browser-visible diagnostics. It is a helper around typed runtime delivery,
not a statement that runtime is JSON-only.

The caller sends one typed runtime request:

```js
const response = await runtime.askJson(
  "samples.customer.frontend",
  "samples.customer.store",
  command,
  new PayloadIdentity("samples.customer.create.request.v1", 1, PayloadFormat.JSON),
  5000,
  "create_customer",
  DeliveryHint.ROUTER_DEFAULT,
);
```

The extra backend endpoint still spreads application runtime work across HTTP
parsing, headers, middleware, status/error mapping, timeout policy, and test setup.
CoAkka keeps that work as a runtime target with request/reply and deadletter
semantics, while HTTP stays at the public or legacy edge. Existing code using
`ConnectorOrchestrator.start(...)` still works as the compatibility name; new
samples use `RuntimeHost.start(...)`.

## Production Notes

- Keep target names stable and payload identities versioned.
- Use `strictNoDrop=true` while integrating to expose overload.
- Handle `DeadletterError` explicitly.
- Customer scenarios keep inter-service business traffic runtime-only and avoid a store REST fallback.

## Continue Integrating

Use this lane's runnable sample as the source for package imports and basic
lifecycle names. Before generating connection strategy, TLS/mTLS, File Lane, or
Stream Lane code, follow [AI-Assisted Integration](../../docs/coakka-runtime/guides/ai-assisted-integration.md).
It links the canonical feature guides, exact package catalog, and platform
evidence, and it defines when only workflow pseudocode is justified.

The current public package train includes File Lane and Stream Lane. Use the
exact connector names and lifecycle rules shipped by `2.4.0` when integrating
either lane.
