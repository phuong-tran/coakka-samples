# Questions And Answers

This note captures recurring questions about the CoAkka runtime story and is
updated as users ask sharper questions.

## Most Common Questions

Start here before reading the full index:

| Question | Short answer |
| --- | --- |
| [What Does CoAkka Add Compared With No Runtime Boundary?](#what-does-coakka-add-compared-with-no-runtime-boundary) | A shared target, route snapshot, bounded admission, reply/deadletter, and diagnostics vocabulary across process and language boundaries. |
| [Is CoAkka Equivalent To gRPC?](#is-coakka-equivalent-to-grpc) | No. gRPC is right for real service APIs; CoAkka avoids promoting app-owned internal capabilities into L7 APIs too early. |
| [Do Spring Users Still Need @FeignClient?](#do-spring-users-still-need-feignclient) | Yes for real HTTP services; no for app-owned runtime handoffs that only became HTTP to gain an address. |
| [Is CoAkka The Same Thing As Erlang, Akka, Elixir, Or The Actor Model?](#is-coakka-the-same-thing-as-erlang-akka-elixir-or-the-actor-model) | No. It borrows messaging vocabulary but does not require actor identity, actor lifecycle, or actor-first app modeling. |
| [Is CoAkka Equivalent To Kafka Or RabbitMQ?](#is-coakka-equivalent-to-kafka-or-rabbitmq) | No. Durable topics, replay, consumer groups, and broker-owned backpressure still belong to brokers. |
| [Can CoAkka Replace A Service Mesh Such As Istio?](#can-coakka-replace-a-service-mesh-such-as-istio) | No. It can remove synthetic internal service hops; real mesh/network policy remains a platform concern. |
| [Does CoAkka Support TLS, mTLS, And Multiple Connection Strategies?](#does-coakka-support-tls-mtls-and-multiple-connection-strategies) | Yes. Full runtime connectors expose capability-gated transport security and connection-strategy configuration. |
| [What Are Runtime Addons?](#what-are-runtime-addons) | Optional, independently released capabilities that compose with Runtime without entering core or the default package. |
| [When Is CoAkka Worth Adding?](#when-is-coakka-worth-adding) | When stable runtime targets and honest delivery evidence are worth the added boundary. |

## Table Of Contents

Positioning:

- [Is CoAkka Architecturally Distinct?](#is-coakka-architecturally-distinct)
- [What Does CoAkka Add Compared With No Runtime Boundary?](#what-does-coakka-add-compared-with-no-runtime-boundary)
- [When Is CoAkka Worth Adding?](#when-is-coakka-worth-adding)
- [Is Adding A Runtime Overkill If The Current System Works?](#is-adding-a-runtime-overkill-if-the-current-system-works)
- [Is The CoAkka Boundary A Business Boundary Or Technical Boundary?](#is-the-coakka-boundary-a-business-boundary-or-technical-boundary)
- [Does CoAkka Have A Dashboard?](#does-coakka-have-a-dashboard)
- [How Do Users Test Runtime Targets Compared With curl Or Swagger?](#how-do-users-test-runtime-targets-compared-with-curl-or-swagger)
- [Does CoAkka Handle Auth Or Authorization?](#does-coakka-handle-auth-or-authorization)
- [What Are Runtime Addons?](#what-are-runtime-addons)
- [How Does Observability And Trace Context Work?](#how-does-observability-and-trace-context-work)

L7, mesh, and platform boundaries:

- [Is CoAkka Equivalent To gRPC?](#is-coakka-equivalent-to-grpc)
- [Is CoAkka A gRPC Add-On?](#is-coakka-a-grpc-add-on)
- [Do Spring Users Still Need @FeignClient?](#do-spring-users-still-need-feignclient)
- [Why A Transport-Backed Runtime Boundary Instead Of Another L7 Application API?](#why-a-transport-backed-runtime-boundary-instead-of-another-l7-application-api)
- [Can CoAkka Replace A Service Mesh Such As Istio?](#can-coakka-replace-a-service-mesh-such-as-istio)
- [Why Do Teams Reach For Istio In The First Place, And How Can CoAkka Change That?](#why-do-teams-reach-for-istio-in-the-first-place-and-how-can-coakka-change-that)

Runtime and application patterns:

- [How Should Spring Boot And Quarkus Users Think About CoAkka?](#how-should-spring-boot-and-quarkus-users-think-about-coakka)
- [If Many Services Call Each Other, Will CoAkka Maintain Too Many Sockets?](#if-many-services-call-each-other-will-coakka-maintain-too-many-sockets)
- [How Does CoAkka Balance Load Across Handlers Or Service Instances?](#how-does-coakka-balance-load-across-handlers-or-service-instances)
- [Does A CoAkka Spec Need To List Every Service Instance?](#does-a-coakka-spec-need-to-list-every-service-instance)
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

Advanced topology and infra ownership:

- [What Happens If Runtime Participants See Different Route Generations?](#what-happens-if-runtime-participants-see-different-route-generations)
- [Where Does The Runtime Endpoint Come From?](#where-does-the-runtime-endpoint-come-from)
- [Does CoAkka Support TLS, mTLS, And Multiple Connection Strategies?](#does-coakka-support-tls-mtls-and-multiple-connection-strategies)

Logger and explanation:

- [Does CoAkka Replace Log4j, SLF4J, OpenTelemetry, Or Observability Tools?](#does-coakka-replace-log4j-slf4j-opentelemetry-or-observability-tools)
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
| Spring Boot/Quarkus | Use the framework adapters to keep HTTP at the app edge and route selected app-owned work as runtime targets. | The work is just ordinary in-process code or a real external HTTP service API. |
| HTTP/API gateway | CoAkka sits behind or beside the app-host edge. | The caller is external, public, or needs product API semantics. |
| Dapr | CoAkka is narrower: target routing, bounded delivery, replies, deadletters, and diagnostics. | The team wants a broad distributed application runtime with state, pub/sub, bindings, secrets, and workflow. |
| Akka/Erlang/Elixir | CoAkka shares some messaging vocabulary but is not actor-first. | The team wants actors, supervision trees, actor identity, and actor lifecycle as the application model. |
| CQRS/Event Sourcing | CoAkka can carry commands, queries, events, projections, and replay work. | The question is command validation, aggregate invariants, event store, consistency, or read-model design. |
| Temporal/workflow engines | CoAkka can deliver work into workflow steps or handlers. | The system needs durable workflow history, timers, retries, compensation, or human-in-the-loop orchestration. |
| Kafka/RabbitMQ | CoAkka can reduce broker use for app-owned request/reply handoffs. | The system needs durable topics, consumer groups, replay, fanout, or broker-owned backpressure semantics. |
| Outbox | CoAkka can route dispatcher or projection work after the app commits. | The question is transactional durability between a database commit and asynchronous publication. |
| Auth/authz | CoAkka carries already-submitted runtime work and reports delivery outcomes. | The question is identity proof, tenant policy, permission checks, or business authorization. |
| Runtime addons | Add one focused optional capability without growing runtime core or the default package. | The workflow is application-specific or no verified public addon coordinate exists. |
| Dashboard | `coakka-runtime-inspect` is a runtime explorer, not an admin dashboard or observability platform. | The team needs fleet dashboards, alerting, long-term metrics, tracing, or tenant operations. |
| curl/Swagger | Use `coakka-client` or inspect route-try for runtime targets. | The boundary is an HTTP API with paths, methods, status codes, and OpenAPI docs. |
| Observability/OTel | CoAkka exposes runtime delivery facts that app-hosts can correlate. | The question is trace collection, span export, dashboards, sampling, or organization-wide telemetry policy. |
| Many service calls | CoAkka routes logical envelopes to targets; socket mechanics stay in transport/runtime policy. | The system truly needs a public service API or a service-mesh governed network boundary. |
| Load balancing | CoAkka can choose among equivalent handlers by target-aware route policy and pressure, not only by connection shape. | The boundary is an HTTP upstream pool, where Nginx/gateway load balancing is already the right tool. |
| Service instances | App-facing specs declare process identity and capability ownership; route snapshots declare eligible endpoints. | The caller truly owns a fixed peer list and endpoint names are part of the business contract. |
| Saga | CoAkka can reduce Saga pressure when the split was premature. | The flow truly crosses independent owners, stores, commits, or long-running compensation semantics. |
| Istio/service mesh | CoAkka can remove synthetic internal service hops. | The system has real network-service boundaries needing zero-trust mTLS, traffic governance, or cross-cluster policy. |
| Runtime endpoint | Use the stable endpoint provided by platform config, the same way apps use database, cache, or internal service endpoints. | The deployment has custom topology, direct endpoint expansion, or identity policy that must be modeled above runtime. |
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
  built-in runtime TLS/mTLS or a platform-owned secure boundary, selected by
  deployment policy
```

The app-host owns request parsing, authentication, authorization, validation,
CQRS/app policy, and the decision to submit work. CoAkka starts after that
decision and owns runtime delivery: target routing, route generation, bounded
admission, timeout, reply, deadletter, health, and diagnostics.

## How Should Spring Boot And Quarkus Users Think About CoAkka?

Treat CoAkka as a runtime capability boundary inside the application host, not
as a replacement for Spring Boot or Quarkus.

Spring Boot and Quarkus should still own the familiar framework concerns:
HTTP resources/controllers, dependency injection, configuration, lifecycle,
validation, security, transactions, and the public response shape. CoAkka starts
after the app has decided that work should be submitted to a runtime target.

The useful split is:

```text
Spring Boot or Quarkus edge
  -> app policy and request mapping
  -> CoAkka target
  -> local or peer runtime handler
  -> reply or deadletter
```

That lets teams remove internal HTTP handoff endpoints that exist primarily to
give app-owned capability code an address, without giving up the framework that
already hosts the application. A handler can start as same-process work and
later move behind another runtime route while the caller keeps the same target
vocabulary.

Read [CoAkka Spring Boot](coakka-spring-boot.md) and
[CoAkka Quarkus](coakka-quarkus.md) for framework-specific onboarding.

## If Many Services Call Each Other, Will CoAkka Maintain Too Many Sockets?

This is a good performance question because sockets do have real cost:
connection resources, buffers, session state, wakeups, scheduling, and failure
handling.

The important distinction is that CoAkka does not make a socket the application
contract.

Application code talks in terms of:

```text
caller -> target -> envelope -> reply, timeout, or deadletter
```

Transport/runtime code owns:

```text
peer connection -> transport policy -> bounded delivery
```

That means a CoAkka target, capability, or logical call should not be read as
"one dedicated socket." The current application contract is target and
envelope delivery, not socket ownership. Exact connection reuse, pooling, idle
eviction, connection caps, and multiplexing are transport-profile behavior and
release-specific implementation details.

Routing is also policy, not the socket contract. In the current common
Kubernetes Service DNS shape, CoAkka may see one endpoint and Kubernetes
distributes to pods. If CoAkka is given expanded endpoints, route policy can
select among those endpoints. Pressure-aware selection, locality, shard key,
affinity, and similar policies should be treated as advanced or
transport-dependent unless a specific release documents them as available.

Overload is normal in real systems. The goal is not to pretend it will never
happen, and it is also not to reject or deadletter immediately when the first
burst appears. The goal is bounded admission: absorb the short burst, report
pressure honestly, and fail clearly only when delivery would violate the
runtime contract.

That is the same operating discipline people already accept at the HTTP edge
with Nginx or a gateway. Nginx does not make upstream capacity infinite. It
keeps connection limits, request buffers, upstream timeouts, queues, and clear
failure responses at the edge boundary. CoAkka applies the same idea one layer
lower, at the runtime target boundary: bounded queues, visible pressure,
timeouts, rejection, and deadletter evidence.

| Concern | Nginx / gateway boundary | CoAkka runtime boundary |
| --- | --- | --- |
| Main unit | HTTP request and upstream | Runtime envelope and target |
| Capacity shape | Connection/request limits, buffers, upstream timeouts | Bounded queues, admission policy, timeout budget |
| Load shape | Upstream pool | Eligible handlers or peer runtime endpoints |
| Overload signal | HTTP failure response or timeout | Rejection, timeout, pressure, or deadletter evidence |
| What should not leak | Upstream internals into product API semantics | Socket/peer mechanics into application target semantics |

The correct response to overload is not to hide the problem by opening sockets
without limit or retrying invisibly at an infrastructure layer. CoAkka should
make overload attributable. Then the owner can choose the right fix:

- increase a queue only when the workload is a bounded burst
- add handler or service instances when throughput is genuinely too low
- shard a target when ownership can be partitioned
- apply backpressure or rate limits when callers submit too fast
- revisit service boundaries if the graph has become accidental full mesh

A queue is a buffer, not a replacement for capacity. If the system needs more
capacity, add capacity. If the system needs a larger short burst window, tune
the queue. If the graph is too tangled, fix the ownership boundary.

The short answer:

```text
CoAkka routes envelopes, not sockets.
Socket count is a transport/runtime concern.
Overload is expected; it should be bounded, visible, and attributable.
```

## How Does CoAkka Balance Load Across Handlers Or Service Instances?

CoAkka should not be described as an HTTP load balancer.

Nginx is a useful comparison because it shows the right discipline at the
right boundary. Nginx balances HTTP requests across upstream servers at an
edge or service API boundary. It owns HTTP connection handling, upstream pools,
request buffering, timeouts, and failure responses for that boundary.

CoAkka applies the same kind of discipline at a different boundary. It routes
runtime envelopes to named targets. If several handlers or service instances
can serve the same target, the runtime or connector can choose a route policy
for that target.

In the common Kubernetes Service DNS shape, CoAkka may see only one route
endpoint:

```text
billing -> billing.default.svc.cluster.local:19301
```

In that case CoAkka is not doing pod-level round-robin, because the route table
has one endpoint for `billing`. Kubernetes distributes traffic to backing pods
through its Service, EndpointSlice, kube-proxy, CNI, session-affinity, and
topology policy. Use expanded endpoints only when CoAkka should see each pod or
runtime participant directly.

| Question | Nginx / gateway answer | CoAkka answer |
| --- | --- | --- |
| What is balanced? | HTTP requests | Runtime envelopes |
| What is selected? | An upstream server | A handler or peer endpoint for a target |
| What is the stable caller vocabulary? | URL, method, headers, status shape | Target, payload identity, reply/deadletter shape |
| What can influence selection? | Upstream health, weights, connection/request state | Service DNS by default; documented route policy when CoAkka sees multiple endpoints |
| Where does evidence belong? | Access logs, upstream status, gateway metrics | Route snapshot, handler acceptance, pressure, reply/timeout/deadletter |

Round-robin, weighted routing, pressure-aware routing, affinity, and shard-key
routing are expanded-endpoint concerns. Treat each one as current only when the
active connector/runtime release documents and exposes that policy. If the
route uses one Kubernetes Service DNS endpoint, prefer the boring path: CoAkka
routes to that endpoint and Kubernetes distributes to pods.

The contract is not:

```text
open one socket to every service and hope the network balances it
```

The contract is:

```text
target -> route snapshot -> selected handler or peer -> reply, timeout, or deadletter
```

That distinction matters. Nginx can say which upstream handled an HTTP
request. CoAkka should say which target was selected, which route snapshot was
used, whether the handler accepted the work, whether pressure affected the
decision, and whether the outcome was a reply, timeout, rejection, or
deadletter.

If a selected handler is overloaded, CoAkka should not keep sending work
blindly just because that endpoint is present in the route snapshot. It should
account for pressure and policy. If all available handlers are over capacity,
the honest result is bounded waiting, rejection, timeout, or deadletter
evidence, depending on the runtime contract.

The short answer:

```text
Nginx balances HTTP requests at the edge.
With Service DNS, CoAkka sees one endpoint and Kubernetes distributes to pods.
With expanded endpoints, CoAkka can select among runtime endpoints at the target boundary.
Round-robin and weighted routing are advanced policies only when the active release exposes them.
Bounded admission, pressure, and delivery evidence are the contract.
```

For endpoint selection, route generation, failover attempts, and cluster-style
route snapshots, read [Runtime Cluster Routing](runtime-cluster-routing.md).

## Does A CoAkka Spec Need To List Every Service Instance?

No. An app-facing CoAkka spec should not force the caller to know every service
replica such as `billing-a`, `billing-b`, or `billing-c`.

The caller should use a stable runtime target:

```text
billing.charge.create
billing.invoice.issue
```

The app or connector spec describes this process and the capabilities it owns
or can handle. It is about identity, handlers, queue policy, and the runtime
boundary this process participates in.

The topology belongs somewhere else: route snapshots, deployment config,
platform discovery, or a control-plane feed. In the common Kubernetes Service
DNS shape, that layer can be simple:

```text
target = billing.charge.create
endpoint = billing.default.svc.cluster.local:19301
generation = 1
```

CoAkka sees one route endpoint. Kubernetes owns how that service maps to the
current pod IP list.

When CoAkka needs direct endpoint visibility, the same topology layer can
expand the route snapshot:

```text
target = billing.charge.create
eligible endpoints = billing-runtime-a, billing-runtime-b, billing-runtime-c
generation = 42
strategy = weighted_round_robin
```

Weighted round-robin only makes sense in the expanded shape, because CoAkka
must know the candidate endpoints before it can choose among them.

That distinction keeps the boundary clean:

- callers use target names, not replica names
- handlers register the capabilities they own or can handle
- route snapshots describe which endpoints are currently eligible
- deployment or control-plane code owns topology changes
- transport code owns connection mechanics

In local or static bootstrap mode, a start spec may carry an initial route
snapshot so the runtime can start without an external control plane. That is
still topology input, not the application contract. The important rule is that
business code should not have to name every replica just to call a capability.

For a concrete start-spec shape, read
[Runtime Integration Guide](runtime-integration-guide.md) and
[Runtime Message And Routing Model](runtime-message-and-routing-model.md). For
a deployment-shaped example that maps environment and platform config into
`RuntimeStartSpec`, read
[Containerized Runtime Notes](containerized-runtime.md).

If a caller must know `billing-a`, `billing-b`, and `billing-c`, the system has
slipped back into caller-owned topology. CoAkka avoids that by keeping the
stable app vocabulary at the target boundary and the mutable deployment
vocabulary in route snapshots.

The short answer:

```text
App specs declare process identity and capability ownership.
Route snapshots declare topology.
Callers use targets, not replica names.
Kubernetes Service DNS can keep generation and endpoint lists simple by default.
```

## What Happens If Runtime Participants See Different Route Generations?

This is an advanced routing question, not a normal first-run concern.

Most systems should not hit this often.

For the common Kubernetes shape, a route can stay at `generation = 1` for a
long time:

```text
billing -> billing.default.svc.cluster.local:19301
```

Scaling pods behind that Service DNS name usually changes Kubernetes
Service/EndpointSlice state, not the CoAkka route snapshot. In that default
shape, backend teams should not think about route generations every day.

Generation skew matters only when the CoAkka route snapshot itself changes:
new target, new service endpoint, expanded pod endpoints, endpoint flags,
strategy change, hot reload, or rollback.

When that happens during rollout, skew should be visible, not surprising.

`generation` is a version on a route snapshot. It protects a runtime process
from stale route updates and gives diagnostics a concrete route version to
report. It is not Raft, leader election, or a promise that every process in a
deployment changes routes at the same instant.

Each process enforces a strict local rule:

| Situation | Expected behavior |
| --- | --- |
| incoming generation is older than active | reject stale snapshot |
| incoming generation is newer but invalid | reject snapshot and keep active routes |
| incoming generation is newer and valid | atomically replace local route table |
| rollback is needed | publish a newer valid generation |

Across processes, temporary skew is normal:

```text
checkout active generation = 43
billing active generation = 42
```

The important contract is evidence. Route decisions, timeouts, rejections, and
deadletters should report the target and generation involved. In-flight work
keeps the generation used when it was routed; a newer generation affects new
route decisions, not work already selected under the old route snapshot.

If a caller on generation 43 reaches a peer still on generation 42, the runtime
should not guess or silently downgrade semantics. The result must be explicit:
reply, rejection, timeout, or deadletter with route evidence.

If a new service endpoint has never registered a handler for that target, it
cannot successfully handle the work. That should appear as route/handler
evidence, not as an ambiguous success or hidden fallback.

If a system requires atomic cutover across all participants, that belongs above
core runtime: deployment policy, endpoint draining, a single route publisher,
or a control plane. CoAkka core runtime should make skew diagnosable; it should
not pretend distributed rollout is globally atomic.

The short answer:

```text
Generation protects route state from stale updates and makes skew diagnosable.
It does not make distributed rollout globally atomic.
```

For the deeper routing rules, read
[Runtime Cluster Routing](runtime-cluster-routing.md).

## Does CoAkka Have A Dashboard?

Not in the sense of an admin dashboard or observability platform.

CoAkka has two public runtime tooling surfaces:

- `coakka-client`: terminal-first runtime client for diagnostics, `call`,
  `ask`, scripted checks, and explicit reply/timeout/deadletter outcomes
- `coakka-runtime-inspect`: browser runtime explorer and route-try UI for
  runtime identity, route catalog, endpoint topology, health, pressure, recent
  events, and copied `coakka-client` commands

Those tools make runtime facts visible. They are not the source of truth for
topology, not a schema registry, not a service discovery server, not an mTLS
control plane, and not a replacement for an organization's telemetry stack.

Use existing observability tools for dashboards, alerting, traces, metrics,
retention, fleet operations, and incident workflows. Use CoAkka tooling to
inspect or drive the runtime boundary itself.

For the tool docs, read [CoAkka Runtime Client](coakka-runtime-client.md) and
[CoAkka Runtime Inspect](coakka-runtime-inspect.md).

## How Do Users Test Runtime Targets Compared With curl Or Swagger?

Use the right tool for the boundary.

Use `curl`, Postman, Swagger, or OpenAPI tooling when the boundary is an HTTP
API:

```text
HTTP method + path + status code + API schema
```

Use `coakka-client` or `coakka-runtime-inspect` when the boundary is a CoAkka
runtime target:

```text
target + payload identity + route snapshot + reply, timeout, or deadletter
```

`coakka-client` is the closest runtime equivalent to a terminal probe: it can
drive `call` / `ask`, run `doctor`, print version diagnostics, and execute a
scripted command batch. `coakka-runtime-inspect` gives a browser route-try form
for the same runtime shape and can copy an equivalent `coakka-client` command.

This is why CoAkka does not need to turn every runtime target into an HTTP
endpoint just to make it testable. HTTP APIs remain testable through HTTP
tools. Runtime targets become testable through runtime tools.

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
a runtime capability boundary, with stable targets, explicit routing, route
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

The call-site no longer needs an internal HTTP API or a Feign client just to
reach application-owned work. CoAkka owns target routing, active route
generation, bounded admission, timeout, reply matching, deadletters, and
diagnostics. The Spring app still owns business validation, user policy,
transaction boundaries, and the public HTTP response.

Keep `@FeignClient` when the target is truly an HTTP service API with separate
ownership or platform policy. Prefer CoAkka when the target is an application
capability that should be named, routed, observed, and moved without turning it
into another backend HTTP service.

## Why A Transport-Backed Runtime Boundary Instead Of Another L7 Application API?

CoAkka deliberately keeps the delivery path below the application API layer
instead of becoming another L7 API framework. The point is to move
application-owned work by target, route snapshot, bounded queues, delivery
outcome, and reply matching, not to compete with HTTP/gRPC on endpoints,
methods, status codes, interceptors, service docs, gateways, or API edge
tooling.

HTTP and gRPC stay the right L7 choices for public or service API edges. CoAkka
belongs behind or beside those edges when the work is better modeled as a
runtime capability than another network API.

Benchmark CoAkka against runtime delivery responsibilities: route selection,
queue pressure, framing, reply matching, timeout, and deadletter behavior. Do
not describe it as faster or slower than HTTP/gRPC unless the benchmark
intentionally includes comparable L7 concerns.

A transport-backed runtime boundary can have mechanical advantages for this
class of work: fewer bytes to parse, no method/path/status mapping, no L7
interceptor chain, and bounded admission at the runtime intake. That can show
up as lower overhead and more predictable behavior under queue pressure. Keep
that as supporting evidence, not the main product claim.

A fair benchmark needs a same-class comparator. Bun vs Node is a reasonable
kind of comparison because both are JavaScript runtimes competing for many of
the same jobs. CoAkka vs HTTP/gRPC is not that shape: HTTP/gRPC is an L7 API
boundary, while CoAkka Runtime is a transport-backed capability-delivery
boundary. If there is no comparable runtime system with target routing,
bounded admission,
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
capabilities, with stable targets, identified or connector-typed payloads,
route snapshots, bounded delivery, replies, deadletters, and runtime
diagnostics.

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
- stable target instead of another internal URL surface
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

## What Are Runtime Addons?

Runtime addons are optional, independently released capabilities that compose
with CoAkka Runtime through stable public contracts. They keep external
protocol mechanics, dependencies, credentials, and workflow policy out of
runtime core and out of the default runtime package.

One addon should own one coherent capability or protocol family. For example,
the SFTP artifact publisher acquires and verifies an artifact, then uses the
existing File Lane to distribute it. SFTP does not become part of File Lane,
and unrelated FTP, FTPS, or object-storage protocols do not belong in the same
addon merely because they also move files.

Addon versions are independent from Runtime versions. Compatibility comes from
the addon manifest: runtime ABI major, minimum native runtime version, required
features, platform evidence, exports, and checksums. A source directory or
package template is not an installable release.

Current status:

```text
runtime-addons release family: defined
SFTP artifact publisher: public native 1.1.0+42841ae2
published platforms: Linux ARM64/x86-64, macOS ARM64, Windows 11 ARM64/x86-64
native SFTP-to-File-Lane sample: immutable archive consumer
connector package changes: none
```

Read [Runtime Addons](runtime-addons.md) before selecting or generating addon
integration code.

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

Do not make `coakka-runtime-core` the system's policy authority just because it
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

## Does CoAkka Replace Log4j, SLF4J, OpenTelemetry, Or Observability Tools?

No.

CoAkka Runtime reports runtime delivery evidence. It does not own business
logs or fleet observability.

Use existing app loggers such as SLF4J, log4j, logback, Python logging, Go
`slog` or `zap`, .NET `ILogger`, Android logging, or the team's platform logger
for business meaning, app lifecycle, audit context, and operator messages.

Use CoAkka Logger when logging behavior itself needs a bounded, cross-language,
pressure-aware contract. Use OpenTelemetry, Prometheus, dashboards, log
shipping, and vendor APM where the team already exports and operates
observability.

The practical split is:

```text
runtime evidence -> delivery facts
business log     -> domain meaning
observability    -> export, dashboards, alerts, traces, retention
```

Logging is work. It can allocate, format, serialize, block, flush, and cross
the network. CoAkka can report pressure honestly, but it cannot make unbounded
synchronous logging free.

Read [Runtime Logging And Observability](runtime-logging-observability.md) for
the full boundary.

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
network API boundary." That is the situation where the protocol is real, but
the service boundary is thinner than the operational cost it creates.

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

## Where Does The Runtime Endpoint Come From?

From normal platform or application configuration.

That is the same operating shape teams already use for Postgres, MySQL, Redis,
or ordinary internal services:

```text
POSTGRES_HOST=postgres.default.svc.cluster.local
MYSQL_HOST=mysql.default.svc.cluster.local
REDIS_HOST=redis.default.svc.cluster.local
BILLING_RUNTIME_ENDPOINT=billing.default.svc.cluster.local:19301
```

The application receives a stable endpoint from the deployment layer. It does
not need to inspect every backing replica to use that endpoint.

For Kubernetes, the common shape is a Service DNS endpoint:

```text
billing.default.svc.cluster.local:19301
```

The app or connector maps that endpoint into a CoAkka route snapshot:

```text
target = billing.charge.create
endpoint = billing.default.svc.cluster.local:19301
generation = 1
```

Kubernetes owns the familiar infrastructure behavior behind that endpoint:

- Service DNS
- readiness
- pod churn handling
- EndpointSlice membership
- pod-level distribution

The daily developer model stays familiar:

```text
declare HTTP route -> controller
declare CoAkka target -> handler
```

CoAkka consumes the configured route and keeps runtime delivery explicit:

```text
target -> route snapshot -> handler -> reply, timeout, rejection, or deadletter
```

Topology becomes an advanced concern only when the team is not using a
platform-provided stable endpoint, or when CoAkka intentionally needs direct
endpoint visibility for LAN nodes, on-prem servers, edge devices, custom
fleets, affinity, locality, or pressure-aware route policy.

Short answer:

```text
Use the stable endpoint the deployment layer already provides.

CoAkka consumes that route and keeps runtime delivery explicit.

Do not make business code inspect replicas just to call a capability.
```

## Does CoAkka Support TLS, mTLS, And Multiple Connection Strategies?

Yes. Use [Runtime TLS And mTLS](tls-and-mtls.md) and
[Runtime Connection Strategies](connection-strategies.md) as the canonical
guides for connector configuration, effective capabilities, lifecycle rules,
and examples.

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
