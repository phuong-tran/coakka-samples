# Native C/C++ Runtime v2 Samples

These samples consume the published runtime v2 native C/C++ archive from
`coakka-publish`.

The native package is not a Maven artifact. On macOS ARM64 and Linux
ARM64/x86-64, the sample runner resolves:

```text
runtime/native/releases/2.3.0+a83ab412/coakka-runtime-native-v2-2.3.0.tar.gz
```

The same archive contains the exact five-platform matrix; the sample runner
selects the native payload for the current host and reports its loaded runtime
identity.

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

The two-process Raspberry Pi camera sample is under
[`rpi-camera/`](rpi-camera/README.md). It provides a Pi publisher and a
macOS/Linux/Windows host gateway with named CLI options for camera identity,
V4L2 device selection, Stream/control ports, browser port, resolution, and
recording with optional audio.

Prebuilt `1.1.0` evaluation archives live only in
`coakka-publish/samples/runtime/native/rpi-camera/releases/1.1.0/`. This sample
directory contains source and build instructions; it does not duplicate the
binaries.

The pressure sample uses the public C ABI from C with `queueCapacity=2` and
`strictNoDrop=true`. It submits a burst through the runtime request pipe and
verifies that bounded queue pressure becomes queue-rejected deadletters and
counters instead of silent drops or unbounded growth.

This is the current source of runtime intake-pressure evidence in the public
sample set. Language connector samples should not copy this result by wrapping
the C sample; they need connector-owned pressure hooks so the result proves the
connector boundary as well as the native runtime boundary.

## Continue Integrating

Use this lane's runnable sample as the source for package imports and basic
lifecycle names. Before generating connection strategy, TLS/mTLS, File Lane, or
Stream Lane code, follow [AI-Assisted Integration](../../docs/ai-assisted-integration.md).
It links the canonical feature guides, exact package catalog, and platform
evidence, and it defines when only workflow pseudocode is justified.

The current public package train includes File Lane and Stream Lane. Use the
exact public contract and lifecycle rules shipped by the `2.3.0` archive when
integrating either lane.
