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
docker.io/gabrielgun1983/runtime-base:0.2.0-c124a9e-remote
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
  --build-arg COAKKA_ARTIFACT_MANIFEST_SHA256=862f002ab443c0e6f3607541131fac306d90f7ee208c3ed8b41c0a3d05570e5b \
  -t coakka/runtime-base:0.2.0-b8ecfae-local \
  .
```

Publish the refreshed multi-arch image line when a remote tag is ready:

```sh
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -f containers/runtime-base/Dockerfile \
  --build-arg COAKKA_ARTIFACT_MANIFEST_SHA256=862f002ab443c0e6f3607541131fac306d90f7ee208c3ed8b41c0a3d05570e5b \
  -t docker.io/gabrielgun1983/runtime-base:0.2.0-b8ecfae-remote \
  --push \
  .
```

The image writes `/opt/coakka/runtime/runtime-base.env` and exposes the native
library at `/opt/coakka/runtime/libcoakka_runtime_v2.so`.
