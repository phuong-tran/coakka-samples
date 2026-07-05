# Node.js Web To Python Store

This is the first container runtime sample. It runs two containers:

- `node-web`: browser UI and HTTP API on `http://localhost:8080`.
- `python-store`: runtime-owned customer store plus read-only UI on
  `http://localhost:8081`.

Business traffic crosses the runtime path:

```text
Browser -> Node HTTP edge -> CoAkka runtime -> Python store -> CoAkka runtime reply -> Node UI
```

The Python UI is observation-only. There is no Node-to-Python REST fallback.

Run through the repository entrypoint. The default path uses prebuilt images
that already contain the native runtime:

```sh
bash run.sh containers node-python
```

Currently available remote image tags:

```text
docker.io/gabrielgun1983/sample-node-web:0.2.0-b8ecfae-remote
docker.io/gabrielgun1983/sample-python-store:0.2.0-b8ecfae-remote
```

Those tags are the current published Node.js/Python container image line. The
local rebuild path in this repository installs the current public Node.js and
Python connector artifact set `0.2.0+c124a9e-c4be778` and loads the refreshed
native runtime from `docker.io/gabrielgun1983/runtime-base:0.2.0-b8ecfae-remote`.
The repo-local rebuild path uses the same connector set over
`coakka/runtime-base:0.2.0-b8ecfae-local` through `COAKKA_RUNTIME_LIB`.

Then open:

```text
http://localhost:8080
http://localhost:8081
```

Smoke a running stack:

```sh
bash run.sh containers node-python smoke
```

Stop this stack:

```sh
bash run.sh containers node-python down
```

Run Docker Compose directly:

```sh
docker compose -f containers/node-python/compose.yaml up
docker-compose -f containers/node-python/compose.yaml up
```

Run Podman Compose directly:

```sh
podman compose -f containers/node-python/compose.yaml up
podman-compose -f containers/node-python/compose.yaml up
```

The Dockerfiles are the image-source path for maintainers. They download the
public Node.js and Python runtime connector artifacts, verify them through the
public artifact manifest before install, then load the native runtime from the
pinned runtime-base image. The prebuilt images are the recommended user path.

If the selected runtime artifact cannot complete cross-process delivery, the
Node web UI shows the runtime error. The sample intentionally has no REST
fallback; a successful customer update must cross the runtime path.

Build the current local image line after building `coakka/runtime-base:0.2.0-b8ecfae-local`:

```sh
bash run.sh containers node-python build
```

The remote image line is still useful for the fastest first run. The local
image line is the current repo-side rebuild path.

Maintainer multi-arch publish commands:

```sh
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -f containers/node-python/Dockerfile.python \
  --build-arg COAKKA_RUNTIME_BASE_IMAGE=docker.io/gabrielgun1983/runtime-base:0.2.0-b8ecfae-remote \
  --build-arg COAKKA_ARTIFACT_MANIFEST_SHA256=6d8d533b3584f332be4eb19e42e46f82bead8242c2d1a4aed5cf141f98e8cdf2 \
  --build-arg COAKKA_RUNTIME_GENERATION=0.2.0-b8ecfae-remote \
  -t docker.io/gabrielgun1983/sample-python-store:0.2.0-b8ecfae-remote \
  --push \
  .

docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -f containers/node-python/Dockerfile.node \
  --build-arg COAKKA_RUNTIME_BASE_IMAGE=docker.io/gabrielgun1983/runtime-base:0.2.0-b8ecfae-remote \
  --build-arg COAKKA_ARTIFACT_MANIFEST_SHA256=6d8d533b3584f332be4eb19e42e46f82bead8242c2d1a4aed5cf141f98e8cdf2 \
  --build-arg COAKKA_RUNTIME_GENERATION=0.2.0-b8ecfae-remote \
  -t docker.io/gabrielgun1983/sample-node-web:0.2.0-b8ecfae-remote \
  --push \
  .
```
