# Spring Boot to Node.js Customer CRUD

This scenario runs:

| Service | Language | Role | HTTP | Runtime target | Runtime endpoint |
| --- | --- | --- | --- | --- | --- |
| `customer-web` | JVM / Spring Boot | browser UI and HTTP API | `8081` | `samples.customer.frontend` | `127.0.0.1:19111` |
| `customer-store-node` | Node.js | headless in-memory customer table | none | `samples.customer.store` | `127.0.0.1:19112` |

The Spring Boot web service reuses the same customer UI/API from the
Spring-to-Spring scenario. The Node.js store uses the published
`coakka-v2-connector-node` package and runs as a headless message handler.

## Current Runtime Backend Note

The current public runtime v2 artifact reports `backend=stub`. Both services
boot and show diagnostics. The web service sends business requests only through
the runtime route. There is no Node.js store REST fallback, so CRUD requests
return explicit runtime delivery failures until a remote-capable backend is
published.

The web UI is shared with the Spring-to-Spring scenario. Store state is visible
only through the web service list action, which is also a runtime message.

## Run

From this directory:

```sh
bash run.sh
```

The default command is `check`: it builds the Spring Boot web jar and verifies
the Node.js store entrypoint against the published connector package without
keeping servers running.

To start the services, run:

```sh
bash run.sh dev
```

This prepares the Node.js store, builds the Spring Boot web jar, and starts
both processes. To run services manually, use separate terminals:

```sh
bash run.sh store
bash run.sh web
```

Open:

- Customer Web: `http://localhost:8081`

## Smoke

With both services running:

```sh
bash run.sh smoke
```

With the current `backend=stub` artifact, `bash run.sh smoke` verifies
diagnostics, route-miss behavior, and that create returns
`503 RUNTIME_DELIVERY_FAILED` instead of falling back to store REST.

## Stop

```sh
bash run.sh stop
```
