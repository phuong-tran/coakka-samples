# Native C/C++ Runtime v2 Basic

Builds one C executable and one C++ executable against the published runtime v2
native archive.

Run from the repository root:

```sh
bash run.sh runtime native basic
```

Expected output shape:

```text
coakka_runtime_info abi=1 version=1.4.1 git=<git> language=c
coakka_runtime_stats generation=1 routes=1 routeMisses=1 deadletters=1 language=c
coakka_runtime_info abi=1 version=1.4.1 git=<git> language=cpp
coakka_runtime_stats generation=1 routes=1 routeMisses=1 deadletters=1 language=cpp
```
