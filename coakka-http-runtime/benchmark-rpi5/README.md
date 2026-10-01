# CoAkka HTTP Runtime Raspberry Pi 5 Benchmark

This directory measures the same fixed HTTP/1.1 application through CoAkka's
host-inlined surface and established frameworks in each language ecosystem.
It does not compare CoAkka with a language's built-in HTTP server, and it does
not create a cross-language leaderboard.

The campaign is run only on the physical Raspberry Pi 5 described below.
Every candidate is built from the exact runtime and connector source copied to
the board. No language registry package or previously published binary enters
the measurement.

## Workload

Every lane implements exactly this endpoint:

```text
GET /fixed HTTP/1.1
status: 200
content-type: application/octet-stream
body: 0123456789abcdef0123456789abcdef
```

The runner uses loopback HTTP/1.1 with 64 persistent connections, two load
threads, and one in-flight request per connection. It first sends a fixed
calibration set, derives a request count targeting ten measured seconds, and
rejects any run with a failed, errored, timed-out, or incomplete request. It
also requires exactly one 2xx status observation per completed request and no
3xx/4xx/5xx observation; a superficially successful but internally
inconsistent `h2load` summary is rejected.
Calibration and measurement both enable the same per-request timing log;
otherwise log overhead could make the calibrated request rate misleading.

CoAkka lanes use the end-user host-inlined API for their language. The C and
C++ lanes use the public C host surface directly. No lane calls an internal
runtime surface.

## Lanes

| Ecosystem | CoAkka lane | Framework comparisons |
| --- | --- | --- |
| C | CoAkka host-inlined | GNU libmicrohttpd (installed Debian version captured in evidence) |
| C++ | CoAkka host-inlined | cpp-httplib (installed Debian version captured in evidence) |
| Go | CoAkka host-inlined | Chi 5.3.2, Gin 1.12.0 |
| Kotlin/JVM | CoAkka host-inlined | Netty 4.1.137.Final, Jetty 12.1.13 |
| Python | CoAkka host-inlined | FastAPI 0.141.1 and Starlette 1.6.0 on Uvicorn 0.52.4 |
| Node.js | CoAkka host-inlined | Express 5.2.1, Fastify 5.12.4 |
| Bun | CoAkka host-inlined | Elysia 1.4.30, Hono 4.13.7 |

There is deliberately no direct `net/http`, JDK HTTP server, Python standard
library server, `node:http`, or direct `Bun.serve` comparison. A framework is
the normal application choice in these ecosystems, so the tables compare that
realistic integration boundary.

## Measured Machine

| Field | Value |
| --- | --- |
| Board | Raspberry Pi 5 Model B Rev 1.1 |
| CPU | Four ARM Cortex-A76 cores, up to 2.4 GHz |
| Memory | 16 GiB |
| Architecture | Linux AArch64 |
| Prior installation storage | SK hynix 256 GB NVMe; not the benchmark boot device |
| Campaign boot storage | SanDisk USB 3.2Gen1 250 GB; `/dev/sda2` root during Trixie qualification |
| Required OS baseline | Current Raspberry Pi OS Lite 64-bit (Debian 13 Trixie), clean install |
| Kernel at campaign preparation | `6.18.50+rpt-rpi-2712`; final campaign captures the then-current kernel |
| Server placement | CPUs `0-1` |
| Load generator placement | CPUs `2-3`, two load threads |
| Load generator | `h2load --h1` from nghttp2-client |
| Cooling policy | Pi5 firmware fan first stage at 40 C, PWM 250; original boot config retained for rollback |

The campaign output records the exact OS, kernel, CPU model, memory, tool
versions, source identities, source manifest digest, and built executable
digests. The table is descriptive; captured evidence is authoritative for a
specific result.

Do not measure on the prior Bookworm installation or perform an in-place major
upgrade. The clean Trixie installation now boots from the separate SanDisk USB;
the prior NVMe remains outside the campaign. Result tables remain pending until
short qualification and the full campaign pass on that exact installation.

An initial single-generator-CPU qualification was rejected when it reached
91.8% busy in one framework lane, above the declared 90% ceiling. No result
from that incomplete run is publishable. Before any complete qualification,
the protocol was revised to reserve two CPUs for the server and two isolated
CPUs for the generator. The same revised placement applies to every lane; the
busiest generator CPU, not a two-CPU average, must remain below the ceiling.
The stock Pi5 fan profile could not reach the fixed 50 C idle gate with the
performance governor. The documented firmware fan parameters now start its
first stage at 40 C with PWM 250; the temperature and throttling gates are
unchanged. That cooling change is recorded as part of the machine baseline,
not as a per-lane adjustment.

A later short qualification rejected the libmicrohttpd lane when one load CPU
reached 98.8% busy. Wire inspection found that this sample queued its response
on libmicrohttpd's first callback, which made it close every connection and
forced a reconnect for each measured request. The sample now follows
[libmicrohttpd's documented callback lifecycle](https://git.gnunet.org/gnunet/libmicrohttpd/file/doc/chapters/hellobrowser.inc.html):
it queues the response on the later callback. A two-request socket-reuse probe
passed on the Pi. The rejected campaign remains diagnostic, not publishable.

## Fairness And Cooldown

The runner applies the same controls to every lane:

1. Set the CPU governor to `performance` and remember the prior governor.
2. Wait at least 15 seconds and require a board temperature at or below 50 C,
   `get_throttled=0x0`, and no CPU above 5% utilization during a one-second
   idle sample before the campaign starts.
3. Randomize lane order independently in each round using a recorded,
   deterministic seed from the checked-in workload configuration.
4. Start the server on CPUs `0-1`; keep two `h2load` threads on CPUs `2-3`.
   Every CoAkka lane uses the native runtime's single bounded event loop.
   Idiomatic connector samples use three bounded application workers where the
   language facade provides them; all server processes remain inside the same
   two-CPU placement as their ecosystem peers.
5. Check the exact response and prove two requests reuse one HTTP/1.1 socket
   before calibration. A close/reconnect lane is rejected rather than compared
   against persistent-connection lanes.
6. Run calibration, then pass the same temperature, power, and CPU-idle gate
   after at least 15 seconds before the measured request set.
7. Reject the sample if any request fails, status observations do not match
   successful request accounting, firmware reports power or thermal
   throttling, or either load-generator CPU is above 90% non-idle during the
   measured request set. I/O wait counts as non-idle: logging must not become
   a hidden client-side bottleneck.
8. Stop the server, pass the same cooldown and CPU-idle gate after at least 15
   seconds, and only then start the next lane.
9. Restore the original governor on success or failure.

The default campaign has three matched rounds. Results use the median for
requests per second, mean request time, p99 request time, server CPU, the
busiest load-generator CPU, and server RSS. Relative throughput is calculated
only against the CoAkka lane in the same ecosystem. It is never used to rank
languages.

Server CPU is the aggregate user-plus-system time of every process in the
server process group. Server RSS is the sum of their resident-page counts; for
multi-process servers this intentionally counts each process and may count
shared pages more than once. The result table also records that process count.
The runner rejects a measured sample if group membership changes during the
request set. These definitions are identical for every lane.

`h2load` writes per-request latency rows during calibration and measurement
to a capacity-checked `/dev/shm` tmpfs. The runner refuses a disk-backed or
undersized temporary mount: request-log writes to the boot USB could otherwise
cap the fastest lane and distort the comparison. It reduces the rows to p50,
p95, and p99 values and removes the task-owned temporary directory even when a
lane fails. A force-killed runner may leave its named temporary directory; remove
only that exact task-owned directory after inspection before another campaign.
Raw `h2load` summaries, server logs, and all reduced measurements remain in the
evidence directory.

## Candidate Preparation

From the development machine, deploy the candidate to the Pi. For a release
qualification campaign, set the exact frozen runtime commit and its already
qualified on-board CMake build directory; preparation checks that the copied
source matches that build before reusing the host library:

```sh
bash scripts/deploy-rpi5.sh
```

Optional environment variables:

```text
COAKKA_RPI5_HOST       SSH host; default pi5
COAKKA_RPI5_ROOT       dedicated absolute directory on the Pi
COAKKA_RPI5_KNOWN_HOSTS  dedicated SSH known-hosts file, when required
COAKKA_HTTP_RUNTIME_ROOT
COAKKA_HTTP_CONNECTOR_ROOT
COAKKA_COMMONS_ROOT
COAKKA_HTTP_RUNTIME_REF  exact frozen 40-hex runtime commit; paired with qualified build
COAKKA_HTTP_QUALIFIED_BUILD_DIR  qualified CMake build directory on the Pi
COAKKA_HTTP_QUALIFIED_BINARY_SHA256  independently qualified host library digest
```

Deployment copies the benchmark plus the runtime and connector source trees.
In qualified-build mode it copies the runtime and locked `coakka-commons` trees
from that same on-board qualification root. Otherwise it exports the exact
`coakka-commons` commit named by the runtime dependency lock. The Pi therefore
needs no private repository credential, and a newer local `coakka-commons`
checkout cannot silently change the measured binary.
Qualified-build mode checks the independently recorded host-library SHA-256
before and after installation; a relink or a different installed binary stops
preparation before any measurement.
Preparation then:

- installs the required Linux build tools, JDK 21, and comparison libraries;
- installs pinned Node.js 22.23.3, Go 1.27.1, and Bun 1.4.2 ARM64 binaries
  after checking their published SHA-256 digests; the Node and Bun lanes invoke
  those exact local binaries;
- installs the focused native host from the qualified build when supplied,
  after checking the source commit and byte-for-byte source tree; otherwise it
  builds the host from the deployed source;
- builds Go directly against connector source;
- imports the Python connector directly from connector source;
- builds the JavaScript native adapter and installs the local JavaScript
  connector directory without creating an archive;
- builds the JVM connector JAR and native adapter, then builds the Kotlin
  application distribution;
- builds the C and C++ applications and their framework comparisons;
- records source, dependency, tool, and executable identities.

This is benchmark preparation, not release packaging. It does not build a Go
module archive, Python wheel, npm tarball, Maven publication, or registry
upload.

## Qualification

Run one short round before spending time on the full campaign:

```sh
cd /home/pi5/coakka-http-runtime-benchmark-20261001
taskset -c 2-3 python3 scripts/run-rpi5.py \
  --output evidence/qualification \
  --rounds 1 \
  --duration 2 \
  --calibration-requests 5000
python3 scripts/summarize.py \
  evidence/qualification/campaign.json \
  --output evidence/qualification/RESULTS.md
```

Qualification must complete every lane with exact responses, zero request
errors, clean shutdown, clean firmware throttle state, and plausible
order-of-magnitude results. Stop and diagnose any outlier; do not promote a
surprising value by averaging it into a longer run.

Every `--output` directory must be new or empty. The runner refuses to mix a
retry with stale raw logs or measurements; use a new diagnosis directory or
remove a rejected task-owned directory deliberately before rerunning.

To isolate one or more lanes while diagnosing, repeat `--lane`:

```sh
taskset -c 2-3 python3 scripts/run-rpi5.py \
  --output evidence/qualification-go \
  --rounds 1 --duration 2 --calibration-requests 5000 \
  --lane go-coakka --lane go-chi --lane go-gin
```

## Full Campaign

After qualification and a fresh cooldown:

```sh
taskset -c 2-3 python3 scripts/run-rpi5.py \
  --output evidence/campaign
python3 scripts/summarize.py \
  evidence/campaign/campaign.json \
  --output evidence/campaign/RESULTS.md
```

The checked-in configuration is `config/lanes.json`. Do not change a lane,
workload, CPU placement, duration, or cooldown after measuring one ecosystem.
If a correction is needed, discard the incomplete comparison and rerun every
affected lane under one revised configuration.

## `io_uring` Scope

This HTTP/1.1 framework campaign leaves `io_uring` at its connector default of
`false`. Backend eligibility and fallback are native responsibilities and are
verified by the release test matrix, not inferred by sample code. A separate
backend study may opt in later, but its numbers must not be mixed into these
framework tables.

## Evidence Layout

| Path | Contents |
| --- | --- |
| `evidence/locks/source-identities.txt` | Base revisions and dirty-state flags |
| `evidence/locks/source-files.sha256` | Digest for every staged source file |
| `evidence/locks/source-manifest.sha256` | Identity of the source manifest |
| `evidence/locks/tool-versions.txt` | Language, compiler, build, and load tools |
| `evidence/locks/built-artifacts.sha256` | Exact executables and native libraries |
| `evidence/<campaign>/campaign.json` | Machine facts, workload, lanes, and reduced measurements |
| `evidence/<campaign>/raw/` | Server logs and raw `h2load` summaries |
| `evidence/<campaign>/RESULTS.md` | Per-ecosystem median tables |

An interrupted campaign remains marked incomplete. Only a campaign with
`"complete": true`, all configured lanes in all rounds, zero request errors,
and clean throttle state is eligible for review.
