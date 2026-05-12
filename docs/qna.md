# QNA

This note captures recurring questions about the CoAkka runtime story.
It is intentionally incomplete and should grow as users ask sharper questions.

## Is CoAkka Equivalent To gRPC?

No.

`gRPC` is a framework for calling a remote service over a network API.
`CoAkka` is a runtime boundary for internal capabilities.

Short answer:

```text
gRPC calls a service across a real network boundary.
CoAkka delivers work through a runtime boundary with target routing, bounded
queueing, deadletters, diagnostics, and a local-first path.
```

Use gRPC when the boundary is already a real service API:

- separate service ownership
- network hop is intentional
- generated clients are part of the contract
- HTTP/2, interceptors, auth, and standard gRPC tooling are desired

Use CoAkka when the team wants a stronger boundary than a direct function call,
but the work is still an internal capability:

- named target or capability
- local handler today, possible remote handler later
- route generation and route hot reload
- queue pressure and explicit rejection
- deadletter reasons instead of mystery timeouts
- runtime health and delivery diagnostics

Do not say:

```text
CoAkka replaces gRPC.
```

Say:

```text
CoAkka avoids turning local internal capabilities into fake network APIs too
early. gRPC still belongs at real remote API boundaries.
```

## Is CoAkka A gRPC Add-On?

Not directly.

CoAkka can live next to gRPC, behind gRPC, or without gRPC.
It should not be described as a plugin for gRPC.

Common shapes:

```text
No gRPC:
  HTTP/CLI/job -> CoAkka runtime -> local capability handler

gRPC at the ingress edge:
  gRPC endpoint -> CoAkka runtime -> internal capability handler

Existing service-to-service gRPC:
  keep gRPC where the service API is already the correct boundary
```

The distinction is ownership:

- gRPC owns service API call mechanics.
- CoAkka owns internal runtime delivery semantics.

## What Does CoAkka Add Compared With No Runtime Boundary?

Without CoAkka, a system usually chooses one of these paths.

Direct call:

```text
controller -> service_a -> service_b
```

This is cheap and correct for a clean monolith.
The limitation is that the boundary is mostly code ownership.
It does not naturally model route generations, queue pressure, deadletters, or
a later local-to-remote migration.

Internal REST/gRPC:

```text
service_a -> HTTP/gRPC client -> local or remote endpoint -> service_b
```

This creates a stronger boundary, but it pays network API costs:

- endpoint and URL/channel configuration
- port and readiness setup
- timeout and status mapping
- test server setup
- duplicated retry and correlation vocabulary
- possible local network hop before the boundary is actually remote

Message broker:

```text
service_a -> broker -> service_b
```

This is useful when the system needs broker semantics.
It also adds broker operations, topic/queue policy, lag, ordering, replay, and
poison-message handling.

CoAkka local capability:

```text
ingress -> CoAkka runtime -> target -> handler
```

This keeps the work local by default while making the runtime boundary explicit:

- target identity
- payload contract
- bounded queues
- route policy and generation
- route miss and drained endpoint behavior
- deadletter reasons
- health and stats
- future remote or polyglot handoff without making the first version a fake
  network service

## What Are The Tradeoffs?

Use CoAkka when runtime-boundary semantics are worth the extra layer.

Benefits:

- stronger boundary than direct dependency injection
- less network plumbing than fake internal REST/gRPC
- explicit route, queue, timeout, and deadletter vocabulary
- local-first design with a later remote path
- connector-owned control plane and runtime-owned data plane
- shared native runtime ABI for multiple host-language connectors

Costs:

- additional runtime lifecycle to configure and observe
- route snapshots and handler registration need discipline
- teams must learn target, envelope, route generation, stats, and deadletter
  vocabulary
- it is too much for simple CRUD paths that only need direct service calls
- it does not replace platform concerns such as service discovery, TLS policy,
  authorization, metrics export, or business retry policy

Short filter:

```text
If direct service calls are enough, do not add CoAkka.
If the team is already inventing internal REST/gRPC, queues, route maps,
timeouts, and failure vocabulary, CoAkka may be the smaller boundary.
```

## Is The CoAkka Boundary A Business Boundary Or Technical Boundary?

CoAkka is a technical/runtime boundary that should follow a business capability
boundary.

Business design decides what the capability is:

```text
order.reserve
payment.authorize
invoice.issue
document.render_pdf
notification.dispatch
tenant.rebuild_index
```

CoAkka decides how work is delivered to that capability:

- target routing
- queue admission
- local or remote endpoint selection
- timeout and terminal outcome handling
- deadletter and diagnostics

Do not use CoAkka to split tiny technical helper calls:

```text
json.parse
map_dto_to_entity
format_currency
calculate_tax_line
```

Those should usually stay normal function or module calls.

Short answer:

```text
Business boundaries are designed by the domain and team.
CoAkka gives selected business capability boundaries a runtime contract.
```

## Is CoAkka Equivalent To CQRS?

No.

`CQRS` is an application architecture pattern that separates command/write
models from query/read models.

`CoAkka` is a runtime delivery layer.

Short answer:

```text
CQRS classifies intent as command or query.
CoAkka delivers intent to a target handler and explains delivery outcomes.
```

CQRS asks:

- is this a command or a query?
- which model owns writes?
- which model serves reads?
- what consistency does the read model provide?

CoAkka asks:

- which target should receive this work?
- is the route available in the active generation?
- can the runtime admit the work without violating queue policy?
- did delivery produce a response or a deadletter?
- what should operators see in stats and diagnostics?

They can be used together, but they are not the same pattern.

## Can CoAkka Implement CQRS?

Yes, CoAkka can be used as the runtime dispatch layer for CQRS.

Example:

```text
HTTP/gRPC/CLI
  -> CoAkka runtime
  -> target: "order.command.create"
  -> OrderCommandHandler
  -> write model

HTTP/gRPC/CLI
  -> CoAkka runtime
  -> target: "order.query.get"
  -> OrderQueryHandler
  -> read model
```

CoAkka can help with:

- command and query dispatch by target
- local versus remote handler placement
- bounded command/query lanes
- route miss and queue rejection as explicit outcomes
- runtime stats for pressure and delivery failures

CoAkka does not define:

- aggregate invariants
- command validation rules
- read model shape
- transaction boundaries
- consistency guarantees
- query authorization

Those remain application or framework concerns above the runtime.

## Can CoAkka Implement Event Sourcing?

Yes, CoAkka can carry commands, committed events, projection work, and replay
jobs.
It is still not a complete Event Sourcing framework by itself.

Possible shape:

```text
command
  -> CoAkka target: "order.command.reserve"
  -> aggregate command handler
  -> append events to event store
  -> CoAkka target: "projection.order_summary"
  -> projection handler
  -> update read model
```

CoAkka can help with:

- delivering committed events to projectors
- routing projection work by tenant, stream, or aggregate key
- isolating projection queues
- deadlettering malformed or repeatedly failing projection work
- exposing runtime pressure and delivery diagnostics
- moving a projector from local to remote later

CoAkka does not provide by itself:

- event store
- stream version checks
- optimistic concurrency control
- aggregate snapshotting
- event upcasters
- projection checkpointing
- exactly-once guarantees
- replay policy
- event schema governance

Short answer:

```text
CoAkka is a good runtime substrate for CQRS/Event Sourcing, but CQRS/Event
Sourcing still need their own application contracts and persistence model.
```

## What Is The Clean Public Position?

Use this when someone compares CoAkka to gRPC, CQRS, actors, or brokers:

```text
CoAkka is a local-first runtime boundary for internal capabilities. It names
targets, routes work through a generationed runtime snapshot, applies bounded
queue policy, and returns response or deadletter outcomes with diagnostics.

It can carry RPC-style requests, CQRS commands, CQRS queries, events, or
projection work. Those are payload and application patterns. CoAkka's core
claim is the runtime boundary, not ownership of every pattern above it.
```

