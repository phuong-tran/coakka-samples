# CoAkka Runtime Inspect

`coakka-runtime-inspect` is the browser runtime explorer and route-try UI for
CoAkka Runtime. It is the visual sibling of `coakka-client`.

![CoAkka Runtime Inspect browser walkthrough](assets/coakka-runtime-inspect.gif)

Full recording: [coakka-runtime-inspect.mp4](assets/coakka-runtime-inspect.mp4)

Use inspect when a user needs to see runtime facts in a browser:

- runtime identity
- route catalog
- endpoint topology
- effective connection strategy, defaults revision, and tuning provenance
- runtime feature availability, including independent File Lane and Stream Lane
  support when reported
- effective capability masks and non-secret TLS/mTLS state
- health and pressure state
- recent runtime events
- a route-try form for request/reply experiments
- a copied equivalent `coakka-client` command from the browser form

It is not an admin dashboard, observability platform, schema registry, service
discovery server, mTLS control plane, or topology authority. Runtime core
remains the source of truth; inspect reads and renders runtime facts.

The transport section reports what the connected runtime says is active. TLS
credential contents, private keys, and credential file paths are never exposed.
When certificate identity metadata is available, inspect shows only non-secret
fields such as credential ID and generation, validity bounds, and fingerprint.

Inspect reports lane availability only. Active File Lane and Stream Lane
sessions remain application-owned; the UI does not invent active-session facts
that the runtime snapshot does not expose.

## How It Differs From Swagger

Swagger and OpenAPI describe HTTP APIs: paths, methods, request/response
schemas, status codes, auth, and API docs.

`coakka-runtime-inspect` describes runtime targets and runtime delivery:

```text
target -> route snapshot -> endpoint -> reply, timeout, or deadletter
```

The goal is not to clone Swagger for HTTP. The goal is to make runtime-owned
targets, routes, pressure, and outcomes visible when the system is using CoAkka
for application-owned work behind or beside the HTTP edge.

## Published Artifacts

Current public native generation: `2.4.0+c2f53117`.

Native UI archives live under:

```text
coakka-tools/coakka-runtime-inspect/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a/
```

Current public platforms:

- macOS ARM64
- Linux x86_64
- Linux ARM64
- Windows x86_64
- Windows ARM64

Use `coakka-publish/artifacts/public-artifacts.tsv` for exact paths and
checksums.

The macOS ARM64 and both Linux archives completed command and `serve` smoke on
matching hosts. The Windows archives passed Zig cross-build, PE architecture,
native dependency, archive, and checksum gates; matching-host Windows execution
is not claimed for this generation.

## Sample Entry Points

In `coakka-samples`, use:

```sh
bash run.sh runtime-inspect check
bash run.sh runtime-inspect published-smoke
bash run.sh runtime-inspect serve
bash run.sh runtime-inspect docker-smoke
bash run.sh runtime-inspect docker-serve
bash run.sh runtime-inspect dockerhub-smoke
```

For the Docker Hub zero-install path:

```sh
docker run --rm docker.io/gabrielgun1983/coakka-runtime-inspect-sample:1.3.2-caff6d6d-remote
```

To serve the browser UI from the Docker Hub sample image:

```sh
docker run --rm -p 18080:18080 docker.io/gabrielgun1983/coakka-runtime-inspect-sample:1.3.2-caff6d6d-remote serve
```

## Relationship To coakka-client

`coakka-runtime-inspect` is read-first visual exploration.
`coakka-client` is script-first terminal tooling.

Both should preserve the same request metadata shape and explicit terminal
outcomes: reply, timeout, deadletter, and configuration error.

`coakka-client runtime-info` reads the same bounded runtime snapshot for
scripts; inspect renders that runtime-owned truth for interactive diagnosis.

For the terminal tool, read [CoAkka Runtime Client](coakka-runtime-client.md).
