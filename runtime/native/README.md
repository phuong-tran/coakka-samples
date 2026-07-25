# Native C/C++ Runtime v2 Samples

These samples consume the published runtime v2 native C/C++ archive from
`coakka-publish`.

The native package is not a Maven artifact. The sample runner resolves:

```text
runtime/native/releases/1.3.2+caff6d6d/coakka-runtime-native-v2-1.3.2.tar.gz
```

It then builds against the archive with CMake target:

```text
CoAkkaRuntimeNativeV2::runtime_v2
```

Run the native runtime sample:

```sh
bash run.sh runtime native basic
```

Watch the Native C/C++ runtime walkthrough:

![CoAkka Runtime Native C/C++ walkthrough](../../docs/assets/coakka-runtime-native.gif)

Full recording: [coakka-runtime-native.mp4](../../docs/assets/coakka-runtime-native.mp4)

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
