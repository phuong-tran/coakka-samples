# CoAkka HTTP Runtime Samples

> Polyglot applications should not require polyglot HTTP infrastructure.

Native is our measurement baseline, not a promise of identical language
throughput. Each host has scheduling, conversion and memory-management costs;
measuring those costs under matched conditions helps plan capacity and scaling.
See [native baseline and language costs](../docs/coakka-http-runtime-introduction.md#native-baseline-and-language-costs)
and the [monitoring guide](monitoring.md).

These runnable applications show CoAkka HTTP Runtime from each supported
language without hiding lifecycle, capacity, security, or failure handling.
The JVM lane includes Kotlin recipes and a Java consumer; both languages use
the same JVM connector. One TypeScript source set runs on both
Node.js and Bun.

**First time here?** Choose your language's **Start here** guide below. Run one
server and send one request before exploring features or benchmark evidence.
You do not need to run the all-language verification suite to get started.

## Why A Shared HTTP Runtime?

Instead of building separate transport and lifecycle policies for every
language, CoAkka HTTP Runtime supplies one native HTTP implementation. Routing,
limits, timeouts, monitoring and graceful shutdown follow a shared contract.
Fixes and optimizations in that implementation can benefit every connector
that includes the updated runtime, while handlers remain idiomatic application
code. Each host still has its own scheduling and conversion costs.

Read the [introduction, benefits and capability overview](../docs/coakka-http-runtime-introduction.md)
for the product rationale. The examples below show how to use it; they are not
the introduction to CoAkka Runtime's separate distributed messaging model.

## Distribution

The samples consume checksum-pinned offline candidates from `coakka-publish`.
No private runtime or connector checkout is required, and neither is rebuilt.
These exact archives are available on `coakka-publish/main`, not in package registries.
Build tools may download their own dependencies; CoAkka comes only from the
explicit warehouse checkout. Nothing is uploaded to a registry.

For the distributed target/message runtime, start with
[CoAkka Runtime samples](../runtime/README.md). HTTP Runtime is an independent
HTTP server/client; the two products have different package coordinates.

## Contents

- [Inspect: metadata, OpenAPI and Swagger UI](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/inspect/metadata-and-openapi.md)

- [Roadmap: distribution, certificates and connectors](roadmap.md)

- [Why A Shared HTTP Runtime?](#why-a-shared-http-runtime)
- [Distribution](#distribution)
- [Languages](#languages)
- [URL grammar, request/response and glossary](glossary.md)
- [File delivery, sendfile and uploads](file-delivery.md)
- [Feature Map](#feature-map)
- [Run](#run)
- [Verified Candidate Scope](#verified-candidate-scope)
- [Routes](#routes)
- [Security Sample](#security-sample)
- [Raspberry Pi 5 Benchmark](#raspberry-pi-5-benchmark)
- [Boundaries](#boundaries)
- [Detailed feature coverage](FEATURES.md)

## Languages

| Language and host | Start here | Details and evidence |
| --- | --- | --- |
| C11 | [C integration](c/integration.md) | [C sample](c/README.md) |
| C++20 | [C++ integration](cpp/integration.md) | [C++ sample](cpp/README.md) |
| Go 1.23 or newer | [Go integration](go/integration.md) | [Go sample](go/README.md) |
| JVM: Kotlin and Java; Java 8 bytecode, JDK 17 sample compilation | [JVM integration](kotlin/integration.md) | [Kotlin and Java samples](kotlin/README.md) |
| Python 3.11 or newer | [Python integration](python/integration.md) | [Python sample](python/README.md) |
| TypeScript on Node.js 22+ and Bun 1.2.22+ | [Node/Bun integration](typescript/integration.md) | [TypeScript sample](typescript/README.md) |

## Feature Map

See the [detailed feature index and remaining coverage](FEATURES.md). A listed
source example is not a claim that every edge case has already been tested.

Each lane is a runnable application, not disconnected snippets. Native C/C++
use `server.c`/`server.cpp` with public callbacks and a shared sample-only
configuration helper. Other lanes use `main` plus a separate `security` source.
Temporary test identities never enter the source tree.

| Feature | Runnable source |
| --- | --- |
| Buffered routing and responses | Every `main` application |
| Path/query values and ordered headers | Go, Kotlin, Python, and TypeScript `main` |
| Streamed request body | Go, Kotlin, Python, and TypeScript `main` |
| Streamed response with final trailers | Go, Kotlin, Python, and TypeScript `main` |
| Server-Sent Events | Go, Kotlin, Python, and TypeScript `main` |
| WebSocket upgrade and message callback | Go, Kotlin, Python, and TypeScript `main` |
| Static frontend, index, and SPA fallback | Every `main` application |
| Confined application-file response | Every `main` application |
| Logical-target outbound HTTP | Go, Kotlin, Python, and TypeScript `main` |
| Health, fresh liveness, and bounded monitor events | Every `main` application |
| Handler-only activation | Every `main` application |
| Complete route-generation publication | Go, Kotlin, Python, and TypeScript `main` |
| io_uring opt-in with observable native fallback | Go, Kotlin, Python, TypeScript; the native convenience-server samples retain platform defaults |
| TLS and mutual TLS | Every `security` application |
| HTTP/1.1, HTTP/2, and HTTP/3 listener selection | Native `security` applications; the language packages use the same listener declaration |
| Graceful close and process signals | Every language lane |
| Finite connections, queues, bodies, streams, files, events, and shutdown | Every language lane |

The feature applications deliberately use ordinary public service APIs. The
low-level configuration surfaces remain available for specialized control
planes, but are not a better starting point for application code.

## Run

From the repository root:

```bash
export COAKKA_HTTP_SAMPLE_WORK_ROOT=/path/to/a-build-volume/coakka-http-runtime-samples
export COAKKA_PUBLISH_ROOT=/path/to/coakka-publish
bash coakka-http-runtime/run.sh verify
```

`verify` performs static checks, builds every lane with warnings treated as
errors where the compiler supports it, runs the application smoke tests, and
then runs real TLS and mutual-TLS handshakes. A client without an identity must
be rejected by every mutual-TLS sample.

Run one lane instead:

```bash
bash coakka-http-runtime/run.sh go smoke
bash coakka-http-runtime/run.sh kotlin security-smoke
bash coakka-http-runtime/run.sh typescript run
bash coakka-http-runtime/run.sh typescript run-bun
```

Use the matching `coakka-publish` warehouse checkout. The resolver
checks independently pinned archive hashes before extraction and verifies the
extracted tree before reuse; damage fails rather than selecting another library.
Every build, cache, generated identity and staged application stays below
`COAKKA_HTTP_SAMPLE_WORK_ROOT`. Remove that dedicated directory after all sample
processes exit to clean generated files; never point it at a shared system root.

The shell runners currently cover macOS ARM64 and Linux ARM64/x86-64. Windows
package qualification is separate from these POSIX runner checks; Windows
sample-runner execution is not claimed here. Python 3.11+, CMake 3.24+, a native
compiler, curl and a certificate-generation tool are required for native smoke.
Install each selected language toolchain; `verify` additionally uses ShellCheck.
Node/Bun share one package. Windows Bun users should follow the package's tested
Bun 1.4.2+ recommendation, independently of its Unix 1.2.22 API floor.

## Verified Candidate Scope

The October 7 artifact-backed check passes on macOS ARM64 for C, C++, Go,
Kotlin, Python, Node.js and Bun: static/build checks, application smoke,
TLS/mTLS and missing-client-identity rejection. The same applications and secure
smokes pass on Raspberry Pi Linux ARM64. The Pi JVM run executes the same Java 8
sample class files built on macOS, with the Linux candidate libraries on Java 21;
it does not claim a separate Pi Gradle/JDK 17 compilation.

Native sample code additionally passes scoped Clang analysis, ASan/LSan/UBSan
and separate TSan on macOS. Those instruments cover the sample executable, not
the prebuilt runtime library. All 25 archive layouts and their pinned identities
are checked; Windows and Linux x86-64 sample execution are not implied by that
inventory check. Historical benchmark results are not part of this evidence.

## Routes

The four application-language lanes share these recognizable routes:

| Route | Purpose |
| --- | --- |
| `/hello/...` or `/hello?name=...` | Read a path or query value |
| `/echo` | Buffered request and response |
| `/upload` | Incremental request body |
| `/stream` | Incremental response and final trailer |
| `/events` | Finite Server-Sent Event response |
| `/socket` | WebSocket upgrade and text echo |
| `/download` | File response confined to an authority root |
| `/app/...` | Static files and SPA navigation fallback |
| `/version` | Generation-checked handler activation |

Each Go/Kotlin/Python/TypeScript smoke also starts a loopback upstream, submits one logical-target
outbound request, publishes a complete route generation on an isolated
service, reads fresh liveness and monitoring truth, and closes all owners in
reverse order.

## Security Sample

`scripts/generate-test-certificates.sh` creates a short-lived authority,
server identity, and client identity in the configured build directory. The
files are test-only and are removed with that directory. Production services
should use their normal secret manager and advance the credential generation
when identities rotate.

## Raspberry Pi 5 Benchmark

The [Raspberry Pi 5 benchmark](benchmark-rpi5/README.md) consumes the same pinned
public packages as the samples; preparation builds benchmark applications, not
the runtime or connectors. Selected r3 package cohorts now have verified localhost
observations with machine facts, resources and per-run results in the linked
ledger. Missing current-package profiles and framework pairs remain Pending;
sample smoke never requalifies an old throughput result. Native C is measured
alone. Connector measurements use the `host-inlined` path; standalone Netty is
excluded. The explicitly requested Bun.serve pair is reported separately.

## Boundaries

For an optional local browser view of an inspection-enabled service, see the
[HTTP Runtime Inspect guide](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/inspect/README.md).
The standalone application has separate macOS ARM64/Linux ARM64 packages.
Inspection is opt-in; the ordinary sample smoke commands do not enable it or
claim an Inspect integration run. Follow the guide's target-read prerequisites
and local-only security constraints before connecting.

- Application routing, codecs, authentication policy, and domain behavior stay
  in application code or addons.
- The file samples grant only a declared root and never accept an arbitrary
  operating-system path from a request.
- Monitor retention excludes headers, bodies, credentials, cookies, and
  certificate material.
- The generated test identities are not deployment credentials.
- Package-manager publication is outside this sample change.
