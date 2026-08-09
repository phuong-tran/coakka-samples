# Rust Runtime Basic

This runtime sample unpacks the public Rust `1.4.1` archive and runs its
packaged smoke binary. The published archive executes on macOS ARM64. On Linux,
that immutable archive passes payload checks but cannot load because its Unix
loader encoded the macOS `RTLD_LOCAL` value for every Unix target. The current
connector source uses the correct target-specific value and passes native
request/reply on Linux ARM64 and x86-64; a later package must carry that fix.

This sample covers:

- Rust package install from the public artifact surface
- embedded native runtime loading from the package
- one process-owned route target owned by the Rust process
- request/reply from Rust into a registered runtime handler
- route-miss deadletter handling without a backend HTTP endpoint
- runtime info/config and client request/reply counters

Run from this directory:

```sh
bash run.sh
```

Or from the repository root:

```sh
bash run.sh runtime rust basic
```

Expected output shape:

```text
CoAkka Rust runtime smoke ok
runtime=1.4.1 git=<git> lib=<packaged-native-library>
response={"echo":{"message":"hello-rust-runtime"}} delivered=1 matched=1 deadletters=1
```

This is the pinned published archive line; crates.io packaging is a separate
distribution step. Do not use Linux payload presence as evidence that the
published `1.4.1` Rust loader executed there.
