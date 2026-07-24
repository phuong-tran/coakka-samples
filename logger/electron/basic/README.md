# Electron Logger Basic

This sample installs `coakka-logger-electron@1.2.2` from npm into a
temporary Electron app. Renderer JavaScript sends one log intent through
preload/IPC; the main process owns the logger bridge.

Run from this directory:

```sh
bash run.sh
```

Or from the repository root:

```sh
bash run.sh logger electron basic
```

Expected output shape:

```text
coakka_logger_record sequence=1 level=info category=samples.logger.electron.basic message={"event":"hello","language":"electron"}
coakka_logger_stats emitted=1 delivered=1 dropped=0
```
