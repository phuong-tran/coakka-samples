# JVM Java Logger Basic

This sample shows the Java API for the JVM logger jar. It prints native logger
info, emits one INFO record, drains it, and prints counters.

Run:

```sh
bash run.sh logger jvm java-basic
```

Expected output shape:

```text
coakka_logger_info abi=10 version=1.2.1 git=<git> language=java
coakka_logger_record sequence=1 level=info category=samples.logger.jvm.java.basic message={"event":"hello","language":"java"}
coakka_logger_stats emitted=1 delivered=1 dropped=0 language=java
```
