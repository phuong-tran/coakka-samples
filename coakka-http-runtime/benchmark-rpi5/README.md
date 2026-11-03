# Raspberry Pi 5 Benchmark Methodology

## Contents

- [HTTP Runtime 1.1.2 on Raspberry Pi 5](#http-runtime-112-on-raspberry-pi-5)
- [Comparison Scope](#comparison-scope)
- [Machine Record](#machine-record)
- [Workload And CPU Budgets](#workload-and-cpu-budgets)
- [Warm-Up And Cooldown](#warm-up-and-cooldown)
- [Metrics And Accounting](#metrics-and-accounting)
- [Acceptance And Reproduction](#acceptance-and-reproduction)
- [Runner Status](#runner-status)
- [Preparation](#preparation)

## HTTP Runtime 1.1.2 on Raspberry Pi 5

These results come from the qualified 1.1.2 packages on a physical Raspberry
Pi 5. For each connector, the table shows the highest throughput observed
among two accepted runs, with latency and memory from the same run. Each
configuration ran once, so these are confirmation measurements rather than
throughput medians across repeated runs.

| Connector | Requests/s | p50 (ms) | p95 (ms) | p99 (ms) | RSS (MiB) | Peak RSS (MiB) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Python | 74,898.0 | 0.821 | 1.056 | 1.217 | 46.17 | 46.17 |
| Node.js | 88,288.3 | 0.705 | 0.902 | 0.953 | 72.83 | 72.83 |
| Bun | 82,615.4 | 0.743 | 0.865 | 1.477 | 71.64 | 72.81 |
| Go | 94,522.6 | 0.645 | 0.811 | 1.203 | 25.81 | 39.69 |
| JVM | 82,951.3 | 0.760 | 0.789 | 1.101 | 247.34 | 248.53 |

Each timed run lasted 10 seconds over loopback using HTTP/1.1 keep-alive,
64 clients and a 32-byte response. The server used AUTO on CPUs 0–1 and the
load generator used CPUs 2–3. All accepted temperature samples were below
50°C, with no throttling. RSS is the whole server process after load, and
peak RSS is its process high-water mark. The 1.1.2 pass covered connectors.

## Comparison Scope

| Host | Measured application | Comparison applications |
| --- | --- | --- |
| Native C | CoAkka configured public C host-inlined API | None; standalone native baseline |
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
| Effective configuration | runtime-reported CPU/loop/batch/timeout/backend state, alongside requested configuration |
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
CPU budget and event-loop count are distinct facts. The explicit native C
profile requests one/two loops for one/two CPUs; Core reports actual loops and
batches, while the harness independently verifies process/thread affinity.
Language connector tuning is unchanged. Historical callback/default measurements
are a different application surface and are not interchangeable with this profile. Preserve the actual
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
not build the runtime or connectors, change the OS, or select a new toolchain. It
records package/consumer identity; the runner rechecks identity before changing
the CPU governor. Old source-built workspaces are refused. Loop counts are
runtime-issued observations. Only the native C fixture explicitly supplies its
documented execution profile; language connectors retain their own tuning.

Preparation explicitly selects the current native CMake package directory;
changing only a prefix does not override an older cached package lookup.
Before timed load, the runner also hashes the runtime/bridge files actually
mapped in the server process group and matches them to the independently
pinned package. Framework processes must not map a CoAkka library. Receipt
intent alone is not proof of which library a consumer loaded.

The native C fixture uses one application event reader, releasing every request
and terminal lease exactly once. A duration-limited client may disconnect
before response submission; only Core-issued STALE_EXCHANGE is accepted
as that race. No retry or other error suppression is allowed. Timed HTTP
failures, missing terminal accounting or forced shutdown reject the run.

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
