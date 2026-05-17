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

Default image tags:

```text
docker.io/gabrielgun1983/sample-node-web:0.1.0-fbab60154993-remote
docker.io/gabrielgun1983/sample-python-store:0.1.0-fbab60154993-remote
```

These prebuilt image tags are the current public container images. The
Dockerfile source path targets the current runtime connector artifact set
`0.2.0+94a5729`; build it only when a matching `coakka/runtime-base` image is
available locally or in the selected registry.

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

Build locally only when a matching runtime-base image and matching connector
artifacts are available:

```sh
bash run.sh containers node-python build
```

Prebuilt images are for the fastest first run after the image tags are
published.
