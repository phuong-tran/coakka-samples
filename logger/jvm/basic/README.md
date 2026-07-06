# JVM Logger Basic

This sample loads the published JVM logger jar, prints native version
diagnostics, emits one INFO record, drains it manually, and prints counters.

Run from this directory:

```sh
bash run.sh
```

Run from the repository root:

```sh
bash run.sh logger basic
```

For IDE runs, open/import the `coakka-samples` repository root as the Gradle
project. Opening only this subdirectory leaves the sample without the root
Gradle wrapper and included-project settings.

Direct Gradle run from the repository root:

```sh
./gradlew :logger:jvm:basic:run
```

Expected output shape:

```text
coakka_logger_info abi=10 version=1.2.1 git=<git>
coakka_logger_record sequence=1 level=info category=samples.logger.jvm.basic message={"event":"hello","language":"jvm"}
coakka_logger_stats emitted=1 delivered=1 dropped=0
```
