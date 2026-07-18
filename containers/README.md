# Container Samples

Container samples show the runtime boundary without requiring every host
language toolchain on the user's machine.

Docker images in this directory are a runnable sample UX, not the canonical
artifact distribution channel. Public archives, packages, manifests, and
checksums live in
[`coakka-publish`](https://github.com/phuong-tran/coakka-publish); container
images consume those artifacts to make first-run checks faster.

Available samples:

```sh
bash run.sh containers node-python
bash run.sh containers spring-go
```

Stop all running container samples:

```sh
bash run.sh containers down
```

Direct runtime equivalents:

```sh
docker compose -f containers/node-python/compose.yaml up
podman compose -f containers/node-python/compose.yaml up
podman-compose -f containers/node-python/compose.yaml up
docker compose -f containers/spring-go/compose.yaml up
podman compose -f containers/spring-go/compose.yaml up
podman-compose -f containers/spring-go/compose.yaml up
```

The first sample is intentionally small: a Node.js web process sends customer
commands through the CoAkka runtime to a Python store process. The Node UI is
the browser edge; the Python UI is a read-only view of store state changed by
runtime messages.

The Spring Boot JVM to Go sample uses the same visible two-container shape with
a framework web edge and a Go store. Framework native-image builds are not the
default container path; they remain optional research if a concrete deployment
case needs that packaging shape.
