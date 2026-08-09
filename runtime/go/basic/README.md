# Go Runtime Basic

This public runtime sample runs a same-process request/reply echo through the Go
runtime v2 package.

This sample covers:

- public Go module install from `github.com/phuong-tran/coakka-runtime-go`
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
bash run.sh runtime go basic
```

Expected output shape:

```text
coakka_runtime_info abi=1 version=2.1.0 git=60ddf70d
coakka_runtime_response payload={"echo":"hello-runtime-go"}
coakka_runtime_stats generation=1 routes=1 delivered=1 matchedResponses=1
```
