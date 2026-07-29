# CoAkka Runtime Field Guide

This guide is the bridge between a small sample and a production-shaped
deployment. Read it after [New To CoAkka](new-to-coakka.md) and one runnable
sample, before jumping into advanced cluster routing details.

The path is deliberately boring first:

```text
local target
  -> one app-host and one billing runtime
  -> Kubernetes Service DNS shape
  -> bounded queues and visible overload
  -> advanced expanded endpoints only when needed
```

It is not a benchmark, and it is not a replacement for connector reference
docs. It is a boundary guide: how to choose targets, where to put topology,
how to size the first queues, how to read pressure, and how to keep HTTP,
Nginx, mTLS, app-host policy, and CoAkka in their proper layers.

The examples are Kotlin-shaped because named arguments make the configuration
easy to read. The same concepts should map to every connector:

```text
process identity -> route snapshot -> bounded admission -> handler -> reply or deadletter
```

For the underlying vocabulary, read
[Runtime Message And Routing Model](runtime-message-and-routing-model.md),
[Runtime Integration Guide](runtime-integration-guide.md), and
[Runtime Cluster Routing](runtime-cluster-routing.md).

## Scenario

Use a checkout system with one internal billing capability:

```text
public request
  -> nginx or API gateway
    -> checkout app-host
      -> CoAkka target: billing.charge.create
        -> one or more billing runtime participants
```

The public HTTP edge is real. It owns external request policy, TLS/mTLS when
needed, request limits, access logs, and external response shape.

The app-host is real. It owns authentication context, tenant policy,
validation, idempotency policy, and the decision to submit internal work.

The CoAkka runtime boundary is also real. It owns target routing, route
generation, bounded queues, pressure, timeout, reply matching, rejection, and
deadletter evidence.

The goal is not to make every internal capability pretend to be a public HTTP
API. The goal is to keep the public edge public, keep app ownership in the
app, and make runtime delivery explicit.

## What This Guide Teaches

- start one local runtime participant
- register a target and return a reply
- move from one handler to two runtime participants
- call a stable target such as `billing`, not replicas such as `billing-a`
- start with the familiar Kubernetes Service DNS shape
- keep route snapshots as topology input, not business code
- use bounded queues and visible overload
- compare Nginx load balancing with CoAkka target routing
- decide when to tune queues versus adding capacity
- attach logger evidence without hiding pressure
- move round-robin, weights, endpoint expansion, and generation changes into
  the advanced section instead of making them first-run concepts

## Suggested Local Environment

Use boring local defaults first:

| Setting | Local value | Why |
| --- | --- | --- |
| Runtime endpoint port | `19301` for billing, `19302` for checkout | Stable and easy to inspect. |
| Queue capacity | `128` | Small enough to reveal pressure, large enough for normal bursts. |
| Strict no-drop | `true` | Integration should expose overload instead of hiding it. |
| Route generation | `1` | Start here; in the common Service DNS shape this can stay stable for a long time. |
| Ask timeout | `500ms` to `2s` | Short enough to catch broken routing during setup. |
| Payload identity | Domain-specific text such as `billing.charge.v1` | Lets logs and deadletters identify the payload shape. |

Do not treat these as production numbers. They are useful starting numbers for
learning the boundary. Real values come from latency budget, memory budget,
burst size, handler throughput, and incident evidence.

## Stage 1: Single Process Baseline

Start with one process and one local target.

```kotlin
import coakka.v2.connector.CoAkka
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val target = "billing.charge.create"

    CoAkka.local("checkout-dev").use { runtime ->
        runtime.handler(target) { request ->
            "charged:$request"
        }

        val reply = runtime.ask(target, "order-1001")
        println("billing.reply=$reply")
    }
}
```

Read this as:

```text
target -> local handler -> reply
```

This stage proves the core vocabulary. It does not prove distribution,
load balancing, or production topology.

## Stage 2: One App-Host, One Billing Runtime

Now split the app-host and the billing capability into two runtime
participants.

The checkout process owns the public app boundary:

```kotlin
val checkoutStartSpec = RuntimeStartSpec(
    systemName = "checkout-api",
    nodeId = "checkout-api-local-1",
    queueCapacity = 128,
    strictNoDrop = true,
    generation = 1,
    routes = listOf(
        RuntimeRouteSpec(
            target = "billing.charge.create",
            endpoints = listOf(
                RuntimeEndpointSpec(
                    host = "127.0.0.1",
                    port = 19301,
                    // NONE = PEER from checkout's view: eligible, but not local.
                    flags = RuntimeEndpointFlags.NONE,
                ),
            ),
        ),
    ),
)
```

The billing process owns the handler:

```kotlin
val billingStartSpec = RuntimeStartSpec(
    systemName = "billing",
    nodeId = "billing-local-1",
    queueCapacity = 128,
    strictNoDrop = true,
    generation = 1,
    routes = listOf(
        RuntimeRouteSpec(
            target = "billing.charge.create",
            endpoints = listOf(
                RuntimeEndpointSpec(
                    host = "127.0.0.1",
                    port = 19301,
                    flags = RuntimeEndpointFlags.LOCAL,
                ),
            ),
        ),
    ),
)
```

The important detail is not the localhost address. The important detail is
ownership:

```text
checkout-api knows the target.
billing owns the handler.
the route snapshot knows the endpoint.
transport owns connection mechanics.
```

The caller should not call `billing-local-1`. It should call the billing
capability target.

Endpoint flags describe this process's relationship to an endpoint:

| Flag | Meaning in a route snapshot | Use it when |
| --- | --- | --- |
| `RuntimeEndpointFlags.LOCAL` | This endpoint is owned by the current process. | The current process must register the handler for that target. |
| `RuntimeEndpointFlags.NONE` | Zero flag. Read it as `PEER` in caller-side route snapshots: a normal eligible endpoint from this process's view. | The endpoint can receive work, but this process does not own that handler locally. |
| `RuntimeEndpointFlags.UNAVAILABLE` | The endpoint remains visible but should not receive new work. | The endpoint is draining, unhealthy, or intentionally excluded from selection. |

So in the checkout route, `billing` uses `NONE` because checkout is not the
billing handler owner. Read it as `PEER` there. In the billing process, the
same target uses `LOCAL` because that process owns the handler.

Depending on the naming style of a product, that target can be short:

```text
billing
```

or more specific:

```text
billing.charge.create
```

The rule is the same: the caller names the capability, not the replica.

## Stage 3: Start With The Familiar Kubernetes Shape

Start from the shape most backend teams already know:

```text
business code calls billing
Kubernetes Service decides which pod receives the connection
DevOps/platform owns pod IP churn
```

CoAkka keeps that path available. The business caller still names the
capability:

```kotlin
runtime.ask(
    target = "billing",
    payload = chargeRequest,
)
```

The caller does not name replicas:

```kotlin
runtime.ask(target = "billing-a", payload = chargeRequest)
runtime.ask(target = "billing-b", payload = chargeRequest)
runtime.ask(target = "billing-c", payload = chargeRequest)
```

In the default Kubernetes shape, the connector or app-host maps `billing` to
one Service DNS endpoint:

```text
billing.default.svc.cluster.local:19301
```

or, inside the same namespace:

```text
billing:19301
```

The route snapshot can stay simple:

```kotlin
val billingRoute = RuntimeRouteSpec(
    target = "billing",
    endpoints = listOf(
        RuntimeEndpointSpec(
            host = "billing.default.svc.cluster.local",
            port = 19301,
            // NONE = PEER from checkout's view: eligible, but not local.
            flags = RuntimeEndpointFlags.NONE,
        ),
    ),
)
```

Read it as:

```text
business code -> target = billing
CoAkka route snapshot -> billing.default.svc.cluster.local:19301
Kubernetes -> current pod IPs behind that Service
```

In this shape, CoAkka sees one route endpoint. It does not do pod-level
round-robin because the route table has only one entry for `billing`.
Kubernetes distributes traffic to backing pods according to the cluster's
Service, EndpointSlice, kube-proxy, CNI, session-affinity, and topology policy.

That is the least surprising first deployment shape:

| Concern | Default owner |
| --- | --- |
| Business target | App code says `billing`. |
| Runtime route entry | Connector/app-host maps `billing` to one Service DNS endpoint. |
| Pod IP list | Kubernetes Service and EndpointSlice. |
| Pod-level distribution | Kubernetes networking policy. |
| Route generation | Usually `1` for a stable Service DNS route. |

With this shape, scaling `billing` from two pods to five pods does not
necessarily change the CoAkka route snapshot:

```text
CoAkka route snapshot:
  generation = 1
  billing -> billing.default.svc.cluster.local:19301

Kubernetes changes underneath:
  EndpointSlice: [pod-a, pod-b]
  EndpointSlice: [pod-a, pod-b, pod-c, pod-d, pod-e]
```

The route generation changes only when the CoAkka route snapshot changes, not
for every normal Kubernetes pod churn event.

Kubernetes is the common production mental model, not a CoAkka requirement.
CoAkka only needs route endpoints that the connector can hand to the runtime:

```text
target -> host:port
```

That endpoint can come from Kubernetes Service DNS, Docker Compose service
names, on-prem VM or bare-metal hostnames, static LAN addresses, an edge
gateway, or an IoT deployment registry. The same rule remains: start with the
environment's native service address, then expand endpoints only when CoAkka
should choose among them itself.

Advanced endpoint expansion, weighted policies, and generation changes are
covered later. Do not start there unless the default Service DNS shape is not
enough.

## Stage 4: Route Policy And Load Balancing

Nginx and CoAkka can both balance work, but they balance different units at
different boundaries.

| Question | Nginx / gateway | CoAkka |
| --- | --- | --- |
| What is balanced? | HTTP requests | Runtime envelopes |
| What is selected? | Upstream server | Handler or peer endpoint for a target |
| Stable caller vocabulary | URL, method, headers, status shape | Target, payload identity, reply/deadletter shape |
| Evidence | Access logs, upstream status, gateway metrics | Route generation, endpoint selection, pressure, reply/timeout/deadletter |
| Correct place | Public edge or service API boundary | Runtime target boundary |

With the Service DNS shape, CoAkka does not balance individual pods. It routes
the target to the single Service DNS endpoint, and Kubernetes decides how that
service reaches backing pods. With expanded endpoints, CoAkka sees multiple
runtime endpoints and can apply its own route policy.

If you are using the Service DNS shape with `generation = 1`, you can keep this
simple and let Kubernetes handle pod distribution. The route-policy details
belong in the advanced section. They matter only when CoAkka sees multiple
endpoints or must enforce runtime-level ownership.

The route contract should still read:

```text
target -> active route snapshot -> selected endpoint -> reply, timeout, rejection, or deadletter
```

## Advanced: Expanded Endpoints And Generations

Most teams should not start here. Use the familiar Service DNS shape first:

```text
target = billing
endpoint = billing.default.svc.cluster.local:19301
generation = 1
```

Move into this section only when CoAkka must operate runtime-level topology
directly.

### Expanded Endpoints

Use expanded endpoints only when CoAkka should see each eligible runtime
participant directly. Typical reasons are:

- CoAkka should choose by route policy instead of Kubernetes Service policy
- endpoint pressure should influence selection
- locality, shard key, tenant key, or affinity matters
- failover should be visible as runtime endpoint evidence

In that shape, the topology owner publishes the actual endpoint list:

```text
billing-a -> billing
billing-b -> billing
billing-c -> billing
```

The route snapshot can then list those endpoints:

```kotlin
val billingRoute = RuntimeRouteSpec(
    target = "billing",
    endpoints = listOf(
        RuntimeEndpointSpec(
            host = "billing-a",
            port = 19301,
            // NONE = PEER from checkout's view: eligible, but not local.
            flags = RuntimeEndpointFlags.NONE,
        ),
        RuntimeEndpointSpec(
            host = "billing-b",
            port = 19301,
            // NONE = PEER from checkout's view: eligible, but not local.
            flags = RuntimeEndpointFlags.NONE,
        ),
        RuntimeEndpointSpec(
            host = "billing-c",
            port = 19301,
            // NONE = PEER from checkout's view: eligible, but not local.
            flags = RuntimeEndpointFlags.NONE,
        ),
    ),
)
```

Do not read this as business code hard-coding `billing-a/b/c`. Read it as
topology input from Kubernetes EndpointSlice data, DNS, Consul, Helm values,
static deployment config, or a control-plane feed:

```text
topology input
  -> connector/app-host maps it into RuntimeStartSpec and route snapshot
  -> business code still calls target = billing
```

Choose the route shape deliberately:

| Route shape | What CoAkka sees | Who distributes work to pods |
| --- | --- | --- |
| Service DNS endpoint | One endpoint: `billing:19301` or `billing.default.svc.cluster.local:19301` | Kubernetes Service / EndpointSlice path |
| Expanded pod endpoints | Many endpoints: `billing-a`, `billing-b`, `billing-c` | CoAkka route policy |

Do not configure weighted round-robin unless the route snapshot actually
contains multiple endpoints. With a single Kubernetes Service DNS endpoint,
there is nothing for CoAkka to choose among at pod level.

That is the boundary:

```text
Business code names billing.
Topology names Service DNS or concrete endpoints.
Runtime selects only among endpoints it can see.
Transport carries the selected delivery.
```

### Generation Changes

If you are not sure you need route generation changes, you probably do not.
For the default Service DNS shape, `generation = 1` is often enough.

Use a new generation only when the CoAkka route snapshot itself changes:

```text
generation 1:
  billing -> billing.default.svc.cluster.local:19301

generation 2:
  billing -> billing-v2.default.svc.cluster.local:19301
```

or when expanded endpoint topology changes:

```text
generation 1:
  billing -> [billing-a, billing-b]

generation 2:
  billing -> [billing-a, billing-b, billing-c]
```

Only after route snapshots can change does generation skew become a real
question. A rollout can temporarily create skew:

```text
checkout active generation = 43
billing active generation = 42
```

That should be diagnosable, not magical. `generation` protects a local runtime
from stale route updates and gives each delivery outcome a route version to
report. It is not a cluster-wide consensus protocol.

Local rule:

```text
older snapshot -> reject
newer invalid snapshot -> reject and keep active route table
newer valid snapshot -> atomically replace local route table
rollback -> publish another newer valid snapshot
```

Cross-process rule:

```text
temporary skew may exist during rollout
in-flight work keeps the generation used when it was routed
new route decisions use the currently active local snapshot
outcomes must be explicit: reply, rejection, timeout, or deadletter
```

Concrete example:

```text
generation 42:
  checkout routes billing -> billing.default.svc.cluster.local:19301
  billing owns target billing

generation 43:
  checkout routes billing -> billing-v2.default.svc.cluster.local:19301
  billing-v2 owns target billing
```

During rollout, checkout may receive generation 43 before the billing side is
ready:

```text
checkout active generation = 43
billing-v2 not ready yet, or still on generation 42
```

A new request from checkout is routed using checkout's active generation 43.
If `billing-v2` is ready, the request can reply normally. If it is not ready,
the outcome should be explicit:

```text
reply        -> billing-v2 accepted and handled the target
rejection    -> selected endpoint refused or was over capacity
timeout      -> no reply within the caller's budget
deadletter   -> target/route/endpoint state could not satisfy the contract
```

If `billing-v2` is a new service that has never registered a handler for
`billing`, it cannot successfully handle that target. That is not a mysterious
generation problem. It is a route/handler mismatch, and the runtime evidence
should make it visible as rejection, timeout, or deadletter depending on where
delivery failed.

The runtime should not silently fall back to generation 42 unless the route
snapshot and route policy explicitly allow a generation 43 fallback endpoint.
Rollback is also explicit: publish generation 44 that points `billing` back to
the old service endpoint.

Runtime failover and app retry should also stay separate:

```text
runtime failover -> same request lifecycle, endpoint-level delivery evidence
app retry        -> new business attempt, idempotency and user semantics
```

If a deployment needs hard atomic cutover across every participant, that is a
deployment/control-plane policy. CoAkka runtime should make skew visible; it
should not pretend distributed rollout is globally atomic.

For the deeper rules, read
[Runtime Cluster Routing](runtime-cluster-routing.md).

## Stage 5: Bounded Admission And Overload

Overload is normal. The system should not pretend it never happens.

The first goal is bounded admission:

```text
short burst -> queue absorbs within budget
sustained overload -> pressure becomes visible
contract violation -> rejection, timeout, or deadletter evidence
```

Use a deliberately small queue in local testing:

```kotlin
val startSpec = RuntimeStartSpec(
    systemName = "billing",
    nodeId = "billing-pressure-test-1",
    queueCapacity = 16,
    strictNoDrop = true,
    generation = 1,
    routes = billingRoutes,
)
```

Then submit more work than the handler can process quickly.

What should happen:

- normal requests still reply
- short bursts wait within the queue budget
- queue pressure appears in diagnostics or logs
- work that cannot be accepted fails clearly
- route misses and delivery failures become deadletters

What should not happen:

- unbounded memory growth
- silent drops
- invisible retry loops that change business behavior
- every overload case turning into a vague `5xx`
- callers needing to know socket or replica internals

## Stage 6: Read The Evidence

Runtime evidence should carry the same small vocabulary across languages:

```text
source
target
payload identity
route generation
selected endpoint
queue pressure
reply
timeout
rejection
deadletter
```

Logger integration should preserve that vocabulary instead of flattening it
into unrelated strings.

Example operational records:

```text
runtime.ask source=checkout-api target=billing.charge.create payload=billing.charge.v1 generation=12 timeoutMs=750
runtime.route.selected target=billing.charge.create generation=12 endpoint=billing-runtime-b strategy=weighted_round_robin
runtime.pressure target=billing.charge.create node=billing-runtime-b depth=120 capacity=128
runtime.deadletter reason=route_miss target=billing.invoice.issue generation=12 source=checkout-api
```

The exact log schema can differ by language connector or host logger. The
important rule is that runtime delivery facts remain attributable.

Use CoAkka Logger when the host needs bounded, cross-language, pressure-aware
logging that keeps the same runtime vocabulary. Use the host logger for normal
application events. Correlate them with the same target, request id, payload
identity, and route generation.

## Stage 7: Put Nginx At The Edge

For a normal product service, a practical deployment shape is:

```text
internet
  -> nginx / ingress / API gateway: TLS, request limits, access logs
    -> checkout app-host: auth, tenant policy, validation, idempotency
      -> CoAkka runtime: target routing, bounded queues, pressure, deadletter
        -> billing handler
```

That keeps responsibility clear:

| Boundary | Owns | Should not own |
| --- | --- | --- |
| Nginx / gateway | Public HTTP edge, TLS/mTLS when needed, request limits | Runtime target semantics |
| App-host | Business admission, auth context, validation | Route selection internals |
| CoAkka runtime | Envelope delivery, route generation, bounded admission | Public API status vocabulary |
| Handler | Business capability | Transport retries and peer discovery policy |

mTLS belongs where the network boundary and identity policy are real. That may
be ingress, API gateway, sidecar, connector addon, or a true cross-service
transport boundary. It should not become a default requirement inside
`coakka-runtime-core` for every app-owned capability handoff.

## Stage 8: Move To Containers

A containerized setup should not bake instance identity into the image.

Build one image:

```text
billing:1.0.0
```

Let the platform supply identity:

```yaml
env:
  - name: COAKKA_SYSTEM_NAME
    value: billing
  - name: COAKKA_NODE_ID
    valueFrom:
      fieldRef:
        fieldPath: metadata.name
  - name: COAKKA_LOCAL_HOST
    valueFrom:
      fieldRef:
        fieldPath: status.podIP
  - name: COAKKA_LOCAL_PORT
    value: "19301"
```

Map those values into the start spec:

```kotlin
val startSpec = RuntimeStartSpec(
    systemName = env("COAKKA_SYSTEM_NAME", "billing"),
    nodeId = env("COAKKA_NODE_ID", fallbackHostName()),
    queueCapacity = envInt("COAKKA_QUEUE_CAPACITY", 128),
    strictNoDrop = true,
    generation = envLong("COAKKA_ROUTE_GENERATION", 1),
    routes = routeSnapshotFromConfig(),
)
```

The runtime does not need to secretly read Kubernetes metadata. The app-host or
connector maps platform config into `RuntimeStartSpec` and the route snapshot.

For more deployment detail, read
[Containerized Runtime Notes](containerized-runtime.md).

## Tuning Guide

Start by identifying the symptom. Do not tune every knob at once.

| Symptom | First check | CoAkka action | Infra action |
| --- | --- | --- | --- |
| Short burst causes rejection | Queue depth, burst size, timeout budget | Increase queue only if memory budget allows and burst is bounded | Usually none |
| Sustained overload | Handler latency and throughput | Add handler/runtime instances or shard the target | Add CPU/pods only if the handler can use them |
| Slow downstream dependency | Handler timing and downstream calls | Reduce timeout budget, add backpressure, isolate target | Fix database/network/service dependency |
| Route misses | Target name and active generation | Fix route snapshot or publish newer generation | Fix config/discovery feed |
| Too many internal HTTP APIs | Ownership boundary | Keep app-owned work behind runtime targets | Avoid adding mesh policy just to govern accidental service APIs |
| External trust boundary | Public ingress and identity policy | Keep runtime behind app-host admission | Use nginx/API-gateway TLS/mTLS where needed |
| Unclear failure | Deadletter/log fields | Preserve target, generation, reason, source | Correlate with gateway/app-host traces |

Queue tuning rule:

```text
If the workload is a bounded burst, tune the queue.
If throughput is too low, add capacity.
If ownership is wrong, fix the boundary.
```

## A Complete Mental Model

When the system is shaped correctly, business code should be able to say:

```text
I need billing.charge.create.
I do not need to know which billing replica will handle it.
I expect a reply, timeout, rejection, or deadletter.
I expect logs to tell me the target, route generation, and reason.
```

Infrastructure should be able to say:

```text
I own the public edge, transport policy, deployment identity, endpoint feeds,
and capacity.
I do not need to guess business semantics inside hidden retries.
```

CoAkka runtime should be able to say:

```text
I was given a route snapshot.
I selected an eligible handler or endpoint.
I enforced bounded admission.
I produced reply, timeout, rejection, pressure, or deadletter evidence.
```

That is the field guide version of the CoAkka boundary:

```text
Name the target.
Carry the envelope.
Route by snapshot.
Bound the queue.
Report pressure.
Return the reply.
Explain the deadletter.
Keep the contract portable.
```
