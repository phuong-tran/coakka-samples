# Bun Runtime Basic

This sample runs a same-process request/reply echo through the Bun runtime
connector package.

This sample covers:

- install `coakka-v2-connector-bun@2.4.1` from npm
- embedded native runtime loading
- no runtime package dependency install beyond the published connector package
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
coakka_runtime_info abi=1 version=2.4.0 git=c2f53117
coakka_runtime_response payload={"echo":"hello-runtime-bun"}
coakka_runtime_stats generation=1 routes=1 delivered=1 matchedResponses=1
```
