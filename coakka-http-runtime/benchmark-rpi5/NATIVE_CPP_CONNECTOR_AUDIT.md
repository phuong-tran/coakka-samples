# Native C++ Connector Fixed-Response Evidence

Date: 2026-09-20

## Decision

The physical Raspberry Pi 5 measurement is accepted as one controlled
workload snapshot. It measures the public C++ `NativeConnector`, not the
low-level Core C ABI fixture. Under the default one-worker configuration, the
connector is materially slower than the direct pinned-uWebSockets control for
this fixed response. This evidence does not justify a parity claim or a
portable regression budget.

## Identity And Workload

- locked native runtime source commit:
  `846bfdf52e71577bb321a6b9c7a81c8f2960c21c`
- benchmark source manifest:
  `8e5e9a509624447916737360a7c3f21dffd7c1a700c8dbbaa2891d4d961da36c`
- connector executable SHA-256:
  `a2e0437a54daea3e1fe3f64e48535cf99f1fd9efc28b6a68e5998ebb505c7593`
- direct executable SHA-256:
  `f51443f53783a930b5e0a637e4a20ed810b4559efe67169602d4c5d87ee9961c`
- sealed evidence SHA-256:
  `3a8d28b89ff812e18a9548e3c4fee802ef082e86d09450fa7d3c0977efea824b`
- target: Raspberry Pi 5 Model B Rev 1.1, AArch64, Linux
  `6.12.96+rpt-rpi-2712`, GCC/G++ 12.2.0
- workload: HTTP/1.1 `GET /fixed`, exact 32-byte body, concurrency 8, server
  CPUs 0-2, oha 1.14.0 on CPU 3, 30 seconds warmup plus 30 seconds measure
- admission: fresh boot, `performance` governor during each sample, polling
  until temperature is at most 52 C, and firmware throttle `0x0`

Both executables are ELF64 little-endian AArch64 PIE files. Dependency closure
was inspected with `ldd`; the connector resolves the locked CoAkka runtime and
system C++/OpenSSL libraries, while the direct control resolves only its
system C++/OpenSSL closure.

## Result

| Metric | NativeConnector | direct uWebSockets |
| --- | ---: | ---: |
| Requests/second | 15,445.89 | 105,579.20 |
| p50 | 0.515 ms | 0.081 ms |
| p95 | 0.526 ms | 0.083 ms |
| p99 | 0.649 ms | 0.088 ms |
| Measured HTTP 200 responses | 463,402 | 3,167,525 |
| Median/peak RSS | 13,600/13,600 KiB | 5,232/5,232 KiB |
| Maximum threads | 4 | 2 |
| Maximum file descriptors | 19 | 19 |
| Server CPU | 34.97 s | 19.67 s |

The connector throughput delta is -85.37% and its p99 delta is +637.70%.
Both oha error distributions are empty. Connector handler, dispatch,
submission, cleanup, rejection, and fatal counters are zero; its adopted
handler context is destroyed exactly once. Both processes exit successfully,
all governors and declared host services are restored, and the post-campaign
throttle record remains `0x0`.

## Boundary Review

`NativeConnector` owns the runtime, dispatch queue, default worker, and handler
registry after creation. The fixture owns one handler context until registry
adoption, keeps the referenced counters alive through connector close, and
builds each response against limits read from the connector. The direct
control owns one uWebSockets loop and closes its listener on that loop through
`Loop::defer`.

Neither fixture probes `io_uring`, reads descriptors to infer a backend, or
calls a kernel interface for capability detection. Core remains the source of
truth for runtime capabilities. POSIX signal masking and `sigwait` are used
only to give each benchmark process deterministic shutdown ownership.

## Systems Quality Review

| Control | Status | Evidence and promotion impact |
| --- | --- | --- |
| Ownership, lifecycle, cancellation, shutdown | pass | One adopted connector context is destroyed once; both fixtures stop on `SIGTERM`, publish structured stopped records, and exit zero. |
| Bounded memory, queues, strings, threads, descriptors | pass | The connector uses a one-entry frozen registry and its bounded defaults; the 30-second run peaks at 13,600 KiB, four threads, and 19 fds with no rejection or growth claim. |
| Senior systems review | pass | Ownership handoff, borrowed lifetimes, queue boundary, heap use, wakeups, and shutdown convergence are explicit; no new production state or ABI is introduced. |
| Expert/kernel review | pass | No custom syscall, affinity inside either fixture, realtime policy, kernel bypass, NUMA, IRQ/DMA/MMU, RCU, or capability probe is warranted. CPU placement belongs to the external harness. |
| Strict compiler diagnostics | pass | Both Pi targets compile as C++20 with `-Wall -Wextra -Wpedantic -Werror`. |
| Static analysis | pass | Cppcheck 2.20.0 reports no warning, performance, or portability finding in either owned source. |
| Focused functional tests | pass | Qualification and two controlled Release campaigns validate exact response, ready/stopped identity, counters, and cleanup. The first campaign is excluded only because its environment record omitted the two executable hashes. |
| Public connector black box | pass | The measured lane enters through the public C++ `NativeConnector`, `HandlerRegistry`, `HandlerInvocation`, and `ResponseFrame` surface. Production ABI and schema are unchanged. |
| UBSan | pass | A separate matching-host build with `COAKKA_HTTP_ENABLE_UBSAN=ON` and `UBSAN_OPTIONS=halt_on_error=1:print_stacktrace=1` passes c8 qualification; all server and load stderr files are empty. Instrumented throughput is not used. |
| Documentation and evidence seal | pass | README, execution ledger, exact source/artifact identities, host restoration record, and all sealed file checksums are synchronized. |
| ASan/LSan | blocked for publication | Not run for this benchmark-only slice. No package or production-release claim is opened by this result. |
| TSan | blocked for publication | Not run for this benchmark-only slice. A future production promotion involving changed connector concurrency must supply separate TSan evidence. |
| Fuzzing | not applicable | The fixtures add no parser, codec, framing grammar, or untrusted admission surface. |
| Fault injection | not applicable | No production ownership or failure path changed; the fixture fails closed on create, start, bind, response, snapshot, close, and signal errors. |
| Stress, pressure, soak, regression budget | blocked | One measured round is sufficient only for the requested snapshot. It cannot establish variance, a portable budget, or a production performance claim. |
| Installed/package-tree consumer | not applicable | This benchmark compiles exact locked source and does not claim a published package; registries remain out of scope. |
| Matching-host artifact execution | pass | Both exact hashed AArch64 executables run on the physical Pi, complete the same workload, and have their architecture and dependency closure inspected. |
