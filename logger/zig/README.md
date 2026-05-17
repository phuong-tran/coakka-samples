# Zig Logger Samples

Zig logger samples use the published native logger archive through the public C
ABI. The current basic sample emits one record, drains it manually, and reads
the bounded logger counters.

Run the basic sample:

```sh
bash run.sh logger zig basic
```
