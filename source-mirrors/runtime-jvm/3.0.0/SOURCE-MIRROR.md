# CoAkka Runtime JVM3.0.0 source projection

This private staged tree prepares the immutable source tag for
`io.github.phuong-tran.coakka:runtime:3.0.0`. Publication is a separate user
command. The managed API is a new baseline; migrate and recompile2.x callers.

The tree contains current Kotlin sources, tests, narrow native lookup rules,
real shrink tooling, package documentation and the standalone Gradle build.
Codec schemas are independent test oracles under src/test only. Production has
four ordinary language runtime dependencies; no separate native provider or
codec installation is required by an application.

Native Core and its JVM bridge remain binary inputs under the file-scoped
Native Artifact License1.2. They are not native implementation source in this
tree. Supply the exact qualified3.0.0 distribution JAR using
`-PcoakkaNativeInputJar=/absolute/runtime-3.0.0.jar`; its independent hash is
pinned in gradle.properties. Assembly adopts only the five exact Core/bridge
pairs and their integrity records, never input classes. No native build,
download, ambient library search or host-selected subset is performed.

Compile managed production sources without native inputs:

```sh
./gradlew compileKotlin --no-daemon
```

Rebuild and exercise the unsigned base artifacts with an actual Java8 home and
the pinned real ProGuard7.10.0 CLI:

```sh
./gradlew test assembleCentralBaseArtifacts --no-daemon \
  -PcoakkaNativeInputJar=/absolute/runtime-3.0.0.jar \
  -PcoakkaRuntimeJava8Home=/absolute/java8 \
  -PcoakkaRuntimeProguardJar=/absolute/proguard-7.10.0/lib/proguard.jar
```

The build enforces Java8 APIs/major52, runs actual JNI against the native pair,
shrinks private implementation names, executes the packaged JAR on Java8 and
writes unsigned Maven artifacts under build/central-reproduction. Use native
Windows ARM64 JDK11 for that target's installed consumer qualification; build
Gradle runs on JDK17. Source compilation does not substitute for matching-host
execution of another native architecture.

SOURCE-MANIFEST.sha256 binds all projected source, test, legal, guide and build
files. Build caches, private shrink mappings and native inputs are excluded from
the public source projection. Production signing, anonymous source-tag
reachability, registry credentials and upload remain publication steps.
