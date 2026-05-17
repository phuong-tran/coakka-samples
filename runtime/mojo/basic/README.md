# Mojo Runtime Basic

This sample unpacks the public Mojo source connector package and runs its
packaged runtime smoke through `std.ffi.OwnedDLHandle`.

The package-local shim keeps this first Mojo slice small while still proving
that a Mojo program can load and execute a native runtime-backed smoke. It
covers raw request/reply and route-miss deadletter, but intentionally does not
cover queue pressure or cluster behavior.

Run from the repository root:

```sh
bash run.sh runtime mojo basic
```
