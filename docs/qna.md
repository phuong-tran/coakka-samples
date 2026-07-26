# QNA

This note captures recurring questions about the CoAkka runtime story and is
updated as users ask sharper questions.

## Table Of Contents

Positioning:

- [Is CoAkka Architecturally Distinct?](#is-coakka-architecturally-distinct)
- [What Does CoAkka Add Compared With No Runtime Boundary?](#what-does-coakka-add-compared-with-no-runtime-boundary)
- [When Is CoAkka Worth Adding?](#when-is-coakka-worth-adding)
- [Is Adding A Runtime Overkill If The Current System Works?](#is-adding-a-runtime-overkill-if-the-current-system-works)
- [Is The CoAkka Boundary A Business Boundary Or Technical Boundary?](#is-the-coakka-boundary-a-business-boundary-or-technical-boundary)
- [Does CoAkka Handle Auth Or Authorization?](#does-coakka-handle-auth-or-authorization)
- [How Does Observability And Trace Context Work?](#how-does-observability-and-trace-context-work)

L7, mesh, and platform boundaries:

- [Is CoAkka Equivalent To gRPC?](#is-coakka-equivalent-to-grpc)
- [Is CoAkka A gRPC Add-On?](#is-coakka-a-grpc-add-on)
- [Do Spring Users Still Need @FeignClient?](#do-spring-users-still-need-feignclient)
- [Why L4 Rather Than L7?](#why-l4-rather-than-l7)
- [Can CoAkka Replace A Service Mesh Such As Istio?](#can-coakka-replace-a-service-mesh-such-as-istio)
- [Why Do Teams Reach For Istio In The First Place, And How Can CoAkka Change That?](#why-do-teams-reach-for-istio-in-the-first-place-and-how-can-coakka-change-that)
- [Why Does CoAkka Not Include Service Discovery Or mTLS In coakka-core-runtime?](#why-does-coakka-not-include-service-discovery-or-mtls-in-coakka-core-runtime)
- [Why Is Demanding mTLS Inside Core A Layer Violation?](#why-is-demanding-mtls-inside-core-a-layer-violation)
- [If A Team Needs Discovery Or mTLS, Where Should That Live?](#if-a-team-needs-discovery-or-mtls-where-should-that-live)

Runtime and application patterns:

- [Is CoAkka Equivalent To Dapr?](#is-coakka-equivalent-to-dapr)
- [Is CoAkka The Same Thing As Erlang, Akka, Elixir, Or The Actor Model?](#is-coakka-the-same-thing-as-erlang-akka-elixir-or-the-actor-model)
- [Is CoAkka Equivalent To CQRS?](#is-coakka-equivalent-to-cqrs)
- [Does CoAkka Replace CQRS Or App-Level Policy?](#does-coakka-replace-cqrs-or-app-level-policy)
- [Can CoAkka Implement CQRS?](#can-coakka-implement-cqrs)
- [Can CoAkka Implement Event Sourcing?](#can-coakka-implement-event-sourcing)
- [Is CoAkka Equivalent To Temporal Or A Workflow Engine?](#is-coakka-equivalent-to-temporal-or-a-workflow-engine)
- [Is CoAkka Equivalent To Kafka Or RabbitMQ?](#is-coakka-equivalent-to-kafka-or-rabbitmq)
- [How Does CoAkka Relate To The Outbox Pattern?](#how-does-coakka-relate-to-the-outbox-pattern)
- [Can CoAkka Replace Saga?](#can-coakka-replace-saga)
- [Why Do Teams Need Saga In The First Place, And How Can CoAkka Change That?](#why-do-teams-need-saga-in-the-first-place-and-how-can-coakka-change-that)

Logger and explanation:

- [Why Does CoAkka Also Have A Logger Surface?](#why-does-coakka-also-have-a-logger-surface)
- [When Is The CoAkka Logger Useful?](#when-is-the-coakka-logger-useful)
- [How Does The Logger Help With Trace, Debug, And Runtime Work?](#how-does-the-logger-help-with-trace-debug-and-runtime-work)
- [Does The Logger Replace Existing Logging Frameworks?](#does-the-logger-replace-existing-logging-frameworks)
- [Is Runtime Plus Logger A Good Combination?](#is-runtime-plus-logger-a-good-combination)
- [How Should A User Explain CoAkka In One Short Paragraph?](#how-should-a-user-explain-coakka-in-one-short-paragraph)

## Quick Decision Table

| Question | CoAkka answer | Use the other thing when |
| --- | --- | --- |
| Direct calls | Use CoAkka only when a runtime boundary is worth making explicit. | The path is small, stable, single-language, and easy to trace directly. |
| gRPC | CoAkka is not gRPC; it avoids promoting app-owned capabilities into L7 APIs too early. | The boundary is a real service API with generated clients, HTTP/2 semantics, and service ownership. |
| Spring `@FeignClient` | CoAkka can remove Feign from app-owned runtime handoffs that only became HTTP to gain an address. | The target is a real HTTP service API with independent ownership, URL/discovery policy, and HTTP client semantics. |
| HTTP/API gateway | CoAkka sits behind or beside the app-host edge. | The caller is external, public, or needs product API semantics. |
| Dapr | CoAkka is narrower: target routing, bounded delivery, replies, deadletters, and diagnostics. | The team wants a broad distributed application runtime with state, pub/sub, bindings, secrets, and workflow. |
| Akka/Erlang/Elixir | CoAkka shares some messaging vocabulary but is not actor-first. | The team wants actors, supervision trees, actor identity, and actor lifecycle as the application model. |
| CQRS/Event Sourcing | CoAkka can carry commands, queries, events, projections, and replay work. | The question is command validation, aggregate invariants, event store, consistency, or read-model design. |
| Temporal/workflow engines | CoAkka can deliver work into workflow steps or handlers. | The system needs durable workflow history, timers, retries, compensation, or human-in-the-loop orchestration. |
| Kafka/RabbitMQ | CoAkka can reduce broker use for app-owned request/reply handoffs. | The system needs durable topics, consumer groups, replay, fanout, or broker-owned backpressure semantics. |
| Outbox | CoAkka can route dispatcher or projection work after the app commits. | The question is transactional durability between a database commit and asynchronous publication. |
| Auth/authz | CoAkka carries already-submitted runtime work and reports delivery outcomes. | The question is identity proof, tenant policy, permission checks, or business authorization. |
| Observability/OTel | CoAkka exposes runtime delivery facts that app-hosts can correlate. | The question is trace collection, span export, dashboards, sampling, or organization-wide telemetry policy. |
| Saga | CoAkka can reduce Saga pressure when the split was premature. | The flow truly crosses independent owners, stores, commits, or long-running compensation semantics. |
| Istio/service mesh | CoAkka can remove synthetic internal service hops. | The system has real network-service boundaries needing zero-trust mTLS, traffic governance, or cross-cluster policy. |
| Discovery/mTLS | Keep it above runtime: app-host, connector addon, platform, or mesh. | The network boundary and identity policy are real platform concerns. |
| Logger | CoAkka logger gives bounded, cross-language operational evidence. | A normal framework logger is already honest under pressure and enough for the app. |

## Runtime Boundary Shape

The normal public path keeps L7 and business policy above the runtime:

```text
external client
  -> ingress/nginx or API gateway (TLS/mTLS, edge policy)
  -> app-host/controller/resource (auth, CQRS, validation)
  -> connector
  -> CoAkka runtime (target, route, bounded admission, reply/deadletter)
  -> target handler

optional real network hop:
  connector addon or transport profile with TLS/mTLS where that boundary is real
```

The app-host owns request parsing, authentication, authorization, validation,
CQRS/app policy, and the decision to submit work. CoAkka starts after that
decision and owns runtime delivery: target routing, route generation, bounded
admission, timeout, reply, deadletter, health, and diagnostics.

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

The hard part is the boundary mindset, not winning a protocol benchmark. CoAkka
asks teams to stop turning app-owned handoffs into extra L7 APIs by default and
to name that work as runtime targets when the ownership fits.

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
but the work is still a runtime capability:

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

## Do Spring Users Still Need @FeignClient?

Sometimes yes, but not for the boundary CoAkka is meant to remove.

Spring Cloud OpenFeign's `@FeignClient` is useful when a Spring application is
calling a real HTTP service API. In that shape, the HTTP boundary is the
contract: service name or URL, request path, request/response DTOs, status-code
mapping, headers, interceptors, timeouts, retries, auth propagation, load
balancing, and observability all belong to the service-to-service call.

The problem starts when `@FeignClient` is used only because an application
capability was split into another module, process, or language host and needed
an address. The team then creates a backend HTTP endpoint that is not a product
API and not a true service boundary. Feign makes that endpoint convenient to
call, but it also preserves the L7 shape:

```text
Spring controller
  -> @FeignClient
  -> backend HTTP endpoint
  -> application-owned work
```

That can spread one internal handoff across URL strings, controller methods,
HTTP status mapping, client interfaces, retry policy, timeout policy, and test
fixtures before the business handler runs.

With CoAkka, the Spring edge can stay Spring and HTTP where it belongs. After
authentication, authorization, validation, and request mapping, the app submits
work to a runtime target:

```text
Spring controller
  -> CoAkka target "billing.invoice.create"
  -> local or peer runtime handler
  -> reply or deadletter
```

The call-site no longer needs a fake backend HTTP API or a Feign client just to
reach application-owned work. CoAkka owns target routing, active route
generation, bounded admission, timeout, reply matching, deadletters, and
diagnostics. The Spring app still owns business validation, user policy,
transaction boundaries, and the public HTTP response.

Keep `@FeignClient` when the target is truly an HTTP service API with separate
ownership or platform policy. Prefer CoAkka when the target is an application
capability that should be named, routed, observed, and moved without turning it
into another backend HTTP service.

## Why L4 Rather Than L7?

CoAkka deliberately keeps the runtime delivery path closer to an L4/runtime
transport boundary instead of becoming another L7 API framework. The point is
to move application-owned work by target, route snapshot, bounded queues,
delivery outcome, and reply matching, not to compete with HTTP/gRPC on
endpoints, methods, status codes, interceptors, service docs, gateways, or API
edge tooling.

HTTP and gRPC stay the right L7 choices for public or service API edges. CoAkka
belongs behind or beside those edges when the work is better modeled as a
runtime capability than another network API.

Benchmark CoAkka against runtime delivery responsibilities: route selection,
queue pressure, framing, reply matching, timeout, and deadletter behavior. Do
not describe it as faster or slower than HTTP/gRPC unless the benchmark
intentionally includes comparable L7 concerns.

L4/runtime transport has a mechanical advantage for this class of work: fewer
bytes to parse, no method/path/status mapping, no L7 interceptor chain, and
bounded admission at the runtime intake. That can show up as lower overhead and
more predictable behavior under queue pressure. Keep that as supporting
evidence, not the main product claim.

A fair benchmark needs a same-class comparator. Bun vs Node is a reasonable
kind of comparison because both are JavaScript runtimes competing for many of
the same jobs. CoAkka vs HTTP/gRPC is not that shape: HTTP/gRPC is an L7 API
boundary, while CoAkka Runtime is a capability-delivery boundary. If there is
no comparable runtime system with target routing, bounded admission,
reply/deadletter matching, and route ownership, the honest comparison is
against CoAkka's own releases and deployment profiles.

Even when CoAkka has practical runtime advantages, benchmark numbers should
stay supporting evidence. The main claim is cleaner application boundary
ownership: HTTP/gRPC for real API edges, CoAkka Runtime for app-owned capability
delivery behind or beside those edges.

## Is CoAkka Equivalent To Dapr?

Yes, if the comparison is narrow: both can sit between application code and
remote work, reduce repeated plumbing, and give application teams a more
structured runtime path than ad-hoc client code.

No, when the comparison is broad and architectural.

`Dapr` is a distributed application runtime with building blocks around
service invocation, pub/sub, bindings, state stores, secrets, configuration,
and workflow.

`CoAkka` is much narrower. It is a runtime boundary for application-owned
capabilities, with typed targets, route snapshots, bounded delivery, replies,
deadletters, and runtime diagnostics.

Short answer:

```text
Dapr gives applications a broad distributed-runtime toolbox.
CoAkka gives application-owned capability delivery one explicit runtime
contract before that work has to become a larger distributed platform story.
```

The overlap is easy to see:

- both can sit between application code and remote work
- both can reduce repeated plumbing in app code
- both try to keep operational mechanics out of business handlers

The difference is where they want the center of gravity to be:

- Dapr is broader and more infrastructure-shaped
- CoAkka is narrower and more runtime-boundary-shaped

Use Dapr when the team wants a broader distributed application substrate:

- state store abstraction
- pub/sub abstraction
- bindings and component model
- secrets/config building blocks
- workflow and broader sidecar/runtime patterns

Use CoAkka when the problem is narrower:

- one application-owned capability boundary
- typed target instead of another internal URL surface
- same-process today, peer runtime later
- explicit route miss, rejection, reply, and deadletter outcomes
- less interest in adopting a broader sidecar/component runtime

If the question is specifically about how CoAkka thinks about route ownership
and peer-runtime delivery instead of a broader sidecar platform, read
[Runtime Cluster Routing](runtime-cluster-routing.md).

Do not say:

```text
CoAkka is a Dapr clone.
```

Say:

```text
CoAkka overlaps with one part of the problem space Dapr also touches, but it
is deliberately narrower. It is not trying to become a general distributed
application platform.
```

## Is CoAkka The Same Thing As Erlang, Akka, Elixir, Or The Actor Model?

Yes, if the comparison stays at the level of message delivery, named
destinations, and asynchronous boundaries.

No, when the comparison is about the primary application model.

The names are close enough that people will assume a match, especially with
`Akka`, but the architectural center is different.

`Erlang`, `Elixir`, and `Akka` are strongly associated with the actor model:

- actor identity
- mailbox ownership
- supervision trees
- actor lifecycle
- actor-to-actor messaging as the native application model

CoAkka does not ask teams to model the system as actors first.

Short answer:

```text
Actor systems usually make actors the primary unit of design.
CoAkka makes runtime capability boundaries the primary unit of design.
```

That means CoAkka is usually closer to:

- target or capability ownership
- route snapshots
- bounded delivery and rejection
- reply or deadletter outcomes
- polyglot connector boundaries

than it is to:

- actor mailboxes as the default application model
- supervision hierarchies as the center of failure handling
- actor identity as the main way to think about business structure

Why people confuse CoAkka with Akka:

- both talk about runtime delivery instead of plain method calls
- both involve named destinations and message-shaped work
- both care about failure outcomes and asynchronous boundaries

Why they are still different:

- Akka is actor-first
- CoAkka is a runtime capability boundary
- Akka-style systems often want the application model to lean into actors
- CoAkka does not require the application model to become actor-oriented
- CoAkka is designed to stay comfortable for app-hosts and CRUD-oriented teams
  that want a stronger runtime boundary without rebuilding the system as an
  actor system

Do not say:

```text
CoAkka is Akka rewritten.
```

Say:

```text
CoAkka shares some messaging vocabulary with actor systems, but it is not an
actor-first runtime model. It is a runtime boundary for application-owned
capabilities, especially where teams want route ownership, explicit delivery
outcomes, and polyglot runtime paths without adopting an actor-first
application model.
```

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
- less network plumbing than backend HTTP/gRPC created only for application-owned work
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

No, when a path is small, stable, single-language, easy to trace, and direct
service calls already answer the operational questions. In that case, keep it
direct.

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

## Does CoAkka Handle Auth Or Authorization?

Not as the default authority.

Authentication and authorization belong above runtime unless a specific
connector, adapter, or deployment profile explicitly owns that policy.

Typical ownership:

- ingress, gateway, or app-host proves caller identity
- controller, CQRS bus, framework adapter, or connector filter checks tenant
  and operation policy
- receiver still protects important business rules
- CoAkka runtime delivers work that has already been accepted for submission

Runtime can carry useful context such as source, target, headers, correlation
IDs, route generation, and delivery outcome. That context helps audit and
diagnostics, but it is not the same thing as deciding who is allowed to call a
target.

Short answer:

```text
Auth/authz decide whether work is allowed to enter the runtime path.
CoAkka runtime decides whether accepted work can be routed, admitted,
delivered, replied to, timed out, or deadlettered.
```

Do not make `coakka-core-runtime` the system's policy authority just because it
sees messages. That would mix application security rules with the delivery
engine and make the core harder to reason about.

## How Does Observability And Trace Context Work?

CoAkka should make runtime delivery observable without becoming the
organization's telemetry platform.

The app-host or connector should create or propagate request context before
submitting work. Runtime can then report delivery facts around that context:

- source and target
- route generation
- accepted, rejected, timeout, reply, or deadletter outcome
- queue pressure and endpoint state
- message or correlation identifiers when available

If the deployment uses OpenTelemetry, the bridge belongs in the app-host,
connector, framework adapter, logger integration, or observability addon. The
core runtime should expose stable delivery facts; it should not own span export,
sampling policy, collector configuration, dashboards, or vendor-specific
telemetry behavior.

Short answer:

```text
Trace context enters above runtime and can be carried through runtime metadata.
CoAkka contributes target, route, queue, reply, timeout, and deadletter facts.
Telemetry export belongs to the host, connector, logger, or observability
adapter.
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

## Is CoAkka Equivalent To Temporal Or A Workflow Engine?

No.

Temporal and similar workflow engines own durable workflow execution:

- persisted workflow history
- timers and sleeps
- activity retries
- long-running orchestration
- compensation or recovery policy
- worker task queues

CoAkka owns runtime delivery to application targets. It can be useful inside or
beside a workflow system when a workflow step needs to call a runtime
capability, but it does not replace durable workflow history or orchestration
semantics.

Use Temporal or another workflow engine when the central problem is durable
workflow state, long timers, human-in-the-loop orchestration, or replayable
execution history.

Use CoAkka when the central problem is delivery of accepted work to a named
runtime target with bounded admission, route ownership, reply, timeout, and
deadletter outcomes.

## Is CoAkka Equivalent To Kafka Or RabbitMQ?

No.

Kafka and RabbitMQ are brokers. They are the right tools when the system needs
broker-owned durability or messaging semantics:

- durable topics or queues
- consumer groups
- replay or retention
- fanout
- broker-level ordering and backpressure policy
- independent producer and consumer lifecycles

CoAkka is a runtime delivery boundary for application-owned capabilities. It
can reduce the need to introduce a broker for simple internal request/reply
handoffs, but it should not be described as a replacement for broker semantics.

Short answer:

```text
Use Kafka or RabbitMQ when the broker is the right durability and distribution
boundary. Use CoAkka when the work is an app-owned runtime capability and the
team needs route, queue, reply, timeout, and deadletter semantics without
promoting the handoff into broker-owned messaging.
```

## How Does CoAkka Relate To The Outbox Pattern?

The outbox pattern solves a different problem: making a database commit and
asynchronous publication durable together.

CoAkka can sit after an outbox dispatcher or carry projection work, but it does
not replace the transactional outbox boundary.

Typical shape:

```text
app transaction
  -> write domain row and outbox row
  -> commit
  -> outbox dispatcher
  -> CoAkka target or broker topic
```

Use outbox when the key question is:

- did the business state commit?
- did the async message become durable with that commit?
- can the dispatcher resume safely after crash?

Use CoAkka when the key question is:

- which target should receive already-accepted work?
- can runtime admit it now?
- did it reply, time out, or deadletter?
- what route and queue diagnostics explain the outcome?

## Why Does CoAkka Also Have A Logger Surface?

Because logging is one of the easiest places for systems to become
operationally dishonest.

Many applications say they are using "async logging," but the important
questions stay blurry:

- where is the queue boundary?
- what happens when it is full?
- does the caller block?
- does the system drop records?
- can operators see emitted, delivered, and dropped counts?

The CoAkka logger exists to make those answers explicit. It gives applications
one bounded logging core with visible counters and the same cross-language
discipline that the runtime applies to capability delivery.

Short answer:

```text
The logger exists because logs are part of system behavior, not just developer
convenience. When logging is opaque under pressure, it can distort latency,
hide loss, and weaken incident analysis.
```

## When Is The CoAkka Logger Useful?

It is useful when the team wants logging behavior to stay predictable under
load, across languages, and during incident analysis.

Good fits include:

- runtime-heavy services where queue pressure and deadletters need matching log
  evidence
- polyglot deployments that do not want each language logger to drift into a
  different contract
- systems where blocking log sinks have already shown up in latency or
  throughput issues
- systems where "best effort async logging" has made it hard to explain lost or
  delayed records

It is less compelling when the application is small, single-language, lightly
loaded, and a normal framework logger is already sufficient.

## How Does The Logger Help With Trace, Debug, And Runtime Work?

The logger is most useful when it is treated as part of the same operational
story as the runtime.

The runtime can tell you:

- which target was called
- which route generation was active
- whether work was delivered, timed out, or deadlettered
- whether queue pressure or endpoint state affected delivery

The logger can add:

- correlated application/system records around the same event
- explicit evidence about whether log records were accepted, delivered, or
  dropped
- drain-friendly records for local debug or operator export
- one shared contract across multiple language hosts

Together they make failure analysis tighter:

```text
runtime outcome + bounded logger counters + correlated records
```

That is more useful than a system where runtime behavior is explicit but
logging becomes vague again under pressure.

## Does The Logger Replace Existing Logging Frameworks?

No, not as a blanket claim.

Yes, it can replace them in paths where the real need is a bounded, explicit,
cross-language logging contract rather than a large framework feature set.

No, when the application mainly needs framework-native appenders, existing sink
ecosystems, or local conventions that are already working well.

The useful distinction is the same one that appears elsewhere in CoAkka:

- if the current logger setup is simple and honest under pressure, keep it
- if logging behavior is now part of performance, runtime diagnostics, and
  polyglot operational consistency, the CoAkka logger becomes more compelling

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

That often leads to a simpler and more honest boundary shape:

- `nginx` or another ingress handles the public edge
- an API gateway or app-host handles auth, request policy, and public HTTP
- standard `TLS` stays at that real public boundary
- CoAkka handles selected internal capability delivery without forcing every
  internal handoff to become another sidecar-managed service hop

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
premature public-service shape.

That can remove a large share of the reasons Istio shows up in the middle of a
system:

- fewer internal network hops
- fewer per-hop retry and timeout stacks
- less duplicated service client policy
- less need to attach mesh behavior to boundaries that are still app-owned
- less pressure to govern internal "service" traffic that only exists because
  the architecture externalized app-owned work too early

In many product systems that already have a clean public HTTP edge, the
practical shape is often enough without Istio:

- `nginx`, API gateway, or edge proxy at the public boundary
- `TLS` at that public boundary
- CoAkka for selected internal capability delivery

That does not make Istio "wrong." It means the system may not need sidecars
and mesh policy for traffic that never needed to become a full internal
service API in the first place.

It does not remove reasons for Istio at real service boundaries:

- zero-trust or organization-wide mTLS policy
- ingress and egress control
- cross-cluster traffic governance
- multi-team independently deployed services
- external-facing API estates

## Why Does CoAkka Not Include Service Discovery Or mTLS In coakka-core-runtime?

It does not, and that is intentional.

`coakka-core-runtime` is a delivery engine. It is not meant to become a
discovery server, certificate authority, mesh control plane, or cluster
inventory system.

Service discovery and `mTLS` answer different questions from runtime delivery:

- discovery answers which hosts, endpoints, or replicas should exist
- `mTLS` answers which identities should be trusted and under what policy
- runtime delivery answers whether accepted work can be routed and handed to
  the next responsible hop

Those are not the same responsibility.

If CoAkka pulled discovery and `mTLS` into the core runtime, it would start
mixing:

- app-host lifecycle
- platform topology
- certificate and identity policy
- runtime delivery semantics

There is also a practical reason to keep `mTLS` out of the default internal
story. CoAkka usually carries internal application-owned work, not an
internet-facing product edge. In many deployments, demanding `mTLS`
everywhere on that path is more ceremony than value unless the boundary is
truly cross-team, zero-trust, cross-cluster, or compliance-driven.

That would make the runtime heavier, blur ownership, and turn a delivery
engine into partial infrastructure.

Short answer:

```text
CoAkka does not omit discovery and mTLS by accident.
It keeps them out of coakka-core-runtime on purpose, because they belong to
the app-host, platform, or an adapter layer above runtime.
```

## Why Is Demanding mTLS Inside Core A Layer Violation?

Because it applies service-mesh thinking to the runtime delivery engine.

Inside a normal app-owned path, the runtime is already behind the public edge
and behind app-host policy:

```text
public edge: nginx / gateway + TLS or mTLS
  -> app-host: auth, CQRS, tenant policy, validation
    -> CoAkka runtime: target, route generation, queue pressure, reply/deadletter
      -> target handler

optional real network hop:
  -> connector addon or transport profile with TLS/mTLS
```

Putting `mTLS` directly into `coakka-core-runtime` would force the delivery
engine to own certificate lifecycle, identity policy, and platform topology.
That mixes four layers that should stay separate:

- app-host lifecycle
- platform topology
- certificate and identity policy
- runtime delivery semantics

That does not improve the common same-deployment, same-team, app-owned handoff.
It makes the core larger, harder to test, harder to embed, and closer to a
partial service mesh.

Short answer:

```text
Use mTLS at ingress, gateways, sidecars, connector addons, or real transport
boundaries where identity policy is real. Do not put it in coakka-core-runtime
by default; core runtime should execute the route and delivery contract it was
given.
```

## If A Team Needs Discovery Or mTLS, Where Should That Live?

Above runtime, not inside the core delivery engine.

Typical ownership shapes are:

- app-host wiring
- connector configuration
- platform/infra policy
- connector addons or adapter layers

Examples:

- Kubernetes, DNS, Consul, or operator data chooses which endpoints should
  appear in a route snapshot
- app-host or connector code maps that topology into runtime config
- gateway, host TLS stack, sidecar when truly needed, or connector addon
  applies `TLS/mTLS` policy at the real network boundary

That also means a future HTTP/TLS-oriented connector addon is not the same
thing as moving `mTLS` into `coakka-core-runtime`.

- the addon may terminate or initiate HTTPS at an HTTP boundary it owns
- that still does not make the core runtime an identity-policy engine
- it also does not replace the separate runtime-to-runtime transport question
  such as the current private transport lane

That is the intended design:

```text
topology and identity policy stay above runtime
runtime executes the route and delivery contract it was given
```

If a future team wants discovery or `mTLS` support around CoAkka, the right
shape is usually:

- app-host integration
- connector addon
- deployment adapter

not a larger `coakka-core-runtime`.

In other words:

```text
Use mTLS where the network boundary is real and the policy is real.
Do not drag sidecars into internal application-owned delivery by default just
because the system already knows Istio.
```

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

## Is Runtime Plus Logger A Good Combination?

Yes.

The runtime gives the application a strict delivery vocabulary. The logger can
carry the same operational discipline into the evidence around that delivery.

That combination is especially useful when a team wants to follow:

- one request or command into a runtime target
- one route generation during a rollout or incident
- one deadletter or timeout back to the surrounding app behavior
- one pressure event across both work delivery and log delivery

That does not mean every application must adopt both at once. It means the two
surfaces were designed to fit together when the system wants one clearer story
for runtime behavior and operational evidence.

## How Should A User Explain CoAkka In One Short Paragraph?

Short version:

```text
CoAkka is a runtime boundary for application-owned capabilities. Instead of
turning every internal handoff into another HTTP or gRPC API, it lets an
application send work to a named target, route that work through a runtime
snapshot, and receive either a reply or a deadletter with diagnostics.

It works for same-process delivery and for peer runtime delivery across process
or host boundaries. The core idea is not "everything becomes an actor," "this
replaces CQRS," or "this replaces every broker." The core idea is that
selected application-owned work gets one explicit runtime contract instead of
being spread across ad-hoc internal APIs, client plumbing, and timeout/retry
guesswork.
```
