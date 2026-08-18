# Bun Logger Basic

This sample installs `coakka-logger-bun@1.2.7` from npm into a temporary
Bun project and runs a small logger flow.

This sample covers:

- native logger version information
- one accepted `category + message` record
- manual drain for the record
- emitted/delivered/dropped counters

Run from this directory:

```sh
bash run.sh
```

Or from the repository root:

```sh
bash run.sh logger bun basic
```

Expected output shape:

```text
coakka_logger_info abi=10 version=1.2.1 git=f50756ebff0d
coakka_logger_record sequence=1 level=info category=samples.logger.bun.basic message={"event":"hello","language":"bun"}
coakka_logger_stats emitted=1 delivered=1 dropped=0
```
