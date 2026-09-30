# CoAkka HTTP Runtime Samples

These runnable applications show CoAkka HTTP Runtime from each supported
language without hiding lifecycle, capacity, security, or failure handling.
Kotlin is the JVM sample language. One TypeScript source set runs on both
Node.js and Bun.

The samples build the sibling runtime and connector source into an external
work directory. They do not install CoAkka from npm, Maven, PyPI, or a Go
module proxy, and they do not publish anything to those registries.

## Languages

| Directory | Language and host | Guide |
| --- | --- | --- |
| `c/` | C11 | [C sample](c/README.md) |
| `cpp/` | C++20 | [C++ sample](cpp/README.md) |
| `go/` | Go 1.23 or newer | [Go sample](go/README.md) |
| `kotlin/` | Kotlin on JVM 17 | [Kotlin sample](kotlin/README.md) |
| `python/` | Python 3.11 or newer | [Python sample](python/README.md) |
| `typescript/` | TypeScript on Node.js 22+ and Bun 1.3+ | [TypeScript sample](typescript/README.md) |

## Feature Map

`main` is a runnable application, not a collection of disconnected snippets.
It combines the features an application commonly uses together. `security`
is separate so temporary test identities never enter the source tree.

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
| io_uring opt-in with observable native fallback | Every language lane |
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

Sibling checkouts are discovered as `../coakka-http-runtime` and
`../coakka-http-runtime-connector`. Set `COAKKA_HTTP_RUNTIME_ROOT` and
`COAKKA_HTTP_CONNECTOR_ROOT` for another layout. Every build, cache, generated
identity, and staged application stays below `COAKKA_HTTP_SAMPLE_WORK_ROOT`.

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

Each smoke command also starts a loopback upstream, submits one logical-target
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

The [Raspberry Pi 5 benchmark](benchmark-rpi5/README.md) is run only after all
language samples pass. It measures the `host-inlined` path and compares it with
frameworks developers actually choose in each ecosystem. Language-standard
HTTP servers are intentionally excluded. Raw evidence and machine facts are
required before any result table is treated as publishable.

## Boundaries

- Application routing, codecs, authentication policy, and domain behavior stay
  in application code or addons.
- The file samples grant only a declared root and never accept an arbitrary
  operating-system path from a request.
- Monitor retention excludes headers, bodies, credentials, cookies, and
  certificate material.
- The generated test identities are not deployment credentials.
- Package-manager publication is outside this sample change.
