# Native C/C++ Logger Basic

Builds one C executable and one C++ executable against the published native
logger archive.

Run from the repository root:

```sh
bash run.sh logger native basic
```

Expected output shape:

```text
coakka_logger_info abi=10 version=0.1.0 git=<git> language=c
coakka_logger_record sequence=1 level=info category=samples.logger.native.c.basic message={"event":"hello","language":"c"}
coakka_logger_stats emitted=1 delivered=1 dropped=0 language=c
coakka_logger_info abi=10 version=0.1.0 git=<git> language=cpp
coakka_logger_record sequence=1 level=info category=samples.logger.native.cpp.basic message={"event":"hello","language":"cpp"}
coakka_logger_stats emitted=1 delivered=1 dropped=0 language=cpp
```
