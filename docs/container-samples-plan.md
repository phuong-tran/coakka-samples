# Container Samples Plan

This note records the next sample direction: Docker/Podman-first public
samples that make the CoAkka runtime boundary visible without asking users to
install every host-language toolchain.

## Position

The first container sample should be intentionally small:

```text
Node.js web process
  -> CoAkka runtime delivery
  -> Python store process
  -> response back to Node.js UI
```

The goal is not to cover every framework at once. The goal is to show that two
real processes, in two different language hosts, communicate through the same
runtime contract while a user can see state change in the browser.

The next container sample keeps that same product shape but uses a framework web
edge and a Go store:

```text
Spring Boot JVM web process
  -> CoAkka runtime delivery
  -> Go store process
  -> response back to Spring Boot UI
```

## Why Containers

Public users may come from Java, Go, Python, Node.js, C#, Rust, Mojo, Zig, or native C/C++.
Requiring them to install several toolchains before seeing one runtime delivery
path creates avoidable friction.

Containers give a shorter first-run path:

```sh
docker compose up
```

or:

```sh
podman compose up
```

The sample design should not require Docker Desktop. It should work with Docker
Engine plus `docker compose`, or with Podman plus `podman compose` or
`podman-compose` where available. Enterprise users can choose the container
runtime approved by their environment.

## Wave Plan

| Wave | Sample | Purpose |
| --- | --- | --- |
| 1 | Node.js web -> Python store | smallest visible cross-language, cross-process runtime path; sample lives in `containers/node-python` |
| 1b | Spring Boot JVM web -> Go store | visible framework web edge plus Go store UI; compose skeleton exists, refreshed image-source path pending |
| 2 | Python -> Node.js or Go variant | show the pattern is not tied to one caller |
| 3 | Optional native-image research | only if a concrete deployment case makes it useful |

Wave 1 should not wait for the full matrix. One clear two-container sample is
more useful than many heavy examples that are hard to run.

Current wave 1 command:

```sh
bash run.sh containers node-python
```

## Image Strategy

Prefer boring, reliable images first:

```text
node:<version>-slim
python:<version>-slim
```

Do not optimize for the smallest image in the first wave. The public sample
should favor:

- reliable first run
- readable Dockerfile/Compose files
- predictable native library loading
- CA certificates and DNS behavior that work by default
- clear logs showing request and response

Optimized images, distroless images, and shared runtime base images can come
later.

## Prebuilt Docker Hub Images

Prebuilt images are the fastest product-shipping path for developer rollout.
They let a user try the runtime before building native runtime artifacts or
language connector packages locally.

The image strategy should have two layers:

```text
docker.io/gabrielgun1983/runtime-base:<runtime-generation>
  -> public native runtime artifacts
  -> required native runtime dependencies
  -> wire runtime/build bits when needed by samples
  -> runtime transport dependencies when needed by the selected runtime flavor
  -> CA certificates
  -> version/diagnostic metadata

docker.io/gabrielgun1983/sample-node-web:<runtime-generation>
  -> small Node.js web edge

docker.io/gabrielgun1983/sample-python-store:<runtime-generation>
  -> small Python store service

docker.io/gabrielgun1983/sample-spring-web:<runtime-generation>
  -> Spring Boot JVM web edge

docker.io/gabrielgun1983/sample-go-store:<runtime-generation>
  -> Go store service
```

The public artifact manifest remains the source of truth. Docker Hub images are
preassembled runtime environments built from those public artifacts, not a
separate contract.

Recommended tag discipline:

```text
<artifact-generation>-remote
<artifact-generation>-remote-local
<artifact-generation>-remote-linux-amd64
<artifact-generation>-remote-linux-arm64
```

Documentation should use pinned tags. `latest` can exist for convenience, but
it should not be the primary sample path.

Images should carry OCI labels and CoAkka-specific metadata:

```text
org.opencontainers.image.version
org.opencontainers.image.revision
org.opencontainers.image.source
coakka.runtime.generation
coakka.artifact.manifest.sha256
```

Release rules:

- build images only from scanner-clean public artifacts
- publish multi-arch images when both `linux/amd64` and `linux/arm64` are ready
- do not bake local paths, local cache paths, or credentials into image layers
- keep transport-specific backend flavors separate until their
  public surface is explicitly safe
- sample startup should print/verify the runtime generation it loaded

This lets the first public container path become:

```sh
docker compose -f containers/node-python/compose.yaml up
docker compose -f containers/spring-go/compose.yaml up
```

or:

```sh
podman-compose -f containers/node-python/compose.yaml up
podman-compose -f containers/spring-go/compose.yaml up
```

The two-container view is the public path for wave 1. Prebuilt images remove
the slowest part of the first-run experience.

## Expected Output

The sample flow should tell the story in logs:

```text
python-store | ready: http://localhost:8081
node-web     | ready: http://localhost:8080
python-store | handled: type=samples.container.customer.create.request.v1
```

The important product behavior:

- two containers
- two processes
- two language hosts
- browser-visible store update
- one runtime delivery path
- explicit failure if runtime delivery fails
- no REST fallback path

This sample is a product walkthrough, not a benchmark source. Benchmark claims
belong in benchmark artifacts with metadata.

## Runtime Rules

- use public artifacts only
- verify artifact checksums through the existing manifest path
- do not bake local paths into images
- prefer pinned Docker Hub tags for public docs once images are published
- keep Docker/Podman support as a sample UX layer, not the source of truth
- do not require Docker Desktop
- keep framework native-image builds out of the default container path
- treat framework native-image support as optional research, not a public
  sample promise

## Future Commands

The final command shape can be decided during implementation, but the target UX
should be close to:

```sh
bash run.sh containers node-python
```

with direct runtime equivalents documented:

```sh
docker compose -f containers/node-python/compose.yaml up
podman compose -f containers/node-python/compose.yaml up
docker compose -f containers/spring-go/compose.yaml up
podman compose -f containers/spring-go/compose.yaml up
```

If the local environment uses `podman-compose`, document that alternative too.

## Open Questions

- whether the first sample should use two containers or one container running
  two processes if the public cross-container transport needs more hardening
- whether the sample should rely on the existing public TCP-capable runtime
  artifact generation or wait for the next refresh
- how much artifact caching should be done inside Docker build layers
- whether CI should run the container sample on every push or only as a manual
  smoke path at first
