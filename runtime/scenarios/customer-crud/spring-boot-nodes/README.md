# Spring Boot to Multiple Node.js Customer CRUD

This scenario runs:

| Service | Language | Role | HTTP | Runtime target | Runtime endpoint |
| --- | --- | --- | --- | --- | --- |
| `customer-web` | JVM / Spring Boot | browser UI and HTTP API | `8081` | `samples.customer.frontend` | `127.0.0.1:19131` |
| `customer-store-node` | Node.js | authoritative customer table | `8092` | `samples.customer.store` | `127.0.0.1:19132` |
| `customer-audit-node` | Node.js | mutation event stream | `8094` | `samples.customer.audit` | `127.0.0.1:19134` |

The Spring Boot web service reuses the same customer UI/API from the
Spring-to-Spring scenario. The Node.js store owns state and emits typed audit
events to a second Node.js service after create, update, and delete operations.

## Current Runtime Backend Note

The current public runtime v2 artifact reports `backend=stub`. All services can
boot and show diagnostics. The web service sends business requests only through
the runtime route. There is no Node.js store REST fallback, so CRUD requests
return explicit runtime delivery failures until a remote-capable backend is
published. The audit fan-out path is present in the store code and will become
observable through runtime delivery when that backend is available.

The web UI is shared with the Spring-to-Spring scenario. Store and audit HTTP
ports are for viewing state and diagnostics directly; they are not used as the
web-to-store business transport.

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
bash run.sh audit
bash run.sh store
```

In another terminal:

```sh
bash run.sh web
```

Open:

- Customer Web: `http://localhost:8081`
- Node Store: `http://localhost:8092`
- Node Audit: `http://localhost:8094`

## Smoke

With all services running:

```sh
bash run.sh smoke
```

With the current `backend=stub` artifact, `bash run.sh smoke` verifies
diagnostics, route-miss behavior, and that create returns
`503 RUNTIME_DELIVERY_FAILED` instead of falling back to store REST.

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
