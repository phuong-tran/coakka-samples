# Native C/C++ Runtime v2 Samples

These samples consume the published runtime v2 native C/C++ archive from
`coakka-publish`.

The native package is not a Maven artifact. The sample runner resolves:

```text
runtime/native/releases/0.1.0+65b36b8ad2ec/coakka-runtime-native-v2-0.1.0.tar.gz
```

It then builds against the archive with CMake target:

```text
CoAkkaRuntimeNativeV2::runtime_v2
```

Run the native runtime sample:

```sh
bash run.sh runtime native basic
```

The sample uses the public C ABI directly from both C and C++. It starts a
runtime, applies a route snapshot, submits a one-way request to a missing route,
and verifies route-miss/deadletter diagnostics.
