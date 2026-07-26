# Runtime Integration Guide

This guide is for moving from the samples to an existing service. The samples
are intentionally small, but the integration shape should stay the same:

For vocabulary first, start with [Runtime Glossary](runtime-glossary.md). For
fit and operational ownership, read
[Production Readiness](production-readiness.md). For the public sample
evidence checklist, read [Production Evidence](production-evidence.md). For
repository ownership boundaries, read
[Repository Boundaries](repository-boundaries.md).

1. add the language connector artifact
2. start one runtime participant per process
3. publish a route table
4. register local handlers for targets owned by the process
5. send typed requests/events to peer targets
6. handle deadletters and shut down cleanly

## Published Public Transport

The published artifact surface exposes logger packages, the public native
runtime C ABI package, runtime JVM/language connector packages, and the Spring
Boot and Quarkus adapters. With a matching public artifact checkout, local
in-process request/reply samples and cross-process customer scenarios keep
business traffic on the runtime path.

Delivery failures remain explicit runtime failures. The samples should not hide
route, queue, or transporter failures behind a store HTTP fallback.
HTTP stays at real external edges such as the browser-facing web API.

The customer scenario exposes that distinction through a `deliveryMode` field:

| Value | Meaning |
| --- | --- |
| `runtime` | The business response came back through the runtime route. |
| `store-http-direct` | The store HTTP API was called directly. |

## Start Spec

Every language exposes the same start-spec concepts. Treat `RuntimeStartSpec`
as the startup declaration for one runtime participant:

```text
RuntimeStartSpec
  process identity
  queue and delivery policy
  initial route snapshot
```

| Field | Question it answers | Integration rule |
| --- | --- | --- |
| `systemName` | Which logical service do I belong to? | Stable logical service name, such as `billing-api` or `customer-store`. |
| `nodeId` | Which concrete instance/process am I? | Concrete process identity. Include instance/host/pod identity when multiple instances run. |
| `queueCapacity` | How much work can runtime buffer before applying pressure? | Bounded queue size. Start conservative, measure pressure, then tune. |
| `strictNoDrop` | Should overload be visible instead of silently dropping work? | Prefer `true` while integrating so overload becomes visible. |
| delivered-request lane | Should runtime keep delivered requests on their own runtime lane? | Enabled by default in current connectors; keep the default for request/reply services so inbound handler work does not delay reply/deadletter matching. |
| `generation` | Which version of the route table is this? | Monotonic route-table version. Increment when applying a new route snapshot. |
| `routes` | What targets does this runtime know how to route? | Target-to-endpoint map. Local targets are owned here; peer targets point elsewhere. |

The runtime does not read environment variables, Kubernetes metadata, or
service-discovery data by itself. The app host or connector maps deployment
configuration into these fields and passes the start spec to the runtime.

### Reading The Runtime Types

The nested types are route-table vocabulary, not business domain objects.

| Type | Question it answers | Meaning |
| --- | --- | --- |
| `RuntimeStartSpec` | What does this process declare when joining runtime? | Full startup/config declaration for one runtime process. |
| `RuntimeRouteSpec` | Which target/capability is routed where? | One route-table row: a target/capability and its endpoint candidates. |
| `RuntimeEndpointSpec` | Where can that target be handled? | One endpoint with `host`, `port`, and endpoint flags. |
| `RuntimeEndpointFlags` | What is the endpoint's routing state? | Endpoint state used by route selection, such as `LOCAL` or `UNAVAILABLE`. |

Example shape:

```text
RuntimeStartSpec
  systemName = customer-store
  nodeId = customer-store-pod-7
  generation = 12
  routes:
    RuntimeRouteSpec
      target = samples.customer.store
      endpoints:
        RuntimeEndpointSpec
          host = 10.20.1.45
          port = 19301
          flags = LOCAL
```

Read that as:

```text
This process is one customer-store runtime participant. Its current route-table
generation is 12. It owns the samples.customer.store target locally, so this
process must register the handler for that target.
```

If the endpoint has no `LOCAL` flag, this process does not own that handler.
The route points at a peer runtime endpoint instead.

### Field Details

`systemName` is the logical participant or service name. Keep it stable across
restarts and replicas:

```text
customer-store
billing-worker
document-service
```

`nodeId` is the concrete instance identity. In Kubernetes, include enough pod or
instance identity to distinguish replicas:

```text
customer-store-pod-7
customer-store-us-east-1a-0003
```

Do not hard-code `nodeId` in dynamic deployments. The connector should read it
from platform/runtime configuration when the process starts. Common sources are:

- Kubernetes pod name
- Kubernetes pod UID
- hostname
- container ID
- explicit `COAKKA_NODE_ID` or `INSTANCE_ID` environment variable

`nodeId` is runtime identity, not image identity. Build one reusable container
image for every replica and supply `nodeId` when each container or pod starts.
For Kubernetes, Docker Compose, and image-build examples, see
[Containerized Runtime Notes](containerized-runtime.md).

Treat duplicate `nodeId` values as misconfiguration in real deployments.
Requests may still be delivered if route selection does not depend on node
identity, but logs, stats, health, deadletters, and route ownership become
ambiguous. A practical rule is that `nodeId` should be unique within a
`systemName`.

`queueCapacity` is the bounded runtime queue size. Start small enough that
pressure is visible, then tune from observed queue depth, burst size, and memory
budget. Do not treat sample values as production sizing.

`strictNoDrop` should usually be `true` while integrating. Queue pressure,
missing routes, and rejected work should become explicit failures or
deadletters instead of silent message loss.

`separateDeliveredRequestLane` should usually be `true` for request/reply
services. A process may receive new requests for local handlers while it is also
waiting for replies or deadletters for asks it sent to another target. With
`true`, delivered requests use a separate runtime lane from ask completion.
With `false`, incoming handler work and reply/deadletter matching share one
lane. Treat `true` as the default; use `false` only for very small one-way
hosts after measurement.

`generation` is the route snapshot version. The first snapshot commonly starts
at `1` and is usually enough for ordinary container startup. A route reload, if
used, should publish a newer generation. The runtime rejects stale generations
so an old config update cannot silently roll back the active route table.

`routes` is the target-to-endpoint map. Each route names a stable capability
target and lists the endpoints eligible to handle that target.

Route configuration is not extra work invented by CoAkka. With or without
CoAkka, a system must know which service or capability it intends to call.
CoAkka changes where that knowledge lives: it becomes explicit, centralized,
versioned runtime state instead of scattered client wiring.

### Before And After Route Knowledge

Before CoAkka, the same information often exists as ad-hoc client or framework
configuration:

```text
customer-web needs customer-store
customer-store lives at http://customer-store:8080
the client owns timeout mapping, retry wrapper, status handling, logging,
and correlation conventions
```

With CoAkka, the connector maps that topology into runtime route state:

```text
target = samples.customer.store
endpoints = customer-store runtime endpoint(s)
strategy = SINGLE_OWNER | WEIGHTED_ROUND_ROBIN | RENDEZVOUS_HASH
generation = 12
```

The system still has to know the same relationship. The difference is that the
relationship enters one runtime contract. That makes topology easier to
inspect, diagnose, keep consistent across languages, and reload when an
operator or control plane actually needs a live route change. Route misses,
unavailable endpoints, and queue pressure become runtime outcomes with shared
deadletter and stats vocabulary instead of each client inventing its own
failure shape.

Samples often construct `RuntimeRouteSpec` directly so the moving parts are
visible. Real integrations should usually generate route specs from framework
config, Kubernetes or Consul data, service discovery, Helm values, or a control
plane.

This should feel like ordinary application configuration in Kubernetes: the app
reads env, framework config, Helm values, ConfigMaps, Service DNS, or pod
metadata at startup, and the connector maps those values into
`RuntimeStartSpec` plus the initial route snapshot.

`host` and `port` identify the runtime endpoint. For a local sample this may be
`127.0.0.1` plus a sample port. In a real deployment it usually comes from the
connector's startup config source, such as Kubernetes, Consul, a config service,
or framework config.

Do not read `127.0.0.1` samples as the production model. In Kubernetes, `host`
is commonly a Service DNS name, pod DNS name, advertised pod IP, or a value
produced by a control plane. If one application role has multiple replicas, keep
the runtime port stable across those replicas. For example, three `billing`
pods can all advertise port `19301`; the differing part is the host identity or
the Service DNS indirection.

Two common route shapes are:

```text
Service DNS:
  billing.default.svc.cluster.local:19301

Expanded pod endpoints:
  billing-0.billing.default.svc.cluster.local:19301
  billing-1.billing.default.svc.cluster.local:19301
  billing-2.billing.default.svc.cluster.local:19301
```

The first shape gives the runtime one logical endpoint and lets the platform
resolve the service name. The second shape gives the route snapshot direct
visibility into each replica. Neither shape requires the application image to
know pod hostnames ahead of time.

For container and Kubernetes examples, see
[Containerized Runtime Notes](containerized-runtime.md).

`RuntimeEndpointFlags.LOCAL` means the endpoint belongs to this process. Only
targets with a process-owned endpoint should have a handler registered in this
process.

`RuntimeEndpointFlags.UNAVAILABLE` means the endpoint remains visible in the
snapshot but should be excluded from new request route selection. Use it for
drain/rollout behavior where the route should stay observable but not receive
new work.

## Source And Target

`source` and `target` answer different questions.

| Field | Question it answers | Runtime behavior |
| --- | --- | --- |
| `source` | Who is sending this request, event, or reply? | Carried for diagnostics, correlation, deadletters, and reply naming. |
| `target` | What capability should receive this work? | Used by the runtime route table to choose an endpoint. |

Example:

```text
source = samples.customer.frontend
target = samples.customer.store
```

Read that as:

```text
The frontend is asking the customer store capability to do work.
```

The runtime routes by `target`, not by `source`.
`source` should still be stable and meaningful because it appears in logs,
deadletters, traces, and replies.

For a reply, the handler usually sends from the capability that handled the
request:

```text
request:
  source = samples.customer.frontend
  target = samples.customer.store

reply:
  source = samples.customer.store
  correlated with the original request
```

Do not use `source` as a routing shortcut or authorization substitute.
Authorization and business policy belong above the runtime. Runtime uses
`target` to resolve delivery.

## Request Parameters

CoAkka does not use URL path or query parameters because `target` is not a URL.
Split request data by responsibility:

| Concern | Put it in | Example |
| --- | --- | --- |
| Capability address | `target` | `customer.hold` |
| Business command/query data | payload | `{"customerId":"cust-001","reason":"manual_review"}` |
| Request context | envelope `headers` map | `tenant=acme`, `x-request-id=req-123`, `idempotency-key=hold-001` |

Read this REST-style call:

```text
POST /backend/customers/cust-001/hold?tenant=acme
x-request-id: req-123
```

as this runtime envelope shape:

```text
target  = customer.hold
payload = {"customerId":"cust-001","reason":"manual_review"}
headers = {"tenant":"acme","x-request-id":"req-123"}
```

The envelope `headers` field is a small `map<string,string>` for host-side
context that does not deserve a first-class runtime field yet. Some connector
APIs may expose that bag as headers, metadata, `extraParam`, or `extraParams`.
Use it for diagnostics, correlation, tenancy, idempotency keys, and other
request context.

Do not put core business data in headers. If a value changes the command or
query semantics, put it in the payload and version it through payload identity.

## Target Design

Use target names as stable service-contract addresses, not process names:

```text
samples.customer.frontend
samples.customer.store
samples.customer.audit
```

A target should answer “who owns this contract?” rather than “where is the
current host?”. The route table maps that target to a host/port endpoint.

## Endpoint Flags

Mark only targets served by the current process as `LOCAL`.

```text
current process owns samples.customer.store -> LOCAL
peer process owns samples.customer.frontend -> non-local
```

Register handlers only for local targets. Sending to a missing or unreachable
target should produce a deadletter that the caller can log, surface, or retry
according to business rules.

## Route Resolution Strategy

Route strategy answers this question:

```text
If a target has multiple eligible endpoints, which endpoint should runtime
choose?
```

This is runtime routing policy. It is not business priority, authorization,
or retry policy.

| Strategy | Question it answers | Typical use |
| --- | --- | --- |
| `SINGLE_OWNER` | Does exactly one endpoint own this target? | Owner-bound commands, local samples, actor-like ownership. |
| `WEIGHTED_ROUND_ROBIN` | Can equivalent endpoints share this work? | Stateless or replicated handlers that can scale horizontally. |
| `RENDEZVOUS_HASH` | Should the same key keep going to the same endpoint? | Sharded ownership by `order_id`, `tenant_id`, `customer_id`, or another stable key. |

Examples:

```text
target = payment.authorize
strategy = SINGLE_OWNER
```

Read that as:

```text
payment.authorize has one responsible owner. If that owner is unavailable,
runtime should fail closed instead of selecting an arbitrary endpoint.
```

```text
target = order.validate
strategy = WEIGHTED_ROUND_ROBIN
```

Read that as:

```text
order.validate can run on multiple equivalent endpoints. Runtime may distribute
new requests across eligible endpoints.
```

```text
target = order.create
strategy = RENDEZVOUS_HASH
route_key_hint = order_id
```

Read that as:

```text
Requests with the same order_id should resolve to the same eligible endpoint,
which is useful when ownership or cache locality matters.
```

Endpoint flags and strategy work together:

- `LOCAL` says the endpoint belongs to this process.
- `UNAVAILABLE` keeps an endpoint visible but excludes it from new route
  selection.
- `strategy` chooses among endpoints that remain eligible.

## Payload Contract

Every payload identity has:

- message type
- schema version
- payload format

The samples use JSON because it is easy to inspect:

```text
samples.customer.create.request.v1
schema version: 1
format: JSON
```

For a real system, keep payload identities stable and version them deliberately.
Do not reuse a message type for an incompatible payload shape.

When two services share a payload contract, keep the DTOs and message-type
strings in one shared source of truth. The Spring customer scenario uses a
`customer-contract` module for that purpose. The web and store services each
adapt those shared strings into runtime `ConnectorPayloadIdentity` values at the
connector boundary.

## Handler Pattern

A handler should:

1. decode the payload
2. validate the request
3. perform the local operation
4. return a typed reply using the original request correlation

Keep handler ownership local. The runtime owns routing and correlation; the
service still owns its business state and validation.

## Caller Pattern

A caller should:

1. choose source and target
2. attach a payload identity
3. put business arguments in payload
4. add envelope headers only for request context
5. set a timeout
6. name the operation for diagnostics
7. handle success, timeout, and deadletter paths

Start with explicit timeout values. The samples use `2s` for tiny local sample
flows
and `5s` in web scenarios. Production values should come from service SLOs and
failure budgets, not from the sample defaults.

## Queue And Failure Policy

The samples use:

```text
queueCapacity = 128
strictNoDrop = true
```

That is a conservative integration default. It makes queue pressure visible
instead of hiding it behind unbounded memory growth or silent drops.

For production:

- size queues from expected burst size and memory budget
- expose queue/deadletter counters in service diagnostics
- treat deadletters as first-class errors
- avoid retry loops that can amplify pressure

## Shutdown

Close the orchestrator during process shutdown. In long-running services, wire
this into the host framework lifecycle:

- JVM/Spring: bean lifecycle or `AutoCloseable`
- Python: context manager or application shutdown hook
- Node.js: `SIGINT`/`SIGTERM` handlers
- Go: `defer orchestrator.Close()` plus signal handling for servers

## Language Recipes

Start from the language closest to your service:

- [JVM runtime samples](../runtime/jvm/README.md)
- [Python runtime samples](../runtime/python/README.md)
- [Node.js runtime samples](../runtime/node/README.md)
- [Go runtime samples](../runtime/go/README.md)

Then inspect the customer scenarios for cross-process wiring:

```sh
bash run.sh scenarios check
```

Artifact-backed runtime runs can use the public publish checkout. Use
`COAKKA_PUBLISH_ROOT`, and for JVM lanes `COAKKA_PUBLISH_MAVEN_LOCAL`, to point
at a local public artifact checkout. For public non-Maven packages, the sample
resolver verifies artifact SHA256 against `artifacts/public-artifacts.tsv` from
the publish surface before the package is unpacked or installed.
