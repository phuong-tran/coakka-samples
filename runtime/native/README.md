# Native C/C++ Runtime v2 Samples

These samples consume the published runtime v2 native C/C++ archive from
`coakka-publish`.

The native package is not a Maven artifact. The sample runner resolves:

```text
runtime/native/releases/0.1.0+a671b3a/coakka-runtime-native-v2-0.1.0.tar.gz
```

It then builds against the archive with CMake target:

```text
CoAkkaRuntimeNativeV2::runtime_v2
```

Run the native runtime sample:

```sh
bash run.sh runtime native basic
```

The basic sample uses the public C ABI directly from both C and C++. It starts
a runtime, applies a route snapshot, submits a one-way request to a missing
route, and verifies route-miss/deadletter diagnostics.

Run the native pressure sample:

```sh
bash run.sh runtime native pressure
```

The pressure sample uses the public C ABI from C with `queueCapacity=2` and
`strictNoDrop=true`. It submits a burst through the runtime request pipe and
verifies that bounded queue pressure becomes queue-rejected deadletters and
counters instead of silent drops or unbounded growth.

This is the current source of runtime intake-pressure evidence in the public
sample set. Language connector samples should not copy this result by wrapping
the C sample; they need connector-owned pressure hooks so the result proves the
connector boundary as well as the native runtime boundary.
