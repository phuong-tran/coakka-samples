# Bun Runtime Basic

This sample runs a same-process request/reply echo through the Bun runtime
connector package.

This sample covers:

- install from the sibling Bun connector package checkout
- embedded native runtime loading
- runtime version/git diagnostics
- one process-owned route and one process-owned handler
- one request/reply round trip
- basic route/client counters

Run from this directory:

```sh
bash run.sh
```

Or from the repository root:

```sh
bash run.sh runtime bun basic
```

Expected output shape:

```text
coakka_runtime_info abi=1 version=1.3.1 git=<git>
coakka_runtime_response payload={"echo":"hello-runtime-bun"}
coakka_runtime_stats generation=1 routes=1 delivered=1 matchedResponses=1
```
