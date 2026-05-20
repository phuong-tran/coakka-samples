# JVM Runtime Basic

This sample runs a same-process request/reply echo through the published JVM runtime v2
artifact.

It demonstrates:

- embedded native runtime loading
- `CoAkka.local(...)` as the Kotlin first-run API
- one plain-text local handler
- one request/reply round trip without explicit route or endpoint wiring

Run:

```sh
bash run.sh
```

Or from the repository root:

```sh
bash run.sh runtime basic
```

For IDE runs, open/import the `coakka-samples` repository root as the Gradle
project.

Direct Gradle run from the repository root:

```sh
./gradlew :runtime:jvm:basic:run
```

Expected output shape:

```text
coakka_runtime_response payload=hello-runtime-jvm
```
