# JVM Runtime Connector

<p align="center">
  <img src="https://raw.githubusercontent.com/phuong-tran/coakka-samples/main/docs/assets/brand/coakka-logo.png" alt="CoAkka" width="480">
</p>

The JVM connector brings Kotlin and Java applications into the polyglot,
multi-language, multi-platform CoAkka Runtime ecosystem. Its public package is
`coakka.v2.connector`; the JAR embeds the matching native runtime so consumers
do not need a separate implementation download.

This branch prepares an **unpublished** JNI candidate. It does not upgrade an
already published coordinate or establish whole-platform release readiness.
Core owns defaults, validation, capability probes, delivery and causal outcomes;
the connector only projects language values and owns JVM/FFI lifetimes.

Start with `CoAkka.local(...)`, `handler(...)`, and `ask(...)`. Applications
that own route generations, endpoint inventory, or typed envelopes can use
`ConnectorOrchestrator` and `RuntimeClient` directly.

For an ask-only remote client, use `RuntimeAskClient.askPayloadBlocking(...)`.
Core builds the request, matches the terminal response and extracts the payload.
Synchronous services may use `registerPayloadHandler(...)`; rich/suspending
handlers use `registerHandler(...)`. Both use Core's delivered-request boundary,
not a connector-owned response reader or matching map. Bounded UTF-8 and direct
scratch reuse are JVM representation choices, not copies of Core policy. Large
records use the same-take fallback without truncation or a reduced frame limit.

The ask-only client bounds its retained source/target text encodings and releases
them on close. Longer names remain usable without being cached; the runtime
still decides whether a request is valid. Cache size is not an application
name-length limit, and closing a cache does not invalidate an in-flight call's
borrowed values.

Omit advanced startup groups for Core's accepted defaults: AUTO placement,
MULTIPLEXING connection mode, optional I/O and monitor off. Read `startupConfig()`,
`networkConfig()` and `deliveryConfig()` for actual accepted facts, including
presence and probe outcome. Requests and responses use Core-owned queues and
doorbells on macOS, Linux and Windows. No legacy pipe selector is required.

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

- the candidate JAR embeds one integrity-bound Core/JNI pair per staged platform,
  with exact size/hash metadata; Core loads before the bridge
- mismatched, corrupt, incomplete or partially loaded pairs fail closed
- current candidate execution covers:
  - `macos-aarch64`
  - `linux-aarch64`
  - `windows-x86_64` with the Java8 path limitation below
- `linux-x86_64` and `windows-aarch64` remain separate matching-host gates
- consuming the jar must not require a separate native artifact download
- native binding uses direct JNI registration, with no reflective binding or JNA
- full message/control mapping delegates to Core; generated codecs, probe CLI
  code and their libraries are not runtime dependencies

The loader owns an immutable eight-pair cache under
`user.home/.coakka-runtime-native-v1`. Published pairs are verified, never repaired
or evicted in place; stop all using JVMs before administrative cleanup. Corrupt
entries and full admission fail rather than choosing a different image. The
cache is loader storage, not runtime state or an effective-configuration cache.

To choose a different cache directory, set `-Dcoakka.runtime.nativeDir=<path>`
before the first runtime use. This selects the cache itself, not its parent;
the parent must already exist and be trusted and writable by the application.
Relative paths resolve against the process working directory, not the JAR's
directory. The loader creates the cache directory if absent, retains the same
eight-pair bound, and fails on invalid or unusable storage without falling back.
It does not change `user.home`, write beside the JAR, or relocate a loaded pair.

Windows Java8 native loading requires an ASCII-only absolute cache path for
this candidate's supported deployment scope. For example, when `C:\CoAkka`
is an application-owned writable directory, use
`-Dcoakka.runtime.nativeDir=C:\CoAkka\native-cache`. This does not restrict
Unicode application messages or payloads. On the tested Windows Java8/Cp1252
host, an independent JNI DLL also fails to load from a path outside the system
code page, before any Core call. Unicode native-library paths remain unsupported;
no security/locale change, silent path fallback or reflection workaround is used.

## Build And Test

```sh
./gradlew :v2:jvm:test
./gradlew :v2:jvm:verifyRuntimeJvmCompatibility
```

The managed floor remains Java8 APIs and class-file major52. Native Windows
ARM64 requires an ARM64 JDK11 or newer; an x64 Java8 process uses the Windows
x64 pair instead. The JVM's architecture selects the pair, independently of
the physical CPU. No connector-side capability policy is inferred from it.
Gradle/toolchain and framework-adapter JDK requirements are separate. Execute
the actual shrunk package on every claimed runtime; a newer build JDK or
target-bytecode setting is not baseline evidence. See
[JVM Compatibility Policy](JVM_COMPATIBILITY.md) for the wider qualification plan.

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

The3.0.0 baseline uses Core1522b4a9/ABI1 and real ProGuard output. It requires
source migration and recompilation from2.x; for example, Core uint32 fields
use Long instead of int, and startup/delivery inspection uses the grouped
Core-issued snapshots. Current exact package identities and execution records
are in the selected candidate manifest.

| Platform/runtime profile | Evidence in this slice |
| --- | --- |
| macOS ARM64 / Java8 |Installed shrunk consumer, cold/reused pair and public lifecycle |
| Linux ARM64 / Java8 / Raspberry Pi5 |Installed shrunk consumer and public lifecycle |
| Physical Windows x64 / Java8 |Installed native x64 pair; ASCII cache restriction below applies |
| Linux x64 / Java8 |Separate matching-host pair and managed-floor execution required |
| Windows ARM64 / JDK11 |Separate matching-host ARM64 pair; x64 emulation is not ARM64 evidence |

Package consumers cover Core defaults, rich values,96KiB ownership, lifecycle,
atomic refusal/recovery, request/reply and typed deadletter. Native leak/race,
signing, framework no-reflection, wider-target and coordinated ecosystem gates
remain independent; throughput alone cannot close them. The five private probe
CLI tests are separate from the runtime suite and are not distributed.

## Value And Configuration Migration

Unknown message/routing/status/failure numeric values remain representable.
Do not interpret unspecified business status as success, or absent deadletter
original/failure groups as fabricated empty objects. Text preserves embedded
NUL where Core uses spans; unpaired UTF-16 is rejected at the JVM boundary.
Core's uint32 fields use `Long`; generation/sequence preserve all64 bits.
Decoded arrays and snapshots are independent JVM-owned copies. Do not mutate
inputs concurrently during a synchronous encode/call.

Snapshot overload-policy omission means Core's configured boot policy, not
the previous snapshot override. Repeat an override explicitly per generation.
Control refusal leaves the active snapshot/policy unchanged. Monitor collection
is off when omitted; no-event and closed/error outcomes are distinct.

The3.0 API removes `startupConnectionResult()` and the delivered-lane
flag; inspect `startupConfig().connection` and `deliveryConfig()` instead.
Public provider/transport metadata is named `transportProvider`,
`allocatorProvider`, `transportBindHost` and `transportBindPort`. Bit4's JVM name
is `TRANSPORT_PROBE_ONLY`. These changes do not alter the frozen native C ABI.

## Publish To Maven Local

```sh
./gradlew :v2:jvm:publishToMavenLocal
```

The Maven Central publication uses separate coordinates and a fail-closed
signed bundle workflow at `io.github.phuong-tran.coakka:runtime`, representing
the public name `coakka.runtime`. Its POM directs consumers to
[`coakka-publish`](https://github.com/phuong-tran/coakka-publish) for canonical
artifacts/docs and [`coakka-samples`](https://github.com/phuong-tran/coakka-samples)
for runnable examples. See [MAVEN_CENTRAL.md](MAVEN_CENTRAL.md). The documented
`2.5.3` publication is historical; this candidate has not been published.

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
It exchanges remote request/reply between two JVM processes. Qualify it against
the exact staged Core/JNI pair before claiming candidate demo readiness. Core
owns request delivery and ask matching; JNI does not drain a response pipe or
reconstruct transport state. External sample/package convergence is separate.

## Consumer Notes

- [Consuming Guide](CONSUMING.md)
- [Release Checklist](RELEASE.md)

## File Lane

In the current private candidate, File and Stream snapshot terminal flags come
from Core's terminal transition timestamp, not the numeric ordering of states.
File `PAUSED` is retryable and nonterminal; cancel can make it terminal. Success
also requires a recognized completed/ended state and an `OK` result. Core may
allow forgetting an inactive paused transfer separately: that permission does
not make `PAUSED` terminal. Do not wait for a paused transfer to complete without
choosing resume, cancel, or another Core-supported action.

`FileLane.open(...)` exposes the independent native bulk-transfer lane. It is
not owned by `RuntimeHost`; see the shared [file-lane contract](https://github.com/phuong-tran/coakka-samples/blob/main/docs/runtime-file-transfer.md)
for lifecycle, security, native-version, and conformance requirements.

## Stream Lane

This source workspace contains the published connector surface for Stream Lane.
Pair it only with the exact matching core-runtime generation
and follow the public [streaming contract](https://github.com/phuong-tran/coakka-samples/blob/main/docs/runtime-streaming.md).
Candidate `3.0.0` contains Stream Lane over the exact Core `3.0.0` generation
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
