# Raspberry Pi 5 Benchmark Methodology

## Contents

- [Status](#status)
- [Comparison Scope](#comparison-scope)
- [Machine Record](#machine-record)
- [Workload And CPU Budgets](#workload-and-cpu-budgets)
- [Warm-Up And Cooldown](#warm-up-and-cooldown)
- [Metrics And Accounting](#metrics-and-accounting)
- [Acceptance And Reproduction](#acceptance-and-reproduction)
- [Runner Status](#runner-status)
- [Preparation](#preparation)

## Status

Selected installed r3 cohorts have verified localhost observations below.
Unmeasured final-package profiles and framework comparisons remain Pending.
Historical source-build peaks are not substituted for current package evidence.

## Comparison Scope

| Host | Measured application | Comparison applications |
| --- | --- | --- |
| Native C | CoAkka public callback API | None; standalone native baseline |
| Go | CoAkka host-inlined | Chi, Gin |
| Kotlin/JVM | CoAkka host-inlined | Spring WebFlux, Spring MVC with Tomcat, Jetty, Undertow, Vert.x |
| Python | CoAkka host-inlined | FastAPI, Starlette; exact server stack named |
| Node.js | CoAkka host-inlined | Express, Fastify |
| Bun | CoAkka host-inlined | Elysia, Hono |

Vert.x and Undertow comparisons are **Pending** for both CPU budgets. The
current JVM remeasurement covers CoAkka only; no accepted comparison results
are published for those two frameworks in this round.

No standalone Netty or native-library competitor is included. The separately
requested Bun.serve paired study is reported explicitly; it does not add other
language-standard HTTP servers to the comparison matrix. Framework dependencies are not extra comparison rows.
Spring MVC with Tomcat is one stack, not two independent results. C++ samples
refer to the native C baseline without claiming a separate C++ measurement.

## Machine Record

Capture these facts from the Pi at campaign start and attach them to every
result through a campaign identifier. Do not reuse a former machine snapshot
as proof of the current boot environment.

| Category | Required facts |
| --- | --- |
| Board | Raspberry Pi model/revision; physical machine, not emulation |
| CPU | Architecture, logical CPU count, CPU model, affinity sets, governor, frequency limits and observed frequency |
| Memory | Installed/OS-visible RAM, available RAM before each run, swap configuration/activity |
| Operating system | Distribution/release, kernel, firmware identity, boot command line where relevant |
| Storage | Boot device/model and filesystem; evidence/output location |
| Cooling | Fan policy, ambient temperature if measured, board temperature before/after each run, throttle flags |
| Software | Exact CoAkka archive hashes, language/framework/load-tool versions and startup flags |
| Effective configuration | Core-reported CPU/loop/batch/timeout/backend state, alongside requested configuration |
| Network | Loopback or external topology, interfaces, HTTP/TLS mode, client placement |

Unknown values are explicitly unavailable, never guessed. Exclude credentials,
private keys, unrelated environment variables and other sensitive machine data.

## Workload And CPU Budgets

The initial fixed-response workload is `GET /fixed`, HTTP/1.1 keep-alive,
status `200`, `Content-Type: application/octet-stream`, and the 32-byte body
`0123456789abcdef0123456789abcdef`. Verify bytes and headers before timing.
No database, logging, compression, TLS, proxy or application authentication is
silently added to only one implementation. This workload is a transport and
handler-overhead measurement, not production application capacity.

Measure independent 1-CPU and 2-CPU profiles. Record actual CPU IDs, all server
workers/threads, runtime flags and the generator CPU set. Server and generator
CPU sets must not overlap in a loopback run. An allowed two-CPU set does not
prove that a single-threaded host uses both CPUs; show observed CPU usage.
CPU budget and event-loop count are distinct Core-reported facts. The current
native callback fixture reports one event loop under both CPU profiles;
historical measurements using a different application surface or loop/batch
configuration are not interchangeable with its results. Preserve the actual
surface and effective settings beside every number; never relabel an older
measurement as a result for this sample.
Do not silently add clustered workers to only one side. If a supported worker
configuration is used, label it as a distinct profile. Missing profiles say
**not measured**, not zero throughput.

Freeze concurrency, warm-up, measurement duration, repetitions, tool arguments,
CPU sets, framework settings and acceptance thresholds before the campaign.
Use at least three accepted repeated rounds with reproducible balanced order.
Validate generator headroom: saturation makes the server result inconclusive.

### Explicit Client-Limited Loopback Study

When a separate load machine is unavailable, `--study-mode
client-limited-loopback` records an observational localhost study. The default
remains `strict`. The study retains the same90% generator threshold and every
CPU interval; an exceeded threshold is labelled `client-limited`, not accepted
as server-capacity evidence. A passing headroom observation is labelled
`no-generator-saturation-observed`, not proof of a performance ceiling.

Study files use schema3 and `evidence_kind=client-limited-loopback`; the strict
summary tool deliberately refuses them by default; explicit `summarize.py
campaign.json --study --output STUDY.md` rechecks the raw observations and
renders a separately labelled report without relative-capacity rankings.
Any reported study table must
identify this scope, show per-run headroom and limitations, and avoid winner or
unconstrained-capacity claims for limited rows. All response, package identity,
cooldown, thermal, accounting and graceful-shutdown checks remain mandatory.
This mode does not waive JVM warm-up qualification or repeated-run stability.

## Warm-Up And Cooldown

Warm each process before its timed interval. Qualify JVM/JIT warm-up stability;
a short fixed delay alone is not evidence of steady state. Record the chosen
warm-up and observed stability instead of applying unequal hidden warm-up.

Before **every** run, including the first and retries, stop the preceding
server and wait at least 30 seconds, until board temperature is at most 50 °C,
background CPU busy is at most 5%, and throttle status is clear. Freeze the
sampling interval and number of consecutive qualifying observations in the
campaign configuration. The current gate requires three clean observations,
with a five-second wait between one-second CPU samples; any failure resets the
streak. A bounded cooldown deadline expiring rejects or defers
the run; it never bypasses the gate. Keep cooling policy fixed throughout.

Warm-up can heat the CPU again: record conditions at the start and end of the
timed interval too. Any throttling during measurement rejects that run.

## Metrics And Accounting

| Metric | Definition and reporting rule |
| --- | --- |
| Throughput | Successful, workload-valid responses per measured second; retain per-run values, median and range |
| Latency | Client-observed p50/p95/p99 with units and measurement method; do not invent percentiles from an average |
| Outcomes | Total completed, valid success, wrong status/body, connection errors, timeouts and incomplete requests |
| CPU | User/system CPU time for the complete server process tree; 100% means one logical CPU, so two busy CPUs approach 200% |
| RSS | Sampled mean and peak in MiB for all server processes, with sampling interval and measurement window |
| Memory caveat | Summed process RSS can double-count shared pages; it is not unique physical-memory usage or an instantaneous unsampled peak |
| Scheduling | Voluntary/involuntary context switches where available; separate intrusive profiling from throughput measurements |
| Environment | Before/after temperature, throttling, observed frequencies and background activity |
| Generator | Separate CPU/RSS and errors; never include generator resources in the server totals |

Include supervisors and workers consistently; record how exited workers are
accounted for. Missing metrics say **N/A — reason**, not zero. Record collector
cost. Never merge latency histograms incorrectly or average percentiles while
labelling the result as a pooled percentile: report per-run percentiles or use
compatible raw histograms.

## Acceptance And Reproduction

Every result needs: candidate identities, fixture/config identity, full commands,
raw output and telemetry, timestamps, accepted/rejected status and reason.
Retain rejected attempts rather than selecting only the fastest run. A workload
mismatch, unexplained errors, throttling, cooldown failure, missing required
telemetry or saturated generator prevents publication of that run.
The explicit observational study above may retain generator-limited runs in
a separately labelled study report, never in an accepted capacity table.

Publish the machine table, per-profile result tables and raw evidence together.
Compare only matched workloads and CPU budgets. Do not use old records as new
results, rank unrelated ecosystems by this tiny workload, or substitute a
throughput win for feature/lifecycle correctness.

## Runner Status

Preparation now consumes five independently pinned public archives. It does
not build Core or connectors, change the OS, or select a new toolchain. It
records package/consumer identity; the runner rechecks identity before changing
the CPU governor. Old source-built workspaces are refused. Loop counts are
Core-issued observations, never a shared benchmark override.

Preparation explicitly selects the current native CMake package directory;
changing only a prefix does not override an older cached package lookup.
Before timed load, the runner also hashes the runtime/bridge files actually
mapped in the server process group and matches them to the independently
pinned package. Framework processes must not map a CoAkka library. Receipt
intent alone is not proof of which library a consumer loaded.

The native callback response call records the handler's outcome; Core submits
it after the handler returns. A successful call does not confirm delivery to
the peer. Core owns a concurrent client disconnect and its exchange retirement;
the sample must not duplicate lower-level submission handling. A real-socket
cutoff check on the candidate verifies continued service and graceful shutdown
under both CPU policies. This functional check is not a throughput result.

All 19 lanes have passed functional HTTP/keep-alive and shutdown checks on both
CPU profiles. This does not qualify JIT warm-up, sustained throughput, or the
new resource collector. Pilot qualification and the final repeated campaign
remain open. A forced server kill is a failed run. Uvicorn's normal re-raised
SIGTERM is accepted only with complete shutdown evidence for every worker.

CPU and sampled RSS cover the complete load-process lifetime (warm-up plus
measurement), not a falsely exact per-worker timing boundary. Server RSS is
sampled at a target 250 ms cadence, reporting time-weighted mean and observed
peak. Raw samples and observer CPU cost are retained. Per-thread context-switch
profiling is separate and explicitly N/A in this low-overhead campaign.
Generator CPU is also sampled in roughly one-second intervals on each assigned
CPU. Both the whole-run average and every sampled interval must meet the 90%
headroom ceiling; a light JVM startup phase must not hide measurement-phase
saturation. These are interval averages, not instantaneous CPU peaks. Raw
intervals and the highest observed value are retained with each result.

Framework CPU settings are explicit: Go GOMAXPROCS follows the budget;
Uvicorn uses one/two workers; Node/Bun remain single processes without hidden
clustering. Jetty uses a nonblocking handler, one acceptor and one/two selectors;
Undertow uses one/two I/O threads; Vert.x Web deploys one router verticle per
assigned CPU; WebFlux uses one/two workers. Spring MVC uses Tomcat's servlet/NIO
path with 8–64 workers. These are framework comparisons, not bare transport
rows. See the [sample coverage](../FEATURES.md) before scheduling measurements.

## Preparation

This is a Pi-only benchmark harness, not an installer or a change to connector
minimum versions. The current cohort uses Go 1.27.1, Node 22.23.3, Bun 1.4.2,
Trixie's OpenJDK 21, Python 3.13 and h2load 1.64.0. The separate Go benchmark
module requires Go 1.25 because its Gin fixture requires it; the CoAkka connector
retains its independently verified Go 1.23 minimum. JVM benchmark fixtures do
not raise the connector's Java 8 minimum.

Provision the stated tools before preparation. The tool directory must contain
`go/bin/go`, `node/bin/node` (with npm) and `bun/bin/bun`; the board also needs
the JDK, Python venv support, CMake, Ninja, a C compiler and h2load. Use a clean,
dedicated board workspace, SSH key authentication and a known-host entry.
Preparation refuses mixed legacy source-build workspaces and verifies archive
pins again after transfer. It never deletes another workspace or updates the OS.

From this benchmark directory, set these variables to your own paths and alias:

```sh
export COAKKA_RPI5_HOST=your-pi-ssh-alias
export COAKKA_RPI5_ROOT=/home/your-user/coakka-http-runtime-benchmark-candidate
export COAKKA_BENCH_TOOL_ROOT=/home/your-user/benchmark-tools
export COAKKA_BENCH_GRADLE_CACHE=/home/your-user/benchmark-gradle-cache
export COAKKA_PUBLISH_ROOT=/absolute/path/to/coakka-publish
bash scripts/deploy-rpi5.sh
```

Source, tool and compiled-consumer hash receipts live under `evidence/locks`
on the Pi. A run fails before changing the CPU governor if any receipt no longer
matches. Do not run build/preparation commands concurrently with measurements.
Campaign commands and final warm-up settings remain pending qualification;
the preparation success marker alone is not authorization to publish numbers.

<!-- VERIFIED RESULTS -->

## Verified Package Results

## Contents

- [Results](#results)
- [Machine and measurement](#machine-and-measurement)
- [Per-run throughput](#per-run-throughput)
- [Evidence identities](#evidence-identities)

## Results

| Application | CPUs | Req/s median (range) | p50 / p95 / p99 ms | CPU % | RSS mean / sampled peak MiB | Errors / timeouts |
| --- | ---: | --- | --- | ---: | --- | --- |
| Go + CoAkka | 1 | 52,040.3 (51,821.0–53,269.8) | 1.165 / 1.771 / 1.981 | 93.7 | 25.5 / 25.8 | 0 / 0 |
| Python + CoAkka | 1 | 10,532.0 (10,516.0–10,584.3) | 5.865 / 9.669 / 10.526 | 99.8 | 35.2 / 35.2 | 0 / 0 |
| Node.js + CoAkka | 1 | 29,377.8 (29,351.7–29,526.0) | 2.138 / 2.353 / 3.687 | 83.9 | 71.1 / 72.6 | 0 / 0 |
| Go + CoAkka | 2 | 93,169.9 (92,407.1–93,324.7) | 0.662 / 0.830 / 1.213 | 159.0 | 25.9 / 26.4 | 0 / 0 |
| Python + CoAkka | 2 | 11,919.1 (11,845.1–11,985.8) | 5.384 / 6.128 / 6.985 | 137.1 | 35.2 / 35.2 | 0 / 0 |
| Node.js + CoAkka | 2 | 56,487.6 (55,532.0–58,252.8) | 1.099 / 1.310 / 2.134 | 159.5 | 69.3 / 70.0 | 0 / 0 |
| Bun + CoAkka | 1 | 30,561.8 (29,891.5–31,867.3) | 2.065 / 2.425 / 2.744 | 99.5 | 66.8 / 68.1 | 0 / 0 |
| Bun.serve | 1 | 46,201.4 (46,043.5–46,323.7) | 1.334 / 1.765 / 2.158 | 99.8 | 38.7 / 41.7 | 0 / 0 |
| Bun + CoAkka | 2 | 66,026.3 (65,502.2–66,225.1) | 0.944 / 1.063 / 1.585 | 171.0 | 66.1 / 67.1 | 0 / 0 |
| Bun.serve | 2 | 46,521.7 (46,263.5–46,557.1) | 1.330 / 1.741 / 2.186 | 100.3 | 39.7 / 42.0 | 0 / 0 |
| Kotlin/JVM + CoAkka | 2 | 94,918.3 (94,639.6–95,380.8) | 0.664 / 0.700 / 0.922 | 154.8 | 219.4 / 223.9 | 0 / 0 |

These are three-run **localhost observations**, not unconstrained capacity or
a universal framework ranking. All rows use installed packages admitted as
`2026-10-08-r3`; no package was rebuilt for this documentation update. Go,
Node.js and Python reuse their unchanged qualified cohort. Bun uses the later
paired Bun.serve cohort. JVM uses only the final selected package's two-CPU
cohort; the older JVM archive's results are not relabeled.

Native C 1/2-CPU and final JVM 1-CPU measurements remain **Pending** in this
table. C++ refers to native C, not a separate measurement. Chi/Gin,
FastAPI/Starlette, Express/Fastify, Elysia/Hono, Spring WebFlux, Spring MVC with
Tomcat, Jetty, Vert.x and Undertow comparisons remain **Pending** for this
exact-package table; no missing comparison is treated as a win. Vert.x and
Undertow are intentionally not rerun. There is no public Netty comparison.

## Machine and measurement

| Field | Recorded value |
| --- | --- |
| Board / CPU | Raspberry Pi 5 Model B Rev 1.1; four Cortex-A76, 2.4 GHz |
| RAM | 16 GB board; 16,607,536 KiB OS-visible |
| OS / kernel | Debian GNU/Linux 13 Trixie; 6.18.50+rpt-rpi-2712 aarch64; USB root |
| Firmware | 2026-09-25, version 1accd665 |
| Cooling | Maximum fan, state 4; ambient temperature not measured |
| Governor / throttling | Performance during load; original governors restored; throttled=0x0 |
| Workload | IPv4 loopback, plaintext HTTP/1.1, GET /fixed, status 200, 32-byte response |
| Connections | 64 persistent clients, one request in flight per connection |
| Process model | One application process; no Bun cluster/worker variant |
| CPU budget | 1 CPU: server 0, generator 1–3; 2 CPUs: server 0–1, generator 2–3 |
| Duration | Three fresh-process runs; 10s measured; 5s warm-up, JVM 30s |
| Cooldown | At least 30s, <=50°C and busiest CPU <=5%, three consecutive clean samples before each run |
| Runtime settings | Ordinary host-inlined package defaults; no application loop override; notification batches 8/8 |
| Tools | Go 1.27.1; Node 22.23.3; Bun 1.4.2; Python 3.13.5; OpenJDK 21.0.12.1; h2load 1.64.0 |

Tool versions above are benchmark versions, not minimum connector requirements.
CPU100% means one logical CPU. CPU/RSS include warm-up plus measurement. RSS
uses 250ms samples: time-weighted process-tree sum and sampled peak (shared
pages may be counted twice). Table resource values and latency percentiles are
medians of per-run values, not pooled-request percentiles. Context switches
were not measured. No generator saturation was observed at the 90% gate; this
does not prove an unconstrained ceiling. Exact mapped-package checks, cooldown,
post-load readiness and graceful shutdown passed in the retained campaigns.

Bun.serve uses method/path dispatch, URL parsing, shared payload bytes and a
fresh Fetch Response. CoAkka uses its public Builder and reusable immutable
Response. Both use the same Bun binary. CoAkka/Bun.serve median ratios are
0.661 at 1CPU and 1.419 at 2CPU; these describe this paired workload only.

## Per-run throughput

| Application | CPUs | Round 1 / 2 / 3 req/s |
| --- | ---: | --- |
| Go + CoAkka | 1 | 52,040.3 / 51,821.0 / 53,269.8 |
| Python + CoAkka | 1 | 10,516.0 / 10,532.0 / 10,584.3 |
| Node.js + CoAkka | 1 | 29,377.8 / 29,526.0 / 29,351.7 |
| Go + CoAkka | 2 | 92,407.1 / 93,324.7 / 93,169.9 |
| Python + CoAkka | 2 | 11,845.1 / 11,919.1 / 11,985.8 |
| Node.js + CoAkka | 2 | 58,252.8 / 56,487.6 / 55,532.0 |
| Bun + CoAkka | 1 | 30,561.8 / 29,891.5 / 31,867.3 |
| Bun.serve | 1 | 46,043.5 / 46,323.7 / 46,201.4 |
| Bun + CoAkka | 2 | 66,225.1 / 65,502.2 / 66,026.3 |
| Bun.serve | 2 | 46,557.1 / 46,521.7 / 46,263.5 |
| Kotlin/JVM + CoAkka | 2 | 94,639.6 / 94,918.3 / 95,380.8 |

## Evidence identities

Raw campaigns, telemetry, failures and tool/package locks are retained in the
private release evidence store. The following hashes identify those campaign
records without exposing local paths or runtime implementation details.
Package hashes are independently listed in each r3 warehouse checksum ledger.

| Campaign / CPU budget | Campaign JSON SHA-256 |
| --- | --- |
| Connector cohort / 1 CPUs | `31640e5a80fbf01260cd6506469f9225c9492b775445457493bfc6b4b3d7f811` |
| Connector cohort / 2 CPUs | `24d8ad37f2b8beee946915ca3a1c10bea7a5c8eb08cede04bc18a12250aa90f4` |
| Bun paired study / 1 CPUs | `4c24cae2dc93fdd4c580b236e6b5454e7dbf262f456e9dd043ffff27ee022636` |
| Bun paired study / 2 CPUs | `355f41ae0d0ebf885bfa065a8b44136be982f8557ab14a2961d77cae42c30d20` |
| JVM selected package / 2 CPUs | `90b38a76fa723bf6f6af7c50133f7f16a481c84ebbd7404dbfa00cd3a303e433` |
