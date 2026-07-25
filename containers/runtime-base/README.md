# Runtime Base Image

This image packages the public native runtime artifact for container samples.
It is not a separate runtime contract; it is a Docker layer over the published
runtime archive.

The canonical native runtime archive, manifest, and checksum rows live in
[`coakka-publish`](https://github.com/phuong-tran/coakka-publish). This image is
only the container sample base that consumes that published runtime artifact.

Pinned local rebuild tag:

```text
coakka/runtime-base:1.3.2-caff6d6d-local
```

Published remote tag for this runtime-base line:

```text
docker.io/gabrielgun1983/runtime-base:1.3.2-caff6d6d-remote
```

Pinned native runtime artifact:

```text
runtime/native/releases/1.3.2+caff6d6d/coakka-runtime-native-v2-1.3.2.tar.gz
```

Build the local image from the repository root:

```sh
docker buildx build \
  --platform linux/arm64 \
  --load \
  -f containers/runtime-base/Dockerfile \
  --build-arg COAKKA_ARTIFACT_MANIFEST_SHA256=490340838d8f79df7cb5fbebc92893a1afd13c71f780b1489054bde6da2158ca \
  -t coakka/runtime-base:1.3.2-caff6d6d-local \
  .
```

Publish the refreshed multi-arch image line when a remote tag is ready:

```sh
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -f containers/runtime-base/Dockerfile \
  --build-arg COAKKA_ARTIFACT_MANIFEST_SHA256=490340838d8f79df7cb5fbebc92893a1afd13c71f780b1489054bde6da2158ca \
  --build-arg COAKKA_RUNTIME_GENERATION=1.3.2-caff6d6d-remote \
  -t docker.io/gabrielgun1983/runtime-base:1.3.2-caff6d6d-remote \
  --push \
  .
```

The image writes `/opt/coakka/runtime/runtime-base.env` and exposes the native
library at `/opt/coakka/runtime/libcoakka_runtime_v2.so`.
