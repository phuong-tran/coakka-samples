# Native C++ Host-Inline Fixed-Response Evidence

Date: 2026-09-20

## Decision

Accept the final physical Raspberry Pi 5 measurement as one controlled
workload snapshot. The measured CoAkka lane is a C++ host-inline connector:
Core validates the complete bounded route declaration synchronously before
bind, then host-owned pinned uWebSockets invokes the application handler and
emits the response on its event-loop thread. The pair is within 0.62% in
throughput and 0.80% at p99 for this workload.

The previous `NativeConnector` result of 15,445.89 req/s at 0.649 ms p99 is
excluded. It measured a reader, bounded dispatch queue, worker, request and
response framing, and completion submission. It is diagnostic evidence for a
different full-runtime topology, not C++ host-inline evidence, and must not be
compared with JVM host-inline.

## Identity And Workload

- locked application-Core source commit:
  `b3d56281b83a77b109120c21cbd991310ed94cb7`
- locked application-Core archive SHA-256:
  `4fd1b2ebfa467e3f84b79aa4bd61faa19f22cafb291169cfc270e202b53220e2`
- measured source manifest SHA-256:
  `951b604690571e71fad9c4b671f2355bdd3adc1954c26851354824870829b7bc`
- host-inline executable SHA-256:
  `9822f08bda5f1e85efb3f808985d73539c458e4e431ae1f9444bfd994762de43`
- direct executable SHA-256:
  `1becc1d84f48f4cda83aa6ad62cd4538a86d34fc99e1d37ec64d29a29fee9143`
- dynamically loaded Core host-inline library SHA-256:
  `a43b84fa5291c7949fc588d4fe50b3a557ad4eb5a23d7d2be82e7383ef119009`
- sealed evidence SHA-256:
  `b9f55710f2160767e4dcb5ab78dbfa6c6ade8c89671e98cb01f1a115c94b47f5`
- target: Raspberry Pi 5 Model B Rev 1.1, AArch64, Linux
  `6.12.96+rpt-rpi-2712`, GCC/G++ 12.2.0
- workload: HTTP/1.1 `GET /fixed`, exact 32-byte body, concurrency 8, server
  CPUs 0-2, oha 1.14.0 on CPU 3, 30 seconds warmup plus 30 seconds measure
- admission: fresh boot, `performance` governor during each sample, polling
  until temperature is at most 52 C, and firmware throttle `0x0`

Both executables are ELF64 little-endian AArch64 PIE files. `ldd` resolves the
host-inline lane to the exact hashed Core library plus system C++ libraries;
the direct control resolves only its system C++ closure. The first host-inline
campaign is excluded because environment capture did not yet hash that dynamic
Core dependency; the final rerun does.

## Result

| Metric | C++ host-inline | direct uWebSockets |
| --- | ---: | ---: |
| Requests/second | 104,843.35 | 105,499.64 |
| p50 | 0.082 ms | 0.081 ms |
| p95 | 0.084 ms | 0.083 ms |
| p99 | 0.089 ms | 0.088 ms |
| Measured HTTP 200 responses | 3,145,438 | 3,165,159 |
| Median/peak RSS | 3,632/3,632 KiB | 3,552/3,552 KiB |
| Maximum threads | 2 | 2 |
| Maximum file descriptors | 19 | 19 |
| Server CPU | 19.62 s | 19.87 s |
| Shutdown latency | 0.477 ms | 0.465 ms |

The host-inline throughput delta is -0.62% and its p99 delta is +0.80%. Both
oha error distributions are empty; all 6,310,597 measured responses are HTTP
200. Both processes report zero handler errors, exit successfully after
`SIGTERM`, and retain no request state. Host services, timers, user services,
and all four `ondemand` governors are restored; the post-campaign firmware
throttle record is `0x0`.

## Ownership And Bounds

- The fixture owns one immutable `GET /fixed` declaration. Core borrows its
  method and pattern only during the synchronous public C ABI call and destroys
  all temporary validation state before returning.
- Core is the source of truth for route admission but never enters the request
  path. uWebSockets owns the listener, route table, connections,
  request/response objects, and event loop.
- The handler runs synchronously on that loop. There is no connector request
  queue, worker dispatch, native request envelope, completion handoff, retry,
  or retained request state.
- The signal-owner thread owns only `sigwait` and defers listener close to the
  event-loop owner. It does not access request state.
- Startup fails closed on ABI mismatch, route rejection, provider construction
  failure, or bind failure. The fixture has one route and one static 32-byte
  response, and reports structured ready/stopped records.

Neither fixture probes `io_uring`, reads procfs/sysfs, inspects descriptors to
infer a backend, or issues a capability syscall. This HTTP/1.1 comparison does
not select a Core I/O backend. POSIX signal masking and `sigwait` are used only
because the benchmark process owns its shutdown lifecycle.

## Systems Quality Review

| Control | Status | Evidence and promotion impact |
| --- | --- | --- |
| Ownership, lifecycle, cancellation, shutdown | pass | Qualification and the accepted campaign pass exact response, identity, structured ready/stopped, signal shutdown, and zero-exit checks. |
| Bounded memory, queues, strings, threads, descriptors | pass | One immutable route and static response; no connector request queue. The accepted run peaks at 3,632 KiB, two threads, and 19 fds. |
| Senior systems review | pass | Request ownership remains on one event loop; startup borrowing, shutdown handoff, heap use, wakeups, and failure convergence are explicit. |
| Expert/kernel review | pass | No `io_uring`, custom capability syscall, kernel bypass, NUMA, realtime, IRQ, DMA, MMU, or per-core mechanism is applicable. |
| Strict compiler diagnostics | pass | Both targets build as C++20 with warnings as errors under matching-host GCC/G++ 12.2.0; AppleClang 17 syntax/format checks also pass. |
| Static analysis | pass with host note | Cppcheck 2.20.0 reports no warning, performance, or portability finding locally. Cppcheck is absent on the Pi and is not claimed there. |
| Focused functional tests | pass | Release qualification and two sealed campaigns validate route admission, exact body/status, lifecycle identity, counters, cooldown, and host restoration. The first campaign is excluded only for incomplete dependency digest capture. |
| Core public ABI black box | pass | The C++ fixture checks host-inline ABI 1 and calls `coakka_http_host_inline_validate`; any ABI, route, or failed-index mismatch stops before bind. |
| UBSan | pass | A separate matching-host halt-on-error qualification passes for both instrumented lanes; all server/load stderr logs are empty. Instrumented throughput is excluded. |
| Documentation and evidence seal | pass | README, execution ledger, exact source/binary/library identities, host restoration, and all 128 sealed checksums are synchronized. |
| ASan/LSan | blocked for publication | Not run for this benchmark-only correction. No package or production-release claim is opened. |
| TSan | blocked for publication | Not run. The only cross-thread operation is the documented host-loop shutdown deferral; publication still requires a separate race-tool run. |
| Fuzzing | not applicable | The fixture adds no parser or protocol grammar; Core's existing bounded route validator owns declaration parsing. |
| Fault injection | not applicable | No production implementation changes; startup and shutdown failures are explicit and fail closed. |
| Stress, pressure, soak, regression budget | blocked | One accepted round cannot establish variance, a portable budget, or a general parity claim. |
| Installed/package-tree consumer | not applicable | Exact private source is measured; no registry/package publication or compatibility path is claimed. |
| Matching-host artifact execution | pass | Exact hashed AArch64 binaries and the Core library execute on the physical Pi; architecture, dependency closure, resource behavior, throttle state, and restoration are captured. |
