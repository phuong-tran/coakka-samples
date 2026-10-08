# Introducing CoAkka HTTP Runtime

**One shared native HTTP runtime, with idiomatic application APIs across languages.**

CoAkka HTTP Runtime is the HTTP server and client product in the CoAkka
ecosystem. It lets applications use C/C++, Go, Kotlin/Java, Python, or
JavaScript/TypeScript on Node.js and Bun while sharing the implementation of
HTTP transport, routing, resource limits and lifecycle.

It is independent of **CoAkka Runtime**, the distributed target/message runtime.
HTTP Runtime handles HTTP requests; Runtime routes application-owned work to
targets. An application can use either alone or compose them at its handler.

## Contents

- [Why It Exists](#why-it-exists)
- [What A Shared Runtime Gives You](#what-a-shared-runtime-gives-you)
- [How Application Code Fits](#how-application-code-fits)
- [Supported Capabilities](#supported-capabilities)
- [What It Does Not Promise](#what-it-does-not-promise)
- [When To Use It](#when-to-use-it)
- [Try It And Read Further](#try-it-and-read-further)

## Why It Exists

In a polyglot system, HTTP behavior can become fragmented by language. A Go
service, a Python service and a JavaScript service may each have different
conventions for admission, timeouts, cancellation, streaming, observability and
graceful shutdown. Teams then implement, tune and diagnose similar transport
mechanics several times.

CoAkka HTTP Runtime puts those shared mechanics in one native implementation.
Language connectors expose that implementation through familiar functions,
values and lifecycle APIs. The goal is consistent HTTP behavior across
languages without forcing every application to adopt the same programming style.

This is more than giving unrelated HTTP servers similar method names. The
server's transport and protocol work belongs to the shared runtime; the
connector does not build a second HTTP server around a language's native server.

## What A Shared Runtime Gives You

| Benefit | What it means for an application team |
| --- | --- |
| Less cross-language fragmentation | Limits, effective configuration, failure outcomes, monitoring and shutdown follow a shared contract instead of unrelated per-language policies. |
| Shared fixes and optimizations | A correction or optimization in the common HTTP implementation can benefit every connector that ships that updated runtime. Teams do not need to port the same transport fix to every language. |
| Familiar application code | Go handlers remain Go functions; JVM APIs are Kotlin-idiomatic and Java-friendly; Python and JavaScript keep their own values and async/resource-lifetime rules. |
| One source of operational truth | Runtime information reports accepted settings and observed capabilities. Connectors expose those facts instead of guessing defaults, CPU use or backend activation. |
| Explicit resource and failure behavior | Bounded connections, requests, buffers and retained state make overload visible. Cancellation, deadlines and graceful close are part of the service contract. |
| Reusable operational knowledge | Teams can carry the same understanding of pressure, route changes and lifecycle across services, even when their application languages differ. |

“One runtime” means a shared implementation used by each application's runtime
instance, not one central server process through which all applications must
send traffic. Each service still owns its own listener, configuration and
lifecycle. It also does not mean already-installed packages update themselves:
consumers must adopt a verified package containing the updated native runtime.

## How Application Code Fits

```text
HTTP client <-> shared native HTTP runtime <-> language connector <-> handler
```

| Layer | Responsibility |
| --- | --- |
| HTTP runtime | Transport and protocol state, parsing, route matching, bounded resources, effective configuration, deadlines, terminal outcomes and shutdown. |
| Language connector | Idiomatic API, projection of request/response values, language scheduling and safe lifetimes at the native boundary. |
| Application handler | Business behavior, authentication/authorization policy, data access, serialization choices and application retries. |

Handlers use the **host-inlined** application surface: ordinary application
handlers run in their language host and return through the connector to the
runtime. Users do not need to build a separate queue/pipe consumer or select
an internal execution mode. Business code is not moved into native code.

The native runtime may use multiple CPUs for its own work. That does not make
a single JavaScript event loop parallel, remove Python interpreter constraints,
or eliminate connector conversion costs. Language scheduling and application
work still matter.

## Supported Capabilities

| Area | Capabilities |
| --- | --- |
| HTTP server and routing | HTTP/1.1 and available HTTP/2/HTTP/3 support; method/path routes, captures, query values and ordered headers. |
| Request and response bodies | Buffered and streamed bodies, response trailers, bounded upload/download handling and compression. |
| Realtime connections | Server-Sent Events and WebSocket sessions. |
| Frontend and file delivery | Static mounts, index files, SPA fallback, confined application-file responses, validators and byte ranges. |
| Outbound HTTP | Service-owned HTTP client requests with bounded capacity, deadlines, cancellation and TLS/mTLS. |
| Transport security | TLS and mutual TLS, trust and identity configuration, with the selected package's capability checks. |
| Live route changes | Handler replacement, complete route publication and coherent route snapshots with stale-update refusal. |
| Operations | Health, liveness, runtime information, bounded monitoring/events and explicit overload or failure outcomes. |
| Lifecycle and tuning | Graceful shutdown, timeout policies, CPU intent and batch settings; the runtime reports effective values. |
| Optional inspection | A separate local development inspection application; it is not required to serve HTTP. |

Use the [capability matrix](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/capabilities.md)
for exact contracts and the [feature/sample index](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-http-runtime/FEATURES.md)
for runnable examples, tested profiles and remaining coverage gaps. A feature
name here is not a claim that every host/package implements every optional
protocol or that every failure combination has been tested.

## What It Does Not Promise

- Identical throughput across languages, or superiority to every HTTP server.
  Common implementation improvements still pass through each host's scheduling,
  allocation, conversion and business-code costs.
- A native-code implementation of application handlers. Handlers remain in the
  host language; an HTTP-runtime CPU budget is not a process-wide CPU quota.
- A mandatory replacement for an existing framework, or automatic porting of
  its middleware/plugins. Framework conventions and business policy remain above
  the HTTP runtime.
- A single global version or compatibility matrix for every CoAkka product.
  Runtime, HTTP Runtime, Logger and addons are independently versioned.

## When To Use It

Use it when consistent HTTP mechanics and operations across several languages
are valuable, or when an application wants the bounded HTTP server/client
contract without adopting a complete application framework.

Keep an existing server when its API and middleware already fit the application
and a shared cross-language HTTP runtime would not solve a concrete problem.
For distributed application-owned target/request/reply work rather than HTTP,
start with [CoAkka Runtime](new-to-coakka.md).

## Try It And Read Further

HTTP Runtime currently ships as checksum-pinned archives in
[`coakka-publish/main`](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-http-runtime).
It is **not yet distributed through npm, PyPI, Maven Central or a tagged Go
module**. CoAkka Runtime's existing registry coordinates do not install it.

1. Choose a language in the [HTTP sample guide](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-http-runtime).
2. Follow that language's prerequisites and archive-backed run instructions.
3. Explore the feature index above, then [operations](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/operations.md).
4. Read [Pi 5 benchmark results](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/benchmark-results-rpi5.md)
   with their payload, CPU configuration, machine and measurement conditions;
   unmeasured comparisons remain pending.
