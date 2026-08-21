# First npm Smoke

This page is the smallest no-checkout path for a new npm user. It uses only
packages from the public registry and a temporary local project.

More onboarding docs live here:
https://github.com/phuong-tran/coakka-samples/tree/main/docs

The runtime example is intentionally shaped like a common backend refactor:
keep real HTTP at the browser/API edge, but stop creating a second fake backend
HTTP endpoint just to call application-owned work.

## Runtime: Replace Fake Backend HTTP

The example operation is one customer command:

```json
{
  "id": "cust-001",
  "name": "Ada Lovelace"
}
```

### Before

This is the shape CoAkka is meant to remove when the backend HTTP boundary is
only there to give local work owned by the same app or team an address:

```js
// customer-store process or module
app.post("/backend/customers", async (req, res) => {
  const customer = await store.create(req.body);
  res.json({ status: "created", customer });
});

// browser-facing API process or module
app.post("/api/customers", async (req, res) => {
  const reply = await fetch("http://customer-store/backend/customers", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(req.body),
  });

  res.json(await reply.json());
});
```

That backend URL is not a product API. It is plumbing for application work:
URL config, HTTP parsing, headers, status mapping, timeout mapping, and test
fixtures before the store even runs.

### After

With CoAkka, the store work has a runtime target instead of a fake backend URL:

```text
POST /api/customers
  -> ask target "samples.customer.store.create"
  -> registered handler
  -> runtime reply or deadletter
```

Install the runtime package:

```sh
mkdir coakka-runtime-first-run
cd coakka-runtime-first-run
npm init -y
npm install coakka-v2-connector-node@2.5.3
```

Create `runtime.mjs`:

```js
import {
  DeliveryHint,
  localRoute,
  NodeRuntimeClient,
  PayloadFormat,
  PayloadIdentity,
  RuntimeHost,
} from "coakka-v2-connector-node";

const target = "samples.customer.store.create";
const store = new Map();

const runtime = RuntimeHost.start({
  systemName: "customer-app",
  nodeId: "customer-app-node-1",
  queueCapacity: 64,
  strictNoDrop: true,
  generation: 1,
  routes: [localRoute(target, 19001)],
});

try {
  runtime.registerHandler(target, (request) => {
    const draft = JSON.parse(Buffer.from(request.payload).toString("utf8"));
    const customer = {
      id: draft.id,
      name: draft.name,
      createdBy: request.source,
    };
    store.set(customer.id, customer);

    return NodeRuntimeClient.makeJsonReplyFromRequestIdentity(request, target, {
      status: "created",
      customer,
      storedCount: store.size,
    });
  });

  const response = await runtime.askJson(
    "customer-api",
    target,
    { id: "cust-001", name: "Ada Lovelace" },
    new PayloadIdentity("samples.customer.create.request.v1", 1, PayloadFormat.JSON),
    2000,
    "create_customer",
    DeliveryHint.ROUTER_DEFAULT,
  );

  console.log(response);
} finally {
  runtime.close();
}
```

Run it:

```sh
node runtime.mjs
```

Expected shape:

```text
{
  status: 'created',
  customer: { id: 'cust-001', name: 'Ada Lovelace', createdBy: 'customer-api' },
  storedCount: 1
}
```

The browser/API HTTP route can still exist in a real app. The difference is
that the controller asks a runtime target instead of forwarding to
`/backend/customers`.

## Logger Record

```sh
mkdir coakka-logger-first-run
cd coakka-logger-first-run
npm init -y
npm install coakka-logger-node@1.2.7
```

Create `logger.mjs`:

```js
import { CoakkaLoggerLevel, Logger } from "coakka-logger-node";

const logger = Logger.start({
  systemName: "first-user-logger",
  minLevel: CoakkaLoggerLevel.INFO,
});

try {
  const sequence = logger.info("first.user", JSON.stringify({ hello: "logger" }));
  const record = logger.awaitNext(1000);
  if (sequence == null || record == null) {
    throw new Error("expected one accepted and drained log record");
  }

  console.log({ sequence: record.sequence, category: record.category });
} finally {
  logger.close();
}
```

Run it:

```sh
node logger.mjs
```

Expected shape:

```text
{ sequence: 1, category: 'first.user' }
```

## Next

Run the maintained samples after this smoke passes:

```sh
git clone https://github.com/phuong-tran/coakka-samples.git
cd coakka-samples
bash run.sh runtime node basic
bash run.sh logger node basic
```
