# JVM Runtime Basic

This sample runs a same-process request/reply echo through the published JVM runtime v2
artifact.

It demonstrates:

- embedded native runtime loading
- runtime version/git diagnostics
- one process-owned route and one process-owned handler
- one request/reply round trip
- basic route/client counters

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
coakka_runtime_info abi=1 version=0.2.0 git=<git>
coakka_runtime_response payload={"echo":"hello-runtime-jvm"}
coakka_runtime_stats generation=1 routes=1 delivered=1 matchedResponses=1
```
