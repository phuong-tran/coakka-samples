# JVM Java Logger Pressure

This sample shows bounded-queue behavior from Java. It starts a logger with
capacity `2`, writes eight records without draining first, verifies rejected
writes, drains accepted records, and prints dropped counters.

Run:

```sh
bash run.sh logger jvm java-pressure
```

Expected output shape:

```text
coakka_logger_pressure attempts=8 accepted=2 rejected=6 capacity=2 highWatermark=2 language=java
coakka_logger_stats emitted=2 delivered=2 dropped=6 language=java
```
