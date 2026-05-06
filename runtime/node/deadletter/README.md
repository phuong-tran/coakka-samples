# Node Runtime Deadletter

This sample sends a request to a missing route, verifies the request fails with
a matched route-miss deadletter, and observes the same deadletter through the
language diagnostics lane.

Run from this directory:

```sh
bash run.sh
```

Or from the repository root:

```sh
bash run.sh runtime node deadletter
```

Expected output shape:

```text
coakka_runtime_deadletter reason=DEADLETTER_REASON_ROUTE_MISS target=samples.runtime.node.deadletter.missing generation=1
coakka_runtime_deadletter_observed matchedPending=true target=samples.runtime.node.deadletter.missing
coakka_runtime_stats routeMisses=1 deadletters=1 matchedDeadletters=1
```
