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

The runner uses loopback HTTP/1.1 with 64 persistent connections, one load
thread, and one in-flight request per connection. It first sends a fixed
calibration set, derives a request count targeting ten measured seconds, and
rejects any run with a failed, errored, timed-out, or incomplete request.

CoAkka lanes use the end-user host-inlined API for their language. The C and
C++ lanes use the public C host surface directly. No lane calls an internal
runtime surface.

## Lanes

| Ecosystem | CoAkka lane | Framework comparisons |
| --- | --- | --- |
| C | CoAkka host-inlined | GNU libmicrohttpd 0.9.75 |
| C++ | CoAkka host-inlined | cpp-httplib 0.11.4 |
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
| Storage | SK hynix 256 GB NVMe, ext4 root |
| Required OS baseline | Current Raspberry Pi OS Lite 64-bit (Debian 13 Trixie), clean install |
| Kernel at campaign preparation | Pending capture after the clean Trixie install |
| Server placement | CPUs `0-2` |
| Load generator placement | CPU `3` |
| Load generator | `h2load --h1` from nghttp2-client |

The campaign output records the exact OS, kernel, CPU model, memory, tool
versions, source identities, source manifest digest, and built executable
digests. The table is descriptive; captured evidence is authoritative for a
specific result.

Do not measure on the prior Bookworm installation or perform an in-place major
upgrade. Raspberry Pi documents Bookworm-to-Trixie migration as a clean-install
operation. Provision separate boot media, install the current image, fully
update it, reboot, and capture the exact OS/kernel/firmware state before the
qualification round. Until that clean boot exists, every result table remains
pending.

## Fairness And Cooldown

The runner applies the same controls to every lane:

1. Set the CPU governor to `performance` and remember the prior governor.
2. Wait at least 15 seconds and require a board temperature at or below 50 C,
   `get_throttled=0x0`, and no CPU above 5% utilization during a one-second
   idle sample before the campaign starts.
3. Randomize lane order independently in each round using a recorded,
   deterministic seed from the checked-in workload configuration.
4. Start the server on CPUs `0-2`; keep `h2load` on CPU `3`.
   Every CoAkka lane uses the native runtime's single bounded event loop.
   Idiomatic connector samples use three bounded application workers where the
   language facade provides them; all server processes remain inside the same
   three-CPU placement as their ecosystem peers.
5. Check the exact response before calibration.
6. Run calibration, then pass the same temperature, power, and CPU-idle gate
   after at least 15 seconds before the measured request set.
7. Reject the sample if any request fails or firmware reports power or thermal
   throttling.
8. Stop the server, pass the same cooldown and CPU-idle gate after at least 15
   seconds, and only then start the next lane.
9. Restore the original governor on success or failure.

The default campaign has three matched rounds. Results use the median for
requests per second, mean request time, p99 request time, server CPU, and server
RSS. Relative throughput is calculated only against the CoAkka lane in the same
ecosystem. It is never used to rank languages.

Server CPU is the aggregate user-plus-system time of every process in the
server process group. Server RSS is the sum of their resident-page counts; for
multi-process servers this intentionally counts each process and may count
shared pages more than once. The result table also records that process count.
The runner rejects a measured sample if group membership changes during the
request set. These definitions are identical for every lane.

`h2load` writes per-request latency rows during one sample. The runner reduces
them to p50, p95, and p99 values and immediately removes the temporary row file
so a full campaign does not retain several gigabytes of reproducible data.
Raw `h2load` summaries, server logs, and all reduced measurements remain in the
evidence directory.

## Candidate Preparation

From the development machine, deploy the current working trees and build them
on the Pi:

```sh
bash scripts/deploy-rpi5.sh
```

Optional environment variables:

```text
COAKKA_RPI5_HOST       SSH host; default pi5
COAKKA_RPI5_ROOT       dedicated absolute directory on the Pi
COAKKA_HTTP_RUNTIME_ROOT
COAKKA_HTTP_CONNECTOR_ROOT
COAKKA_COMMONS_ROOT
```

Deployment copies the benchmark plus the runtime and connector source trees.
It also exports the exact `coakka-commons` commit named by the runtime dependency
lock. The Pi therefore needs no private repository credential, and a newer
local `coakka-commons` checkout cannot silently change the measured binary.
Preparation then:

- installs the required Linux build tools and comparison libraries;
- builds and installs the focused native host from runtime source;
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
cd /home/pi5/coakka-http-runtime-benchmark-20260930
taskset -c 3 python3 scripts/run-rpi5.py \
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
taskset -c 3 python3 scripts/run-rpi5.py \
  --output evidence/qualification-go \
  --rounds 1 --duration 2 --calibration-requests 5000 \
  --lane go-coakka --lane go-chi --lane go-gin
```

## Full Campaign

After qualification and a fresh cooldown:

```sh
taskset -c 3 python3 scripts/run-rpi5.py \
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
