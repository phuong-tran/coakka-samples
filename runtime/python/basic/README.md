# Python Runtime Basic

This sample runs a local request/reply echo through the published Python runtime
v2 wheel.

It demonstrates:

- wheel install from `coakka-publish`
- embedded native runtime loading
- runtime version/git/backend diagnostics
- one local route and one local handler
- one request/reply round trip
- basic route/client counters

Run from this directory:

```sh
bash run.sh
```

Or from the repository root:

```sh
bash run.sh runtime python basic
```

Expected output shape:

```text
coakka_runtime_info abi=1 version=0.1.0 git=<git> backend=<backend>
coakka_runtime_response payload={"echo":"hello-runtime-python"}
coakka_runtime_stats generation=1 routes=1 delivered=1 matchedResponses=1
```
