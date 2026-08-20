# Spring Boot to Node.js Customer CRUD

This scenario runs:

| Service | Language | Role | HTTP | Runtime target | Runtime endpoint |
| --- | --- | --- | --- | --- | --- |
| `customer-web` | JVM / Spring Boot | browser UI and HTTP API | `8081` | `samples.customer.frontend` | `127.0.0.1:19111` |
| `customer-store-node` | Node.js | headless in-memory customer table | none | `samples.customer.store` | `127.0.0.1:19112` |

The Spring Boot web service reuses the same customer UI/API from the
Spring-to-Spring scenario. The Node.js store uses the published
`coakka-v2-connector-node@2.5.1` npm package and runs as a headless message
handler.

## Runtime Transport Note

This scenario expects a runtime with cross-process delivery enabled v2 artifact. The web service
sends business requests only through the runtime route. There is no Node.js
store REST fallback, so delivery failures are returned explicitly instead of
being hidden by HTTP fallback behavior.

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

The smoke creates, updates, lists, and deletes a customer through the web API.
It also verifies diagnostics and the intentional route-miss behavior.

## Stop

```sh
bash run.sh stop
```
