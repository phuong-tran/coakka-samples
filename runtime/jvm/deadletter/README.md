# JVM Runtime Deadletter

This sample sends a request to a missing route, verifies the `ask(...)` fails
with a matched route-miss deadletter, and observes the same deadletter through
the Kotlin `Flow` diagnostics lane.

Run:

```sh
bash run.sh
```

Or from the repository root:

```sh
bash run.sh runtime jvm deadletter
```

For IDE runs, open/import the `coakka-samples` repository root as the Gradle
project.

Direct Gradle run from the repository root:

```sh
./gradlew :runtime:jvm:deadletter:run
```

Expected output shape:

```text
coakka_runtime_deadletter reason=DEADLETTER_REASON_ROUTE_MISS target=samples.runtime.jvm.deadletter.missing generation=1
coakka_runtime_deadletter_observed matchedPending=true target=samples.runtime.jvm.deadletter.missing
coakka_runtime_stats routeMisses=1 deadletters=1 matchedDeadletters=1
```
