# QNA

This note captures recurring questions about the CoAkka runtime story.
It is intentionally incomplete and should grow as users ask sharper questions.

## Is CoAkka Architecturally Distinct?

Yes, in a narrow and specific way.

CoAkka addresses a problem that is both old and newly intensified. The old part
is familiar: application-owned work gets pushed through ad-hoc endpoints,
clients, retries, timeout handling, and status mapping. The newer part is that
modern systems are increasingly polyglot, gradually migrated, and fragmented
across runtimes, processes, and deployment shapes.

CoAkka's architectural response is not to replace HTTP, gRPC, or messaging
systems. Those still belong at real service API, public edge, and broker
boundaries. CoAkka is narrower: it treats selected application-owned work as
a runtime capability boundary, with typed targets, explicit routing, route
ownership, delivery outcomes, and shared diagnostics.

That is the distinct part. Application-owned work is not modeled as just another backend
API surface before it needs to be one. It can start as a same-process runtime
target, move to a peer runtime later, and keep the same target, payload, route,
and deadletter vocabulary across that change.

## Is CoAkka Equivalent To gRPC?

No.

`gRPC` is a framework for calling a remote service over a network API.
`CoAkka` is a runtime boundary for capabilities that are owned by an app or
deployment contract.

The capability is
not being exposed as a public product/service API. The handler may live in the
same process, another process, or another host depending on the active route
snapshot.

Short answer:

```text
gRPC calls a service across a real network API boundary.
CoAkka delivers work to a named target through a runtime boundary; that target
can resolve to a same-process handler, a peer runtime, or a deadletter.
```

Use gRPC when the boundary is already a real service API:

- separate service ownership
- network hop is intentional
- generated clients are part of the contract
- HTTP/2, interceptors, auth, and standard gRPC tooling are desired

Use CoAkka when the team wants a stronger boundary than a direct function call,
but the work is still an runtime capability:

- named target or capability
- same-process handler today, possible peer-runtime handler later
- startup route generation, with hot reload available when needed
- queue pressure and explicit rejection
- deadletter reasons instead of mystery timeouts
- runtime health and delivery diagnostics

Do not say:

```text
CoAkka replaces gRPC.
```

Say:

```text
CoAkka avoids turning runtime capabilities into separate network APIs before
they need to be. Those capabilities may still run same-process or cross-process
through runtime routes. gRPC still belongs at real remote API boundaries.
```

## Is CoAkka A gRPC Add-On?

Not directly.

CoAkka can live next to gRPC, behind gRPC, or without gRPC.
It should not be described as a plugin for gRPC.

Common shapes:

```text
No gRPC:
  HTTP/CLI/job -> CoAkka runtime -> same-process handler

gRPC at the ingress edge:
  gRPC endpoint -> CoAkka runtime -> runtime capability handler

Existing service-to-service gRPC:
  keep gRPC where the service API is already the correct boundary
```

The distinction is ownership:

- gRPC owns service API call mechanics.
- CoAkka owns runtime delivery semantics.

## What Does CoAkka Add Compared With No Runtime Boundary?

Without CoAkka, a system usually chooses one of these paths.

Direct call:

```text
controller -> service_a -> service_b
```

This is cheap and correct for a clean monolith.
The limitation is that the boundary is mostly code ownership.
It does not naturally model route generations, queue pressure, deadletters, or
a later same-process-to-peer-runtime migration.

Backend HTTP/gRPC:

```text
service_a -> HTTP/gRPC client -> same-host or remote endpoint -> service_b
```

This creates a stronger boundary, but the boundary contract is usually spread
across HTTP/gRPC concepts:

- endpoint and URL/channel configuration
- port and readiness setup
- timeout and status mapping
- test server setup
- duplicated retry and correlation vocabulary
- possible same-host network hop before the boundary is actually remote

Message broker:

```text
service_a -> broker -> service_b
```

This is useful when the system needs broker semantics.
It also adds broker operations, topic/queue policy, lag, ordering, replay, and
poison-message handling.

CoAkka runtime capability:

```text
ingress/nginx -> app-host/controller -> CoAkka runtime -> target handler
```

This keeps the first version compact while making the runtime boundary explicit:

- target identity
- payload contract
- bounded queues
- route policy and generation
- route miss and drained endpoint behavior
- deadletter reasons
- health and stats
- future peer-runtime or polyglot handoff without making the first version a
  network service before it needs to be one

## When Is CoAkka Worth Adding?

Use CoAkka when explicit runtime-boundary semantics are useful enough to make
them a shared part of the application shape.

CoAkka is useful when the team wants:

- stronger boundary than direct dependency injection
- less network plumbing tha backend HTTP/gRPC created only for application-owned work
- explicit route, queue, timeout, and deadletter vocabulary
- same-process-first design with a later peer-runtime path
- connector-owned startup config and runtime-owned data plane
- shared native runtime ABI for multiple host-language connectors

The important point is not that CoAkka replaces the layers in front of it. In a
normal deployment, the request path still looks like this:

```text
external client
  -> ingress/nginx
  -> app-host/controller/resource
  -> connector
  -> CoAkka runtime
  -> target handler
```

That means ingress/nginx still owns external traffic policy and edge routing.
The app-host still owns request parsing, authentication, authorization,
validation, CQRS/app policy, and the decision to submit work into runtime.

CoAkka starts after the app-host or connector has decided there is work to
deliver. From that point, CoAkka gives the system a centralized runtime
vocabulary for target identity, route snapshots, bounded queues, route
generation, timeouts, deadletters, health, and stats.

A team should be clear about these responsibilities. They exist with or without
CoAkka; CoAkka gives them one runtime vocabulary instead of leaving them spread
across clients, framework config, and logs:

- app-hosts still choose when to submit work
- connectors still feed runtime config and register handlers
- route snapshots and handler registration need the same ownership discipline
  that backend HTTP/gRPC clients need for URLs, schemas, retries, and rollout
- operators still need to observe runtime lifecycle, stats, and deadletters
- simple CRUD paths that only need direct service calls can stay direct

Short filter:

```text
If direct service calls are enough, do not add CoAkka.
If the team is already inventing backend HTTP/gRPC, queues, route maps,
timeouts, and failure vocabulary, CoAkka may be the smaller boundary.
```

## Is Adding A Runtime Overkill If The Current System Works?

It can be. If a path is small, stable, single-language, easy to trace, and direct
service calls already answer the operational questions, keep it direct.

CoAkka is not an all-or-nothing migration. The intended rollout shape is to wrap
one boundary first:

- one noisy runtime integration
- one polyglot handoff
- one workflow that needs clearer delivery diagnostics
- one route where deadletter reasons would be more useful than vague timeout or
  status-code handling

The rest of the system can keep using its existing HTTP APIs, framework
controllers, service calls, jobs, and deployment model. CoAkka is worth expanding
only when that first boundary makes ownership, routing, failure, and diagnostics
clearer.

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
- same-process or peer-runtime endpoint selection
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

## Does CoAkka Replace CQRS Or App-Level Policy?

No.

CoAkka runtime is not a magic application framework. It is a passive delivery
boundary: when the app-host or connector submits work, runtime routes it,
queues it, delivers it, and reports response or deadletter outcomes.

Runtime does not create business intent by itself. The entrypoint usually lives
above runtime:

```text
HTTP controller
CLI command
scheduled job
desktop action
message consumer
outbox dispatcher
device event
```

That app-host layer decides whether there is work to submit.

Common shape:

```text
HTTP request
  -> controller/resource
  -> CQRS/security/app policy
  -> connector or proxy
  -> CoAkka runtime
  -> target handler
```

CQRS and app policy answer questions such as:

- is this a command or a query?
- is the command valid?
- is `serviceA` allowed to call `serviceB`?
- is this caller allowed for this tenant or operation?
- which read model or write model owns this flow?

CoAkka runtime answers different questions:

- which target should receive the work?
- which endpoint is eligible in the active route generation?
- can runtime admit the work into bounded queues?
- did delivery produce a response, timeout, or deadletter?
- what should diagnostics report?

If a system wants to block a call before it is sent, that check belongs above
runtime:

```text
app-host / CQRS bus / connector filter / framework adapter
  -> allow: submit to runtime
  -> deny: return app/security error
```

The receiver should still protect important business rules. If authorization
only happens inside the receiver, then a forbidden call still reaches the
receiver and returns a forbidden response, just like a traditional HTTP/gRPC
call.

Do not say:

```text
CoAkka replaces CQRS.
CoAkka runtime handles business authorization.
```

Say:

```text
CQRS and app policy decide what is valid and allowed.
CoAkka runtime delivers already-submitted work by target and reports delivery
outcomes.
```

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
- same-process versus peer-runtime handler placement
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
- moving a projector from same-process to peer-runtime later

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

## Can CoAkka Replace A Service Mesh Such As Istio?

Yes, in the class of systems where `Istio` mostly exists to manage internal
HTTP/gRPC boundaries that did not need to become full network services in the
first place.

No, where the system truly has independent network services that need mesh
policy for security, traffic control, and rollout.

`Istio` and similar service meshes solve network-service problems:

- service-to-service mTLS
- traffic policy
- retries and timeout policy
- canary or weighted rollout
- ingress and egress control
- cross-service telemetry

If a system truly has many independent network services, then a service mesh
can still be the right tool.

Where CoAkka changes the picture is earlier in the design. Many teams create
internal HTTP or gRPC boundaries before they actually need a real service API
boundary. Those calls look like services on paper, but functionally they are
often application-owned work pushed through network seams too early.

In that narrower case, CoAkka can reduce the need for a service mesh by
removing some of the synthetic service edges entirely. If the work stays as a
runtime capability boundary instead of being promoted into a network service
too early, there are fewer internal service hops to secure, retry, trace, and
shape with mesh policy.

Short answer:

```text
CoAkka can absolutely remove the need for a service mesh in parts of a system
that only became "mesh-shaped" because app-owned work was turned into internal
HTTP/gRPC services too early.

It does not remove the need for a service mesh where the boundaries are real
network-service boundaries.
```

## Why Do Teams Reach For Istio In The First Place, And How Can CoAkka Change That?

Teams usually reach for a service mesh because the system already contains many
network-facing backend-to-backend calls, and those calls need uniform
operational control.

Common reasons are:

- every internal capability became an HTTP or gRPC service
- each call now needs timeout, retry, and observability policy
- platform teams need mTLS and traffic governance everywhere
- deployment rollout needs canary or traffic splitting between service versions

That is a coherent response if the boundaries are real services.

The weaker case is when a large share of those calls are "HTTP because we split
the codebase that way," not "HTTP because this capability truly needs a stable
network API boundary." That is the situation people often describe as "fake
HTTP": the protocol is real, but the service boundary is thinner than the
operational cost it creates.

CoAkka changes that tradeoff by letting application-owned capability boundaries
stay as runtime targets first. The work can remain same-process today, move to
a peer runtime later, and keep one target and route contract without forcing an
early public-service shape.

That can remove a large share of the reasons Istio shows up in the middle of a
system:

- fewer internal network hops
- fewer per-hop retry and timeout stacks
- less duplicated service client policy
- less need to attach mesh behavior to boundaries that are still app-owned
- less pressure to govern internal "service" traffic that only exists because
  the architecture externalized app-owned work too early

It does not remove reasons for Istio at real service boundaries:

- zero-trust or organization-wide mTLS policy
- ingress and egress control
- cross-cluster traffic governance
- multi-team independently deployed services
- external-facing API estates

## Can CoAkka Replace Saga?

Yes, in flows where `Saga` mostly exists to coordinate work that was split
across service boundaries earlier than the business actually required.

No, where the business flow truly spans independent owners, stores, and
commit points.

`Saga` exists to coordinate work that spans multiple independently committed
boundaries. If one business flow touches several services, stores, or owners
that cannot share one local transaction, Saga-style choreography or
orchestration may still be the right answer.

CoAkka can reduce the need for Saga in a narrower but important class of
systems: cases where the flow was split into distributed service steps before
the business actually required that much distribution.

If several steps still belong to one application-owned capability boundary,
keeping them inside one runtime or one deployment can avoid creating a
distributed consistency problem too early. In those cases the system may not
need compensating workflows across several services, because the work never had
to be fragmented into several independently committed hops to begin with.

Short answer:

```text
CoAkka can absolutely make Saga unnecessary for flows that only became
distributed because the system externalized app-owned work too early.

It does not remove Saga where the flow really crosses independent business and
transaction boundaries.
```

## Why Do Teams Need Saga In The First Place, And How Can CoAkka Change That?

Teams reach for Saga when one business action cannot commit atomically in one
place anymore.

Typical reasons are:

- several services own different stores
- each service commits independently
- the flow is long-running
- failure requires compensation instead of rollback
- one team cannot simply wrap the whole path in one local transaction

Saga is appropriate when those conditions are real.

But some systems create that condition themselves by decomposing one
application-owned flow into many service calls and event hops very early. Once
that split happens, distributed coordination becomes unavoidable, and Saga
looks mandatory because the architecture already forced the problem into the
open.

CoAkka can change that by delaying unnecessary fragmentation. If the work can
stay within one runtime-owned capability path, the system may be able to use a
simpler consistency model:

- one local transaction boundary where it fits
- one outbox boundary where async propagation is truly needed
- one explicit runtime route instead of several faux-service hops
- ordinary failure handling instead of multi-step compensation logic
- business coordination only where the business truly became distributed, not
  where the architecture distributed it by habit

Saga still remains the honest tool when the flow truly crosses:

- separate service owners
- separate databases with independent commits
- long-running business steps
- real compensation semantics
- externally observable intermediate states

## What Is The Clean Public Position?

Use this when someone compares CoAkka to gRPC, CQRS, actors, or brokers:

```text
CoAkka is a runtime boundary for runtime capabilities, with a compact
same-process path and a peer-runtime path through route snapshots. It names
targets, routes work through a generationed runtime snapshot, applies bounded
queue policy, and returns response or deadletter outcomes with diagnostics.

Internal does not mean same-process-only. It means the first boundary is a
runtime target boundary instead of a public HTTP/gRPC API boundary. The handler
can be in the same process, same host, or another runtime participant reached
through transport. The `LOCAL` endpoint flag is the narrower "handler owned by
this process" case.

It can carry RPC-style requests, CQRS commands, CQRS queries, events, or
projection work. Those are payload and application patterns. CoAkka's core
claim is the runtime boundary, not ownership of every pattern above it.
```
