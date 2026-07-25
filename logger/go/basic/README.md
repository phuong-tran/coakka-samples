# Go Logger Basic

This sample installs `github.com/phuong-tran/coakka-logger-go@v1.2.5` into a
temporary Go module and runs a small logger flow through the embedded native
logger package.

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
bash run.sh logger go basic
```

Expected output shape:

```text
coakka_logger_info abi=10 version=1.2.1 git=f50756ebff0d
coakka_logger_record sequence=1 level=info category=samples.logger.go.basic message={"event":"hello","language":"go"}
coakka_logger_stats emitted=1 delivered=1 dropped=0
```
