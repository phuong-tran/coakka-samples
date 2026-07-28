# CoAkka Runtime Client

`coakka-runtime-client` is the CLI runtime client lane for CoAkka Runtime. The
published command and archive prefix are `coakka-client`.

![CoAkka Runtime Client CLI walkthrough](assets/coakka-runtime-client.gif)

Full recording: [coakka-runtime-client.mp4](assets/coakka-runtime-client.mp4)

Use `coakka-client` when the runtime path itself should be visible from a
terminal or script:

- print version and build diagnostics
- run `doctor` checks against the packaged runtime client
- send one request to a runtime target
- use `call` or `ask` for request/reply verification
- carry payload metadata such as content type, message type, schema version,
  and business headers
- run deterministic command batches through shell script mode

It is not a dashboard, topology authority, inspect product, service discovery
layer, business schema registry, or replacement for HTTP/gRPC at real API
edges.

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

Current public generation: `1.3.2+caff6d6d`.

Native CLI archives live under:

```text
coakka-tools/coakka-client/releases/1.3.2+caff6d6d/
```

Docker Linux verification bundles live under:

```text
coakka-tools/coakka-client/docker-demo/releases/1.3.2+caff6d6d/
```

Use `coakka-publish/artifacts/public-artifacts.tsv` for exact paths and
checksums.

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

They should teach the same runtime vocabulary: target, payload identity, route
snapshot, reply, timeout, deadletter, stats, and diagnostics.

For the browser tool, read [CoAkka Runtime Inspect](coakka-runtime-inspect.md).
