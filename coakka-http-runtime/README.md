# CoAkka HTTP Runtime Samples

This directory is the application-source companion to CoAkka HTTP Runtime.
Samples use the same public APIs that an application imports: normal handlers,
language-owned request and response values, explicit capacity, and ordered
shutdown. Release `1.0.0` is an immutable private candidate; registry upload,
production signing, runnable-sample promotion, and performance publication
remain separate actions.

## Contents

- [Learning Path](#learning-path)
- [Language Lanes](#language-lanes)
- [Raspberry Pi 5 Benchmark Lab](#raspberry-pi-5-benchmark-lab)
- [Sample Contract](#sample-contract)
- [Current Status](#current-status)
- [Documentation](#documentation)

## Learning Path

The planned sample set is organized around real application growth, not
isolated API fragments:

| Sample | What it demonstrates |
| --- | --- |
| `hello-api` | One route, one response, bound port, and graceful close |
| `frontend-and-api` | Built `index.html` and assets beside `/api/*` handlers |
| `spa-fallback` | Browser navigation fallback without capturing API routes |
| `tls` | HTTP over a server certificate with explicit protocol and identity generation |
| `mtls` | Mutual client/server authentication and trust-root configuration |
| `monitoring` | Health, fresh liveness, aggregates, cursor-based events, missed-history reporting, wait, and interrupt |
| `handler-change` | Prepare a new binding, atomically activate it, and drain admitted work from the previous binding |
| `sse` | Bounded Server-Sent Event lifecycle and cancellation |
| `websocket` | Upgrade, message flow, pressure, and close |
| `queue-pressure` | Finite admission with observable refusal, recovery, and shutdown outcomes |

The frontend samples matter because one CoAkka service can own the built browser
application, backend API, SSE endpoints, and WebSocket sessions. TLS/mTLS,
monitoring, and live handler activation use the complete
`CoAkka HTTP Runtime` surface in the same language package.

## Language Lanes

| Lane | Application experience |
| --- | --- |
| Native C | Callback-based buffered service or complete runtime lifecycle |
| Native C++ | The stable C service/runtime contract from C++20 |
| Java | `ServiceBuilder`, Java lambdas, `Service`, and complete `HttpCore` control |
| Kotlin | Idiomatic builder calls, `Handler`, and typed `HttpCore` events |
| Python | Synchronous callable handlers plus context-managed runtime event leases |
| JavaScript/TypeScript on Node.js | Synchronous functions plus typed runtime events and promise-based close |
| JavaScript/TypeScript on Bun | The same package and application contract running on Bun |
| Go | Ordinary functions, Go-owned values, `Service`, and typed advanced control |

Each sample keeps its route and observable outcome recognizable across
languages while respecting the host's normal lifecycle and concurrency model.
Users never select an execution mode.

## Raspberry Pi 5 Benchmark Lab

[`benchmark-rpi5/`](benchmark-rpi5/README.md) contains the complete private
benchmark applications, framework comparators, pinned dependencies, workload
configuration, host-quiescence scripts, thermal-rest gate, runner, summaries,
and evidence sealing.

It measures two different questions separately:

1. HTTP/1.1 application comparisons pair CoAkka with direct HTTP and selected
   frameworks in Go, JVM, Python, Node.js, and Bun.
2. HTTP/2 over TLS backend comparisons run the same CoAkka handler in each of
   those five ecosystems once with the Linux platform default and once with
   explicit `io_uring`.

Native C/C++ remain standalone references. Every reported result must include
the exact measured source and artifact identities, raw output, latency, CPU,
memory, thermal state, lifecycle evidence, and restored-host proof.

## Sample Contract

Every promoted sample must:

- consume only a frozen repository artifact or a later public package
  coordinate;
- use the ordinary language API for every non-native language;
- verify the selected target image and capabilities instead of reaching into a
  build tree;
- set finite request, body, dispatch, stream, session, and shutdown budgets
  relevant to the sample;
- start, serve a real request, and validate status and body;
- expose health or monitor truth appropriate to the selected API level;
- close through the public graceful lifecycle;
- document supported platforms, security assumptions, and known limits;
- retain matching-host evidence before being described as supported.

## Current Status

| Gate | State |
| --- | --- |
| Language application packages | Five-target private candidates verified |
| Benchmark source for Go, JVM, Python, Node.js, Bun, and per-language `io_uring` A/B | Present and under local validation |
| Physical Raspberry Pi 5 campaigns | Pending an available, cool, quiesced authority host |
| Learning-path sample promotion | Pending package agreement and matching-host smoke evidence |
| External registries and public performance tables | Closed |

Keeping the release private does not reduce the product capability described in
the documentation. It means install coordinates and performance numbers remain
gated until package bytes, samples, and retained evidence agree.

## Documentation

Start with the [documentation hub](../docs/coakka-http-runtime/README.md), then
continue with:

- [How It Works](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/how-it-works.md)
- [Frontend And Backend](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/frontend-and-backend.md)
- [Observability And Monitoring](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/observability-and-monitoring.md)
- [TLS And mTLS](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/tls-and-mtls.md)
- [Live Handler Changes](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/handler-swap-and-hot-reload.md)
- [Platform Comparisons](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-http-runtime/docs/comparisons)
