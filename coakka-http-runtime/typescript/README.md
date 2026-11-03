# TypeScript Sample For Node.js And Bun

**Start here:** [Installation and integration guide](../../docs/coakka-http-runtime/typescript/integration.md) — prerequisites,
package setup, first HTTP request, feature walkthroughs and checked shutdown.
This README records sample details and verification scope.

One strict TypeScript source set runs unchanged on Node.js and Bun. Handlers
use normal functions, Web APIs, async stream writers, `bigint` identities, and
promise-based close.

## Contents

- [What It Shows](#what-it-shows)
- [Run](#run)
- [Feature Recipes And Coverage](#feature-recipes-and-coverage)
- [Control And Failure Recipes](#control-and-failure-recipes)
- [HTTP2 And HTTP3 Listeners](#http2-and-http3-listeners)
- [Raspberry Pi 5 Benchmark](#raspberry-pi-5-benchmark)

## What It Shows

The streamed-upload collector releases retained chunks on the typed `dispose`
notification, including peer cancellation. Read the
[cleanup contract](../../docs/coakka-http-runtime/reference/FEATURES.md#upload-cleanup); keep both byte limits and
admission limits bounded when adapting this collector.

- runtime-parsed encoded path/query values, duplicate ordered headers, buffered echo,
  and a 64KiB-bounded streamed upload with observed trailers;
- response streaming with a final trailer, Server-Sent Events, and WebSocket;
- static frontend files, SPA fallback, and a confined file response;
- logical-target outbound HTTP with named the runtime terminal causes;
- actual buffered GZIP negotiation alongside identity response streaming;
- health, fresh liveness, and bounded monitor events;
- handler-only activation and complete route-generation publication;
- default platform I/O plus an explicit `--io-uring` opt-in;
- `SIGINT`/`SIGTERM` waiting and reverse-order asynchronous close.

`io_uring` is off unless `--io-uring` is present. The TypeScript builder
forwards the preference; native startup owns capability detection and falls
back to `epoll` when needed. The sample prints both requested and effective
states on Node.js and Bun.

`security.ts` uses a bounded `node:https` test peer on both hosts to verify TLS
and mutual TLS, including rejection without a client identity. It separately
uses the runtime's outbound lane with explicit trust/client-identity generations;
the peer is not a replacement implementation for outbound transport.

## Run

Start with `smoke` for the server and client examples on Node.js and Bun.
Use `adverse-smoke` to see how the application handles deadlines,
full capacity, cancellation, and a later request. The recipe prints a
`=pass` line for each check.

```bash
bash coakka-http-runtime/typescript/run.sh check
bash coakka-http-runtime/typescript/run.sh smoke
bash coakka-http-runtime/typescript/run.sh security-smoke
bash coakka-http-runtime/typescript/run.sh tuning-smoke
bash coakka-http-runtime/typescript/run.sh adverse-smoke
bash coakka-http-runtime/typescript/run.sh run
bash coakka-http-runtime/typescript/run.sh run-bun
```

`tsconfig.json` enables strict checks, exact optional properties, and unchecked
index protection. The runner installs the pinned package with its paired native
libraries into an external staged application, disables install hooks, type-checks it,
and compiles it with `tsc` for Node.js. Bun executes the same TypeScript source
directly. This keeps the Node.js 22 floor independent of optional built-in
TypeScript stripping. No CoAkka npm publication is required. Set
`COAKKA_PUBLISH_ROOT` and `COAKKA_HTTP_SAMPLE_WORK_ROOT` as described in the
[sample overview](../README.md). Unix Bun's API floor is 1.2.22; Windows package
guidance recommends the tested 1.4.2+ line. These POSIX runners do not claim
Windows sample execution.

## Feature Recipes And Coverage

Use the [feature index](../../docs/coakka-http-runtime/reference/FEATURES.md) for source links, route commands,
expected responses and outstanding test coverage. Sample presence, package
capability and matching-host execution are separate claims.

`GET /parameters/a%2Fb?tag=one&flag&tag=&tag=two` preserves the encoded slash,
query order and absent-versus-empty values. Repeated `x-sample-value` headers
remain ordered in the `headers` result. `GET /gzip` negotiates GZIP when the
peer accepts it; the smoke uses a bounded decoder to verify actual wire bytes.
The `/stream` and SSE examples remain identity streams with compression enabled.

`service.routes` is a coherent immutable runtime snapshot, not a list of local
route declarations. Application route IDs identify entries; expected generation
and binding revision come from that snapshot. The sample checks stale
publication refusal and preservation of the effective route cut.

## Control And Failure Recipes

[`control.ts`](control.ts) reports runtime-issued CPU placement and active loop
observations, independently selects SMALL request/MEDIUM terminal notification
profiles, and checks accepted header/body deadlines. It exercises monitor
accepted reload, stale-generation refusal, over-reservation refusal and restore.
CPU and batch profiles are startup settings, not monitor hot-reload fields.
SINGLE is exercised only when the runtime reports supported placement; unsupported
does not mean measured zero CPUs. Do not guess effective settings from input.

[`adverse.ts`](adverse.ts) demonstrates a native handler deadline, a held active
slot causing HTTP503 before release, and outbound cancellation/deadline followed
by reuse. The asynchronous handler still owns its application work after a
transport timeout: release that work explicitly and await checked close. Typed
`OutboundReason` values come from the runtime; HTTP status and message text do not
replace those causes. These finite scenarios are not throughput measurements.

The main sample announces shutdown success only after both services close;
failure closing one does not prevent attempting to close the other. See the
[shared upload cleanup contract](../../docs/coakka-http-runtime/reference/FEATURES.md#upload-cleanup).

## HTTP2 And HTTP3 Listeners

```bash
bash coakka-http-runtime/typescript/run.sh http2-node
bash coakka-http-runtime/typescript/run.sh http3-node
bash coakka-http-runtime/typescript/run.sh http2-bun
bash coakka-http-runtime/typescript/run.sh http3-bun
```

[`protocol.ts`](protocol.ts) selects an explicit secure listener with generated
development identities and a normal callback at `/protocol`. A matching wire
client must negotiate that protocol. SIGINT/SIGTERM awaits the runtime close before
printing `typescript-protocol-shutdown=pass`; startup alone is not wire proof.

Named feature, control, security, wire and HTTP2/3 drain recipes pass on macOS
ARM64 and Pi Linux ARM64 with the exact pinned package, on Node22.0.0 and
Bun1.2.22. Adverse recipes run five times per engine/host. This does not imply
all fault combinations or Windows POSIX-runner support.

## Raspberry Pi 5 Benchmark

See the [HTTP Runtime 1.1.2 measurements](../benchmark-rpi5/README.md#http-runtime-112-on-raspberry-pi-5).
