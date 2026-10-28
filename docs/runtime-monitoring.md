# Runtime Defaults And Monitoring

This guide covers monitoring in **CoAkka Runtime across host languages**.
Kotlin is used for the example, but the same choices and usage steps apply to
other languages through their own connector APIs.

The defaults below describe Core's current startup settings. The Kotlin
example uses the **unpublished JVM 3.0.0 candidate**.
For an older released package, follow that package's included guide; its
defaults and API names may differ. See [Current Packages](current-packages.md)
when choosing a published version.

## The Defaults And Why They Exist

| Choice | Default | Reason |
| --- | --- | --- |
| CPU use | `AUTO` | Let Core choose from the CPUs available to the process. |
| Connections | `MULTIPLEXED` | Reuse a connection and let several requests use it at the same time, within Core's limits. |
| Extra monitor collection | OFF | Avoid extra diagnostic work when the application does not need it. |

These are independent choices. `AUTO` is about CPU use; it does not select
the connection mode. `MULTIPLEXED` does not turn monitoring on. Some connector
enums call the same connection mode `MULTIPLEXING`.

### Why AUTO Is The Default

Where CPU placement is supported, Core prefers two allowed logical CPUs, or
one when the process is restricted to one. This gives Core room to run its
work without making users choose CPU IDs or using every CPU on the machine.

It does not reserve those CPUs, mean "two threads," or control all of the
application's business threads. Read Core's accepted settings to see what
was supported, selected and applied. On platforms without this placement
support, Core can use normal OS scheduling and report no selected CPU IDs.
Do not assume that asking for `AUTO` proves two CPUs were selected.

### Why MULTIPLEXED Is The Default

A service often sends several requests to the same peer. Keeping a connection
open avoids setting it up again for every request. Allowing several requests
to share it also avoids making every call wait for the previous call to finish.
Core keeps the number of simultaneous requests bounded and matches replies
to the right callers.

This fits the normal concurrent request/reply use case. It does not promise
that every workload will be faster. If the shared connection fails, several
requests using it can fail together; CoAkka does not automatically replay a
business request after it may have been sent. Local-only calls do not need a
remote connection.

Keep this default unless the deployment needs another supported mode and
measurements justify the change. See
[Connection Strategies](connection-strategies.md) for alternatives.

### Why Monitor Is OFF By Default

Extra monitoring records and copies diagnostic information, keeps a limited
recent history and tells readers when to refresh. That takes CPU, memory and
coordination work. It is useful when the application needs those observations,
but should be a deliberate choice for small devices, short-lived programs and
latency-sensitive services.

OFF does not disable normal health checks, operational counters or inspection
of accepted settings. Routing, request handling and timeouts keep working
normally.

ON still has a cost. Measure it with realistic traffic and the rate at which
the application reads and exports information. A bounded monitor is not
a guarantee of zero overhead.

## What Monitor Is Useful For

| Situation | What to look at |
| --- | --- |
| A status screen or local service dashboard | Runtime state and the active route configuration version. |
| Requests slowing down under load | Queue depth, queue capacity and rejection counters over time. |
| A deployment or incident | Whether the intended configuration is active, plus recent diagnostic information where the selected API exposes it. |
| Metrics and alerts | Read runtime facts, then send selected values to the application's monitoring system. |

Enable collection when these observations are needed and account for its
cost. If occasional health/counter reads are enough, those reads can be used
without extra monitor collection.

Each monitor observes one runtime instance. Monitoring several processes
requires collecting from each relevant process. Export, storage, dashboards
and alert rules belong to the application or its existing monitoring tools.
Enabling monitor does not set those up automatically.

## When The Setting Takes Effect

The monitor setting is fixed when a runtime instance is **created**.

- Leave the monitor option absent for Core's OFF default.
- Choose ON before creating the runtime when collection is needed.
- Read accepted settings to confirm ON; a feature being supported by the
  package does not mean it is enabled for that instance.
- Collection can already include configuration changes made after creation
  and before request processing starts.
- Read an initial view, then refresh while the application is running.

There is no supported live ON/OFF switch for the same instance. Editing the
original options, opening a dashboard or attaching an observer does not
change its setting. Stopping that runtime does not make it restartable.

## How To Turn Collection On Or Off

Choose the setting for a **new** runtime instance:

1. Set the desired value in the application's startup configuration.
2. Stop new work and let existing work and monitor reads finish.
3. Close the old instance, then create/start its replacement with the new
   setting and the normal routes and handlers.
4. Check the replacement's accepted setting before using it.

For a service that must remain available, roll out replacement replicas using
the application's normal deployment process.

Pausing the dashboard or reader only stops reading. An ON instance continues
collecting until it is closed. It cannot recover the optional history that was
never collected while OFF. Treat counters and history as belonging to each
instance; a replacement starts a new lifetime.

## Kotlin Example

The example has no business routes. It turns monitoring on, leaves CPU and
connection choices at their defaults, checks accepted settings after start,
reads an initial view and waits up to one second for an update.

It uses the current **3.0.0 candidate JVM API**:

```kotlin
import coakka.v2.connector.RuntimeHandle
import coakka.v2.connector.RuntimeMonitorOptions
import coakka.v2.connector.RuntimeMonitorSnapshot
import coakka.v2.connector.RuntimeStartSpec

private fun show(view: RuntimeMonitorSnapshot) {
    println("state=${view.health.runtimeStateName} " +
        "generation=${view.health.appliedGeneration}")
    println("queue=${view.stats.localWorkQueueDepth}/" +
        "${view.stats.localWorkQueueCapacity} " +
        "rejected=${view.stats.queueRejectedCount} " +
        "monitorDrops=${view.stats.monitorEventDroppedLifetimeCount}")
}

fun main() {
    val spec = RuntimeStartSpec(
        systemName = "monitor-guide",
        nodeId = "node-1",
        routes = emptyList(),
        monitor = RuntimeMonitorOptions(enabled = true),
    )
    RuntimeHandle.open(startSpec = spec).use { runtime ->
        runtime.start()
        val accepted = runtime.startupConfig()
        check(accepted.monitorEnabled)
        println("placement supported=${accepted.execution.supported} " +
            "applied=${accepted.execution.startupApplied} " +
            "selected CPUs=${accepted.execution.selectedCpuIds}")
        println("connection mode=${accepted.connection.mode}")

        show(runtime.monitor.snapshot())
        val update = runtime.monitor.awaitNextBlocking(timeoutMs = 1_000)
        if (update != null) show(update)
    }
}
```

This exact example was compiled for Java 8 and run against the JVM
3.0.0 candidate on macOS ARM64. It does not imply that this version is already
published or supported on additional platforms.

`RuntimeHandle.open(...)` creates the instance, `start()` starts its work,
and `use` closes it when the block finishes or fails. In a real service, use
the application's existing long-lived instance. Do not create another one
for every monitor read. The example prints values for clarity; a service can
send selected values to its existing dashboard or metrics system.

To keep collection OFF for a new instance, omit the `monitor` argument.
Use `monitor = RuntimeMonitorOptions(enabled = false)` when the application
wants to request OFF explicitly. These choices do not change an instance
that is already open.

### What The Example Reads

The Kotlin view contains current health and counters. They are separate
reads, not a guarantee that every field was captured at exactly the same
instant. It does not contain one record for every event.

- `snapshot()` reads health and counters directly; it also works with extra
  collection OFF.
- `awaitNextBlocking(...)` waits for a refresh notification, then reads a
  current view. It requires monitoring to be enabled.
- A `null` result means no update notification was seen within the wait
  time. It does not mean the service is healthy, unhealthy or stopped.
- Disabled monitoring, a closed instance and other failures produce errors;
  they are not silently reported as "no update."
- Startup may already have produced a notification before the first wait.

Several changes can be combined into one update notification. Read the latest
values rather than counting notifications as requests or assuming every
intermediate change was seen. The example's `monitorDrops` value concerns
monitor update accounting; it is **not** a count of lost business requests.

For continuous monitoring, repeat bounded waits under the application's
normal cancellation and shutdown control. Use one reader to share a latest
view where practical. Keep blocking waits and slow output away from request
handlers and UI threads. JVM also has a suspending `awaitNext(...)` method;
use the selected language's normal scheduling style.

## The Same Approach In Other Languages

The steps are the same: choose the setting before creation, check accepted
settings, read health/counters, use update notifications where the connector
supports them, and finish reads before closing the runtime.

The current candidate connectors for Kotlin/JVM, Node/Bun, Python and Go have
wait-for-update helpers. C# and Swift expose the startup choice and
health/counter/settings reads, but do not currently expose the same public
wait helper. Names and available views vary; follow the exact connector
package's guide rather than copying Kotlin method names into another language.

This applies to services, command-line programs, desktop apps and small-device
hosts. In Electron or Tauri, the native application side owns the runtime; the
UI displays an allowed view of its state. Opening the UI does not enable
collection by itself.

## Use The Information Correctly

Read current values to answer current-state questions. Compare counter readings
over time to calculate rates. Some lower-level APIs also expose topology,
observation times and a limited recent-event history. Older entries can be
lost when that history fills, and those APIs are not all exposed by the Kotlin
view above.

Do not treat a lack of updates as a health check, compare local observation
times as if they were a shared clock across machines, or use limited diagnostic
history as durable audit storage. For a browser or remote view, allow only
authorized observers and check the actual access result.

Monitor does not supply business results, save application logs or choose
automatic recovery actions. Those remain separate responsibilities. See
[Runtime Logging And Observability](runtime-logging-observability.md) and
[Runtime Inspect](coakka-runtime-inspect.md) for related workflows.
