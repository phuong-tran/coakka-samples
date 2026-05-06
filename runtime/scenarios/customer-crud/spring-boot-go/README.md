# Spring Boot to Go Customer CRUD

This scenario runs:

| Service | Language | Role | HTTP | Runtime target | Runtime endpoint |
| --- | --- | --- | --- | --- | --- |
| `customer-web` | JVM / Spring Boot | browser UI and HTTP API | `8081` | `samples.customer.frontend` | `127.0.0.1:19121` |
| `customer-store-go` | Go | in-memory customer table | `8093` | `samples.customer.store` | `127.0.0.1:19122` |

The Spring Boot web service reuses the same customer UI/API from the
Spring-to-Spring scenario. The Go store uses the published
`coakka-v2-connector-go` package and exposes its own store table plus runtime
diagnostics.

## Current Runtime Backend Note

The current public runtime v2 artifact reports `backend=stub`. Both services
boot and show diagnostics. The web service sends business requests only through
the runtime route. There is no Go store REST fallback, so CRUD requests return
explicit runtime delivery failures until a remote-capable backend is published.

The web UI is shared with the Spring-to-Spring scenario. The store HTTP port is
for viewing store state and diagnostics directly; it is not used as the
web-to-store business transport.

## Run

From this directory:

```sh
bash run.sh
```

The default command is `check`: it builds the Spring Boot web jar and compiles
the Go store against the published connector package without keeping servers
running.

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
- Go Store: `http://localhost:8093`

## Smoke

With both services running:

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

This builds the Spring Boot web jar and formats/compiles the Go store against
the published connector package inside a temporary workspace.

## Stop

```sh
bash run.sh stop
```
