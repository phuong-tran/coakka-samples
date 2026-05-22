# Runtime Base Image

This image packages the public native runtime artifact for container samples.
It is not a separate runtime contract; it is a Docker layer over the public
artifact manifest.

Current tag:

```text
docker.io/gabrielgun1983/runtime-base:0.2.0-c124a9e-remote
```

Current native artifact:

```text
runtime/native/releases/0.2.0+c124a9e/coakka-runtime-native-v2-0.2.0.tar.gz
```

Build and publish the multi-arch image from the repository root:

```sh
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -f containers/runtime-base/Dockerfile \
  --build-arg COAKKA_ARTIFACT_MANIFEST_SHA256=436742fd5e33ebff08e7e8dc06a3350aeed6e9817c790dfe708aad44482f4e64 \
  -t docker.io/gabrielgun1983/runtime-base:0.2.0-c124a9e-remote \
  --push \
  .
```

The image writes `/opt/coakka/runtime/runtime-base.env` and exposes the native
library at `/opt/coakka/runtime/libcoakka_runtime_v2.so`.
