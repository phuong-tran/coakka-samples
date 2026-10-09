# JVM Samples: Kotlin and Java

**Start here:** [Installation and integration guide](integration.md) — prerequisites,
package setup, first HTTP request, feature walkthroughs and checked shutdown.
This README records sample details, verification scope and benchmark results.

Kotlin is the JVM sample language. The application uses builders, data
classes, lambdas, typed response producers, `use`-style stream ownership, and a
JVM shutdown hook rather than translating a Java sample line by line.

The connector also supports Java applications through the same JAR and public
classes. Start with [Java equivalents](integration.md#java-uses-the-same-jvm-connector)
and the runnable [JavaFeatures.java](src/main/java/sample/JavaFeatures.java).

```sh
bash coakka-http-runtime/kotlin/run.sh java-smoke
```

## Contents

- [What It Shows](#what-it-shows)
- [Run](#run)
- [Feature Recipes And Coverage](#feature-recipes-and-coverage)
- [Control And Failure Recipes](#control-and-failure-recipes)
- [HTTP2 And HTTP3 Listeners](#http2-and-http3-listeners)
- [Raspberry Pi 5 Benchmark](#raspberry-pi-5-benchmark)

## What It Shows

- path/query handling, buffered bodies, and streamed request delivery;
- streamed responses with final trailers, Server-Sent Events, and WebSocket;
- static frontend files, SPA fallback, and a confined file response;
- logical-target outbound HTTP, cancellation/deadline and TLS/mTLS;
- health, fresh liveness, bounded monitor events and generation-checked policy reload;
- handler-only activation and complete route-generation publication;
- default platform I/O plus an explicit `--io-uring` opt-in;
- finite close in reverse construction order.
- bounded buffered GZIP alongside unbuffered streaming;
- runtime-issued route snapshots and CPU/batch/transport-timeout observations.

`io_uring` is off unless `--io-uring` is present. The Kotlin builder forwards
the preference; native startup owns capability detection and falls back to
`epoll` when needed. The sample prints both requested and effective states.

`Security.kt` builds a JVM trust store from the test authority and an optional
client key store from the generated identity. It verifies TLS and proves that
mutual TLS refuses an unidentified client. It also starts a separate client
runtime with explicit CA and client-identity generations: the runtime, not the JVM
client, owns that outbound TLS/mTLS connection and verifies the peer name.

## Run

```bash
bash coakka-http-runtime/kotlin/run.sh check
bash coakka-http-runtime/kotlin/run.sh smoke
bash coakka-http-runtime/kotlin/run.sh tuning-smoke
bash coakka-http-runtime/kotlin/run.sh adverse-smoke
bash coakka-http-runtime/kotlin/run.sh security-smoke
bash coakka-http-runtime/kotlin/run.sh run
```

Gradle and all build output are redirected to the configured external work
directory. The runner uses the pinned candidate JAR and its matching native
libraries, without compiling the connector. JDK 17 is the sample build toolchain,
not a connector runtime floor: generated sample bytecode and referenced JDK APIs
target Java 8. The sample uses only the pinned connector bundle.
Set `COAKKA_PUBLISH_ROOT` and `COAKKA_HTTP_SAMPLE_WORK_ROOT` as described in the
[sample overview](../README.md). No CoAkka Maven publication is required.

For repeated qualification runs, set `COAKKA_HTTP_SAMPLE_GRADLE_USER_HOME` to
an existing task-owned Gradle cache on that build volume. Sample compiled
outputs still remain in `COAKKA_HTTP_SAMPLE_WORK_ROOT`.

## Feature Recipes And Coverage

Use the [feature index](../FEATURES.md) for source links, route commands,
expected responses and outstanding test coverage. Sample presence, package
capability and matching-host execution are separate claims.

`smoke` verifies negotiated GZIP, buffered/streaming binary bodies, response
trailers, multiline SSE, WebSocket text/binary/ping/close/abort/reuse and file
range/validator behavior. The upload reader is capped at64KiB and exposes a
test trailer only after EOF. A peer-abort check observes the actual reader
failure, then verifies a subsequent upload. Diagnostic markers contain no
request data and are for this feature sample, not a benchmark hot path.

The JVM shutdown hook waits until the main owner has closed both services.
`kotlin-shutdown=complete` appears only after successful close. The wire driver
requires that marker together with the JVM's normal SIGTERM exit status143;
signal delivery alone is not proof of graceful shutdown. A close failure is
not swallowed. A failed close never authorizes restarting or freeing an owner.

## Control And Failure Recipes

| Recipe | Contract demonstrated |
| --- | --- |
| [Main.kt](src/main/kotlin/sample/Main.kt) route publication | Pull the runtime snapshot, publish against its generation, reject stale intent without changing the effective route |
| [Control.kt](src/main/kotlin/sample/Control.kt) monitoring | Apply within startup reservation, reject stale/oversized policy atomically, restore initial policy |
| `tuning-smoke` | Named SMALL/MEDIUM notification profiles; independently observed the runtime caps and header/body timeouts |
| `tuning-smoke` on Linux | Also requests SINGLE and checks the runtime's selected CPU count; default remains AUTO |
| [Adverse.kt](src/main/kotlin/sample/Adverse.kt) handler deadline | the runtime returns504 while application work is held; late result does not become a second response; service remains usable |
| Outbound cancellation/deadline | Exactly matched typed terminal followed by successful call reuse; application work is released separately |
| Pressure recipe | Finite request burst and bounded queues; HTTP503 does not identify which admission owner refused it |
| [Security.kt](src/main/kotlin/sample/Security.kt) | Inbound independent TLS verifier plus runtime-owned outbound TLS/mTLS, without disabling identity checks |

CPU placement is not a JVM-wide quota: pre-existing GC/JIT threads are outside
the service scope. Unsupported placement reports unknown counts rather than
inventing them. Notification profiles are startup-only; monitor collection
policy is generation-checked and live. Neither is changed on the request path.
The sample's5000ms transport deadlines and64KiB body ceiling are explicit
application choices, not duplicated runtime defaults. Read accepted settings
through the runtime's runtime-info/effective-limits API.

Java8 execution of the feature/wire, deadline/cancellation/reuse, security and
HTTP2/3 recipes passes on macOS ARM64 and Pi Linux ARM64. Pi uses class files
compiled on Mac; this is not a Pi Gradle build claim. Five-platform connector
package qualification is separate from this two-host sample evidence. Additional
fault combinations remain documented as gaps; no throughput result is implied.

## HTTP2 And HTTP3 Listeners

```sh
bash coakka-http-runtime/kotlin/run.sh http2
# In another terminal, use the printed PORT:
curl --http2 --cacert "$COAKKA_HTTP_SAMPLE_WORK_ROOT/tls/identities/ca.pem" \
  --max-time 5 "https://localhost:$PORT/protocol"

bash coakka-http-runtime/kotlin/run.sh http3
curl --http3-only --cacert "$COAKKA_HTTP_SAMPLE_WORK_ROOT/tls/identities/ca.pem" \
  --max-time 5 "https://localhost:$PORT/protocol"
```

Check `curl --version` for protocol support; an unsupported client is not a
runtime failure. Expected body is `protocol-ready`. These listeners use the runtime's
TLS/HTTP2/HTTP3 implementation with ordinary Kotlin handlers, not a replacement
server. Independent protocol peers verified wire output and graceful drain on
Mac/Pi; the HTTP3 peer remains alive to acknowledge shutdown until the runtime drains.

## Raspberry Pi 5 Benchmark

| Application | CPUs | Req/s median (range) | p50 / p95 / p99 ms | CPU % | RSS mean / sampled peak MiB | Errors / timeouts |
| --- | ---: | --- | --- | ---: | --- | --- |
| Kotlin/JVM + CoAkka | 2 | 94,918.3 (94,639.6–95,380.8) | 0.664 / 0.700 / 0.922 | 154.8 | 219.4 / 223.9 | 0 / 0 |

Installed r3 packages; three-run localhost observations, not a universal
framework ranking. Missing framework comparisons remain **Pending**. The final
JVM package 1-CPU row, Vert.x and Undertow are **Pending**.
See [complete results, machine facts, all runs and evidence identities](../benchmark-rpi5/README.md#verified-package-results)
and the [methodology](../benchmark-rpi5/README.md). CPU100% means one CPU;
RSS is sampled, and resource measurements include warm-up.
