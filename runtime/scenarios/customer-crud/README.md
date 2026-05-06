# Runtime Customer CRUD Scenarios

This scenario track turns runtime v2 samples from protocol primitives into a
small web workflow that is easy to understand from any language background.

The story is intentionally ordinary:

- a user opens a web page
- the user adds, edits, deletes, and lists customers
- the web-facing service sends typed runtime requests to another process
- the owner process mutates or reads its in-memory customer table
- one web page explains the message path and shows runtime diagnostics beside
  the business state

The goal is not to teach customer management. The goal is to make process,
language, routing, request/reply, deadletter, and diagnostics visible through a
workflow people already understand.

## User Experience Contract

Every customer scenario should provide the same surface:

| Surface | Requirement |
| --- | --- |
| Web UI | one customer form/table page on the web service with clear success/error state |
| HTTP API | curlable browser-facing create, update, delete, and list endpoints on the web service |
| Runtime panel | ABI/version/git/backend, routes, delivered count, deadletters |
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

## Planned Topologies

The current public runtime v2 release used by these samples reports
`backend=stub`. The single-process scenario gives a successful CRUD path today
because the store target is local to the same runtime process. Cross-process
customer scenarios are the target web topology for the next remote-capable
runtime release. Web-to-store business traffic is runtime-only; there is no
store REST fallback. Until a remote-capable release exists, cross-process CRUD
attempts return explicit delivery deadletters while build, boot, route config,
and diagnostics remain runnable from public artifacts.

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

### Kotlin Desktop Local

This is scaffolded under `kotlin-desktop-local/` as the smallest visual happy
path for the same customer contract.

| Surface | Language | Role | HTTP | Runtime endpoint |
| --- | --- | --- | --- | --- |
| `customer-desktop` UI | JVM / Kotlin Swing | desktop UI and frontend target | none | source-only |
| `customer-desktop` store handler | JVM / Kotlin | local in-memory customer table | none | `127.0.0.1:19152` |

Flow:

1. The desktop button sends a typed `ask(...)` from `samples.customer.frontend`.
2. The route snapshot resolves `samples.customer.store`.
3. The local store handler mutates or reads the table and replies.
4. The desktop UI shows the customer table, route generation, runtime
   version/git/backend, counters, and one intentional route-miss diagnostic.

This scenario has no HTTP API at all. It exists so the local runtime message
path is visible without the extra browser-to-web-service layer.

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

This scenario proves the runtime contract survives replacing the backend
process language while the web workflow stays unchanged.

### Spring Boot to Go

This is scaffolded under `spring-boot-go/`. It keeps the Spring Boot web/API
service and replaces the state owner with Go.

| Service | Language | Role | HTTP | Runtime endpoint |
| --- | --- | --- | --- | --- |
| `customer-web` | JVM / Spring Boot | web UI and HTTP API | `8081` | `127.0.0.1:19121` |
| `customer-store-go` | Go | headless in-memory customer table | none | `127.0.0.1:19122` |

This scenario makes Go feel like a normal service participant, not a special
benchmark-only sidecar.

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

With the current public `backend=stub` artifact, the smoke command instead
checks diagnostics, route-miss behavior, and that a create request returns
`503 RUNTIME_DELIVERY_FAILED` without falling back to store REST.

## Implementation Order

1. Spring Boot single-process customer CRUD.
2. Kotlin desktop local customer CRUD.
3. Spring Boot to Spring Boot customer CRUD.
4. Spring Boot to Node.js customer store.
5. Spring Boot to Go customer store.
6. Spring Boot to multiple Node.js services with audit fan-out.

The single-process scenario owns the happy-path UI and HTTP contract. Later
scenarios should reuse that contract and change only the store
implementation/topology.
