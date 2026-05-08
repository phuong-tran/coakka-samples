# Rust Runtime Basic

This sample unpacks the published Rust runtime spike tarball and runs its
packaged smoke binary.

It demonstrates:

- Rust package install from `coakka-publish`
- embedded native runtime loading from the package
- one local route target owned by the Rust process
- request/reply from Rust into a registered runtime handler
- route-miss deadletter handling without an internal REST endpoint
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
runtime=0.1.0 git=<git> lib=<packaged-native-library>
response={"echo":{"message":"hello-rust-runtime"}} delivered=1 matched=1 deadletters=1
```

This is still a spike package, not a crates.io-ready API.
