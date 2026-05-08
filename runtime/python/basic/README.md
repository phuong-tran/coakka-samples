# Python Runtime Basic

This paused public-lane sample runs a local request/reply echo through the
Python runtime v2 wheel from a local/private artifact set.

It demonstrates:

- wheel install from a local/private artifact set
- embedded native runtime loading
- runtime version/git diagnostics
- one local route and one local handler
- one request/reply round trip
- basic route/client counters

Run from this directory:

```sh
COAKKA_ALLOW_PAUSED_RUNTIME=1 bash run.sh
```

Or from the repository root:

```sh
COAKKA_ALLOW_PAUSED_RUNTIME=1 bash run.sh runtime python basic
```

Expected output shape:

```text
coakka_runtime_info abi=1 version=0.1.0 git=<git>
coakka_runtime_response payload={"echo":"hello-runtime-python"}
coakka_runtime_stats generation=1 routes=1 delivered=1 matchedResponses=1
```
