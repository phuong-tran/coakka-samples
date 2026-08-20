# CoAkka Runtime Client

`coakka-runtime-client` is the CLI runtime client lane for CoAkka Runtime. The
published command and archive prefix are `coakka-client`.

![CoAkka Runtime Client CLI walkthrough](assets/coakka-runtime-client.gif)

Full recording: [coakka-runtime-client.mp4](assets/coakka-runtime-client.mp4)

Use `coakka-client` when the runtime path itself should be visible from a
terminal or script:

- print version and build diagnostics
- run `doctor` checks against the packaged runtime client
- read a bounded capability, connection-strategy, and non-secret security
  snapshot from a connected runtime
- report independent runtime features such as File Lane and Stream Lane from
  local metadata or a connected runtime snapshot
- send one request to a runtime target
- use `call` or `ask` for request/reply verification
- carry payload metadata such as content type, message type, schema version,
  and business headers
- run deterministic command batches through shell script mode

It is not a dashboard, topology authority, inspect product, service discovery
layer, business schema registry, or replacement for HTTP/gRPC at real API
edges.

## Local And Connected Truth

`version` and `doctor` report the local `coakka-client` binary and its packaged
runtime dependencies. They do not infer the configuration of a remote process.

Use `runtime-info` when the runtime at the selected address must answer for
itself and its host exposes the snapshot target:

```sh
coakka-client runtime-info --connect runtime.example.internal:19301
```

The command sends one bounded request to the host-exposed, runtime-owned
`coakka.runtime.inspect.snapshot` route. The JSON response includes runtime
identity, effective capability masks, connection mode and tuning provenance,
plus non-secret TLS state such as mode, credential generation, certificate
bounds, and fingerprint when available. It never returns private keys,
certificate contents, or credential file paths.

Use `--timeout-ms <milliseconds>` to bound the request, and `--route <target>`
only when an integration intentionally publishes the snapshot under another
target. Connection, timeout, deadletter, invalid snapshot, and runtime error
outcomes are returned as structured JSON with a nonzero process exit status.
The command prints the runtime-provided snapshot unchanged after validating
that the response is a JSON object.

Feature reporting does not make `coakka-client` a file or stream source/sink.
Applications continue to own lane data, lifecycle, and adaptation through
their connector; the client reports available metadata for scripts and release
checks.

## Naming

The product lane is `coakka-runtime-client`. The executable in the published
archives is `coakka-client`.

If a shell reports `coakka-runtime-client: command not found`, use:

```sh
coakka-client --help
```

The longer name describes the product lane. The shorter command is the current
published binary name.

## Where It Fits

`coakka-client` is useful when a team wants a `curl`-like check for the CoAkka
runtime boundary rather than an HTTP endpoint:

```text
coakka-client call or ask
  -> runtime target
  -> reply, timeout, or deadletter
```

That keeps the test focused on the runtime contract: target names, payload
identity, route ownership, explicit replies, and explicit failure outcomes.

Use `curl`, Postman, Swagger, or OpenAPI tooling when the boundary being tested
is an HTTP API. Use `coakka-client` when the boundary being tested is a CoAkka
runtime target.

## Published Artifacts

Current public native CLI generation: `2.4.0+c2f53117`.

Native CLI archives live under:

```text
coakka-tools/coakka-client/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a/
```

The Docker Linux verification bundle remains on its separately released
generation:

```text
coakka-tools/coakka-client/docker-demo/releases/1.3.2+caff6d6d/
```

Use `coakka-publish/artifacts/public-artifacts.tsv` for exact paths and
checksums.

The macOS ARM64, Linux ARM64/x86-64, and Windows ARM64/x86-64 archives complete
matching-host command execution. All five also pass dependency, architecture,
archive, and checksum gates. Windows x86-64 evidence is Core Actions run
`32115663861` over exact Publish commit
`d5cff2a7922470b4b33bd48cac2b472bb75acbc4`.

## Sample Entry Points

In `coakka-samples`, use:

```sh
bash run.sh runtime-client
bash run.sh runtime-client docker-bundle
bash run.sh runtime-client docker-walkthrough
bash run.sh runtime-client dockerhub-demo
```

The Docker bundle and Docker Hub paths run published native services and drive
them with the packaged `coakka-client`, so users can see a request/reply path
without building the native runtime locally.

## Relationship To Inspect

`coakka-client` is script-first terminal tooling.
`coakka-runtime-inspect` is read-first browser exploration.

`runtime-info` is the bounded scriptable snapshot path. It does not turn the
CLI into a long-running dashboard or topology authority.

They should teach the same runtime vocabulary: target, payload identity, route
snapshot, reply, timeout, deadletter, stats, and diagnostics.

For the browser tool, read [CoAkka Runtime Inspect](coakka-runtime-inspect.md).
