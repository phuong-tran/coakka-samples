# CoAkka Runtime Inspect Samples

`coakka-runtime-inspect` is the browser runtime explorer for CoAkka Runtime.
It is the visual sibling of `coakka-client`: both point at a caller-supplied
runtime address, both use runtime-owned truth, and both keep route try behavior
aligned with `call` / `ask`.

This sample lane can verify the published macOS ARM64, Linux x86_64, Linux
ARM64, Windows x86_64, and Windows ARM64 inspect archives from
`coakka-publish`.

Detailed docs live under [docs/](docs/):

- [Introduction](docs/introduction.md)
- [Usage Guide](docs/usage.md)
- [Technical Notes](docs/technical-notes.md)

## Quick Check

Run the public sample wiring check:

```sh
bash run.sh runtime-inspect
bash run.sh runtime-inspect check
```

On macOS ARM64, Linux x86_64/ARM64, or Windows x86_64/ARM64, run the published
archive smoke:

```sh
bash run.sh runtime-inspect published-smoke
```

If a sibling native build is available, smoke the real inspect binary:

```sh
bash run.sh runtime-inspect local-smoke
```

To start the browser UI from that local binary:

```sh
bash run.sh runtime-inspect serve
```

To try inspect through Docker without installing the native binary on the host:

```sh
bash run.sh runtime-inspect docker-smoke
bash run.sh runtime-inspect docker-serve
```

`docker-smoke` builds a local image from the published Linux inspect archive and
runs command smoke inside the container. `docker-serve` exposes the browser UI
on `http://127.0.0.1:18080` by default.

For local/native smoke, the sample expects:

```text
../coakkaCoreNativeDev/build-v2/coakka-runtime-inspect
```

Override it with:

```sh
COAKKA_RUNTIME_INSPECT_BIN=/path/to/coakka-runtime-inspect \
  bash run.sh runtime-inspect local-smoke
```

## Boundary

`coakka-runtime-inspect` is not an admin dashboard, schema registry, service
mesh console, or topology authority. Runtime core owns topology, health,
pressure, route generation, and delivery outcomes. Inspect renders those facts
and provides a browser route-try form that can copy an equivalent
`coakka-client` command.

V1 `serve --connect host:port` configures the route-try request path. Remote
read/observe snapshots are still a future runtime surface; current snapshots
are explicitly labelled `local-linked-runtime`.

## Published Release

The current published inspect release is:

```text
coakka-runtime-inspect native UI
1.3.1+d7ab7fa release id for macOS ARM64
1.3.1+d7ab7fa release id for Linux x86_64
1.3.1+d7ab7fa release id for Linux ARM64
1.3.1+6c63864 release id for Windows x86_64
1.3.1+5c70234 release id for Windows ARM64
```

Direct download:

[coakka-runtime-inspect-v2-1.3.1-linux-aarch64.tar.gz](https://github.com/phuong-tran/coakka-publish/releases/download/coakka-public-artifacts-v1.3.1/coakka-runtime-inspect-v2-1.3.1-linux-aarch64.tar.gz)

[coakka-runtime-inspect-v2-1.3.1-linux-x86_64.tar.gz](https://github.com/phuong-tran/coakka-publish/releases/download/coakka-public-artifacts-v1.3.1/coakka-runtime-inspect-v2-1.3.1-linux-x86_64.tar.gz)

[coakka-runtime-inspect-v2-1.3.1-macos-aarch64.tar.gz](https://github.com/phuong-tran/coakka-publish/releases/download/coakka-public-artifacts-v1.3.1/coakka-runtime-inspect-v2-1.3.1-macos-aarch64.tar.gz)

[coakka-runtime-inspect-v2-1.3.1-windows-x86_64.tar.gz](https://github.com/phuong-tran/coakka-publish/releases/download/coakka-public-artifacts-v1.3.1/coakka-runtime-inspect-v2-1.3.1-windows-x86_64.tar.gz)

[coakka-runtime-inspect-v2-1.3.1-windows-aarch64.tar.gz](https://github.com/phuong-tran/coakka-publish/releases/download/coakka-public-artifacts-v1.3.1/coakka-runtime-inspect-v2-1.3.1-windows-aarch64.tar.gz)

Full release page and manifest:
[CoAkka Public Artifacts 1.3.1](https://github.com/phuong-tran/coakka-publish/releases/tag/coakka-public-artifacts-v1.3.1),
[public-artifacts.tsv](https://github.com/phuong-tran/coakka-publish/releases/download/coakka-public-artifacts-v1.3.1/public-artifacts.tsv)

## Docker

The Docker path is a sample convenience wrapper around the published Linux
inspect archive. It is not the canonical artifact download surface and it does
not move HTTP serving into runtime core.

The image installs the small OS shared-library dependency needed by the native
Linux inspect binary, so users do not have to prepare the host machine first.

Build and smoke the local image:

```sh
bash run.sh runtime-inspect docker-smoke
```

Run the browser UI:

```sh
bash run.sh runtime-inspect docker-serve
```

Override the image tag or host port:

```sh
COAKKA_RUNTIME_INSPECT_DOCKER_IMAGE=coakka-runtime-inspect-sample:local \
COAKKA_RUNTIME_INSPECT_DOCKER_PORT=18081 \
  bash run.sh runtime-inspect docker-serve
```

Run the published Docker Hub image without preparing a local artifact context:

```sh
bash run.sh runtime-inspect dockerhub-smoke
docker run --rm docker.io/gabrielgun1983/coakka-runtime-inspect-sample:1.3.1-d7ab7fa-remote
```

Serve the browser UI from Docker Hub:

```sh
bash run.sh runtime-inspect dockerhub-serve
docker run --rm -p 18080:18080 docker.io/gabrielgun1983/coakka-runtime-inspect-sample:1.3.1-d7ab7fa-remote serve
```
