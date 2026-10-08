# Feature Samples And Coverage

This index separates a supported API from a runnable example and from a
verified test. **Example** means source exists; it does not mean every failure
path or every platform has been exercised. **Gap** is work still required, not
an unsupported-product claim. The current checks use exact offline candidates.

## Contents

- [Find The Source](#find-the-source)
- [Feature Matrix](#feature-matrix)
- [Final r3 Package Checkpoint](#final-r3-package-checkpoint)
- [Try The Existing Routes](#try-the-existing-routes)
- [Configuration And Ownership](#configuration-and-ownership)
- [Verification Boundaries](#verification-boundaries)

## Find The Source

| Host | Feature application | Security application | Guide |
| --- | --- | --- | --- |
| C | [server.c](c/server.c) | Same executable, security options | [C](c/README.md) |
| C++ | [server.cpp](cpp/server.cpp) | Same executable, security options | [C++](cpp/README.md) |
| Go | [main.go](go/main.go) | [security.go](go/security.go) | [Go](go/README.md) |
| Kotlin/JVM | [Main.kt](kotlin/src/main/kotlin/sample/Main.kt) | [Security.kt](kotlin/src/main/kotlin/sample/Security.kt) | [Kotlin](kotlin/README.md) |
| Python | [main.py](python/main.py) | [security.py](python/security.py) | [Python](python/README.md) |
| Node.js/Bun | [main.ts](typescript/main.ts) | [security.ts](typescript/security.ts) | [TypeScript](typescript/README.md) |

Native response streaming/SSE has focused executables:
[`c/streaming.c`](c/streaming.c) and [`cpp/streaming.cpp`](cpp/streaming.cpp).
Run the language's `streaming` command; use its separately printed port.

## Feature Matrix

This table indexes available recipes. The named language records below give
their verified scope; the final r3 checkpoint distinguishes fresh runs from
earlier recipe evidence. None of these entries implies exhaustive fault testing.

“Managed lanes” below means Go, Kotlin, Python and TypeScript, not a claim that
Go uses a virtual machine. Each lane must retain its own idiomatic API.

| Capability | Native C/C++ examples | Managed-lane examples | Verification/gap |
| --- | --- | --- | --- |
| Buffered binary request/reply | `/echo` | `/echo` | Existing smoke |
| Path/query/ordered headers | `/items/{id}` uses Core-parsed borrowed parameters | `/hello/{name}`; TypeScript `/hello` query | Native encoded/duplicate/absent/empty cases and named managed ordered-header recipes; see language records below |
| Streaming upload | `/upload` byte/trailer counter | `/upload` echo collector | Native and managed trailer/peer-abort/reuse recipes; not exhaustive saturation coverage |
| Streaming response/trailers | Separate `streaming` executable, `/stream` | `/stream` | Native final-trailer/slot-reuse and named managed response-trailer wire checks |
| SSE | Separate `streaming` executable, `/events` | `/events` | Native and managed multiline/ID/retry recipes; exhaustive producer-pressure combinations not claimed |
| WebSocket | `/socket` | `/socket` | Native and managed handshake/text/binary/ping/close/abort/reuse recipes |
| Static index/SPA fallback | `/app` | `/app` | Native and managed range/validator recipes; no arbitrary filesystem grant |
| Confined file response | `/download` | `/download` | Named range/validator recipes; broader traversal fault matrix belongs to Core tests |
| Logical-target outbound HTTP | C: [`outbound.c`](c/outbound.c); C++: [`outbound.cpp`](cpp/outbound.cpp) | Startup demonstration against a loopback service | Native 200/404 and terminal release; managed cancel/deadline/reuse and TLS/mTLS recipes |
| Handler-only replacement | `/version`, prepared `v2`, stale revision rejected | `/version`, prepared `v2` | Native and managed success/stale-refusal recipes with continued dispatch |
| Complete route publication | Outbound examples replace `/source` with `/published` | Isolated `/old` to `/published` service | Native apply/replay/stale refusal; managed coherent snapshot/publication/refusal recipes |
| Health/liveness/runtime information | Startup reads with required Core execution and CPU/batch observations | Startup reads | Core-owned observations; current installed native ABI12/info5 suite and r3 Mac startup checks |
| Monitor events/live policy | Bounded read and [reload recipe](native/monitor_example.h) | Bounded startup read | Named accepted/stale/over-reservation/restore recipes; exhaustive cursor/waiter faults not claimed |
| TLS/mTLS | Security mode | Separate security source | HTTP/1.1 success and missing-client-identity rejection checked |
| HTTP/2 and HTTP/3 | Listener CLI selection | Package listener configuration | Independent Mac/Pi wire/graceful-drain recipe evidence below; not inferred from ordinary HTTP/1 smoke |
| CPU/batch/timeouts | Startup CLI for CPU and independent batch profiles; Core-issued effective log | Startup tuning and effective-state recipes | Native and managed tuning recipes; AUTO on Mac/Pi, SINGLE placement on Pi; r3 Mac tuning rerun |
| Optional I/O backend | Convenience server retains default | `--io-uring` intent | No eligibility or successful activation claim from a flag alone |
| Cancellation/typed failures/drain | Checked close | Language lifecycle | Managed adverse deadline/late-result/pressure/reuse recipes and checked close; native ownership suite separate |
| Compression | `--compression gzip` with bounded decoded wire assertion | Go `--compression`, combined with stream/SSE/file/WS smoke | Native and all managed GZIP recipes composed with identity streaming; see per-language evidence |
| Route/schema inspection | Separate optional product | Separate optional product | Not a hidden HTTP endpoint or automatic handler introspection |

Unlisted fault combinations remain outside the verified scope. A feature
example does not promise exhaustive protocol or failure-path coverage.

### Go Verification Update

The [Go control and wire recipes](go/README.md#wire-and-failure-checks) now
pass on macOS ARM64 and Pi Linux ARM64 using checksum-pinned Go candidates.
For Go, this supersedes the generic managed-expansion notes above for encoded
path/query and ordered duplicate headers, upload trailers and peer abort,
response trailers, multiline SSE, WebSocket abort/reuse, file range/validator,
handler stale-revision refusal, route publication replay/stale refusal, monitor
accepted/stale/over-reservation/restore, and effective CPU/batch/deadline settings.
SINGLE placement is checked on Pi; default AUTO is checked on both. The Go race
detector covers Go source, not packaged Core. Other managed languages retain
their individual pending coverage until verified.

Go's `adverse-smoke` additionally verifies handler deadline firing, suppression
of a late handler result, bounded dispatch pressure and subsequent service reuse
(five repetitions on Mac/Pi). Its security recipe covers Core-owned outbound
TLS/mTLS with explicit trust and client identity, as well as the independent
inbound verifier. Mac Go race checks pass; they do not instrument packaged Core.
Outbound cancellation/deadline/reuse and coherent route snapshot recipes now
pass Mac/Pi against the replacement Go candidate. No sample infers a cause from
an integer or reconstructs Core state from local route declarations. The
[`http2`/`http3` listener commands](go/README.md#http2-and-http3-listeners) also
pass independent wire and graceful-drain checks on both hosts. These specific
Go results supersede its generic pending cells above; other hosts/languages and
additional fault-case coverage are not implied.

The Go host-inlined builder now projects optional compression directly to Core.
The October7 candidate fixes Core's buffered-GZIP/stream composition; Go1.23
sample gates pass Mac/Pi, and package tests cover identity refusal with HTTP406
followed by service reuse. No low-level dispatch workaround is used.
These results cover the named recipes, not every possible adverse combination.

### Kotlin Verification Update

The [Kotlin recipes](kotlin/README.md#control-and-failure-recipes) consume the
replacement JVM candidate with Core-issued route snapshots, named outbound
outcomes and optional compression/transport deadlines. Java8 execution passes
on Mac/Pi for GZIP combined with streaming, upload trailers/peer abort/reuse,
response trailers, multiline SSE, WebSocket text/binary/ping/close/abort/reuse,
file validators/ranges, snapshot publication/stale refusal, monitor policy
accepted/stale/over-reservation/restore, and effective configuration observations.
CPU SINGLE is checked on Pi; Mac preserves Core's unsupported/unknown facts.
Handler deadline, outbound cancellation/deadline/reuse and inbound/outbound
TLS/mTLS also pass. HTTP2/3 listeners pass independent wire/graceful-drain checks.
These specific results supersede Kotlin's generic pending cells above; they do
not establish other sample platforms or every fault combination. Benchmark
measurements remain pending.

### Python Verification Update

The [Python recipes](python/README.md#control-and-failure-recipes) now exercise
bounded upload/trailers/abort/reuse, buffered GZIP plus identity streams,
multiline SSE, WebSocket text/binary/ping/close/abort/reuse, file ranges/validators,
Core route snapshots/stale publication refusal and live monitor accepted/stale/
over-reservation/restoration. Core CPU/batch/deadline observations, inbound and
outbound TLS/mTLS, handler deadline, overload and outbound cancel/deadline/reuse
pass Mac/Pi; adverse cases run five times per host. HTTP2/3 sample processes pass
independent wire/graceful-drain checks. These supersede Python's generic pending
cells above only for those named recipes; no exhaustive fault or benchmark claim.

### TypeScript Verification Update

The [Node/Bun recipes](typescript/README.md#control-and-failure-recipes) now
exercise actual GZIP with identity streaming, upload trailers/peer abort/reuse,
multiline SSE, WebSocket text/binary/ping/close/abort/reuse, file ranges/validators,
Core route snapshots/stale publication refusal, native inbound/outbound TLS/mTLS,
Core CPU/batch/deadline observations and monitor accepted/stale/reservation/
restoration. Node22.0.0 and Bun1.2.22 pass these named recipes on Mac/Pi, including
five adverse runs per engine/host and independent HTTP2/3 wire/graceful-drain
checks. The source keeps Core-parsed path/query/header order and absence intact.
These specific results supersede TypeScript's generic pending entries above;
they do not establish exhaustive fault coverage or current benchmark results.

### Upload Cleanup

The TypeScript upload example handles the package's `dispose` notification to
release retained chunks even when the peer disconnects before `end`. Cleanup is
synchronous and idempotent; it returns no response and does not imply HTTP
success. Core owns transport outcomes; the connector owns the JavaScript handler
scope and retains one admission until its asynchronous work settles. Never use a
timer to guess retirement. The corrected candidate has package tests for real
partial-body disconnects, repeated terminal observations, handler failure,
bounded admission and refused close followed by retry. These package tests do
not close the other sample coverage gaps in the table above.

## Final r3 Package Checkpoint

All sample pins now select the same 25 exact r3 archives admitted by the
warehouse. The installed native suite passes nine tests repeated three times
on macOS ARM64, Linux ARM64/x64 and Windows ARM64/x64. Windows x64 runs under
Windows ARM64 emulation; this is correctness evidence, not x64 performance.

Fresh macOS sample verification passes check, application/wire smoke and
TLS/mTLS for C, C++, Go, Kotlin, Python and TypeScript (both Node and Bun).
Go, Kotlin, Python and TypeScript additionally pass tuning and adverse recipes.
Those runs consume installed packages without rebuilding Core or connectors.
Earlier Mac/Pi protocol and named feature receipts above retain their original
candidate identity; they are not relabeled as fresh r3 Pi sample executions.

Public native contract is ABI12/runtime-info5; the former ABI11 references
above describe the preceding recipe checkpoint. Configuration and runtime
observations remain Core-owned. Connector execution tuning is fixed internally,
not an application setting. See the [verified benchmark records](benchmark-rpi5/README.md#verified-package-results)
for current-package observations and explicit Pending profiles.

## Try The Existing Routes

Set the two package/work directories in the [overview](README.md#run), then run
one language's `run` command. Read its printed loopback port; do not assume a
fixed port. Set `PORT` below to that value in a second terminal.

```sh
PORT=12345 # replace with the port printed by the running sample
curl --max-time 5 -i --data-binary 'payload' "http://127.0.0.1:$PORT/echo"
curl --max-time 5 -i "http://127.0.0.1:$PORT/download"
curl --max-time 5 -H 'Accept: text/html' "http://127.0.0.1:$PORT/app/client/route"
curl --max-time 5 -i -H 'Range: bytes=0-3' "http://127.0.0.1:$PORT/download"
```

Echo returns `201` and the same bytes; download returns the confined sample
file; the navigation path returns the static index. The range returns `206`
with four bytes and a `Content-Range` header. Reuse the full response's `ETag`
in `If-None-Match` to request a bodyless `304`; an out-of-file range returns
`416`. These policies belong to Core, not to application header parsing.
The expanded native smoke checks these cases for both file surfaces.
For Go/Kotlin/Python:

```sh
curl --max-time 5 -H 'x-sample-caller: smoke' "http://127.0.0.1:$PORT/hello/reader?title=hello"
```

Expect `hello reader from smoke`. TypeScript uses `/hello?title=hello` and
returns `hello from smoke`. The four managed lanes also demonstrate:

```sh
curl --max-time 5 --data-binary 'streamed' "http://127.0.0.1:$PORT/upload"
curl --max-time 5 --raw -i "http://127.0.0.1:$PORT/stream"
curl --max-time 5 -N "http://127.0.0.1:$PORT/events"
```

Managed uploads echo bytes; native `/upload` returns byte/trailer counts without
retaining the body. `/stream` in managed samples emits `stream-ready` with the final
`x-stream-end: done` trailer; `/events` emits a finite `state` event with
`data: ready`. Use the WebSocket source's `coakka.sample` subprotocol when
trying `/socket`; a plain HTTP GET is not a WebSocket client. Applications
activate `/version` to `v2` before announcing readiness. Native returns `200`,
managed lanes `201`; this is sample response policy, not a runtime difference.

## Configuration And Ownership

Start with defaults; add only the settings your application needs. Startup
intent is not effective configuration: read runtime information from Core.
Do not guess CPU counts, loop counts, backend activation or timeouts in a
connector. A two-CPU budget is not a promise that application code runs in
parallel. Ordinary handlers stay host-inlined.

Handlers own application decisions and any bounded application buffers. Core
owns transport admission, deadlines, routing and terminal outcomes. A stream
must release retained application state on cancellation as well as normal end.
Do not turn a streaming example into unbounded whole-body collection. File
paths remain relative to an explicitly granted root; never accept an arbitrary
filesystem path supplied by an HTTP caller. TLS identities here are test-only.

Stop admitting work, close owners in reverse order and check refusal. A close
timeout does not authorize freeing retained state or restarting the same owner.
Application retries require an explicit idempotency and retry budget policy.

## Verification Boundaries

### Native Runtime Information

The October 7 native candidate corrects the callback-server query to report
Core-owned instance execution and effective configuration. The older candidate
returned unobserved zero loop counts even after startup; zero was not a measured
loop count. Both native samples now require an observed, active Core instance
before announcing readiness. Do not reconstruct loop counts from worker settings.
A concurrent lifecycle operation can refuse the query with `RETAINED`; a
stopped service returns `CLOSED`, with output unchanged. No benchmark improvement
is implied. Each connector's matching Core bytes are tracked independently by
its candidate manifest and the sample checksum pins.

The October 7 application and TLS/mTLS smokes pass on macOS ARM64 and Pi Linux
ARM64. JVM Pi execution uses the Java 8 class files built on macOS; it is not a
Pi Gradle build claim. Windows/Linux x64 sample execution remains separate from
package qualification. Consumer-only sanitizers do not instrument packaged Core.
The table above deliberately distinguishes existing smoke from missing cases.
Benchmark results are governed by the [Pi protocol](benchmark-rpi5/README.md)
and cannot close a feature or lifecycle gap.
