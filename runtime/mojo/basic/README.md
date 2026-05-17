# Mojo Runtime Basic

This sample keeps the runnable Mojo entrypoint and C shim in this repository
while using the public Mojo source connector package for the bundled native
runtime library.

The sample-local shim keeps this first Mojo slice explicit without forcing the
current Mojo FFI surface to model the full runtime C ABI directly. It still
proves that a Mojo program can load and execute a native runtime-backed sample
through `std.ffi.OwnedDLHandle`. It covers raw request/reply and route-miss
deadletter, but intentionally does not cover queue pressure or cluster
behavior.

Run from the repository root:

```sh
bash run.sh runtime mojo basic
```
