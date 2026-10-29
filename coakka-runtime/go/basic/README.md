# Go Runtime Basic

This private candidate sample uses the exact module in the
[candidate guide](../../../docs/coakka-runtime/runtime-go-candidate.md), not a historical
public module version. Read that guide, set its module/work paths and select
Go with a C11 cgo compiler. No separate native Core install is needed.

```sh
bash run.sh
```

Or from the repository root:

```sh
bash coakka-runtime/go/basic/run.sh
```

The app owns one route and typed handler, checks one JSON echo with the original
2s deadline, reads Core-issued startup/runtime/counter facts and closes the
runtime before main reports an error. Incidental runtime tuning is omitted.

Actual floor and real Garble consumers are qualified separately on Mac, Pi and
native Windows. This small app does not close broader release/resource gates.
