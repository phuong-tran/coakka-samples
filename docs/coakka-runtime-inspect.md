# CoAkka Runtime Inspect

`coakka-runtime-inspect` is the browser runtime explorer and route-try UI for
CoAkka Runtime. It is the visual sibling of `coakka-client`.

![CoAkka Runtime Inspect browser walkthrough](assets/coakka-runtime-inspect.gif)

Full recording: [coakka-runtime-inspect.mp4](assets/coakka-runtime-inspect.mp4)

Use inspect when a user needs to see runtime facts in a browser:

- runtime identity
- route catalog
- endpoint topology
- health and pressure state
- recent runtime events
- a route-try form for request/reply experiments
- a copied equivalent `coakka-client` command from the browser form

It is not an admin dashboard, observability platform, schema registry, service
discovery server, mTLS control plane, or topology authority. Runtime core
remains the source of truth; inspect reads and renders runtime facts.

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

Current public native generation: `1.3.4+dc6ec28`.

Native UI archives live under:

```text
coakka-tools/coakka-runtime-inspect/releases/1.3.4+dc6ec28/
```

Current public platforms:

- macOS ARM64
- Linux x86_64
- Linux ARM64
- Windows x86_64
- Windows ARM64

Use `coakka-publish/artifacts/public-artifacts.tsv` for exact paths and
checksums.

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

For the terminal tool, read [CoAkka Runtime Client](coakka-runtime-client.md).
