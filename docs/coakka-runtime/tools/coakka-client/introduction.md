# CoAkka Runtime Client Introduction

`coakka-runtime-client` is the terminal client for driving and diagnosing a
CoAkka Runtime path. The published executable is named `coakka-client`.

It exists for operators and developers who need a direct way to inspect a
packaged runtime client, call a runtime target, and verify request/reply
behavior without building a web UI.

## What It Does

- Prints client/runtime version and build diagnostics.
- Checks whether the packaged client can drive the expected runtime request
  path.
- Sends one request to a runtime target and returns one explicit result.
- Supports `ask` as the request/reply alias for `call`.
- Carries payload metadata such as content type, message type, schema version,
  and business headers.
- Runs deterministic command batches through `shell --script`.

## What It Is Not

`coakka-runtime-client` is not a dashboard, topology authority, inspect product,
business schema registry, service-discovery layer, or replacement for
HTTP/gRPC at real API edges.

CoAkka Runtime owns route snapshots, target ownership, delivery semantics,
deadletters, lifecycle, and runtime diagnostics. The CLI drives those runtime
paths; it does not become the source of truth for topology or business schema.

## Where It Fits

Use the CLI when the runtime path itself should be visible:

```text
native runtime host
  <- TCP frame profile
coakka-client call or ask
  -> runtime target
  -> reply, timeout, or deadletter
```

That keeps the product story focused on the runtime boundary: target names,
payload identity, route ownership, explicit replies, and explicit failure
outcomes.
