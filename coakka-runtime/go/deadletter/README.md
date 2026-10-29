# Go Runtime Deadletter

This private candidate sample uses the exact module in the
[candidate guide](../../../docs/coakka-runtime/runtime-go-candidate.md), not a historical
public module version. Read that guide, set its module/work paths and select
Go with a C11 cgo compiler. No separate native Core install is needed.

```sh
bash run.sh
```

Or from the repository root:

```sh
bash coakka-runtime/go/deadletter/run.sh
```

The app requires a typed route-miss cause, original target and exact terminal
counters. It observes one bounded event using the original1s wait, cancels
observation before host close and keeps the2s ask deadline unchanged.

Actual floor and real Garble consumers are qualified separately on Mac, Pi and
native Windows. This small app does not close broader release/resource gates.
