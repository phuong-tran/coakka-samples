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

Run through the repository entrypoint. The default path uses prebuilt images
that already contain the native runtime:

```sh
bash run.sh containers spring-go
```

Default image tags:

```text
docker.io/gabrielgun1983/sample-spring-web:0.1.0-fbab60154993-remote
docker.io/gabrielgun1983/sample-go-store:0.1.0-fbab60154993-remote
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

This sample is intentionally JVM-based. Spring Boot native and Quarkus native
are planned as later native-image lanes, separate from this Docker wave.
