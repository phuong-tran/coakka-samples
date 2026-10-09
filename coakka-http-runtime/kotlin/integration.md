# JVM (Kotlin and Java): installation and integration

Java and Kotlin are both supported application languages. Kotlin examples are
not a Kotlin-only requirement; see the Java equivalents and runnable consumer below.

Start here to run your first CoAkka HTTP Runtime application, then add only
the features you need. The full sample remains the executable reference;
this guide explains its reading order. Handlers stay host-inlined in your
application language while Core owns HTTP transport and runtime policy.

For monitor startup, policy changes, event retention and exporter ownership,
see [Monitoring an HTTP service](../monitoring.md), including this language's
complete source recipe.

For the rationale and lifecycle behind the APIs, read
[generations and drain](../glossary.md#why-generations-are-needed),
[shutdown hooks and trade-offs](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/operations.md#application-hooks),
and [certificate rotation versus hot reload](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/tls-and-mtls.md#what-can-be-hot-reloaded).

## Contents

- [Inspect, metadata and OpenAPI](#inspect-metadata-and-openapi)

- [1. Install and prepare](#1-install-and-prepare)
- [2. Run your first server](#2-run-your-first-server)
- [3. Read and adapt the application](#3-read-and-adapt-the-application)
- [Request and response on the JVM](#request-and-response-on-the-jvm)
- [URL grammar and glossary](../glossary.md)
- [4. Try the HTTP features](#4-try-the-http-features)
- [5. Configure and observe the service](#5-configure-and-observe-the-service)
- [Static files, frontend and downloads](#static-files-frontend-and-downloads)
- [Swap a handler without replacing routes](#swap-a-handler-without-replacing-routes)
- [Java uses the same JVM connector](#java-uses-the-same-jvm-connector)
- [6. Enable security and other protocols](#6-enable-security-and-other-protocols)
- [7. Handle failures and shutdown](#7-handle-failures-and-shutdown)
- [Troubleshooting and next steps](#troubleshooting-and-next-steps)
- [Sendfile and upload are different](#sendfile-and-upload-are-different)

## 1. Install and prepare

These instructions use the exact repository archives selected by
[`package-pins.json`](../scripts/package-pins.json), currently the
`2026-10-08-r3` train. HTTP Runtime is **not installed from npm, PyPI,
Maven Central or a Go module tag** in this guide. CoAkka Runtime's registry
packages are a different product.

If you do not already have the two public checkouts, clone them side by side
in a workspace with enough storage:

```sh
git clone https://github.com/phuong-tran/coakka-samples.git
git clone https://github.com/phuong-tran/coakka-publish.git
cd coakka-samples
export COAKKA_PUBLISH_ROOT="$(cd ../coakka-publish && pwd)"
export COAKKA_HTTP_SAMPLE_WORK_ROOT=/absolute/path/to/build-volume/http-samples
```

Replace the build-volume path with a dedicated writable directory before running
anything. All commands below start in the **coakka-samples repository root**.
For existing checkouts, set the same two variables to their actual paths;
do not clone over an existing directory. Re-export them in each new terminal.

The runner selects the current host's archive, verifies its pinned SHA-256,
extracts it under the work directory, and verifies the extracted files before
reuse. It does not build Core or the connector. Build tools may download their
own dependencies. Do not bypass a checksum failure or mix native files from
different archives.

These shell commands support macOS ARM64 and Linux ARM64/x86-64. Windows
package use is documented in the linked package guide; these POSIX commands
are not a Windows walkthrough. Package availability and matching-host execution
are separate facts; see [coverage](../FEATURES.md#final-r3-package-checkpoint).

**Tools for this lane:** JDK 17 to run this sample's Gradle build, Python 3.11+ for package resolution, and curl. Use the repository Gradle wrapper; it downloads its pinned build dependencies. Security recipes also need OpenSSL CLI. The connector's consumer floor remains Java 8; sample bytecode and JDK API references target Java 8. A newer build JDK is not a higher consumer requirement.

The runner uses the verified bundle's class-only JAR plus both matching native libraries. [build.gradle.kts](build.gradle.kts) shows the local file dependency, JVM 1.8 target and Java 8 API restriction. For your own Gradle app, follow the package guide's local JAR, Kotlin standard-library and native-directory settings. No CoAkka Maven coordinate is required. Do not mix a JAR and native adapter from separate candidates.

[Package installation and platform details](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/jvm/README.md).

## 2. Run your first server

```sh
bash coakka-http-runtime/kotlin/run.sh check
bash coakka-http-runtime/kotlin/run.sh smoke
bash coakka-http-runtime/kotlin/run.sh run
```

Run the commands sequentially. `check` checks/builds the sample; `smoke`
runs finite checks and exits. `run` keeps the server alive. Wait for the
`kotlin-sample=http://127.0.0.1:...` line, then in another terminal:

```sh
PORT=12345 # replace with the printed port
curl --max-time 5 -i -H 'x-sample-caller: smoke' \
  "http://127.0.0.1:$PORT/hello/reader?title=hello"
```

Expect HTTP 200 and `hello reader from smoke`. Stop the server with Ctrl-C
when finished, and let its shutdown path complete. A startup message is not
a substitute for the actual HTTP request.

## 3. Read and adapt the application

Start with `main`, `createService` and the handler functions in [Main.kt](src/main/kotlin/sample/Main.kt). [Control.kt](src/main/kotlin/sample/Control.kt), [Adverse.kt](src/main/kotlin/sample/Adverse.kt) and [Security.kt](src/main/kotlin/sample/Security.kt) separate control, failure and security concerns.

```kotlin
// Inside the existing ServiceBuilder chain, before .start().
.post("/echo", Handler { request ->
    Responses.bytes(request.bytes(), status = 201)
})
```

This is a builder excerpt using the imports in `Main.kt`, not a standalone program. Keep the sample's lifetime wait and checked shutdown; a `service.use { println(...) }` block alone would close immediately. Kotlin handlers and response values are the application surface, not Servlet or Netty handlers.

For a first integration, keep the listener, one route and the lifecycle code.
Add features one at a time; you do not need the demonstration upstream, monitor,
static mount or test assertions in every application. Re-run the request above
after changing a route. Keep business validation, authorization and data codecs
in your handlers; protocol parsing and effective runtime settings stay in Core.

| Feature | Where to adapt the source |
| --- | --- |
| Streaming | `postStream` uses `readBounded`; `streamingResponse` uses a producer and returns final headers. |
| SSE / WebSocket | `serverSentEvents` and `webSocket`; retain typed event handling and finite output. |
| Outbound HTTP | The logical-target startup request; typed reason/phase/retry/certainty are runtime facts. |
| Files | `StaticMount`, `FileAuthority` and `Responses.file`, confined to the configured root. |

## Request and response on the JVM

Read the [URL grammar and glossary](../glossary.md). Use
`.get("/hello/{name}", Handler { ... })`; query fields do not belong in
the route pattern. Parameters are not Kotlin template interpolation.

| Input | Kotlin projection |
| --- | --- |
| Captured path | `request.pathParameters`; `encodedValue()` returns encoded bytes. |
| Query | `request.queryParameters`; `encodedKey()` and nullable `encodedValue()`. Null means no value; empty bytes mean an explicit empty value. |
| Header | `request.headers["x-sample-caller"]`; handle absence explicitly. |
| Buffered body | `request.bytes()`. |
| Streamed body | `request.body` as an input stream; use the bounded reader and read `request.trailers` after EOF. |

The following handler-body excerpt uses imports already present in `Main.kt`:

```kotlin
Responses.bytes(
    request.bytes(),
    status = 201,
    headers = Headers.of(Header("content-type", "application/octet-stream")),
)
```

The expression is the value returned by `Handler { request -> ... }`.
Use `Responses.text` for text. JSON serialization belongs to the app:
serialize with its chosen codec, supply UTF-8 bytes and `application/json`.
There is no automatic domain-object serializer implied by a response helper.

**Outbound request construction:** after declaring an `OutboundTarget`,
`demonstrateOutbound` calls
`service.submitOutbound(OutboundRequest(OUTBOUND_TARGET, "GET", path,
timeoutMillis = 3_000))`. It reads `service.takeOutbound(5_000)`, requires
a non-null terminal, matches the call identity and checks
`OutboundReason.RESPONSE` before status and `responseBody()`.
Use actual encoded path/query values for `path`, not a route template.
Follow the complete function in [Main.kt](src/main/kotlin/sample/Main.kt).

Java uses the same public classes and explicit overloads/builders. Preserve
byte arrays, nullability and typed results; Java 8 compatibility does not
require reimplementing the connector or using a coroutine channel.

## 4. Try the HTTP features

Keep the server from step 2 running. Use its printed port, not a guessed default.
These requests use ordinary HTTP clients; CoAkka owns the server transport.

```sh
curl --max-time 5 -i --data-binary 'payload' "http://127.0.0.1:$PORT/echo"
curl --max-time 5 -i --data-binary 'streamed' "http://127.0.0.1:$PORT/upload"
curl --max-time 5 --raw -i "http://127.0.0.1:$PORT/stream"
curl --max-time 5 -N "http://127.0.0.1:$PORT/events"
curl --max-time 5 -i "http://127.0.0.1:$PORT/download"
curl --max-time 5 -i -H 'Range: bytes=0-3' "http://127.0.0.1:$PORT/download"
curl --max-time 5 -H 'Accept: text/html' "http://127.0.0.1:$PORT/app/client/route"
curl --max-time 5 -i "http://127.0.0.1:$PORT/version"
```

| Request | Expected observation | What to preserve in your app |
| --- | --- | --- |
| POST `/echo` | 201 and the same bytes | Choose an explicit body ceiling for buffered business data. |
| POST `/upload` | 200 and the same bytes | This sample collects at most 64 KiB; consume bounded chunks for larger uploads. Read trailers after EOF. |
| GET `/stream` | `stream-ready`, final `x-stream-end: done` trailer | Honor writer pressure; do not accumulate an unbounded producer queue. |
| GET `/events` | A finite `state` event with multiline data, ID and retry | This example ends; implement a bounded, cancellable producer for a long-lived feed. |
| GET `/download` | Confined sample file; range request returns 206 | Grant only an application-controlled root. Never turn an incoming path into an arbitrary OS path. |
| GET `/app/client/route` | Static index through SPA navigation fallback | SPA fallback is an explicit app choice, not business routing. |
| GET `/version` | 201 and `v2` | The startup recipe already activated the new handler. |

For WebSocket, connect to `ws://127.0.0.1:$PORT/socket` with subprotocol
`coakka.sample` using a WebSocket client, then send a text or binary message.
The sample echoes it. Plain `curl GET` is not a WebSocket handshake.
Use `smoke` for the lane's automated checks and the
[feature index](../FEATURES.md) for the exact wire/adverse coverage.

Static/file responses also support validators: copy the returned `ETag` into
`If-None-Match` to observe a bodyless 304. An out-of-file range returns 416.
Core owns these protocol decisions.

## Static files, frontend and downloads

Use a static mount when Core should serve assets directly. For an SPA, set an
index and a navigation fallback. Keep APIs outside that prefix, for example
`/api/hello/{name}` beside `/app`. The runtime serves the frontend's **built
output**; the frontend toolchain still owns building it and setting an asset
base such as `/app/`. A dev server and its live reload are separate tools.

```kotlin
// Inside createService: assets is the trusted sample directory.
.staticMount(StaticMount(
    "/app", assets.path,
    indexFile = "index.html",
    spaFallbackFile = "index.html",
))
.fileAuthority(FileAuthority(82, assets.path, 2, 1L shl 20))
.get("/download", Handler {
    Responses.file(82, "/sample.txt",
        headers = Headers.of(Header("content-type", "text/plain")))
})
```

These excerpts use `Main.kt` imports and replace its existing declarations/activation. Do not run the same activation again as a new change after the sample has already activated `v2`. Java equivalents and a runnable consumer follow below.

A file authority grants only its declared root. Returning a file from a handler
lets the application perform authorization before choosing a relative path;
a static mount is directly served and must contain only files meant for public
access. Do not put secrets in the mount and expect an API handler to protect it.
To serve static files without SPA fallback, omit the fallback declaration.

With the main sample running, verify:

```sh
curl --max-time 5 -i "http://127.0.0.1:$PORT/app/sample.txt"
curl --max-time 5 -H 'Accept: text/html' "http://127.0.0.1:$PORT/app/client/route"
curl --max-time 5 -i -H 'Range: bytes=0-3' "http://127.0.0.1:$PORT/download"
```

Expect the asset, the index document for HTML navigation, and HTTP206 with four
bytes respectively. This does not install an authentication policy or a
frontend development server. File snapshot/reload and deployment changes follow
the package contract; do not assume a production directory behaves like hot
module reload.

## Sendfile and upload are different

The file-response recipe above gives Core a confined file reference instead of
reading the entire file into a host-owned response buffer. For eligible
plaintext HTTP/1.1 delivery, Core can use sendfile to avoid the application
buffer round trip. Other transports, including TLS, use their appropriate
bounded delivery path; your handler keeps the same file-response API.

This is **server-to-client download**, not client-to-server upload. Uploads
still need bounded request consumption and application-owned validation and
storage policy. Sendfile does not parse multipart or save incoming files.
Do not assume every file response is zero-copy or turn off TLS to choose a
fast path. See [file delivery, sendfile and uploads](../file-delivery.md) for
the motivation, protocol conditions and responsibilities.

## Swap a handler without replacing routes

Start by registering `GET /version` with a handler returning `v1`.
Prepare the replacement before publication, read the current Core snapshot,
then apply using the observed route generation and binding revision:

```kotlin
// Reuse the feature sample; /version is route 8.
val snapshot = service.routes
val version = snapshot.routes.single { it.routeId == 8L }
service.prepareHandler(100, Handler { Responses.text("v2", status = 201) })
val outcome = service.rebindHandler(RouteRebind(
    1, snapshot.routeGeneration, version.routeId, version.bindingRevision, 100,
))
check(outcome.code == RouteControlCode.APPLIED && outcome.changed) {
    "handler switch refused: ${outcome.code}"
}
```

This runs on application control flow, not inside every HTTP request. Binding
and activation IDs are application-chosen identities; generations/revisions
come from Core. Reserve enough handler capacity before startup for staged and
draining bindings. The feature sample already reserves that capacity.

On APPLIED, later admissions use the new binding; previously captured work
retains its old binding. Refusal leaves effective state unchanged. A stale
result calls for rereading state and deciding whether the original intent is
still wanted, not blind retries with incremented numbers. Keep old application
state alive until its users retire.

Try `GET /version` before and after activation to observe `v1` then `v2`.
The complete feature sample performs activation **before** readiness, so its
normal `run` command already returns `v2`. Handler swap keeps the route
pattern unchanged; adding/removing routes requires complete route publication
as demonstrated separately in the source.

## Java uses the same JVM connector

**The JVM connector supports both Java and Kotlin.** Kotlin is its implementation
language and the main sample language; Java users do not need to write Kotlin.
Use Java lambdas, constructors, getters and static response helpers from the
same JAR. Keep the Kotlin standard library as a runtime dependency even in a
Java-only application, as described in the package installation guide.

| Kotlin spelling | Java spelling |
| --- | --- |
| `ServiceBuilder().get(path, Handler { ... })` | `new ServiceBuilder().get(path, request -> ...)` |
| `Responses.text("ready", status = 201)` | `Responses.text("ready", 201)` |
| `service.port` | `service.getPort()` |
| `service.routes` | `service.getRoutes()` |
| `snapshot.routeGeneration` | `snapshot.getRouteGeneration()` |
| `outcome.changed` | `outcome.getChanged()` |
| `request.pathParameters.first().encodedValue()` | `request.getPathParameters().get(0).encodedValue()` |
| Named arguments | Explicit overloads/constructor arguments, never generated default-argument helpers. |

### Java static files, frontend and response

This excerpt uses `import coakka.http.*;` and
`java.nio.charset.StandardCharsets`; `assets` is a validated absolute
directory. The first registered route is `/version` (ID1).

```java
Service service = new ServiceBuilder()
    .staticMount(new StaticMount(
        "/app", assets, "index.html", null, "index.html"))
    .fileAuthority(new FileAuthority(82L, assets, 2, 1L << 20))
    .get("/version", request -> Responses.text("v1"))
    .get("/download", request -> Responses.file(82L, "/sample.txt"))
    .get("/api/hello/{name}", request -> Responses.text("hello " +
        new String(request.getPathParameters().get(0).encodedValue(),
                   StandardCharsets.UTF_8)))
    .start();
```

The five `StaticMount` arguments are URL prefix, root, index file, optional
cache-control value (null here), and SPA fallback. Do not confuse the fourth
and fifth arguments. Without SPA fallback, use
`new StaticMount("/app", assets, "index.html")`.

This fragment starts the service but does not own its complete lifetime.
Use the checked `finally` close from the runnable class below; a long-running
app also needs its normal lifetime wait before close. Do not return from
`main` immediately after this builder.

### Java handler swap

Continue on the same fresh service:

```java
RouteSnapshot before = service.getRoutes();
RouteState version = before.getRoutes().stream()
    .filter(route -> route.getRouteId() == 1L)
    .findFirst().orElseThrow(() -> new IllegalStateException("missing route"));

service.prepareHandler(100L, request -> Responses.text("v2"));
RouteRebindOutcome outcome = service.rebindHandler(new RouteRebind(
    1L, before.getRouteGeneration(), version.getRouteId(),
    version.getBindingRevision(), 100L));
if (outcome.getCode() != RouteControlCode.APPLIED || !outcome.getChanged()) {
    throw new IllegalStateException("handler switch refused: " + outcome.getCode());
}
```

This bounded snapshot search is control-plane observation, not per-request
dispatch. Generation/revision come from Core. The complete example also checks
`RouteControlCode.REVISION_MISMATCH` on a new activation using the old
revision and proves `/version` remains `v2`.

### Run the Java example

[JavaFeatures.java](src/main/java/sample/JavaFeatures.java) is a complete finite
Java consumer with comments, bounded client reads, HTTP assertions and checked
cleanup. It sits under the JVM sample directory for shared package/build setup;
it imports no application Kotlin code.

```sh
bash coakka-http-runtime/kotlin/run.sh java-smoke
```

Expect `java-features=pass` only after static asset, SPA navigation, confined
download/range, path capture, handler activation, stale refusal and successful
close. Its independent `HttpURLConnection` is only a test client, not a
replacement server or outbound connector implementation.

The source compiles with `--release 8 -Xlint:all -Werror` and the sample
Gradle build uses JDK17. This slice executes on macOS ARM64/JDK17 against the
pinned r3 bundle; Java8 API/bytecode compatibility is checked, but this new
recipe is not newly claimed as executed on a Java8 VM or every platform.

## 5. Configure and observe the service

Read [Control.kt](src/main/kotlin/sample/Control.kt). `runtimeInfo()` and `effectiveLimits()` report accepted configuration; `service.routes` is the runtime snapshot (Java: `getRoutes()`). The sample's `--compression` option feeds `Compression` to the builder; `smoke` checks buffered GZIP alongside identity streaming. Java callers use the same public classes and Java-friendly methods; do not translate Kotlin default arguments into guessed JVM names.

```sh
bash coakka-http-runtime/kotlin/run.sh tuning-smoke
```

Core owns effective configuration, capability probes, routing, transport
deadlines and causal failures. The connector submits intent and projects facts.
Start with omitted tuning; select CPU AUTO/SINGLE only when the deployment
needs it. Loop count is connector tuning, **not an application setting**.
CPU placement is not a process-wide quota or a promise of parallel handlers.

Batch profiles and transport deadlines are startup settings in these examples.
Monitoring alone has a demonstrated generation-checked live policy: reserve
bounded storage before start, pull the effective generation, apply, check the
returned state. Monitoring is disabled by default; the feature sample explicitly
enables it. Do not infer a capability or CPU count from an input option.

Keep header/body/keep-alive/idle deadlines separate from application-handler
deadlines and outbound call deadlines. A transport timeout cannot forcibly stop
application work. Release application resources separately, and never retry
business operations solely because an HTTP status or elapsed time suggests it.

Handler replacement prepares a binding before activation and uses the current
revision. Structural route publication replaces the complete route set against
the observed generation. Read the runtime snapshot, not the local handler list;
a stale update must leave the accepted state intact.

The optional inspection application is separate. Follow the
[Inspect guide](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/inspect/README.md)
to enable target reads and connect it; these samples do not automatically start
an inspector or expose a hidden documentation endpoint.

## 6. Enable security and other protocols

First run the finite TLS/mTLS recipe:

```sh
bash coakka-http-runtime/kotlin/run.sh security-smoke
```

It generates development-only certificates under the work directory and checks
verified TLS plus rejection of a mutual-TLS client without an identity. The
security source also demonstrates runtime-owned outbound TLS/mTLS. Do not
disable peer verification or deploy the generated private keys.

For a secure HTTP/2 or HTTP/3 listener, run one of these at a time:

```sh
bash coakka-http-runtime/kotlin/run.sh http2
# Stop it before trying the other listener:
bash coakka-http-runtime/kotlin/run.sh http3
```

Use the newly printed port and a curl build supporting the selected protocol:

```sh
curl --http2 --max-time 5 \
  --cacert "$COAKKA_HTTP_SAMPLE_WORK_ROOT/tls/identities/ca.pem" \
  "https://localhost:$PORT/protocol"
# For the HTTP/3 listener, replace --http2 with --http3-only.
```

Expect `protocol-ready`. If curl lacks protocol support, use a capable client;
do not describe that as a server failure. Keep peers progressing during drain.

## 7. Handle failures and shutdown

```sh
bash coakka-http-runtime/kotlin/run.sh adverse-smoke
```

This isolated finite recipe holds application work to test handler expiry,
pressure and outbound cancellation/deadline followed by reuse. Do not copy its
deliberately blocked handlers into production. HTTP 503 alone does not tell you
which admission owner refused work; inspect typed outcomes where available.

The JVM shutdown hook coordinates with the main owner and waits for both services to close. `kotlin-shutdown=complete` is emitted only after successful cleanup. The automated signal test expects exit 143 together with that marker; 143 by itself is not success. Do not hide close refusal or free application state still reachable by handlers.

## Inspect, metadata and OpenAPI

The optional standalone Inspect application shows an explicitly enabled
target's route/schema snapshots and exports OpenAPI 3.0.3. It does not infer
business schemas from handlers, types or traffic. These language samples do
not currently enable the inspection target or declare route API metadata.
Snapshot APIs alone are not metadata-publication APIs.

Read [Inspect, metadata and Swagger UI](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/inspect/metadata-and-openapi.md)
for its purpose, connection settings, metadata checklist, export semantics
and the current public-package integration path. Do not copy private schema
or imagined builder calls into your application.

## Troubleshooting and next steps

| Symptom | Check first |
| --- | --- |
| Required environment variable missing | Set both paths in step 1 in this terminal. |
| Archive missing or checksum mismatch | Confirm the warehouse has the exact pinned train; do not substitute a different library. |
| Library load failure | Match process architecture and keep the bundle's native files together. |
| Connection refused | Wait for readiness and use the current process's printed port. |
| TLS verification failure | Use the generated CA and `localhost`; mTLS also requires the client identity. |
| Shutdown refuses or times out | Finish/cancel application work, retain ownership, inspect the typed failure and retry cleanup safely. |

See [README](README.md) for this lane's evidence and benchmark table,
[feature coverage](../FEATURES.md) for known limits, and
[benchmark methodology](../benchmark-rpi5/README.md) for the separate performance
workflow. No benchmark or new platform qualification is implied by this guide.
After all sample processes exit, remove only the dedicated work directory you
created to clean generated builds, dependencies and test credentials.
