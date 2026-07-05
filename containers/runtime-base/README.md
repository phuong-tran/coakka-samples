# Runtime Base Image

This image packages the public native runtime artifact for container samples.
It is not a separate runtime contract; it is a Docker layer over the published
runtime archive.

Current local rebuild tag:

```text
coakka/runtime-base:0.2.0-b8ecfae-local
```

Current published remote tag:

```text
docker.io/gabrielgun1983/runtime-base:0.2.0-b8ecfae-remote
```

Current native artifact:

```text
runtime/native/releases/0.2.0+b8ecfae/coakka-runtime-native-v2-0.2.0.tar.gz
```

Build the current local image from the repository root:

```sh
docker buildx build \
  --platform linux/arm64 \
  --load \
  -f containers/runtime-base/Dockerfile \
  --build-arg COAKKA_ARTIFACT_MANIFEST_SHA256=2d840db77778b9e2b2b320e019c93da0ce13f569bb304f40fba54ee90aa8ede6 \
  -t coakka/runtime-base:0.2.0-b8ecfae-local \
  .
```

Publish the refreshed multi-arch image line when a remote tag is ready:

```sh
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -f containers/runtime-base/Dockerfile \
  --build-arg COAKKA_ARTIFACT_MANIFEST_SHA256=6d8d533b3584f332be4eb19e42e46f82bead8242c2d1a4aed5cf141f98e8cdf2 \
  --build-arg COAKKA_RUNTIME_GENERATION=0.2.0-b8ecfae-remote \
  -t docker.io/gabrielgun1983/runtime-base:0.2.0-b8ecfae-remote \
  --push \
  .
```

The image writes `/opt/coakka/runtime/runtime-base.env` and exposes the native
library at `/opt/coakka/runtime/libcoakka_runtime_v2.so`.
