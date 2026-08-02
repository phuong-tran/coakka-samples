# Runtime Base Image

This image packages the public native runtime artifact for container samples.
It is not a separate runtime contract; it is a Docker layer over the published
runtime archive.

The canonical native runtime archive, manifest, and checksum rows live in
[`coakka-publish`](https://github.com/phuong-tran/coakka-publish). This image is
only the container sample base that consumes that published runtime artifact.

Pinned local rebuild tag:

```text
coakka/runtime-base:1.4.0-2cee86bf-local
```

Published remote tag for this runtime-base line:

```text
docker.io/gabrielgun1983/runtime-base:1.3.2-caff6d6d-remote
```

Pinned native runtime artifact:

```text
runtime/native/releases/1.4.0+2cee86bf/coakka-runtime-native-v2-1.4.0.tar.gz
```

Build the local image from the repository root:

```sh
docker buildx build \
  --platform linux/arm64 \
  --load \
  -f containers/runtime-base/Dockerfile \
  --build-arg COAKKA_ARTIFACT_MANIFEST_SHA256=d8a35276cf9f014bb57c535cc5651d61c84aaaa731266f0bb4ad3aedb548f8cb \
  -t coakka/runtime-base:1.4.0-2cee86bf-local \
  .
```

The currently published remote image remains on the previous multi-arch
generation. The `1.4.0` local rebuild is Linux ARM64 because that is the Linux
platform present in the exact native artifact matrix. Do not label an emulated
or missing Linux x86-64 binary as part of this generation.

The image writes `/opt/coakka/runtime/runtime-base.env` and exposes the native
library at `/opt/coakka/runtime/libcoakka_runtime_v2.so`.
