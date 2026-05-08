# Runtime Customer CRUD Scenarios

This scenario track turns runtime v2 samples from protocol primitives into a
small web workflow that is easy to understand from any language background.

The story is intentionally ordinary:

- a user opens a web page
- the user adds, edits, deletes, and lists customers
- the web-facing service or desktop frontend sends typed runtime requests
- the owner process or local handler mutates or reads its in-memory customer table
- one web page explains the message path and shows runtime diagnostics beside
  the business state

The goal is not to teach customer management. The goal is to make process,
language, routing, request/reply, deadletter, and diagnostics visible through a
workflow people already understand.

## User Experience Contract

Every customer scenario should provide the same surface:

| Surface | Requirement |
| --- | --- |
| UI | one customer form/table page with clear success/error state |
| HTTP API | curlable browser-facing create, update, delete, and list endpoints when the topology has a web service |
| Runtime panel | ABI/version/git, routes, delivered count, deadletters |
| Process panel | service name, language, runtime endpoint/target |
| Logs | one line per accepted runtime operation with correlation id |
| Smoke path | a script or documented curl flow that verifies the scenario without a browser |
| Shutdown path | documented ports and cleanup commands |

The first screen should show the actual customer workflow, not a landing page.
Runtime diagnostics stay visible but secondary.

## Shared Domain Contract

All topologies should keep the same typed payload contract so the language swap
is the only changing variable.

Targets:

| Target | Owner |
| --- | --- |
| `samples.customer.frontend` | web-facing service |
| `samples.customer.store` | customer state owner |
| `samples.customer.audit` | optional audit/event observer |

Operations:

| Operation | Request identity | Response identity |
| --- | --- | --- |
| create | `samples.customer.create.request.v1` | `samples.customer.mutation.response.v1` |
| update | `samples.customer.update.request.v1` | `samples.customer.mutation.response.v1` |
| delete | `samples.customer.delete.request.v1` | `samples.customer.mutation.response.v1` |
| list | `samples.customer.list.request.v1` | `samples.customer.list.response.v1` |

Customer JSON:

```json
{
  "id": "cust-001",
  "name": "Ada Lovelace",
  "email": "ada@example.com",
  "tier": "gold",
  "notes": "first cross-language customer"
}
```

Mutation response JSON:

```json
{
  "status": "ACCEPTED",
  "operation": "create",
  "customerId": "cust-001",
  "revision": 1
}
```

List response JSON:

```json
{
  "customers": [
    {
      "id": "cust-001",
      "name": "Ada Lovelace",
      "email": "ada@example.com",
      "tier": "gold",
      "notes": "first cross-language customer",
      "revision": 1
    }
  ]
}
```

## Topologies

The customer scenarios keep web-to-store business traffic on the runtime path;
there is no store REST fallback. Local demos keep runtime work inside one app
or one desktop process for a compact happy path. Cross-process demos run
separate services and require a remote-capable runtime artifact. If remote
delivery fails, the samples surface an explicit runtime delivery error instead
of hiding the failure behind HTTP fallback behavior.

This is the boundary rule the scenarios are trying to make obvious: REST is
valuable at real edges, but an internal REST endpoint created only to call local
or deployment-owned work pays for a web stack and then forces runtime delivery
failures into HTTP-shaped policy. CoAkka keeps the internal path as a typed
runtime target with request/reply, one-way events, and deadletters. The samples
therefore compare application shape and failure semantics, not just raw
transport speed.

### Spring Boot Single Process

This is scaffolded under `spring-boot-single-process/` as the local happy path.

| Surface | Language | Role | HTTP | Runtime endpoint |
| --- | --- | --- | --- | --- |
| `customer-app` web/API | JVM / Spring Boot | web UI and HTTP API | `8081` | source-only |
| `customer-app` store handler | JVM / Spring Boot | local in-memory customer table | none | `127.0.0.1:19142` |

Flow:

1. Browser posts `create/update/delete/list` to `customer-app`.
2. The controller sends typed `ask(...)` to `samples.customer.store`.
3. The local store handler mutates or reads the table and replies.
4. Browser shows the updated table, runtime counters, and route-miss diagnostics.

This scenario does not exercise remote cross-process transport.

### Spring Boot Starter Local

This is scaffolded under `spring-boot-starter-local/` as the local-first Spring
Boot starter shape.

| Surface | Language | Role | HTTP | Runtime endpoint |
| --- | --- | --- | --- | --- |
| `customer-app` web/API | JVM / Spring Boot | web UI and HTTP API | `8082` | source-only |
| `@CoAkkaHandler` methods | JVM / Spring Boot | local customer capabilities | none | `127.0.0.1:19172` |

Flow:

1. Browser posts `create/update/delete/list` to `customer-app`.
2. The controller calls `CoAkkaRuntimeClient`.
3. The starter routes to local `@CoAkkaHandler` capability methods.
4. Runtime diagnostics show generation, route count, request/reply
   counters, and an intentional route-miss deadletter.

This scenario does not configure remote endpoints, bind/advertise ports for
Kubernetes, service discovery, TLS, or business retry policy. Those belong
after the local starter API shape is stable.

### Quarkus Local

This is scaffolded under `quarkus-local/` as a Quarkus/Kotlin local-first proof
that consumes the published `coakka.quarkus:coakka-quarkus-extension` adapter.

| Surface | Language | Role | HTTP | Runtime endpoint |
| --- | --- | --- | --- | --- |
| `customer-app` web/API | JVM / Quarkus Kotlin | web UI and HTTP API | `8083` | source-only |
| `customer-app` store handler | JVM / Quarkus Kotlin | local in-memory customer table | none | `127.0.0.1:19182` |

Flow:

1. Browser posts `create/update/delete/list` to the Quarkus resource.
2. The resource sends typed `askBlocking(...)` calls through the Quarkus
   adapter.
3. The local `@CoAkkaHandler` CDI bean mutates or reads the table and replies.
4. Runtime diagnostics show generation, route count, request/reply
   counters, and an intentional route-miss deadletter.

This scenario uses the published Quarkus adapter for lifecycle and local handler
registration. Remote/Kubernetes mode remains out of scope for this local-first
slice.

### Kotlin Desktop Local

This is scaffolded under `kotlin-desktop-local/` as the smallest visual happy
path for the same customer contract.

| Surface | Language | Role | HTTP | Runtime endpoint |
| --- | --- | --- | --- | --- |
| `customer-desktop` UI | JVM / Kotlin Swing | desktop UI and frontend runtime | none | `127.0.0.1:19151` |
| `customer-desktop` store handler | JVM / Kotlin | local in-memory customer table on store runtime | none | `127.0.0.1:19152` |

Flow:

1. The desktop button sends a typed `ask(...)` from `samples.customer.frontend`.
2. The route snapshot resolves `samples.customer.store`.
3. The store runtime handler mutates or reads the table and replies.
4. The desktop UI shows the customer table, route generations, runtime
   version/git for both handles, counters, and one intentional
   route-miss diagnostic.

This scenario has no HTTP API at all. It exists so the local runtime message
path is visible without the extra browser-to-web-service layer.

### Python Desktop Local

This is scaffolded under `python-desktop-local/` as the same local visual happy
path through the Python connector, with one `RuntimeHost` inside one Python
process.

| Surface | Language | Role | HTTP | Runtime endpoint |
| --- | --- | --- | --- | --- |
| `app.py` UI | Python / Tk | desktop UI and ask source | none | source-only |
| `app.py` store handler | Python | local in-memory customer table on RuntimeHost | none | `127.0.0.1:19162` |

Flow:

1. The desktop button sends a typed `ask_json(...)` from
   `samples.customer.frontend`.
2. The single local `RuntimeHost` resolves `samples.customer.store`.
3. The local store handler mutates or reads the table and replies.
4. The desktop UI shows the customer table, route generation, runtime
   version/git, counters, and one intentional route-miss diagnostic.

This scenario has no HTTP API at all. It demonstrates the same target and
payload vocabulary from Python without introducing a web layer.

### Spring Boot to Spring Boot

This is now scaffolded under `spring-boot-spring-boot/` as the reference
implementation.

| Service | Language | Role | HTTP | Runtime endpoint |
| --- | --- | --- | --- | --- |
| `customer-web` | JVM / Spring Boot | web UI and HTTP API | `8081` | `127.0.0.1:19101` |
| `customer-store` | JVM / Spring Boot | headless in-memory customer table | none | `127.0.0.1:19102` |

The Spring Boot store is configured with `spring.main.web-application-type:
none`, so it does not start Tomcat or bind an HTTP port.

Flow:

1. Browser posts `create/update/delete/list` to `customer-web`.
2. `customer-web` sends typed `ask(...)` to `samples.customer.store`.
3. `customer-store` mutates or reads the table and replies.
4. Browser shows the result and both services expose runtime panels.

### Spring Boot to Node.js

This is scaffolded under `spring-boot-node/`. It keeps the Spring Boot web/API
service and replaces the state owner with Node.js.

| Service | Language | Role | HTTP | Runtime endpoint |
| --- | --- | --- | --- | --- |
| `customer-web` | JVM / Spring Boot | web UI and HTTP API | `8081` | `127.0.0.1:19111` |
| `customer-store-node` | Node.js | headless in-memory customer table | none | `127.0.0.1:19112` |

This scenario proves the runtime contract survives replacing the store process
process language while the web workflow stays unchanged.

### Spring Boot to Go

This is scaffolded under `spring-boot-go/`. It keeps the Spring Boot web/API
service and replaces the state owner with Go.

| Service | Language | Role | HTTP | Runtime endpoint |
| --- | --- | --- | --- | --- |
| `customer-web` | JVM / Spring Boot | web UI and HTTP API | `8081` | `127.0.0.1:19121` |
| `customer-store-go` | Go | headless in-memory customer table | none | `127.0.0.1:19122` |

This scenario makes Go feel like a normal service participant, not a special
synthetic-demo sidecar.

### Spring Boot to C#

This is scaffolded under `spring-boot-csharp/`. It keeps the Spring Boot
web/API service and replaces the state owner with C#/.NET.

| Service | Language | Role | HTTP | Runtime endpoint |
| --- | --- | --- | --- | --- |
| `customer-web` | JVM / Spring Boot | web UI and HTTP API | `8081` | `127.0.0.1:19141` |
| `customer-store-csharp` | C# / .NET | headless in-memory customer table | none | `127.0.0.1:19142` |

This scenario proves the published `CoAkka.Runtime` NuGet package can
participate in the same Spring Boot web-to-store runtime path as Node.js and
Go, without adding a C# store REST API.

### Spring Boot to Multiple Node.js Services

This is scaffolded under `spring-boot-nodes/`. It keeps the Spring Boot web/API
service and adds two Node.js services: one authoritative store and one audit
event receiver.

| Service | Language | Role | HTTP | Runtime endpoint |
| --- | --- | --- | --- | --- |
| `customer-web` | JVM / Spring Boot | web UI and HTTP API | `8081` | `127.0.0.1:19131` |
| `customer-store-node` | Node.js | headless authoritative customer table | none | `127.0.0.1:19132` |
| `customer-audit-node` | Node.js | headless mutation event receiver | none | `127.0.0.1:19134` |

Flow:

1. `customer-web` sends create/update/delete to `customer-store-node`.
2. `customer-store-node` replies to the original request.
3. `customer-store-node` also emits a typed audit event to
   `samples.customer.audit`.
4. Store and audit logs show the headless services receiving runtime work.

## Browser Walkthrough

The browser story should be identical across topologies:

1. Open the web service page.
2. Read the process strip: Browser HTTP reaches only the web-facing service;
   store traffic is CoAkka message/reply.
3. Create a customer named `Ada Lovelace`.
4. Edit the tier from `silver` to `gold`.
5. Delete the customer.
6. Watch the customer table and runtime counters update on the single UI.
7. Trigger one missing-route action and verify the deadletter diagnostic.

## Headless Smoke Shape

Each topology should support a smoke flow like:

```sh
curl -sS -X POST http://127.0.0.1:8081/api/customers \
  -H 'Content-Type: application/json' \
  -d '{"id":"cust-001","name":"Ada Lovelace","email":"ada@example.com","tier":"silver","notes":"smoke"}'

curl -sS -X PUT http://127.0.0.1:8081/api/customers/cust-001 \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ada Lovelace","email":"ada@example.com","tier":"gold","notes":"updated"}'

curl -sS http://127.0.0.1:8081/api/customers

curl -sS -X DELETE http://127.0.0.1:8081/api/customers/cust-001
```

Expected result:

- mutation responses have `status` set to `ACCEPTED`
- list response reflects the latest state
- runtime panels show delivered requests increasing
- route-miss test increments deadletters

## Implementation Order

1. Spring Boot single-process customer CRUD.
2. Spring Boot starter local customer CRUD.
3. Quarkus local customer CRUD.
4. Kotlin desktop local customer CRUD.
5. Python desktop local customer CRUD.
6. Spring Boot to Spring Boot customer CRUD.
7. Spring Boot to Node.js customer store.
8. Spring Boot to Go customer store.
9. Spring Boot to multiple Node.js services with audit fan-out.

The single-process scenario owns the happy-path UI and HTTP contract. Later
scenarios should reuse that contract and change only the store
implementation/topology.
