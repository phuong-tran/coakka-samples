# Node.js Logger Basic

This sample installs the published `coakka-logger-node` tarball into a temporary
npm project and runs a small logger flow.

It demonstrates:

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
bash run.sh logger node basic
```

Expected output shape:

```text
coakka_logger_info abi=10 version=0.1.0 git=ba2a66d98eb5
coakka_logger_record sequence=1 level=info category=samples.logger.node.basic message={"event":"hello","language":"node"}
coakka_logger_stats emitted=1 delivered=1 dropped=0
```
