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

Published remote image tags for this sample lane:

```text
docker.io/gabrielgun1983/sample-node-web:1.2.1-abde383-fa29f94-remote
docker.io/gabrielgun1983/sample-python-store:1.2.1-abde383-fa29f94-remote
```

Those tags are the published Node.js/Python container image line used by this
sample. The local rebuild path in this repository installs the pinned public
Node.js and Python connector artifact set `1.2.1+abde383-fa29f94` and loads the
native runtime base from
`docker.io/gabrielgun1983/runtime-base:1.2.1-abde383-remote`.
The repo-local rebuild path uses the same connector set over
`coakka/runtime-base:1.2.1-abde383-local` through `COAKKA_RUNTIME_LIB`.

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

Build the local image line after building `coakka/runtime-base:1.2.1-abde383-local`:

```sh
bash run.sh containers node-python build
```

The remote image line is still useful for the fastest first run. The local
image line is the repo-side rebuild path for this pinned connector generation.

Maintainer multi-arch publish commands:

```sh
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -f containers/node-python/Dockerfile.python \
  --build-arg COAKKA_RUNTIME_BASE_IMAGE=docker.io/gabrielgun1983/runtime-base:1.2.1-abde383-remote \
  --build-arg COAKKA_ARTIFACT_MANIFEST_SHA256=f66252f0f953ffb6ba4005d0c0650ef13f21b702974e390e606806d6d693a32e \
  --build-arg COAKKA_RUNTIME_GENERATION=1.2.1-abde383-fa29f94-remote \
  -t docker.io/gabrielgun1983/sample-python-store:1.2.1-abde383-fa29f94-remote \
  --push \
  .

docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -f containers/node-python/Dockerfile.node \
  --build-arg COAKKA_RUNTIME_BASE_IMAGE=docker.io/gabrielgun1983/runtime-base:1.2.1-abde383-remote \
  --build-arg COAKKA_ARTIFACT_MANIFEST_SHA256=f66252f0f953ffb6ba4005d0c0650ef13f21b702974e390e606806d6d693a32e \
  --build-arg COAKKA_RUNTIME_GENERATION=1.2.1-abde383-fa29f94-remote \
  -t docker.io/gabrielgun1983/sample-node-web:1.2.1-abde383-fa29f94-remote \
  --push \
  .
```
