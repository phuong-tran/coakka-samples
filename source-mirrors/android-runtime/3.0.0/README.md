# CoAkka Runtime Android 3.0.0 Source Mirror

This is the pending source projection for
`io.github.phuong-tran.coakka:coakka-runtime-android:3.0.0`. It contains the
Kotlin connector, JNI adapter, build material, tests and exact-AAR consumer app.
Android is explicitly preview, with no official Android platform-support
claim. The version is not published. Historical `1.2.0` source and artifacts remain
separate and immutable.

`release-identity.properties` owns connector `3.0.0`, Core `3.0.0`, the exact
Core source commit, and all four packaged Android ABIs. `SOURCE-MANIFEST.sha256`
records projected build and source bytes. Verify a prepared projection with:

```sh
./verify-source-checksum.sh
```

The Apache-2.0 connector source projection contains no Core source or native
image. Native artifacts have the separately scoped
[CoAkka Native Artifact License 1.2](https://github.com/phuong-tran/coakka-samples/blob/d401e4a62359dcf245ffda093f2cbea21fe5f39a/NATIVE-LICENSE.md);
the [package licensing guide](https://github.com/phuong-tran/coakka-samples/blob/d401e4a62359dcf245ffda093f2cbea21fe5f39a/docs/package-licensing.md)
explains the applicable files. Product version `3.0.0` does not change that
license version.

## Build and verify

A full rebuild requires an authorized, clean Core checkout at the exact
`core.commit`, plus the qualified native build inputs. Point the connector at
that separate checkout; keep Core implementation source outside this projection.

```sh
export ANDROID_SDK_ROOT=/absolute/path/to/Android/sdk
export COAKKA_CORE_REPO=/absolute/path/to/separate-clean-coakka-core
./gradlew testDebugUnitTest assembleRelease lintRelease --no-daemon
```

The build extracts immutable Core source rather than compiling a rolling
working tree. It packages one Core image and one JNI image for each of
`arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64`. The Android minimum is API 24.

Core owns effective runtime configuration, request admission, routing, reply
correlation, timeouts and shutdown. Kotlin submits intent and projects copied
Core facts. Omitted CPU and connection options resolve to Core's `AUTO` and
`MULTIPLEXED` defaults. Monitoring is off unless requested. File and Stream
lanes remain independent bounded resources. Request/reply applications use
`AndroidRuntimeMessages` without an application codec dependency.

The AAR carries separate clean connector and exact Core identities in
`assets/coakka/runtime-package.json`. Release builds use R8, preserve the
published Kotlin/Java API and the narrowly required native lookups, and inspect
all eight native images. Private build evidence and symbol companions are kept
separately from the consumer package.

Run the installed consumer against the exact AAR and a named online target:

```sh
scripts/run-device-smoke.sh \
  build/outputs/aar/coakka-runtime-android-release.aar \
  emulator-5554
```

The consumer checks Runtime request/reply, startup facts, File and Stream
behavior through the minified package. The user-accepted preview gate is a
functional smoke of the final AAR on one existing Mac emulator. Extra minimum
OS campaigns, an ARM/x86 execution matrix, physical-device and 32-bit execution
are excluded. Four-ABI packaging and static hardening remain required;
packaging those ABIs is not an execution or official support claim.

## Publication boundary

This source projection is prepared for review. No public tag, registry upload,
signing operation or production-support claim is authorized by a local build.
Before publication, the exact source tag `android-runtime-3.0.0`, clean source
identities, AAR hash, legal files and installed consumer evidence must agree.
