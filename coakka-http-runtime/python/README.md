# Python Sample

The Python lane uses typed callables, immutable value objects, context-managed
network responses, `threading.Event`, and normal exceptions. Static checks run
with Ruff and strict mypy.

## Contents

- [What It Shows](#what-it-shows)
- [Run](#run)
- [Control And Failure Recipes](#control-and-failure-recipes)
- [Verification](#verification)
- [Feature Recipes And Coverage](#feature-recipes-and-coverage)
- [Raspberry Pi 5 Benchmark](#raspberry-pi-5-benchmark)

## What It Shows

- path values, ordered headers, buffered echo, and streamed upload;
- response streaming with a final trailer, Server-Sent Events, and WebSocket;
- static frontend files, SPA fallback, and a confined file response;
- logical-target outbound HTTP;
- buffered GZIP alongside identity response streams;
- health, fresh liveness, and bounded monitor events;
- handler-only activation and complete route-generation publication;
- default platform I/O plus an explicit `--io-uring` opt-in;
- signal-driven, reverse-order close.

`io_uring` is off unless `--io-uring` is present. The Python builder forwards
the preference; native startup owns capability detection and falls back to
`epoll` when needed. The sample prints both requested and effective states.

`security.py` uses Python's verifying TLS context as an independent peer for
TLS/mTLS; its negative case requires an unidentified client to fail. Separate
calls exercise Core-owned outbound TLS/mTLS with trust/client-identity
generations and server-name verification.

## Run

```bash
bash coakka-http-runtime/python/run.sh check
bash coakka-http-runtime/python/run.sh smoke
bash coakka-http-runtime/python/run.sh security-smoke
bash coakka-http-runtime/python/run.sh tuning-smoke
bash coakka-http-runtime/python/run.sh adverse-smoke
bash coakka-http-runtime/python/run.sh http2
bash coakka-http-runtime/python/run.sh http3
bash coakka-http-runtime/python/run.sh run
```

The runner creates its virtual environment and dependency cache under the
configured external work directory and imports the pinned archive's `python`
directory. Its package selects its paired native image, with no development
override. It does not contact PyPI for CoAkka; `check` downloads pinned lint/type
tools. Set `COAKKA_PUBLISH_ROOT` and `COAKKA_HTTP_SAMPLE_WORK_ROOT` as described
in the [sample overview](../README.md). Use CPython 3.11 or newer.

`http2`/`http3` each keep one secure loopback listener alive until SIGINT/SIGTERM.
`/protocol` returns `protocol-ready` through an ordinary Python handler. Use a
verifying client with the generated development CA and matching wire protocol;
these fixtures are not production credentials.

## Control And Failure Recipes

- `main.py`: bounded 64 KiB upload, trailers after EOF, multiline SSE,
  text/binary WebSocket, Core route snapshots and stale-publication refusal.
  The wire smoke checks disconnect/reuse, response trailers, file range/validators
  and WebSocket close/reuse.
- `control.py`: Core-issued CPU/batch/deadline facts; accepted monitor reload,
  stale-generation/over-reservation refusal and restoration. `--tuning` submits
  SMALL/MEDIUM batch intents; `--single-cpu` submits SINGLE, otherwise AUTO.
  Unsupported placement is reported, not guessed. Placement does not remove
  the GIL or impose a process-wide CPU quota.
- `adverse.py`: held-handler deadline, bounded overload, outbound cancel/deadline
  with exact typed terminals and subsequent reuse. Core expiry cannot forcibly
  stop Python application code; explicit events release finite test handlers.
  Deliberate dispatch saturation emits visible bounded diagnostics. HTTP 503
  alone does not identify which owner refused work.

Shutdown attempts both owners in reverse order. A success marker follows only
successful closes; independent failures are retained in an exception group.
Do not restart a retained owner after close refusal.

## Verification

The October 8 package passes 100 tests and an independent consumer on all five
package targets. These named recipes pass on macOS ARM64/Python 3.11.15 and
Pi5/Linux ARM64/Python 3.13.5, including five adverse runs per host and real
HTTP/2/HTTP/3 peers through graceful drain. Ruff and strict mypy pass.
This does not imply Windows execution of the POSIX signal-driven commands,
exhaustive fault injection, or new performance measurements.

## Feature Recipes And Coverage

Use the [feature index](../FEATURES.md) for source links, route commands,
expected responses and outstanding test coverage. Sample presence, package
capability and matching-host execution are separate claims.

## Raspberry Pi 5 Benchmark

| Application | CPUs | Req/s median (range) | p50 / p95 / p99 ms | CPU % | RSS mean / sampled peak MiB | Errors / timeouts |
| --- | ---: | --- | --- | ---: | --- | --- |
| Python + CoAkka | 1 | 10,532.0 (10,516.0–10,584.3) | 5.865 / 9.669 / 10.526 | 99.8 | 35.2 / 35.2 | 0 / 0 |
| Python + CoAkka | 2 | 11,919.1 (11,845.1–11,985.8) | 5.384 / 6.128 / 6.985 | 137.1 | 35.2 / 35.2 | 0 / 0 |

Installed r3 packages; three-run localhost observations, not a universal
framework ranking. Missing framework comparisons remain **Pending**.
See [complete results, machine facts, all runs and evidence identities](../benchmark-rpi5/README.md#verified-package-results)
and the [methodology](../benchmark-rpi5/README.md). CPU100% means one CPU;
RSS is sampled, and resource measurements include warm-up.
