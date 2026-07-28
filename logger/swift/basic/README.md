# Swift Logger Basic

This public logger sample installs `coakka-logger-swift@1.2.1` from the public
SwiftPM GitHub tag and writes one bounded logger record through the embedded
native logger package.

This sample covers:

- public SwiftPM package resolution from `github.com/phuong-tran/coakka-logger-swift`
- embedded macOS ARM64 native logger loading
- native logger version/git diagnostics
- one accepted `category + message` record
- manual drain for the record
- emitted/delivered/dropped counters

Run from this directory:

```sh
bash run.sh
```

Or from the repository root:

```sh
bash run.sh logger swift basic
```

Expected output shape:

```text
coakka_logger_info abi=10 version=1.2.1 git=f50756ebff0d
coakka_logger_record sequence=1 level=info category=samples.logger.swift.basic message={"event":"hello","language":"swift"}
coakka_logger_stats emitted=1 delivered=1 dropped=0
```
