# JVM Java Runtime Basic

This sample shows the Java API for the runtime v2 request/reply path. It starts
one same-process runtime participant, registers a Java handler, sends one typed ask,
and prints runtime/client counters.

Run:

```sh
bash run.sh runtime jvm java-basic
```

Expected output shape:

```text
coakka_runtime_info abi=1 version=0.2.0 git=<git> language=java
coakka_runtime_response payload={"echo":"hello-runtime-java"}
coakka_runtime_stats generation=1 routes=1 delivered=1 matchedResponses=1 language=java
```
