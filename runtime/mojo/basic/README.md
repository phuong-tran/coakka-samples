# Mojo Runtime Basic

This sample unpacks the public native runtime archive, builds a tiny
sample-local C shim against the C ABI, and calls that shim from Mojo through
`std.ffi.OwnedDLHandle`.

The shim keeps this first Mojo slice small while still proving that a Mojo
program can load and execute a native runtime-backed smoke. It intentionally
does not cover deadletters, request/reply, packaging, or cluster behavior.

Run from the repository root:

```sh
bash run.sh runtime mojo basic
```

