# Mojo Runtime Basic

This sample keeps the runnable Mojo entrypoint in this repository while using
the public Mojo source connector package for the reusable C shim and bundled
native runtime library.

The package-provided shim keeps this first Mojo slice small while still proving
that a Mojo program can load and execute a native runtime-backed sample through
`std.ffi.OwnedDLHandle`. It covers raw request/reply and route-miss deadletter,
but intentionally does not cover queue pressure or cluster behavior.

Run from the repository root:

```sh
bash run.sh runtime mojo basic
```
