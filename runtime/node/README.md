# Node.js Runtime Samples

Node.js runtime samples document the `coakka-v2-connector-node` package shape.
This runtime lane consumes the public Node.js package built against native
runtime `0.1.0+a671b3a`.

## Run

```sh
bash run.sh runtime node basic
bash run.sh runtime node deadletter
```

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
  routes: [
    {
      target: "samples.customer.store",
      endpoints: [{ host: "127.0.0.1", port: 19102, flags: EndpointFlag.LOCAL }],
    },
  ],
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
process.on("SIGTERM", () => {
  runtime.close();
  process.exit(0);
});
```

## Before: Internal REST

The store often becomes an internal Express/Fastify endpoint created only so
another process can call it:

```js
app.post("/internal/customers", async (req, res) => {
  const customer = await store.create(req.body);
  res.json(customer);
});
```

The web/API side then forwards business work through HTTP:

```js
app.post("/api/customers", async (req, res) => {
  const reply = await fetch("http://customer-store/internal/customers", {
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
Before internal REST:
  POST /internal/customers -> route handler

After CoAkka:
  target = "samples.customer.store" -> registered handler
```

The target plays a similar addressing role to an internal REST path, but it is
runtime routing vocabulary, not an HTTP URL.

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

The extra internal endpoint still spreads private runtime work across HTTP
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
