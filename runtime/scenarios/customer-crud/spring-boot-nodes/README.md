# Spring Boot to Multiple Node.js Customer CRUD

This scenario runs:

| Service | Language | Role | HTTP | Runtime target | Runtime endpoint |
| --- | --- | --- | --- | --- | --- |
| `customer-web` | JVM / Spring Boot | browser UI and HTTP API | `8081` | `samples.customer.frontend` | `127.0.0.1:19131` |
| `customer-store-node` | Node.js | headless authoritative customer table | none | `samples.customer.store` | `127.0.0.1:19132` |
| `customer-audit-node` | Node.js | headless mutation event receiver | none | `samples.customer.audit` | `127.0.0.1:19134` |

The Spring Boot web service reuses the same customer UI/API from the
Spring-to-Spring scenario. The Node.js services use
`coakka-v2-connector-node@1.3.5` from npm; the store owns state and emits typed
audit events to a second Node.js service after create, update, and delete
operations.

## Runtime Transport Note

This scenario expects a runtime with cross-process delivery enabled v2 artifact. The web service
sends business requests only through the runtime route. There is no Node.js
store REST fallback, so delivery failures are returned explicitly instead of
being hidden by HTTP fallback behavior. The audit fan-out path is delivered as
a typed runtime event from the store service to the audit service.

The web UI is shared with the Spring-to-Spring scenario. Store and audit state
are visible through runtime replies and service logs, not through separate UIs.

## Run

From this directory:

```sh
bash run.sh
```

The default command is `check`: it builds the Spring Boot web jar and verifies
both Node.js entrypoints against the published connector package without
keeping servers running.

To start the services, run:

```sh
bash run.sh dev
```

This prepares the Node.js store/audit services, builds the Spring Boot web jar,
and starts all three processes. To run services manually, use separate
terminals:

```sh
bash run.sh audit
bash run.sh store
bash run.sh web
```

Open:

- Customer Web: `http://localhost:8081`

## Smoke

With all services running:

```sh
bash run.sh smoke
```

The smoke creates, updates, lists, and deletes a customer through the web API.
It also verifies diagnostics and the intentional route-miss behavior.

## Check Without Running Servers

```sh
bash run.sh check
```

This builds the Spring Boot web jar and verifies the Node.js entrypoints parse
against the published connector package shape.

## Stop

```sh
bash run.sh stop
```
