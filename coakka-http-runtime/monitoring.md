# Monitoring an HTTP service

Monitoring belongs to Core; the connector projects Core's effective state.
This guide starts from the packaged host-inlined `Service`, not a separate
runtime or a second HTTP server. Collection is disabled unless requested.

We use Kotlin below for readability. Other languages follow the same Core
contract through their idiomatic APIs; use the source links at the end for
exact names. Java is supported by the same JVM connector, not excluded by the
choice of Kotlin examples.

## Contents

- [Choose the observation](#choose-the-observation)
- [Enable bounded collection](#enable-bounded-collection)
- [Change policy safely](#change-policy-safely)
- [Read and export](#read-and-export)
- [Sources, adapters and sinks](#sources-adapters-and-sinks)
- [Examples by language](#examples-by-language)

## Choose the observation

| Need | Use | Do not assume |
| --- | --- | --- |
| Accepted CPU, limits and capabilities | Runtime information/effective settings | Input preferences prove what Core applied |
| Service readiness and active work | Health snapshot | A live process is ready for requests |
| Fresh runtime progress | Bounded liveness probe | A cached health read proves new progress |
| Traffic, failures and latency | Selected monitor aggregates | Disabled collection produces a complete time series |
| Recent operational changes | Bounded event pages | Every event is retained forever |
| Application business metrics | Application instrumentation | Core knows database latency or business outcomes |

## Enable bounded collection

The following excerpt uses the same startup options as
[Kotlin Main.kt](kotlin/src/main/kotlin/sample/Main.kt). Add `.monitor(...)` to your existing builder
before `.start()`; keep its routes and checked lifecycle handling. These small
capacities illustrate bounded retention, not production sizing advice.

```kotlin
import coakka.http.MonitorCategory
import coakka.http.MonitorCollection
import coakka.http.MonitorOptions

builder.monitor(MonitorOptions(
    collection = MonitorCollection.AGGREGATES_AND_EVENTS,
    eventCapacity = 32,
    maxEventsPerRead = 8,
    categories = MonitorCategory.LIFECYCLE.bit or MonitorCategory.EXCHANGE.bit,
    signalReserved = true,
))
```

Reserve storage and optional notification capability at startup. After startup,
read Core's configuration to discover the reservation and supported categories;
enum membership is not evidence that a category is supported by the package.
Select only the observations you need. Collection has a bounded but nonzero
cost; record its policy with benchmark and capacity results.

## Change policy safely

This control-plane excerpt uses the existing Kotlin sample's `service`.
It reduces collection to aggregates within the existing reservation; it does
not resize storage. Check the typed outcome before treating the policy as active.

```kotlin
import coakka.http.MonitorApplyReason
import coakka.http.MonitorCollection
import coakka.http.MonitorLatency
import coakka.http.MonitorNotification

val current = service.monitorConfiguration()
val desired = current.policy.copy(
    collection = MonitorCollection.AGGREGATES,
    notification = MonitorNotification.POLL,
    latency = MonitorLatency.NONE,
    activeEventCapacity = 0,
    eventCategories = 0,
)
val outcome = service.applyMonitorPolicy(current.generation, desired)
check(outcome.reason == MonitorApplyReason.APPLIED) {
    "monitor policy refused: ${outcome.reason}"
}
val effective = outcome.effective // Core's accepted state, not reconstructed input.
```

A stale generation or a request exceeding reserved resources leaves the prior
policy intact. Inspect the typed outcome and returned effective state. Do not
blindly retry against a new generation: another operator may have changed the
policy intentionally. The complete examples below exercise accepted changes,
stale refusal, over-reservation refusal and restoration.

## Read and export

- Read health/configuration/snapshots outside request handlers at a bounded
  cadence. Monitoring must not create one exporter call per HTTP request.
- A notification is only a doorbell. Pull the snapshot or bounded event page;
  do not interpret wakeup count as request count.
- Keep one owner for a blocking monitor waiter. Honor the page's cursor and
  loss contract, drain in bounded work units, and report missed history and
  overwrite/drop separately from request failures. Do not jump past unread
  pages merely because a newer global sequence is visible.
- Export copied observations through your own bounded queue and finite I/O
  deadlines. Prometheus/OpenTelemetry integration is application work, not an
  automatically installed endpoint or exporter.
- On shutdown, interrupt and join any application-owned monitor waiter before
  destroying its service. A waiter that did not stop is a cleanup failure;
  do not close underneath it. Follow the selected language's close contract.
- Include policy generation and collection epoch in your export bookkeeping;
  do not calculate deltas across a reset or policy change without handling it.
- Do not export credentials, cookies, headers, bodies or arbitrary sensitive
  labels. Runtime monitor history is not traffic capture.

## Sources, adapters and sinks

Use monitoring to diagnose failure spikes, deadlines, overload and reader lag,
support alerts and capacity planning, and compare the behavior of language
hosts. It does not replace business metrics, a durable audit log or tracing.

```text
Core snapshots / bounded events
    -> application-owned reader and adapter
    -> bounded export queue or cached metrics view
    -> logs / metrics endpoint / telemetry collector
```

Here, **source** means Core's observations; an **adapter** selects and maps
them; a **sink** is the destination. These are integration roles, not new
CoAkka classes or a built-in source/sink registration API.

| Destination | What to send | Application responsibility |
| --- | --- | --- |
| Structured logs / coakka-logger | Selected lifecycle changes, typed failures and monitor-loss summaries | Map safe fields, rate-limit, rotate/retain and bound writes; no automatic logger binding is claimed. |
| Prometheus | Numeric counters, active-work gauges and compatible latency buckets | Provide a protected exposition endpoint backed by copied snapshots or a bounded cache; Prometheus scrapes it. Do not send event text as metric labels. |
| OpenTelemetry Collector | Mapped metrics and selected structured logs | Use a suitable SDK/exporter and collector pipeline; define resource identity, units, temporality, queue limits and deadlines. Monitor events do not automatically become distributed traces. |
| Local dashboard or another sink | Copied health, effective settings and aggregate views | Bound refresh, access and retention; sink failures must not block HTTP work. |

Prometheus's normal collection model is HTTP pull; it is not a generic log
sink. See its [official overview](https://prometheus.io/docs/introduction/overview/).
OpenTelemetry Collector pipelines connect receivers, processors and exporters;
see the [official configuration guide](https://opentelemetry.io/docs/collector/configuration/).
Neither integration is installed simply by enabling CoAkka monitoring.

Keep labels bounded: service, environment and replica identity are deployment
choices; raw URLs, request IDs, users and arbitrary error text are not safe
metric dimensions. Preserve counter resets and histogram bucket boundaries;
do not invent a latency percentile from counts that cannot support it.
Export monitor loss and exporter drops as separate facts. Never use bounded
recent history as an exactly-once billing or security-audit record.

For several instances without Kubernetes, see
[Deployment and clustering boundaries](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/deployment-without-kubernetes.md).
Each instance supplies its own monitor state; cluster aggregation belongs to
the monitoring backend, not to a fabricated local Core snapshot.

## Examples by language

| Host | Startup and observation | Live policy recipe |
| --- | --- | --- |
| C | [server.c](c/server.c) | [Shared public C recipe](native/monitor_example.h) |
| C++ | [server.cpp](cpp/server.cpp) | [Shared public C recipe](native/monitor_example.h) |
| Go | [main.go](go/main.go) | [control.go](go/control.go) |
| JVM: Kotlin and Java | [Main.kt](kotlin/src/main/kotlin/sample/Main.kt) | [Control.kt](kotlin/src/main/kotlin/sample/Control.kt) |
| Python | [main.py](python/main.py) | [control.py](python/control.py) |
| Node.js and Bun | [main.ts](typescript/main.ts) | [control.ts](typescript/control.ts) |

Java uses the same `Service.monitorConfiguration()` and
`Service.applyMonitorPolicy(...)` methods as Kotlin. Read returned Kotlin value
properties through Java getters such as `getGeneration()`; see the
[Java/Kotlin integration guide](kotlin/integration.md#java-uses-the-same-jvm-connector).
The full policy mutation example linked above is Kotlin, not a claim of a
separate Java monitor smoke test.

See the [monitor contract and category reference](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/observability-and-monitoring.md)
and [sample evidence scope](FEATURES.md). The examples are recipes, not proof
of exhaustive cursor, waiter or exporter fault coverage.
