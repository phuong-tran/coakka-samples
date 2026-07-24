# First npm Smoke

This page is the smallest no-checkout path for a new user. It uses only npm
packages from the public registry and a temporary local project.

## Runtime Echo

```sh
mkdir coakka-runtime-first-run
cd coakka-runtime-first-run
npm init -y
npm install coakka-v2-connector-node@1.3.5
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

const target = "first.user.echo";
const runtime = RuntimeHost.start({
  systemName: "first-user-runtime",
  nodeId: "first-user-runtime-node",
  queueCapacity: 64,
  strictNoDrop: true,
  generation: 1,
  routes: [localRoute(target, 19001)],
});

try {
  runtime.registerHandler(target, (request) =>
    NodeRuntimeClient.makeJsonReplyFromRequestIdentity(request, target, { ok: true }),
  );

  const response = await runtime.askJson(
    "first-user-client",
    target,
    { hello: "coakka" },
    new PayloadIdentity("first.user.echo.request.v1", 1, PayloadFormat.JSON),
    2000,
    "echo",
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
{ ok: true }
```

## Logger Record

```sh
mkdir coakka-logger-first-run
cd coakka-logger-first-run
npm init -y
npm install coakka-logger-node@1.2.4
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
