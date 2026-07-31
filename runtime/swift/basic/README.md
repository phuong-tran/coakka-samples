# Swift Runtime Basic

This public runtime sample installs `coakka-runtime-swift@1.3.4` from the
public SwiftPM GitHub tag and runs one same-process request/reply echo through
the CoAkka runtime v2 connector.

This sample covers:

- public SwiftPM package resolution from `github.com/phuong-tran/coakka-runtime-swift`
- embedded macOS ARM64 native runtime loading
- runtime version/git diagnostics
- one process-owned route and handler
- one request/reply round trip
- basic client counters

Run from this directory:

```sh
bash run.sh
```

Or from the repository root:

```sh
bash run.sh runtime swift basic
```

Expected output shape:

```text
coakka_runtime_info abi=1 version=1.3.4 git=dc6ec284
coakka_runtime_response payload=echo-hello-runtime-swift
coakka_runtime_stats delivered=1 matchedResponses=1
```
