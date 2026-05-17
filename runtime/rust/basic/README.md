# Rust Runtime Basic

This public runtime sample unpacks the Rust runtime spike tarball from the
public artifact surface and runs its packaged smoke binary.

It demonstrates:

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
runtime=0.2.0 git=<git> lib=<packaged-native-library>
response={"echo":{"message":"hello-rust-runtime"}} delivered=1 matched=1 deadletters=1
```

This is still a spike package, not a crates.io-ready API.
