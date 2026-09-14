# CoAkka HTTP Runtime Raspberry Pi 5 Benchmark

This directory owns the complete application and comparator source for private
Raspberry Pi 5 measurements. It answers two different questions with two
separate protocols:

1. How does CoAkka compare with direct HTTP and selected frameworks in the same
   language ecosystem?
2. For the same CoAkka application, what changes when Linux uses explicit
   `io_uring` instead of the platform-default I/O backend?

The first protocol uses ordinary HTTP/1.1 application APIs. The second uses the
complete `coakka-http-runtime-core` API with HTTP/2 over TLS because the current
candidate accepts explicit `io_uring` for eligible HTTP/2 and HTTP/3 listeners,
not HTTP/1.1. Results from the two protocols never share a comparison table.

## Contents

- [Measured Contracts](#measured-contracts)
- [Application Comparison Suites](#application-comparison-suites)
- [Per-Language io_uring Suites](#per-language-io_uring-suites)
- [Repository Layout](#repository-layout)
- [Frozen Inputs](#frozen-inputs)
- [Candidate Build](#candidate-build)
- [Authority Host](#authority-host)
- [Prepare The Raspberry Pi](#prepare-the-raspberry-pi)
- [Qualify Before Measuring](#qualify-before-measuring)
- [Thermal Rest Rule](#thermal-rest-rule)
- [Run Controlled Campaigns](#run-controlled-campaigns)
- [Evidence Contract](#evidence-contract)
- [Current Gate](#current-gate)

## Measured Contracts

### Application comparisons

Every HTTP/1.1 server implements:

```text
GET /fixed
status: 200
body: 0123456789abcdef0123456789abcdef
```

The response is a prebuilt 32-byte body. Each process binds loopback, disables
application access logging, reports its ready identity, and shuts down through
its public lifecycle. The runner checks the exact status and body before warmup
and after measurement; the load generator records status and transport errors
during load.

Every non-native CoAkka lane uses the same public builder and service that a
normal application imports. Each comparator is joined only with the CoAkka
samples from the same ecosystem, concurrency, round, CPU policy, host state,
and workload. There is no cross-language leaderboard.

### I/O backend comparisons

Every Go, JVM, Python, Node.js, and Bun backend pair implements the same
32-byte handler over HTTP/2 TLS:

```text
GET /fixed
status: 200
body: 0123456789abcdef0123456789abcdef
```

For one ecosystem, both lanes use the same source, application handler,
protocol, certificate, limits, connection count, parallel streams, CPU
placement, and package image. Only the requested I/O backend changes. The
runner rejects a sample unless startup reports the expected effective backend.
Each application reads runtime info through its public connector. An explicit
`io_uring` lane stops before measurement when Core reports the host unsupported
or when the started Core reports fallback instead of effective `io_uring`.
Applications never inspect `/proc`, probe syscalls, or infer kernel capability.

Every benchmark in this suite is intentionally small: one measured pass per
application lane, and one measured pass with `io_uring` disabled plus one with
it enabled for each connector. Before every pass, the runner polls until the
board is at or below the configured temperature ceiling and reports no firmware
throttling. Results are workload snapshots, not multi-round statistical claims.

The separate native backend suite keeps its existing HTTP/2 TLS JSON request:

```text
POST /customer/add
request:  {"name":"Nguyen Van An","email":"an@example.com","tier":"gold"}
status:   201
response: {"status":"created"}
```

Native C/C++ are standalone references and are not compared with an external
C/C++ HTTP server.

## Application Comparison Suites

### Go

| Lane | Role | Source |
| --- | --- | --- |
| `coakka-go` | CoAkka Go application | `src/go/main.go` |
| `go-direct` | Direct `net/http` | `src/comparisons/go/cmd/direct/main.go` |
| `go-chi` | Chi 5.3.2 | `src/comparisons/go/cmd/chi/main.go` |
| `go-gin` | Gin 1.12.0 | `src/comparisons/go/cmd/gin/main.go` |

### JVM

| Lane | Role | Source |
| --- | --- | --- |
| `coakka-jvm` | CoAkka Java application | `src/jvm/src/main/java/benchmark/jvm/FixedServer.java` |
| `jvm-direct` | JDK `HttpServer` 17.0.20.1 | Same source, selected by command argument |
| `jvm-netty` | Netty 4.1.137.Final | Same source, selected by command argument |
| `jvm-tomcat` | Tomcat 11.0.25 | Same source, selected by command argument |
| `jvm-jetty` | Jetty 12.1.13 | Same source, selected by command argument |

### Python

| Lane | Role | Source |
| --- | --- | --- |
| `coakka-python` | CoAkka asynchronous Python application | `src/python/fixed_server.py` |
| `python-direct` | Python 3.11 standard-library HTTP server | Same source, selected by command argument |
| `python-uvicorn` | Uvicorn 0.52.4 with a raw ASGI application | Same source, selected by command argument |
| `python-fastapi` | FastAPI 0.141.1 on Uvicorn 0.52.4 | Same source, selected by command argument |

### Node.js

| Lane | Role | Source |
| --- | --- | --- |
| `coakka-node` | CoAkka JavaScript application on Node.js | `src/javascript/coakka-node.mjs` |
| `node-direct` | Direct `node:http` 24.13.0 | `src/javascript/comparisons/node/direct.mjs` |
| `node-express` | Express 5.2.1 | `src/javascript/comparisons/node/express.mjs` |
| `node-fastify` | Fastify 5.12.4 | `src/javascript/comparisons/node/fastify.mjs` |

### Bun

| Lane | Role | Source |
| --- | --- | --- |
| `coakka-bun` | CoAkka JavaScript application on Bun | `src/javascript/coakka-bun.mjs` |
| `bun-direct` | Direct `Bun.serve` 1.3.14 | `src/javascript/comparisons/bun/direct.mjs` |
| `bun-elysia` | Elysia 1.4.30 | `src/javascript/comparisons/bun/elysia.mjs` |
| `bun-hono` | Hono 4.13.7 | `src/javascript/comparisons/bun/hono.mjs` |

The configuration files are `config/go-pairs.json`, `jvm-pairs.json`,
`python-pairs.json`, `node-pairs.json`, and `bun-pairs.json`.

## Per-Language io_uring Suites

Each ecosystem has one independent platform-default versus `io_uring` pair:

| Ecosystem | Configuration | CoAkka source |
| --- | --- | --- |
| Go | `config/go-io-uring.json` | `src/core/go/main.go` |
| JVM | `config/jvm-io-uring.json` | `src/jvm/src/main/java/benchmark/jvm/CoreFixedServer.java` |
| Python | `config/python-io-uring.json` | `src/python/coakka_core.py` |
| Node.js | `config/node-io-uring.json` | `src/javascript/coakka-core.mjs` |
| Bun | `config/bun-io-uring.json` | `src/javascript/coakka-core.mjs` |
| Native | `config/native-backends.json` | Exact locked native source archive |

The Node.js and Bun lanes intentionally share one JavaScript application source
while running under different hosts. Every pair uses four parallel HTTP/2
requests per connection. A successful command is insufficient: the effective
backend, HTTP version, TLS mode, exact response, and runtime-info proof must all
match the configuration. For connector lanes, backend proof comes from Core
runtime info, not process descriptor inspection.

## Repository Layout

| Path | Purpose |
| --- | --- |
| `src/` | Reviewable CoAkka applications and comparator source |
| `config/*-pairs.json` | HTTP/1.1 workload, commands, identities, bounds, CPU placement, cooldown, and pairs |
| `config/*-io-uring.json` | Per-language HTTP/2 TLS platform-default/`io_uring` pairs |
| `config/native-backends.json` | Native backend A/B workload |
| `config/artifacts.lock.json` | Exact private application, connector, Core, and shared native source identities |
| `config/tools.lock.json` | Pinned load generator and offline Python wheelhouse |
| `scripts/stage-artifacts.py` | Stages exact locked private inputs |
| `scripts/prepare-rpi5.sh` | Verifies, tests, and builds every suite on the Pi |
| `scripts/run-pairs.py` | Rotates lanes and retains load, lifecycle, thermal, and resource evidence |
| `scripts/quiesce-host.sh` | Records and temporarily stops declared nonessential services |
| `scripts/restore-host.sh` | Restores the exact prior services, timers, and governors |
| `scripts/capture-restored-host.py` | Verifies host restoration, memory, cooling, temperature, and throttle state |
| `scripts/summarize-pairs.py` | Joins matched rounds and derives pair deltas |
| `scripts/source-manifest.py` | Captures the exact reviewed benchmark source and configuration |
| `scripts/seal-evidence.py` | Produces the final evidence checksum |

Generated packages, dependency trees, build output, downloads, and private
evidence are ignored by Git.

## Frozen Inputs

On the development machine:

```sh
python3 scripts/stage-artifacts.py
python3 scripts/fetch-tools.py
python3 scripts/verify-inputs.py
python3 scripts/source-manifest.py
```

The candidate is locked by full commit, tree hash, deterministic source archive
digests, dependency lock files, and consumer source. Staging or verification
fails if an identity differs.

Transfer this complete benchmark directory to the physical Raspberry Pi only
after the lock, source review, and local validation pass.

## Candidate Build

This campaign uses a new private candidate; it does not preserve the behavior
or binary identity of the earlier package merely because that package exists.
Nothing in this directory is a registry release.

The candidate is built on the Pi from exact locked source inputs. The
HTTP/1.1 application comparisons use each ecosystem's normal `Builder` and
`Service` surface with language-native request handling and Core-owned route
admission. Go, JVM, Python, and JavaScript application sources are independently
locked so the benchmark measures the integration shape an application actually
uses, rather than forcing every language through one transport path.

The backend campaigns are a second build from the current locked
`coakka-http-runtime-core` and connector sources with HTTP/2, TLS, and
`io_uring` enabled. Within one backend pair, both lanes use the same application
source, connector, Core image, TLS files, and limits. Only the requested
platform-default or `io_uring` backend changes. Preparation records the exact
source archives, remaining application-integration patches, consumer source,
packages, executables, and resulting binary digests. The locked Core source
already owns optional liburing discovery, the Linux poll shim, public
runtime-info vocabulary, and production fallback; benchmark preparation does
not patch or reproduce those system boundaries. No registry artifact enters
either protocol.

## Authority Host

| Item | Recorded configuration |
| --- | --- |
| Board | Raspberry Pi 5 Model B Rev 1.1 |
| CPU | 4-core ARM Cortex-A76, maximum 2.4 GHz |
| Memory | 16 GiB |
| Storage | SK hynix 256 GB NVMe, ext4 root with `noatime` |
| OS | Debian 12 Bookworm, AArch64 |
| Kernel | `6.12.96+rpt-rpi-2712`, PREEMPT |
| Cooling | Firmware-managed PWM fan; state and RPM captured per campaign |
| Network | Loopback workload; SSH control over `wlan0` |
| CPU placement | server CPUs `0-2`; load generator and runner CPU `3` |
| Governor | `performance` during measurement; exact prior state restored |
| Runtime versions | Go 1.26.3, OpenJDK 17.0.20.1, Python 3.11.2, Node.js 24.13.0, Bun 1.3.14 |
| Load generator | `oha` 1.14.0, locked Linux ARM64 binary |

The runner captures the full CPU/cache view, firmware and bootloader versions,
memory, mounts, storage, power readings, thermal state, throttling flags,
services, timers, toolchains, and executable digests. The table is a readable
summary, not a substitute for `environment.json`.

## Prepare The Raspberry Pi

Run while normal network access remains available:

```sh
./scripts/prepare-rpi5.sh
```

Preparation requires Linux AArch64, Go, a Java 17-compatible Gradle wrapper,
Node.js, Bun, npm, Python 3, CMake, Ninja, OpenSSL, `taskset`, and standard
archive tools. It verifies frozen inputs, restores pinned dependencies, installs
the Python comparison environment from the locked wheelhouse, runs build-time
checks, builds every server locally on the Pi, exercises both native backends,
and records toolchain and source manifests. It fails before building unless the
host matches the declared Go 1.26.3, Java 17.0.20.1, Python 3.11, Node.js
24.13.0, and Bun 1.3.14 versions.

## Qualify Before Measuring

Run one short HTTP/1.1 application suite at a time:

```sh
taskset -c 3 python3 scripts/run-pairs.py \
  --config config/jvm-pairs.json \
  --mode qualify \
  --output evidence/qualification-jvm-application

python3 scripts/summarize-pairs.py \
  --evidence evidence/qualification-jvm-application
```

Repeat with `python-pairs.json`, `node-pairs.json`, `bun-pairs.json`, and
`go-pairs.json`. Review ready identity, exact response checks, runtime and build
identities, shutdown outcome, and throughput order of magnitude. Qualification
numbers are diagnostic and are never publication results.

Qualify the per-language backend pairs independently. For example:

```sh
taskset -c 3 python3 scripts/run-pairs.py \
  --config config/jvm-io-uring.json \
  --mode qualify \
  --concurrency 16 \
  --output evidence/qualification-jvm-io-uring
```

Repeat with `python-io-uring.json`, `node-io-uring.json`,
`bun-io-uring.json`, `go-io-uring.json`, and `native-backends.json`. Here,
`--concurrency 16` means 16 HTTP/2 connections; the checked-in workload uses
four parallel requests per connection.

Stop at qualification when an identity, runtime-info backend proof, response, lifecycle,
thermal gate, or order-of-magnitude check fails. Diagnose that lane before
spending a long campaign.

## Thermal Rest Rule

The runner applies the configured cooldown before every sample, including
qualification:

1. Sample board temperature and firmware throttle state immediately after the
   previous sample has quiesced.
2. Continue polling until temperature is at or below the configured ceiling
   and the throttle state is clean.
3. Start as soon as both conditions pass; there is no fixed five-minute wait.
4. Fail the sample when the maximum wait expires instead of measuring a hot or
   throttled board.

Every cooldown observation is retained under `cooldowns/` in the evidence tree.
This makes elapsed rest, start temperature, accepted temperature, frequency,
and throttle state reviewable. Checked-in profiles use `52 C` as the
conservative ceiling and `minimum_idle_seconds: 0`.

## Run Controlled Campaigns

Reboot after qualification, allow the board to cool, and run one ecosystem per
campaign. The wrapper records host state, stops only declared nonessential
services, pins the server to CPUs `0-2` and load/orchestration to CPU `3`, and
restores the original services, timers, and governors on every exit path.

HTTP/1.1 JVM example:

```sh
BENCHMARK_CONFIG=config/jvm-pairs.json \
CAMPAIGN_ID=20260914-rpi5-jvm-application-c8 \
  ./scripts/run-pairs-on-quiesced-rpi5.sh \
  --rounds 1 --warmup 30 --duration 30 --concurrency 8
```

HTTP/2 TLS JVM backend example:

```sh
BENCHMARK_CONFIG=config/jvm-io-uring.json \
CAMPAIGN_ID=20260914-rpi5-jvm-http2-io-uring-c16p4 \
  ./scripts/run-pairs-on-quiesced-rpi5.sh \
  --rounds 1 --warmup 30 --duration 30 --concurrency 16
```

Run Go, JVM, Python, Node.js, and Bun as separate application campaigns. For
the backend check, run exactly one disabled/enabled pair per connector plus the
native pair. Do not begin another test until restored-state verification passes
and the board has passed the configured temperature and throttle gate.

## Evidence Contract

Each accepted campaign retains:

- a complete copy of every source, config, README, and runner file in the
  source manifest;
- source, candidate artifact, tool, executable, and dependency digests;
- compiler, language runtime, build, kernel, CPU, storage, cooling, and power
  information;
- raw warmup and measurement JSON plus exact status/error distributions;
- server stdout/stderr and startup, ready, runtime-info, and shutdown records;
- per-sample cooldown, frequency, temperature, and throttle observations;
- RSS, CPU, threads, descriptors, context switches, and other available
  resource observations;
- host state before, during, and after quiescence;
- matched-round JSON, CSV, and Markdown summaries;
- complete evidence checksums.

Failed, overheated, throttled, mismatched, or interrupted rounds remain visible
and are never silently deleted.

## Current Gate

The physical authority host is available. Earlier qualification evidence is
superseded because it mixed application and connector execution shapes. Every
language-native application suite and every platform-default versus `io_uring`
suite must pass fresh startup, runtime-info/effective-backend, exact-response,
lifecycle, thermal, throttle, host-restoration, and evidence-seal checks before
a full campaign begins. Qualification numbers remain diagnostic and are not
publication results.

The publication gate remains closed until every one-pass application lane and
every one-pass disabled/enabled backend pair finishes, the native standalone
reference is complete, summaries are reviewed for failures and scope, host
state is restored, and the evidence is sealed. Registry upload, public release,
commit, and push remain outside this private benchmark step.
