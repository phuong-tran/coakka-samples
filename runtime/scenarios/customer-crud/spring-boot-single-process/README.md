# Spring Boot Single-Process Customer CRUD

This scenario runs one Spring Boot process with one browser UI/API and one
local store handler target:

| Surface | Role | HTTP | Runtime target | Runtime endpoint |
| --- | --- | --- | --- | --- |
| `customer-app` web/API | browser UI and HTTP API | `8081` | `samples.customer.frontend` as ask source | source-only |
| `customer-app` store handler | in-memory customer table | none | `samples.customer.store` | `127.0.0.1:19142` |

The browser talks to `customer-app` over HTTP. The controller still sends typed
CoAkka runtime requests from `samples.customer.frontend` to
`samples.customer.store`; the store handler is local to the same process and
replies through runtime request/reply. There is no store REST API.

This is a same-process runtime demo. It proves the target vocabulary, handler
ownership, payload contract, counters, request/reply, and deadletter behavior.
It does not prove remote cross-process delivery.

## Why This Exists

This scenario gives the smallest Spring Boot happy path: customer actions
succeed while still using the same runtime targets and payload contract as the
cross-process demos.

## Run

From this directory:

```sh
bash run.sh
```

The default command is `check`: it builds the Spring Boot jar without keeping a
server running.

To start the app:

```sh
bash run.sh dev
```

Open:

- Customer UI: `http://localhost:8081`

## Smoke

With the app running:

```sh
bash run.sh smoke
```

The smoke creates, updates, lists, deletes, and triggers one intentional route
miss through the browser-facing API. All CRUD operations go through the local
runtime store target.

## Route Hot Reload

This scenario includes a route snapshot file:

```text
routes.yml
```

With the app running, apply the snapshot through the runtime control lane:

```sh
bash run.sh reload-routes
```

The command posts `routes.yml` to
`POST /api/customers/runtime/reload-routes`. The app compiles that YAML into a
full route snapshot, applies the next generation, and returns the generation
before and after the reload. The customer route still points to the same local
store target; this keeps the sample focused on the reload mechanics rather than
remote deployment.

## Stop

```sh
bash run.sh stop
```
