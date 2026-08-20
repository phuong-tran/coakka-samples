# JVM Runtime Connector

<p align="center">
  <img src="https://raw.githubusercontent.com/phuong-tran/coakka-samples/main/docs/assets/brand/coakka-logo.png" alt="CoAkka" width="480">
</p>

The JVM connector brings Kotlin and Java applications into the polyglot,
multi-language, multi-platform CoAkka Runtime ecosystem. Its public package is
`coakka.v2.connector`; the JAR embeds the matching native runtime so consumers
do not need a separate implementation download.

Start with `CoAkka.local(...)`, `handler(...)`, and `ask(...)`. Applications
that own route generations, endpoint inventory, or typed envelopes can use
`ConnectorOrchestrator` and `RuntimeClient` directly.

## Transport Configuration

The connector exposes startup-configured connection strategy, capability
discovery, structured apply results, and atomic TLS/mTLS credential reload.
The public guides document lifecycle, ownership, thread safety, blocking
behavior, capability availability, and Java/Kotlin-facing semantics.

Common ecosystem guidance:

- [CoAkka documentation and samples](https://github.com/phuong-tran/coakka-samples/tree/main/docs)
- [Connection Strategies](https://github.com/phuong-tran/coakka-samples/blob/main/docs/connection-strategies.md)
- [TLS And mTLS](https://github.com/phuong-tran/coakka-samples/blob/main/docs/tls-and-mtls.md)
- [Troubleshooting](https://github.com/phuong-tran/coakka-samples/blob/main/docs/troubleshooting.md)
- [Contact And Support](https://github.com/phuong-tran/coakka-samples/blob/main/docs/contact-and-support.md), including `gabrielgun1983@gmail.com`

Native packaging:

- the jar embeds one `libcoakka_runtime_v2` native library per staged platform
- current file coverage is listed below; file presence is not a claim that the
  JVM connector was executed on every platform:
  - `macos-aarch64`
  - `linux-aarch64`
  - `linux-x86_64`
  - `windows-aarch64`
  - `windows-x86_64`
- consuming the jar must not require a separate native artifact download

## Build And Test

```sh
./gradlew :v2:jvm:test
./gradlew :v2:jvm:verifyRuntimeJvmCompatibility
```

The main runtime artifact supports Java 8 and newer JVMs through one stable
coordinate. See [JVM Compatibility Policy](JVM_COMPATIBILITY.md) for the LTS
test matrix and the rules for future capability-driven Loom or FFM extensions.

## API Levels

Level 1 is the local text path:

```kotlin
val runtime = CoAkka.local("kotlin-practice")
runtime.handler("hello.en") { name -> "Hello $name" }
val reply = runtime.ask("hello.en", "Nam")
```

Level 2 is explicit runtime control through `ConnectorOrchestrator`,
`RuntimeStartSpec`, `RuntimeRouteSpec`, and route generations.

Level 3 is custom envelope work through `ConnectorEnvelope`,
`submitRawEnvelope(...)`, `submitTypedEnvelope(...)`, delivery hints, and
deadletter observation.

## Package The Runtime Jar

Host-native resource only:

```sh
./gradlew :v2:jvm:jar
```

Release-shaped jar with versioned host-native plus staged Linux and Windows
runtime natives:

```sh
./gradlew :v2:jvm:packageRuntimeJvmJar
```

Verify that the packaged runtime jar does not contain demo classes or obsolete
native sidecars:

```sh
./gradlew :v2:jvm:verifyRuntimeJvmJarContents
```

Assemble a small distribution folder:

```sh
./gradlew :v2:jvm:distRuntimeJvm
```

Run the packaged jar smoke with embedded native loading:

```sh
./gradlew :v2:jvm:smokePackagedRuntimeJvmJar
```

Release native file coverage:

- `macos-aarch64`
- `linux-aarch64`
- `linux-x86_64`
- `windows-aarch64`
- `windows-x86_64`

Transport-configuration connector evidence:

| Platform/runtime profile | Evidence in this slice |
| --- | --- |
| macOS ARM64 baseline capabilities | JVM ABI/layout, lifecycle, capability, mode, Spring, and Quarkus tests pass |
| macOS ARM64 full capabilities | JVM TLS startup/reload/rejection and advanced-mode tests pass |
| Linux ARM64 | Exact public package request/reply passes on the matching host |
| Linux x86-64 | Payload is verified; both installed guest JDKs crash inside OpenJDK before the sample reaches CoAkka, so connector execution is not claimed |
| Windows ARM64 | Exact runtime-train JVM request/reply and deadletter execution is recorded |
| Windows x86-64 | Payload format, exports, dependencies, and digest pass; matching x86-64 host execution is not recorded |

## Publish To Maven Local

```sh
./gradlew :v2:jvm:publishToMavenLocal
```

The Maven Central publication uses separate coordinates and a fail-closed
signed bundle workflow at `io.github.phuong-tran.coakka:runtime`, representing
the public name `coakka.runtime`. Its POM directs consumers to
[`coakka-publish`](https://github.com/phuong-tran/coakka-publish) for canonical
artifacts/docs and [`coakka-samples`](https://github.com/phuong-tran/coakka-samples)
for runnable examples. See [MAVEN_CENTRAL.md](MAVEN_CENTRAL.md). Runtime
`2.5.3` is the next Maven Central candidate; do not consume it until Central
reports the coordinate as published.

The older `coakka.v2:coakka-jvm-native-runtime-v2` coordinate in the checked-in
`coakka-publish/maven` repository is a frozen historical archive. Do not append
new Runtime or framework-adapter releases to that static mirror; current
consumers should use the Maven Central coordinates.

## Runtime Demo And Remote Exchange

```sh
./gradlew :v2:jvm:run
./gradlew :v2:jvm:remoteJvmExchange
```

`remoteJvmExchange` is the local developer proof for the remote runtime path.
It builds a transport-enabled runtime, copies the host runtime library into
`lib/`, starts two JVM processes, and exchanges real remote request/reply traffic
between them. The harness enables
the separate delivered-request lane because remote request delivery should not
share the legacy mixed response lane under concurrent ask traffic.

## Consumer Notes

- [Consuming Guide](CONSUMING.md)
- [Standalone Consumer Smoke](consumer-smoke/README.md)
- [Release Checklist](RELEASE.md)

## File Lane

`FileLane.open(...)` exposes the independent native bulk-transfer lane. It is
not owned by `RuntimeHost`; see the shared [file-lane contract](https://github.com/phuong-tran/coakka-samples/blob/main/docs/runtime-file-transfer.md)
for lifecycle, security, native-version, and conformance requirements.

## Stream Lane

This source workspace contains the published connector surface for Stream Lane.
Pair it only with the exact matching core-runtime generation
and follow the public [streaming contract](https://github.com/phuong-tran/coakka-samples/blob/main/docs/runtime-streaming.md).
Package `2.5.3` contains Stream Lane over the exact Core `2.5.1` generation
recorded in its manifest and `gradle.properties`; do not attach
these APIs to older coordinates. Replica-aware applications use
`FileLane.openOwned(...).prepareReceiveGrant(...)` or
`StreamLane.openOwned(...).preparePublishGrant(...)`, serialize the returned
grant through their authenticated control plane, and reconstruct it before
calling `toSendSpec(...)` or `toSubscribeSpec(...)`. Follow the
[owner-grant ONE/ALL sample](https://github.com/phuong-tran/coakka-samples/blob/main/docs/runtime-lane-owner-grants.md).

## AI-Assisted Integration

Before generating application code, use the selected connector README together
with the public [AI-assisted integration guide](https://github.com/phuong-tran/coakka-samples/blob/main/docs/ai-assisted-integration.md).
It requires an exact package coordinate, platform evidence, the runnable
language sample, and the feature-specific lifecycle contract. Do not translate
API identifiers from another language by analogy.

## License

**Free for application use, including commercial and production use.**

Connector source, generated bindings, type declarations, examples, and package
documentation use the [Apache License, Version 2.0](https://github.com/phuong-tran/coakka-samples/blob/main/LICENSE).
Bundled Native Core files use the [CoAkka Native Artifact License 1.2](https://github.com/phuong-tran/coakka-samples/blob/main/NATIVE-LICENSE.md).
Those native terms permit ordinary application and SaaS use but require a
separate agreement to sell or offer CoAkka itself as managed runtime or
infrastructure.

See [CoAkka Package Licensing](https://github.com/phuong-tran/coakka-samples/blob/main/docs/package-licensing.md)
for the file-scope map. The package also carries offline `LICENSE`,
`NATIVE-LICENSE.md`, `PACKAGE-LICENSE.md`, and `NOTICE` copies.
