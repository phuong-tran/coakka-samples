# Swift Runtime Basic

This public runtime sample installs `coakka-runtime-swift@2.5.1` from the
public SwiftPM GitHub tag and runs one same-process request/reply echo through
the CoAkka runtime v2 connector.

This sample covers:

- public SwiftPM package resolution from `github.com/phuong-tran/coakka-runtime-swift`
- automatic selection from the five embedded native runtime payloads
- runtime version/git diagnostics
- one process-owned route and handler
- one request/reply round trip
- basic client counters

The exact package contains macOS ARM64, Linux ARM64 and x86-64, and Windows
ARM64 and x86-64 payloads. This sample's Swift execution evidence is macOS
ARM64; the other payloads have format, digest, and bridge-boundary verification
without a matching Swift toolchain execution claim.

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
coakka_runtime_info abi=1 version=2.5.0 git=4b65d0b2
coakka_runtime_response payload=echo-hello-runtime-swift
coakka_runtime_stats delivered=1 matchedResponses=1
```
