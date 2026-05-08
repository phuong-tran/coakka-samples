# JVM Java Runtime Deadletter

This sample shows the Java 8-friendly deadletter observer API. It subscribes
with a listener, sends one request to a missing route, and verifies that the
observed deadletter matches the failed request.

Run:

```sh
bash run.sh runtime jvm java-deadletter
```

Expected output shape:

```text
coakka_runtime_deadletter_observed matchedPending=true target=samples.runtime.jvm.java.deadletter.missing
coakka_runtime_stats matchedDeadletters=1
```
