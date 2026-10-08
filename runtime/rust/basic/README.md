# Rust Runtime Basic

This runtime sample unpacks the public Rust `2.5.2` archive and runs its
packaged smoke binary. The package contains digest-verified native payloads for
macOS ARM64, Linux ARM64/x86-64, and Windows ARM64/x86-64. Matching-host Rust
execution is recorded separately from package contents.

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
runtime=2.5.0 git=<packaged-runtime-commit> lib=<packaged-native-library>
response={"echo":{"message":"hello-rust-runtime"}} delivered=1 matched=1 deadletters=1
```

The runner pins connector `2.5.2` over native Runtime `2.5.0+4b65d0b2`.
This sample pin is distinct from the newer catalog in
[Current Packages](../../../docs/current-packages.md). crates.io packaging is
a separate distribution step. Platform payload verification is not matching-host
Rust execution evidence; use the [package evidence ledger](../../../docs/runtime-package-platform-evidence.md)
for the exact generation and target being evaluated.
