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
boot and show diagnostics. The web service tries the runtime route first, then
uses the Node.js store HTTP API when the stub backend rejects remote delivery,
so browser CRUD and smoke commands remain runnable. The audit fan-out path is
present in the store code and will become observable through runtime delivery
when a remote-capable backend is available.

The web UI is shared with the Spring-to-Spring scenario. If a Create, Update,
Delete, or List succeeds while `Runtime deadletters` increases, the business
operation succeeded through HTTP fallback after the runtime remote attempt
returned a deadletter. Store and audit pages label runtime counters explicitly
so they are not confused with customer persistence success.

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

With the current `backend=stub` artifact, the web service uses the HTTP store
fallback after the runtime route reports a delivery deadletter.

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
