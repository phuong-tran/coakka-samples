# Bun Logger Pressure

This sample fills a small bounded logger queue without draining it first. The
native logger accepts records up to capacity, rejects later writes with
`queue_full`, and reports the rejected writes as dropped.

Run from this directory:

```sh
bash run.sh
```

Or from the repository root:

```sh
bash run.sh logger bun pressure
```

Expected output shape:

```text
coakka_logger_pressure attempts=8 accepted=2 rejected=6 capacity=2 highWatermark=2
coakka_logger_stats emitted=2 delivered=2 dropped=6
```
