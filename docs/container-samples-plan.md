# Container Samples Plan

This note records the next sample direction: Docker/Podman-first public
samples that make the CoAkka runtime boundary visible without asking users to
install every host-language toolchain.

## Position

The first container sample should be intentionally small:

```text
Node.js client process
  -> CoAkka runtime delivery
  -> Python service process
  -> response back to Node.js
```

The goal is not to prove every framework at once. The goal is to prove that two
real processes, in two different language hosts, can communicate through the
same runtime contract.

## Why Containers

Public users may come from Java, Go, Python, Node.js, C#, Rust, or native C/C++.
Requiring them to install several toolchains before seeing one runtime delivery
path creates avoidable friction.

Containers give a shorter first proof:

```sh
docker compose up --build
```

or:

```sh
podman compose up --build
```

The sample design should not require Docker Desktop. It should work with Docker
Engine plus `docker compose`, or with Podman plus `podman compose` or
`podman-compose` where available. Enterprise users can choose the container
runtime approved by their environment.

## Wave Plan

| Wave | Sample | Purpose |
| --- | --- | --- |
| 1 | Node.js client -> Python service | smallest cross-language, cross-process proof |
| 2 | Python -> Node.js or Go variant | show the pattern is not tied to one caller |
| 3 | Spring Boot / Quarkus | richer framework and business workflow demos |

Wave 1 should not wait for the full matrix. One clear two-container sample is
more useful than many heavy examples that are hard to run.

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

## Expected Output

The demo should tell the story in logs:

```text
python-service | ready: target samples.customer.create
node-client    | send: customer.create {"name":"Ada"}
python-service | handled: customer.create
node-client    | recv: customer.created {"id":"cus_001","name":"Ada"}
```

The important proof:

- two containers
- two processes
- two language hosts
- one runtime delivery path
- explicit failure if runtime delivery fails

No benchmark claims should be made from this sample.

## Runtime Rules

- use public artifacts only
- verify artifact checksums through the existing manifest path
- do not bake private/local paths into images
- keep Docker/Podman support as a sample UX layer, not the source of truth
- do not require Docker Desktop
- keep framework-heavy samples such as Spring Boot for a later wave

## Future Commands

The final command shape can be decided during implementation, but the target UX
should be close to:

```sh
bash run.sh containers node-python
```

with direct runtime equivalents documented:

```sh
docker compose -f containers/node-python/compose.yaml up --build
podman compose -f containers/node-python/compose.yaml up --build
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
