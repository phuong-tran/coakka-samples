# Native C Runtime v2 Pressure

Builds one C executable against the published runtime v2 native archive and
forces bounded queue pressure through the public C ABI.

Run from the repository root:

```sh
bash run.sh runtime native pressure
```

Expected output shape:

```text
coakka_runtime_info abi=1 version=1.3.1 git=<git> language=c
coakka_runtime_pressure attempts=64 delivered=<n> rejected=<n> capacity=2 highWatermark=<n> language=c
coakka_runtime_stats generation=1 routes=1 queueRejected=<n> deadletters=<n> language=c
```

The sample uses `queueCapacity=2` and `strictNoDrop=true`. Runtime pressure is
expected to surface as queue-rejected deadletters and queue rejection counters,
not as silent drops or unbounded queue growth.
