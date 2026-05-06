# Node.js Runtime Samples

Node.js runtime samples consume the published `coakka-v2-connector-node`
package tarball. The package includes the native runtime for supported
platforms.

## Run

```sh
bash run.sh runtime node basic
bash run.sh runtime node deadletter
```

## Integration Recipe

Install the package through your normal package management path. The samples
resolve it from `coakka-publish` into a temporary workspace.

Start one orchestrator per process:

```js
const startSpec = {
  systemName: "customer-store",
  nodeId: "customer-store-node-1",
  queueCapacity: 128,
  strictNoDrop: true,
  separateDeliveredRequestLane: true,
  generation: 1,
  routes: [
    {
      target: "samples.customer.store",
      endpoints: [{ host: "127.0.0.1", port: 19102, flags: EndpointFlag.LOCAL }],
    },
  ],
};

const orchestrator = ConnectorOrchestrator.start(startSpec);
```

Register handlers only for local targets:

```js
orchestrator.registerHandler("samples.customer.store", (request) =>
  NodeRuntimeClient.makeJsonReplyFromRequestIdentity(
    request,
    "samples.customer.store",
    { status: "ACCEPTED" },
  ),
);
```

Send typed requests with explicit timeout and operation metadata:

```js
const response = await orchestrator.askJson(
  "customer-web",
  "samples.customer.store",
  { id: "cust-001" },
  new PayloadIdentity("samples.customer.create.request.v1", 1, PayloadFormat.JSON),
  5000,
  "create_customer",
  DeliveryHint.ROUTER_DEFAULT,
);
```

Close the orchestrator on process shutdown:

```js
process.on("SIGTERM", () => {
  orchestrator.close();
  process.exit(0);
});
```

## Production Notes

- Keep target names stable and payload identities versioned.
- Use `strictNoDrop=true` while integrating to expose overload.
- Handle `DeadletterError` explicitly.
- Customer scenarios keep inter-service business traffic runtime-only and avoid a store REST fallback.
