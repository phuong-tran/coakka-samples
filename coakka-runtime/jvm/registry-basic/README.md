# JVM Runtime 3.0.0 — first run from Maven Central

From a checkout of coakka-samples, use JDK17+ for Gradle:

    ./gradlew -p coakka-runtime/jvm/registry-basic run
    ./gradlew -p coakka-runtime/jvm/registry-basic runJava

The first command runs Kotlin; the second runs Java. Both use the published
`io.github.phuong-tran.coakka:runtime:3.0.0` dependency from Maven Central.
They reuse the ordinary basic example sources, create one handler, send a request,
check the reply and counters, then await shutdown. A separate native installation
or local CoAkka Maven repository is not required.

The library retains Java 8 compatibility. Native Windows ARM64 requires ARM64
JDK11+. Advanced settings stay omitted; Core resolves AUTO/MULTIPLEXED and monitor
off. [Release details](https://github.com/phuong-tran/coakka-samples/blob/main/docs/coakka-runtime/releases/2026-10-30-jvm-spring-quarkus-runtime-3.0.0-maven-central.md)
include platform scope and2.x migration notes.

For baseline verification only, COAKKA_SAMPLE_JAVA_HOME can select a Java 8 runtime
and COAKKA_SAMPLE_WORK_ROOT can redirect build/native-cache output. Normal first
runs use the selected build JDK and the normal loader cache.
