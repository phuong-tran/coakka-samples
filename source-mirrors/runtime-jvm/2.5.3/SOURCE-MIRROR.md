# CoAkka Runtime JVM 2.5.3 Source Mirror

This tagged directory is the public source projection for:

```text
io.github.phuong-tran.coakka:runtime:2.5.3
```

The Kotlin connector, protobuf schemas, tests, package documentation, legal
files, and standalone Gradle build are projected from the private release
producer. `SOURCE-MANIFEST.sha256` covers every projected file, and the producer
release gate rejects any local or tagged byte drift.

The connector material is Apache-2.0. Native Core binaries remain under the
file-scoped CoAkka Native Artifact License 1.2. They are not copied into this
source tree. `LICENSE`, `NATIVE-LICENSE.md`, `PACKAGE-LICENSE.md`, and `NOTICE`
record that boundary offline.

From this directory, compile the public connector source without packaging
native inputs:

```sh
./gradlew compileKotlin --no-daemon
```

To rebuild the unsigned Maven Central base artifacts, provide the exact frozen
five-platform native directory produced for the Core generation recorded in
`gradle.properties`:

```text
<native-input>/manifest.json
<native-input>/macos-aarch64/libcoakka_runtime_v2.dylib
<native-input>/linux-aarch64/libcoakka_runtime_v2.so
<native-input>/linux-x86_64/libcoakka_runtime_v2.so
<native-input>/windows-aarch64/libcoakka_runtime_v2.dll
<native-input>/windows-x86_64/libcoakka_runtime_v2.dll
```

Then run:

```sh
./gradlew assembleCentralBaseArtifacts \
  -PcoakkaNativeInputRoot=/absolute/path/to/native-input \
  --no-daemon
```

The build verifies the native manifest identity and every native SHA-256,
packages exactly five versioned native resources, enforces Java 8 bytecode,
rebuilds the sources and Javadoc jars, and writes the unsigned Maven repository
under `build/central-reproduction/`. Production signatures and Maven Central
upload credentials are intentionally outside this public source mirror.
