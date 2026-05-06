# Spring Boot to Node.js Customer CRUD

This scenario runs:

| Service | Language | Role | HTTP | Runtime target | Runtime endpoint |
| --- | --- | --- | --- | --- | --- |
| `customer-web` | JVM / Spring Boot | browser UI and HTTP API | `8081` | `samples.customer.frontend` | `127.0.0.1:19111` |
| `customer-store-node` | Node.js | in-memory customer table | `8092` | `samples.customer.store` | `127.0.0.1:19112` |

The Spring Boot web service reuses the same customer UI/API from the
Spring-to-Spring scenario. The Node.js store uses the published
`coakka-v2-connector-node` package and exposes its own store table plus runtime
diagnostics.

## Current Runtime Backend Note

The current public runtime v2 artifact reports `backend=stub`. Both services
boot and show diagnostics. The web service tries the runtime route first, then
uses the Node.js store HTTP API when the stub backend rejects remote delivery,
so browser CRUD and smoke commands remain runnable.

The web UI is shared with the Spring-to-Spring scenario. If a Create, Update,
Delete, or List succeeds while `Runtime deadletters` increases, the business
operation succeeded through HTTP fallback after the runtime remote attempt
returned a deadletter.

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
bash run.sh store
```

In another terminal:

```sh
bash run.sh web
```

Open:

- Customer Web: `http://localhost:8081`
- Node Store: `http://localhost:8092`

## Smoke

With both services running:

```sh
bash run.sh smoke
```

With the current `backend=stub` artifact, the web service uses the HTTP store
fallback after the runtime route reports a delivery deadletter.

## Stop

```sh
bash run.sh stop
```
