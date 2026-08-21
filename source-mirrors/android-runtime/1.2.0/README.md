# CoAkka Runtime Android 1.2.0 Source Mirror

This directory is the public Apache-2.0 source projection for the intended
Android coordinate:

```text
io.github.phuong-tran.coakka:coakka-runtime-android:1.2.0
```

It contains the Kotlin connector, JNI bridge, Gradle/CMake build material,
unit tests, and the exact-AAR instrumentation app. `SOURCE-MANIFEST.sha256`
records every projected build and source file. Run the standalone drift check
from this directory:

```sh
./verify-source-checksum.sh
```

The source mirror deliberately contains no `.so` file and no Native Core
source. Connector and JNI source use Apache-2.0. The single
[`release-identity.properties`](release-identity.properties) file pins
connector `1.2.0`, Native Core `2.5.1`, its exact full source commit, and all
four Android ABIs. A built AAR contains that separately scoped native
generation; those native files use the
[CoAkka Native Artifact License 1.2](https://github.com/phuong-tran/coakka-samples/blob/licenses-1.2/NATIVE-LICENSE.md).
The complete file mapping is in the
[package license map](https://github.com/phuong-tran/coakka-samples/blob/licenses-1.2/PACKAGE-LICENSE.md).

## Rebuild

A full AAR rebuild requires an authorized checkout of CoAkka Core containing
the exact pinned commit and its protobuf submodule. Point the mirror at that
separate checkout instead of copying native source into this Apache projection.
For release evidence, the Core checkout must also be clean with `HEAD` at the
recorded `core.commit`:

```sh
export ANDROID_SDK_ROOT=/absolute/path/to/Android/sdk
export COAKKA_CORE_REPO=/absolute/path/to/separate-clean-coakka-core-c1
./gradlew clean testDebugUnitTest assembleRelease lintRelease --no-daemon
```

The build extracts the immutable Core commit rather than compiling the
checkout's rolling working tree. It cross-compiles and packages
`libcoakka_runtime_v2.so` plus `libcoakka_android_jni.so` for `arm64-v8a`,
`armeabi-v7a`, `x86`, and `x86_64`.

To execute the installed-artifact gate on an online ARM64 device or emulator:

```sh
scripts/run-device-smoke.sh \
  build/outputs/aar/coakka-runtime-android-release.aar \
  emulator-5554
```

That release-minified app consumes the exact AAR file, verifies its four-ABI
inventory and metadata schema 2, and requires clean public-connector and
exact-Core provenance. R8 must preserve every statically named JNI class,
native method, callback method, and callback-interface descriptor through the
AAR's consumer rules. Instrumentation then checks native identity/capabilities,
one request outcome, one owner-pinned File transfer, and one owner-pinned
Stream delivery. Evidence from the earlier Core `2.5.0` AAR is historical and
must not be reused for this pin.

The unsigned Central publication shape is independently checkable from the
mirror and needs no release credential:

```sh
./gradlew --init-script maven-central.init.gradle.kts \
  verifyAndroidCentralShape --no-daemon
```

The negative policy test does not assemble, sign, stage, or publish anything:

```sh
./gradlew --init-script maven-central.init.gradle.kts \
  testAndroidCentralPreconditionPolicy --no-daemon --console=plain
```

That shape task is candidate-only unless the project itself comes from the
public Samples tag. In particular, an artifact built from the canonical Core
producer checkout records that repository's commit as the connector source;
it must not be signed or promoted as the Samples connector.

After public tag `android-runtime-1.2.0` exists, prepare the signed Portal
bundle only from a separate clean checkout of that tag and a separate clean
Core checkout at the pinned C1 commit:

```sh
samples_checkout=/absolute/path/to/clean-coakka-samples-tag
core_checkout=/absolute/path/to/separate-clean-coakka-core-c1
signing_fingerprint=2FBD20F919F251E8D984A5EBF90740BDDBBE6638

test "$(git -C "$samples_checkout" cat-file -t android-runtime-1.2.0)" = tag
test "$(git -C "$samples_checkout" rev-parse HEAD)" = \
  "$(git -C "$samples_checkout" rev-parse 'android-runtime-1.2.0^{}')"
test -z "$(git -C "$samples_checkout" status --porcelain --untracked-files=normal)"
cd "$samples_checkout/source-mirrors/android-runtime/1.2.0"
test "$(git -C "$core_checkout" rev-parse HEAD)" = \
  "$(sed -n 's/^core.commit=//p' release-identity.properties)"
test -z "$(git -C "$core_checkout" status --porcelain --untracked-files=normal)"
export COAKKA_CORE_REPO="$core_checkout"
./verify-source-checksum.sh
./gradlew --init-script maven-central.init.gradle.kts \
  bundleAndroidForMavenCentral \
  -PcoakkaMavenCentralNamespaceVerified=true \
  -PcoakkaMavenUseGpgAgent=true \
  -Psigning.gnupg.keyName="$signing_fingerprint" \
  --no-daemon --console=plain
```

The precondition gate requires the AAR connector commit to equal the peeled
Samples tag and requires every native identity field to equal the clean Core
checkout. It rejects lightweight tags, 16-character key IDs, and all
private-key or passphrase Gradle/environment inputs; the complete fingerprint
selects a key already held by the local GPG agent or OS keychain. The command
creates `build/central`; it does not upload.

## Publication Boundary

This mirror is a transparency and reproducibility surface, not a second
implementation branch. The producer checks the projection byte-for-byte
before creating the immutable `android-runtime-1.2.0` tag. The final AAR is
built from that peeled tag commit plus a clean checkout of the Core commit in
`release-identity.properties`. No registry
repository, credential, signing key, publication receipt, or native binary is
stored here. The Maven publication gate remains closed until the public tag,
clean-source artifact, legal-file inventory, matching AAR hash, and device
evidence all agree. The signed AAR, POM, sources, Javadoc, and Gradle-module
bundle is built only after those preconditions pass; preparing it does not
upload it.
