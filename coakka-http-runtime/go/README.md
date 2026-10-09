# Go Sample

**Start here:** [Installation and integration guide](integration.md) — prerequisites,
package setup, first HTTP request, feature walkthroughs and checked shutdown.
This README records sample details, verification scope and benchmark results.

The Go lane uses ordinary functions, structs, errors, contexts, and
`signal.NotifyContext`. It imports the module from the pinned offline Go
candidate rather than a private source checkout or module proxy.

## Contents

- [What It Shows](#what-it-shows)
- [Run](#run)
- [Feature Recipes And Coverage](#feature-recipes-and-coverage)
- [Configuration And Live Monitoring](#configuration-and-live-monitoring)
- [Wire And Failure Checks](#wire-and-failure-checks)
- [Deadline And Pressure Recipes](#deadline-and-pressure-recipes)
- [HTTP2 And HTTP3 Listeners](#http2-and-http3-listeners)
- [Raspberry Pi 5 Benchmark](#raspberry-pi-5-benchmark)

## What It Shows

- path and query values, ordered headers, buffered echo, and streamed upload;
- response streaming with a final trailer, Server-Sent Events, and WebSocket;
- static frontend files, SPA fallback, and a confined file response;
- a logical-target outbound request with one terminal owner;
- health, fresh liveness, and bounded monitoring events;
- handler-only activation and complete route-generation publication;
- default platform I/O plus an explicit `--io-uring` opt-in;
- buffered GZIP alongside uncompressed streaming through `--compression`;
- reverse-order close and signal-driven service lifetime.

`io_uring` is off unless `--io-uring` is present. The Go builder forwards the
preference; native startup owns capability detection and falls back to `epoll`
when needed. The sample prints both requested and effective states.

`security.go` creates server-authenticated TLS and mutual-TLS services. An
independent Go TLS client verifies inbound chain/client-identity enforcement;
the runtime-owned outbound client also exercises both modes with explicit
generation-keyed trust and client credentials. Neither path disables peer
verification. The outbound service closes before its target.

## Run

```bash
bash coakka-http-runtime/go/run.sh check
bash coakka-http-runtime/go/run.sh smoke
bash coakka-http-runtime/go/run.sh tuning-smoke
bash coakka-http-runtime/go/run.sh adverse-smoke
bash coakka-http-runtime/go/run.sh security-smoke
bash coakka-http-runtime/go/run.sh run
```

Set `COAKKA_PUBLISH_ROOT` and `COAKKA_HTTP_SAMPLE_WORK_ROOT` as described in the
[sample overview](../README.md). Go 1.23+ and a C compiler are needed for cgo.
The runner uses the archive's module and paired native SDK, adds a local
`replace` only to staged `go.mod`, runs `go vet`, and keeps caches and generated
files outside source. It does not rebuild the runtime library.

## Feature Recipes And Coverage

Use the [feature index](../FEATURES.md) for source links, route commands,
expected responses and outstanding test coverage. Sample presence, package
capability and matching-host execution are separate claims.

### Configuration And Live Monitoring

[`control.go`](control.go) contains the focused control-plane recipes. Normal
startup leaves CPU, batch and transport deadlines to the runtime; the echo application
declares a 64 KiB request-body ceiling because its upload demonstration collects
the body. Production file uploads should consume bounded chunks instead of
copying this small-body collector blindly.

| Setting | Recipe | Effective observation |
| --- | --- | --- |
| CPU | Default AUTO; `--single-cpu` explicitly requests SINGLE | `RuntimeInfo().CPU`, including unknown/unsupported placement |
| Request notifications | `--tuning` selects SMALL | the runtime reports cap 1 |
| Terminal notifications | `--tuning` selects MEDIUM | the runtime reports cap 4; no terminal delivery is delayed to fill a batch |
| Transport deadlines | `--tuning`: header 3000, body 4000, keep-alive 2000, idle 5000 milliseconds | Accepted values from `RuntimeInfo().Limits` |
| HTTP loops | No sample override | `RuntimeInfo().Execution`, not inferred from CPU or Go worker count |
| Monitoring | Reserved 32 events, reads at most 8 | Generation-checked apply results and effective policy |

`tuning-smoke` checks explicit settings; on Linux it additionally checks SINGLE.
AUTO may report unsupported placement on other hosts. SINGLE must not silently
fall back. These are startup placement observations, not CPU utilization or a
process-wide Go quota. Deadlines use the runtime's monotonic clock; the sample adds no
second transport timer. Batch and timeout settings are startup configuration,
whereas the demonstrated monitor collection policy is live-reloadable.

Every start demonstrates a live monitor change to aggregates, rejection of an
old generation, refusal of one event beyond the immutable reservation, and
restoration of the original policy. Each refusal must preserve the complete
effective policy and generation. Diagnostic markers contain no request data.

`--compression` uses the package's host-inlined `Builder.Compression` with
GZIP, a one-byte eligibility threshold and level6. The runtime owns limits, workspace,
eligibility and negotiation. `smoke` verifies an actually compressed response
with bounded decompression while the same service also handles streams, SSE,
uploads, static files and WebSocket. `tuning-smoke` combines explicit tuning
and GZIP. Both recipes pass on Mac and Pi with Go1.23.

GZIP applies only to eligible buffered responses. Streams remain uncompressed;
the runtime does not collect them for compression. With this policy enabled, a client
that explicitly refuses identity receives HTTP406 before an unencoded stream
starts. The package's consumer tests cover refusal and subsequent service reuse.
Application-supplied content encoding remains application-owned. Use the pinned
October7 package; older candidates lack this builder method and composition fix.

### Wire And Failure Checks

`smoke` first exercises application routes with default backend and explicit
I/O intent, then launches the compiled application as a real child process.
The bounded wire client checks:

- `/parameters/a%2Fb?tag=one&flag&tag=&tag=two`: encoded capture, duplicate
  query order, absent versus empty value, and duplicate `x-sample-value` order;
- binary chunked upload and a trailer observed after the body reader consumes
  it, plus peer abort after handler admission and a subsequent successful upload;
- response bytes/final trailer, multiline SSE with event ID/retry, WebSocket
  subprotocol/welcome/text/binary/ping/close, abort and repeated connection reuse;
- both `/app/sample.txt` and `/download`: full body/ETag, range `206`, validator
  `304`, and unsatisfiable range `416`;
- successful signal-driven close of the actual service process.

The upload failure marker proves the blocked reader returned; it does not
reclassify a read error as a specific the runtime cause or certify every lease retired.
The metadata route reflects only its demonstration header, never arbitrary
headers, credentials or cookies. The runtime parses path/query; the handler serializes
those values without reparsing the raw target or collapsing duplicates.

Startup also checks handler-only activation plus stale-revision refusal and
complete route publication, exact replay, and stale-generation refusal. It reads
`Service.Routes()` with monitoring disabled and checks the complete runtime-issued
generation/binding cut before and after publication/refusal. The snapshot is a
copied control-plane value, not the local prepared-handler registry. Close
errors are preserved alongside earlier errors using `errors.Join`; refusal is
not treated as successful cleanup or permission to restart the same owner.

These checks and TLS/mTLS pass on macOS ARM64 and Pi Linux ARM64 against the
exact Go candidate. Go race detection instruments Go sample/connector code,
not the packaged native library. The tuning check verifies accepted deadline
configuration; the separate adverse recipe below verifies deadline firing.
The remaining [coverage gaps](../FEATURES.md) keep the all-features completion
gate open. No benchmark conclusion follows from a smoke.

### Deadline And Pressure Recipes

[`adverse.go`](adverse.go), selected by `adverse-smoke`, runs isolated services
with deliberately held handlers. This is a bounded diagnostic recipe, not a
production recommendation to block handlers and not a load benchmark.

| Case | Explicit budget and synchronization | Required observation |
| --- | --- | --- |
| Handler deadline | Core250ms; client5s; application channel released only after the wire response | HTTP504, late handler result discarded, next one-worker request succeeds, no duplicate error |
| Dispatch pressure | One worker, one queued request, nine clients; Core10s versus diagnostic4s | At least one HTTP503, all calls terminate, next request succeeds |
| Outbound cancellation | Wait for target handler admission, submit cancel intent, consume the sole terminal reader | runtime-issued `OutboundCancelled`, exact call identity, then a successful call |
| Outbound deadline | Core500ms; upstream handler budget10s; terminal reader5s | `OutboundDeadlineExceeded`, not a reader timeout; complete response on reuse |

The number of refused calls may vary with scheduling; no exact refusal count
is promised. Channels delimit application work, not native state. Diagnostic
client timeouts fail the recipe; they are never counted as the runtime deadline
success. Response copies are bounded to4KiB. Every exit releases held work,
cancels/joins diagnostic clients and checks service close; a refusal is retained
as an error. These recipes pass five repetitions on Mac/Pi with Go1.23 and a
separate Mac Go race run. The runtime sanitizer qualification is recorded separately.

Cancellation is intent until the runtime returns its typed terminal. Never infer the
cause from an HTTP status, a diagnostic string or how long a call took. The
sample releases held application work separately, matches call identity and
checks for an extra terminal; it never releases native leases itself. HTTP404
in the ordinary outbound example remains `OutboundResponse`. Unknown enum
numbers are retained by the connector; they are not success or a retry policy.

### HTTP2 And HTTP3 Listeners

[`protocol.go`](protocol.go) uses the same host-inlined handler with a different
public listener declaration. Run either command in one terminal:

```bash
bash coakka-http-runtime/go/run.sh http2
bash coakka-http-runtime/go/run.sh http3
```

Choose one command at a time and use its printed port. The runner generates
test-only certificates under `$COAKKA_HTTP_SAMPLE_WORK_ROOT/tls/identities`.
An appropriately built client can then request `/protocol`:

```bash
PORT=12345 # replace with the printed port
curl --http2 --max-time 5 --cacert "$COAKKA_HTTP_SAMPLE_WORK_ROOT/tls/identities/ca.pem" "https://localhost:$PORT/protocol"
# For the http3 server, use a curl build with HTTP/3 support:
curl --http3-only --max-time 5 --cacert "$COAKKA_HTTP_SAMPLE_WORK_ROOT/tls/identities/ca.pem" "https://localhost:$PORT/protocol"
```

Expect `protocol-ready`; the selected server must support that wire protocol.
The runtime owns negotiation, sockets and TLS. Ctrl-C requests graceful close and the
sample preserves any drain refusal. Keep active clients progressing while the
server drains; a vanished peer is not proof of graceful completion. Both
listener examples pass independent protocol-peer and graceful-drain checks on
Mac/Pi with Go1.23. This does not claim every installed curl supports HTTP/3.

## Raspberry Pi 5 Benchmark

| Application | CPUs | Req/s median (range) | p50 / p95 / p99 ms | CPU % | RSS mean / sampled peak MiB | Errors / timeouts |
| --- | ---: | --- | --- | ---: | --- | --- |
| Go + CoAkka | 1 | 52,040.3 (51,821.0–53,269.8) | 1.165 / 1.771 / 1.981 | 93.7 | 25.5 / 25.8 | 0 / 0 |
| Go + CoAkka | 2 | 93,169.9 (92,407.1–93,324.7) | 0.662 / 0.830 / 1.213 | 159.0 | 25.9 / 26.4 | 0 / 0 |

Installed r3 packages; three-run localhost observations, not a universal
framework ranking. Missing framework comparisons remain **Pending**.
See [complete results, machine facts, all runs and evidence identities](../benchmark-rpi5/README.md#verified-package-results)
and the [methodology](../benchmark-rpi5/README.md). CPU100% means one CPU;
RSS is sampled, and resource measurements include warm-up.
