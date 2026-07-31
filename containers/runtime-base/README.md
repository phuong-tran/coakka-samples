# Runtime Base Image

This image packages the public native runtime artifact for container samples.
It is not a separate runtime contract; it is a Docker layer over the published
runtime archive.

The canonical native runtime archive, manifest, and checksum rows live in
[`coakka-publish`](https://github.com/phuong-tran/coakka-publish). This image is
only the container sample base that consumes that published runtime artifact.

Pinned local rebuild tag:

```text
coakka/runtime-base:1.3.4-dc6ec284-local
```

Published remote tag for this runtime-base line:

```text
docker.io/gabrielgun1983/runtime-base:1.3.2-caff6d6d-remote
```

Pinned native runtime artifact:

```text
runtime/native/releases/1.3.4+dc6ec284/coakka-runtime-native-v2-1.3.4.tar.gz
```

Build the local image from the repository root:

```sh
docker buildx build \
  --platform linux/arm64 \
  --load \
  -f containers/runtime-base/Dockerfile \
  --build-arg COAKKA_ARTIFACT_MANIFEST_SHA256=918304c6c0944ff5ffadcf8e880cf4efa69c92b36bf70e049479514115f9f0dd \
  -t coakka/runtime-base:1.3.4-dc6ec284-local \
  .
```

The currently published remote image remains on the previous generation.
When a new remote image line is intentionally released, use a new tag instead
of overwriting it.

```sh
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -f containers/runtime-base/Dockerfile \
  --build-arg COAKKA_ARTIFACT_MANIFEST_SHA256=918304c6c0944ff5ffadcf8e880cf4efa69c92b36bf70e049479514115f9f0dd \
  --build-arg COAKKA_RUNTIME_GENERATION=1.3.4-dc6ec284-remote \
  -t docker.io/gabrielgun1983/runtime-base:1.3.4-dc6ec284-remote \
  --push \
  .
```

The image writes `/opt/coakka/runtime/runtime-base.env` and exposes the native
library at `/opt/coakka/runtime/libcoakka_runtime_v2.so`.
