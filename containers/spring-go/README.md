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

Run through the repository entrypoint. The default path uses prebuilt Spring-Go
images that already contain the native runtime:

```sh
bash run.sh containers spring-go
```

Currently available remote image tags:

```text
docker.io/gabrielgun1983/sample-spring-web:0.1.0-fbab60154993-remote
docker.io/gabrielgun1983/sample-go-store:0.1.0-fbab60154993-remote
```

The repo-local Spring Boot and Go sample sources already track Spring starter
`0.2.0-g11071541ea78` and Go runtime package `0.2.0+c124a9e-66ebe58`. The
published Spring-Go container image line has not been refreshed onto that train
yet, so the current cross-process container path to open first is the
Node.js/Python sample from the repository root:

```sh
bash run.sh containers node-python
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
