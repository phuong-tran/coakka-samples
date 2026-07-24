# Spring Boot JVM Web To Go Store

This container sample runs two containers:

- `spring-web`: Spring Boot JVM browser UI and HTTP API on
  `http://localhost:8090`.
- `go-store`: runtime-owned customer store plus read-only UI on
  `http://localhost:8091`.

Business traffic crosses the runtime path:

```text
Browser -> Spring Boot HTTP edge -> CoAkka runtime -> Go store -> CoAkka runtime reply -> Spring Boot UI
```

The Go UI is observation-only. There is no Spring-to-Go REST fallback.

The repo-local Spring Boot and Go sample sources track Spring starter
`1.3.1-g0da8c2d9-8ff6f32` and Go runtime package `1.3.1+0da8c2d9-8ff6f32`. The old
Spring-Go container image line is intentionally not used by default anymore,
because it was built from an earlier runtime generation.

Until refreshed Spring-Go images are published, use the source scenario for
this topology:

```sh
bash run.sh scenario customer-crud spring-boot-go dev
```

The recommended container path for this release train is the verified
Node.js/Python container sample:

```sh
bash run.sh containers node-python
```

If a maintainer supplies refreshed images explicitly, the compose path remains:

```sh
COAKKA_SAMPLE_SPRING_WEB_IMAGE=<spring-web-1.3.1-image> \
COAKKA_SAMPLE_GO_STORE_IMAGE=<go-store-1.3.1-image> \
bash run.sh containers spring-go
```

Then open:

```text
http://localhost:8090
http://localhost:8091
```

Smoke a running stack:

```sh
bash run.sh containers spring-go smoke
```

Stop this stack:

```sh
bash run.sh containers spring-go down
```

Run Docker Compose directly:

```sh
docker compose -f containers/spring-go/compose.yaml up
docker-compose -f containers/spring-go/compose.yaml up
```

Run Podman Compose directly:

```sh
podman compose -f containers/spring-go/compose.yaml up
podman-compose -f containers/spring-go/compose.yaml up
```

This sample is intentionally JVM-based. Framework native-image builds are not
the default container path; they remain optional research if a concrete
deployment case needs that packaging shape.
