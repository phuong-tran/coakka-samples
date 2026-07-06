# Runtime Base Image

This image packages the public native runtime artifact for container samples.
It is not a separate runtime contract; it is a Docker layer over the published
runtime archive.

Current local rebuild tag:

```text
coakka/runtime-base:1.2.1-abde383-local
```

Current published remote tag:

```text
docker.io/gabrielgun1983/runtime-base:1.2.1-abde383-remote
```

Current native artifact:

```text
runtime/native/releases/1.2.1+abde383/coakka-runtime-native-v2-1.2.1.tar.gz
```

Build the current local image from the repository root:

```sh
docker buildx build \
  --platform linux/arm64 \
  --load \
  -f containers/runtime-base/Dockerfile \
  --build-arg COAKKA_ARTIFACT_MANIFEST_SHA256=f66252f0f953ffb6ba4005d0c0650ef13f21b702974e390e606806d6d693a32e \
  -t coakka/runtime-base:1.2.1-abde383-local \
  .
```

Publish the refreshed multi-arch image line when a remote tag is ready:

```sh
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -f containers/runtime-base/Dockerfile \
  --build-arg COAKKA_ARTIFACT_MANIFEST_SHA256=f66252f0f953ffb6ba4005d0c0650ef13f21b702974e390e606806d6d693a32e \
  --build-arg COAKKA_RUNTIME_GENERATION=1.2.1-abde383-remote \
  -t docker.io/gabrielgun1983/runtime-base:1.2.1-abde383-remote \
  --push \
  .
```

The image writes `/opt/coakka/runtime/runtime-base.env` and exposes the native
library at `/opt/coakka/runtime/libcoakka_runtime_v2.so`.
