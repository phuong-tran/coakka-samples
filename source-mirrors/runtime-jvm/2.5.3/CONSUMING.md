# Consuming `coakka.runtime`

## Coordinates

Current Maven Central candidate coordinates:

- `groupId`: `io.github.phuong-tran.coakka`
- `artifactId`: `runtime`
- `version`: `2.5.3`

The older `coakka.v2:coakka-jvm-native-runtime-v2` coordinate is retained only
for workspace-local and frozen historical packaging. New public releases use
the Maven Central coordinate above.

## Java Baseline

- the main runtime connector supports Java 8 and newer JVMs through one
  coordinate
- Kotlin compilation is limited to Java 8 JDK APIs, not only Java 8 bytecode
- release verification runs the exact packaged jar on Java 8, the Java 11,
  17, 21, and 25 LTS lines, and the configured latest feature release
- the jar declares `Automatic-Module-Name: coakka.v2.runtime` for Java 9+
  module-path users
- Spring Boot 3.x adapters sit above this artifact and require Java 17 because
  Spring Boot 3 requires Java 17

Higher-JVM integrations are split only when a concrete capability such as a
virtual-thread executor or FFM binding justifies a separate optional artifact.
See [JVM Compatibility Policy](JVM_COMPATIBILITY.md).

Java 24 and newer applications loading the native runtime from the classpath
must currently launch with:

```sh
java --enable-native-access=ALL-UNNAMED ...
```

Versioning rule:

- the JVM jar version tracks the connector artifact release
- the bundled native package version is recorded separately in the jar metadata
- one published jar version bundles one native package version

## Publish To Maven Local

```sh
./gradlew :v2:jvm:publishToMavenLocal
```

Maven Central is prepared under the separate
`io.github.phuong-tran.coakka:runtime` coordinate, representing the public
product name `coakka.runtime`. Its POM links to the canonical
[`coakka-publish`](https://github.com/phuong-tran/coakka-publish) documentation
and runnable [`coakka-samples`](https://github.com/phuong-tran/coakka-samples).
It must not be used in a
consumer until the corresponding immutable version is visible on Central; see
`MAVEN_CENTRAL.md` for the release gates.

## Dependency Example

```kotlin
dependencies {
    implementation("io.github.phuong-tran.coakka:runtime:2.5.3")
}
```

## Native Loading

Default path:

- the jar loads the embedded native library for the current platform
- public-ready runtime jars must include the native runtime for supported
  platforms
- consuming the jar must not require a separate native artifact download or
  extra native implementation packages
- current embedded-native coverage is:
  - `macos-aarch64`
  - `linux-aarch64`
  - `linux-x86_64`
  - `windows-aarch64`
  - `windows-x86_64`

Override path:

```sh
-Dcoakka.runtime.lib=/abs/path/to/libcoakka_runtime_v2.so
```

or on macOS:

```sh
-Dcoakka.runtime.lib=/abs/path/to/libcoakka_runtime_v2.dylib
```

or on Windows:

```powershell
-Dcoakka.runtime.lib=C:\path\to\libcoakka_runtime_v2.dll
```

## Minimal Kotlin Example

The first-run local API hides route snapshots, local endpoint host/port
placeholders, and payload identity for plain text:

```kotlin
import coakka.v2.connector.CoAkka
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val runtime = CoAkka.local("kotlin-practice")

    try {
        runtime.handler("hello.en") { name ->
            "Hello $name"
        }

        println(runtime.ask("hello.en", "Nam"))
    } finally {
        runtime.shutdown()
    }
}
```

Use `ConnectorOrchestrator` or `RuntimeClient` when the app owns route
generations, endpoint inventory, or custom envelopes.

## Explicit Runtime Example

```kotlin
import coakka.v2.connector.RuntimeClient
import coakka.v2.connector.protocol.ConnectorPayloadIdentity
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val client = RuntimeClient.startLocal(localTargets = listOf("svc.echo"))

    try {
        client.registerHandler("svc.echo") { request ->
            RuntimeClient.replyTextTo(request, source = "svc.echo", payloadUtf8 = request.payloadUtf8())
        }

        val response = client.ask(
            source = "sample-client",
            target = "svc.echo",
            payloadUtf8 = "hello",
            payloadIdentity = ConnectorPayloadIdentity.text("svc.echo.request"),
        )
        println(response.payloadUtf8())
    } finally {
        client.shutdown()
    }
}
```

## Minimal Java Text Helper

```java
ConnectorOrchestrator runtime = ConnectorOrchestrator.start(startSpec);
try {
    runtime.registerTextHandler("hello.en", name -> "Hello " + name);

    String reply = runtime.getJava().askTextBlocking(
        "java-client",
        "hello.en",
        "Nam"
    );
} finally {
    runtime.getJava().shutdown().get();
}
```

## Packaged Jar Smoke

```sh
./gradlew :v2:jvm:smokePackagedRuntimeJvmJar
```

## Standalone Consumer Smoke

```sh
./gradlew :v2:jvm:publishToMavenLocal
./gradlew -p v2/jvm/consumer-smoke run
```

Cross-language guides and samples are available from the
[CoAkka documentation hub](https://github.com/phuong-tran/coakka-samples/tree/main/docs).
