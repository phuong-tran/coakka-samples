# Native C Logger Pressure

Builds a C executable against the published native logger archive and verifies
bounded queue pressure.

Run from the repository root:

```sh
bash run.sh logger native pressure
```

Expected output shape:

```text
coakka_logger_pressure attempts=8 accepted=2 rejected=6 capacity=2 highWatermark=2 language=c
coakka_logger_stats emitted=2 delivered=2 dropped=6 language=c
```
