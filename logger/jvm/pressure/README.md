# JVM Logger Pressure

This sample fills a small bounded logger queue without draining it first. The
native logger accepts records up to capacity, rejects later writes with
`queue_full`, and reports the rejected writes as dropped.

Run:

```sh
bash run.sh
```

Or from the repository root:

```sh
bash run.sh logger jvm pressure
```

For IDE runs, open/import the `coakka-samples` repository root as the Gradle
project.

Direct Gradle run from the repository root:

```sh
./gradlew :logger:jvm:pressure:run
```

Expected output shape:

```text
coakka_logger_pressure attempts=8 accepted=2 rejected=6 capacity=2 highWatermark=2
coakka_logger_stats emitted=2 delivered=2 dropped=6
```
