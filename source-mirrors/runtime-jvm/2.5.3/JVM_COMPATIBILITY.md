# JVM Compatibility Policy

## Main Runtime Artifact

`coakka-jvm-native-runtime-v2` has one JVM compatibility line with Java 8 as
its minimum runtime. It does not publish a different coordinate for every JVM
release.

The build uses a current JDK toolchain while enforcing the Java 8 contract in
three independent places:

- Kotlin compilation uses `-Xjdk-release=8`, which rejects references to JDK
  APIs introduced after Java 8.
- Java compilation uses `--release 8`, and package verification rejects root
  classes newer than Java 8 bytecode.
- release verification executes the packaged jar, embedded native discovery,
  runtime lifecycle, and request/reply on an actual Java 8 runtime.

The release matrix runs the same packaged jar on Java 8 and the supported LTS
lines Java 11, 17, 21, and 25. It also runs one compatibility smoke on the
latest feature release selected by `coakkaLatestJvmCompatibilityVersion`; the
default is Java 26 for the current release train. A newer JVM is not claimed
until its smoke passes with the exact candidate jar and native generation.

The JVM matrix and native platform matrix are intersecting contracts. Passing
Java 8 on macOS ARM64 does not by itself claim Java 8 on every packaged native
platform; each advertised JVM/platform combination still needs matching-host
execution and native dependency evidence.

Java 24 and newer warn or may reject unrestricted native library access unless
the application launcher grants it to classpath libraries:

```sh
java --enable-native-access=ALL-UNNAMED ...
```

The compatibility tasks apply this option on those JVMs. Applications using a
named module should grant native access to the application module instead of
granting it to all unnamed modules.

Run the matrix with:

```sh
./gradlew :v2:jvm:verifyRuntimeJvmCompatibility
```

The Java 8 launcher uses Azul Zulu because macOS ARM64 Java 8 is not available
from every toolchain vendor. Gradle may provision missing test toolchains; an
offline runner must install matching toolchains before executing the gate.

Protobuf `4.31.1` emits a terminal `sun.misc.Unsafe` deprecation warning on the
current Java 25 and 26 runtimes. The tested request/reply path still passes,
but this is forward-compatibility evidence rather than a waived diagnostic.
The dependency must be upgraded and the exact package matrix rerun before a
JVM release removes the referenced operation.

## Capability-Driven Extensions

JVM-version-specific artifacts are allowed only when a concrete runtime
mechanism needs a higher baseline and focused evidence shows a material
benefit. Current examples are a Java 21 virtual-thread handler adapter or a
Java 22 Foreign Function and Memory binding. Neither is part of the supported
surface today.

Any future extension must:

- preserve the main Java 8 public API and lifecycle law;
- own only the higher-JVM executor or native-binding mechanism;
- remain optional, with the Java 8 implementation as the default;
- include workload, allocation, thread, wakeup, latency, and shutdown evidence;
- use a capability name rather than a JVM-number-only artifact name.

The runtime does not use reflection to silently change concurrency or native
ownership semantics merely because a newer JVM is present.

## Framework Adapters

Framework adapters keep the minimum Java version required by their owning
framework. The Spring Boot 3 and current Quarkus adapters therefore remain
separate from the Java 8 runtime baseline and are tested on Java 17 or newer.

`Automatic-Module-Name: coakka.v2.runtime` keeps the main artifact usable on
the Java 9 module path. An explicit multi-release module descriptor is not a
release requirement while the automatic module contract is sufficient.
