# Native Runtime Test

This harness exercises the published CoAkka Runtime native package through its
public C ABI. It provides a reproducible local request/reply scenario and
bounded-admission, concurrency, lifecycle, and live route-snapshot evidence
without reaching into private runtime sources.

It is a native public-ABI baseline, not a claim about the absolute runtime
ceiling, cross-machine performance, connector performance, or production
capacity.

For loader, unsigned-library, Windows, macOS, Linux, and TLS/mTLS diagnosis,
see [Troubleshooting](../docs/troubleshooting.md).

## Measured Target Path

The harness installs one route snapshot:

```text
generation: 1
target:     samples.runtime.native.evidence.local
strategy:   single-owner
endpoint:   local
```

Every admitted request follows the complete local path:

```text
caller
  -> coakka_v2_client_build_raw_request
  -> native-submit or framed request channel
  -> bounded runtime ingress
  -> route snapshot generation 1
  -> target samples.runtime.native.evidence.local
  -> bounded local work handoff
  -> delivered-request channel
  -> deterministic echo handler
  -> coakka_v2_client_build_raw_reply
  -> coakka_v2_runtime_submit_envelope
  -> terminal response routing
  -> response channel
  -> caller drain
```

`payloadBytes` is used for both the request and echo reply payload. The payload
is initialized once and reused; the public request/reply builders still create
the serialized envelope for every message.

This path includes request construction, runtime admission and routing, local
handler handoff, reply construction, terminal response routing, and caller
drain. It excludes:

- runtime creation, startup, and route snapshot application;
- connector, framework, network, TLS, and service-discovery costs;
- business serialization and real handler work;
- application logging and observability export;
- latency percentiles, resident-memory tracking, and allocator profiling.

Those costs require separate scenarios or measurements in the target
deployment.

## Choose The Measurement Environment

Prefer Linux when the result will inform deployment decisions. Use a Linux
host that is close to the Kubernetes or container worker shape you actually
operate: the same architecture, comparable CPU limits, and a compatible libc
and kernel profile.

macOS and Windows runs are still valuable. They prove that the public C ABI,
runtime invariants, and packaging remain portable for local development and
edge or desktop hosts. Treat Docker, CI, UTM, and other virtualized runs as
correctness and portability evidence only. Their throughput values are not
comparable with a controlled Linux host or with each other.

## Modes And Pass Invariants

| Mode | Submission path | Stop condition | Required pass evidence |
| --- | --- | --- | --- |
| `smoke` | Direct native submit API | Finite request count | Every request reaches the handler and returns one reply; no rejection, deadletter, or route miss. |
| `pressure` | Framed request channel | Finite burst | At least one bounded queue rejection becomes a deadletter; every admitted request still completes a reply. |
| `stress` | Direct native submit API | Finite request count | Every request completes a reply with no rejection, deadletter, or route miss. |
| `soak` | Direct native submit API | Submission duration or request limit | Every submitted request reaches one terminal outcome; the default expects replies without rejection. |
| `race` | Concurrent direct native submit API | Finite requests plus bounded stop/lifecycle phases | Multi-producer requests reach exactly one reply or explicit queue-pressure deadletter, submit-versus-stop converges on `CLOSED`, and independent runtime lifecycles complete without shared-state failures. |
| `hot-reload` | Concurrent direct native submit API plus full snapshot apply | Finite requests and generations | New generations replace the full route table atomically while producers remain active; stale and invalid applies preserve the active generation. |

The `smoke`, `pressure`, `stress`, and `soak` modes require:

```text
submitted == attempted
completed + rejected == submitted
handlerReceived == repliesSubmitted == completed
routeGeneration == 1
routeCount == 1
routeMisses == 0
```

For `pressure`, terminal deadletters must match rejected requests. Runtime
`queueRejected` also includes transient reply-submit backpressure that the
harness retries and reports as `replySubmitBackpressure`.

`strictNoDrop=true` means work is never discarded silently. It does not mean
that bounded admission can never reject work; pressure mode exists specifically
to prove that rejection is explicit.

## Concurrency And Snapshot Hot Reload

`race` uses a start gate to release multiple C11 producer threads against one
runtime. The main thread owns the host-lane readers and handler pump. A pass
requires every admitted request to produce exactly one terminal reply or
explicit queue-pressure deadletter, with no route miss, delivery failure, or
unaccounted terminal result. With the default queue capacity, the normal
expectation is all replies. Smaller queues intentionally retain bounded
pressure semantics instead of pretending concurrency makes queues unbounded.
It then runs two separate bounded phases:

- submitters contend with `runtime_stop()` and every submitter must eventually
  observe `COAKKA_V2_ERR_CLOSED`;
- each thread repeatedly owns a complete create, handle export, initial
  snapshot, start, stop, and destroy lifecycle for an independent runtime.

The harness never races `runtime_destroy()` with an active caller. Caller
threads join before their runtime is destroyed, matching the public ownership
contract instead of manufacturing use-after-free behavior.

`hot-reload` means route-snapshot hot reload. It does not mean TLS credential or
connection-strategy reload. While concurrent producers alternate two targets,
the control thread applies monotonically newer full snapshots that alternate
the one active local target. Per-thread quotas prevent producers from finishing
before the final generation. After every accepted apply, the harness reads the
runtime stats and requires that exact generation, one route, and `STARTED`
state to be observable before releasing the next traffic slice. A pass
requires:

- every admitted request reaches exactly one reply or route-miss deadletter;
- the final active target replies and the replaced target deadletters;
- a stale generation returns `COAKKA_V2_ERR_STALE_GENERATION`;
- an invalid newer snapshot returns `COAKKA_V2_ERR_INVALID_ARG`;
- both rejected applies leave the final generation, route count, and started
  lifecycle state unchanged.

Deadletters during this mode are expected evidence that a request resolved
against a complete active snapshot. They are not silent drops. TLS/mTLS test
credentials and live certificate rotation remain a separate future scenario.

## Connection Strategy Contract

The second executable in this source directory exercises connection strategy
configuration through the same published C ABI. It does not reach into private
headers or infer support from an edition name. For each mode it reads the
runtime capability snapshot, then proves the supported apply or the structured
capability rejection:

- `PER_EXCHANGE`
- `BOUNDED_POOL`
- `PERSISTENT_SINGLE_FLIGHT`
- `MULTIPLEXING`

Every case also applies an invalid mode and verifies that effective state is
unchanged. It then exports host handles, applies an empty generation-1 control
snapshot, starts the runtime, and verifies that a valid reconfiguration attempt
returns `BAD_STATE` without mutation. The JSON records whether each stage ran;
an unexecuted stage is never serialized as a successful status.

The bounded-pool case checks explicit tuning only when the runtime advertises
the tuning capability. Otherwise it requires the matching structured rejection
and unchanged state. The lane does not load certificates and does not claim a
TLS/mTLS handshake; TLS behavior remains covered by the runtime's dedicated
security matrix and the public TLS/mTLS guide.

Build this executable against a runtime package that exposes
`coakka/v2/runtime_transport_config.h`:

```sh
cmake -S runtime-test -B build/native-connection-evidence \
  -DCMAKE_PREFIX_PATH=/path/to/coakka-runtime-native-v2 \
  -DCOAKKA_NATIVE_EVIDENCE_REQUIRE_CONNECTION_STRATEGY=ON
cmake --build build/native-connection-evidence --config Release
./build/native-connection-evidence/coakka_runtime_v2_connection_strategy_evidence
```

## Run

From the `coakka-samples` repository root:

```sh
bash run.sh runtime-test smoke
```

On Windows PowerShell:

```powershell
./runtime-test/run.ps1 smoke
```

Common runs:

```sh
bash run.sh runtime-test smoke --payload 64K --requests 128
bash run.sh runtime-test pressure --requests 512 --queue-capacity 2
bash run.sh runtime-test stress --payload 128K --requests 2000
bash run.sh runtime-test soak --payload 64K --duration 30s --max-in-flight 64
bash run.sh runtime-test connection-strategies
bash run.sh runtime-test race --threads 4 --requests 256
bash run.sh runtime-test hot-reload --threads 4 --requests 256 --generations 64
```

macOS ARM64 and Linux ARM64 use native generation `1.4.0+2cee86bf`. Linux
x86-64 can still run the workload modes through the checksum-pinned
`1.3.4+dc6ec284` compatibility package, but that package predates the public
connection-strategy ABI. Run `connection-strategies` on a platform included in
the `1.4.0` artifact matrix.

For the original workload modes, the Windows PowerShell entrypoint executes the
checksum-verified `1.3.4+dc6ec284` compatibility evidence runner. For `race`
and `hot-reload`, it builds this public C11 source against the checksum-verified
Windows x86-64 `1.4.0+2cee86bf` package. Windows ARM64 is not implied because
that native generation was not published for that platform.

Documented payload presets are:

```text
64K, 128K, 256K, 512K, 1M, 2M, 3M
```

`K` and `M` are binary units: `64K` is 65,536 bytes and `1M` is 1,048,576
bytes. The parser also accepts other positive byte, `K`, or `M` values up to
`3M`.

Finite `--requests` values are capped at 500,000. Pressure payloads are capped
at `16K`; use smoke, stress, or soak for larger payloads. In-flight value `0`
means unbounded by the harness and is used by pressure mode to create a burst.

## Timing And Throughput

The monotonic measurement window starts immediately before the first request
envelope is built. It ends after every submitted request has produced one
terminal response or deadletter and the final channels have been drained.

The JSON separates:

- `submissionWindowMs`: request construction and submission, including any
  pumping required by `maxInFlight`;
- `finalDrainMs`: time after submission stops until all terminal outcomes are
  drained;
- `totalElapsedMs`: the complete measured window.

For soak mode, `--duration` controls the submission window. Total elapsed time
can be longer because already-submitted work must still reach a terminal
outcome.

Throughput fields mean:

- `completedRoundTripsPerSecond`: completed replies divided by total elapsed
  time;
- `terminalOutcomesPerSecond`: replies plus explicit rejections divided by
  total elapsed time;
- `completedRequestPayloadMiBPerSecond`: logical request payload bytes for
  completed replies;
- `completedRoundTripPayloadMiBPerSecond`: logical request plus reply payload
  bytes for completed replies.

These are batch measurements for this exact local path. They are not latency
percentiles and must not be extrapolated into fleet capacity or production
SLOs.

## Output Contract

Setup and build messages are written to stderr. Stdout contains exactly one
JSON document, including failures:

```sh
bash run.sh runtime-test smoke > result.json
python3 -m json.tool result.json
```

The result records:

- runtime ABI, version, and commit;
- OS, architecture, logical CPU count, compiler, build profile, and
  source/prebuilt execution path;
- exact target, request path, and reply path;
- configuration and terminal counters;
- monotonic timing windows and scoped throughput;
- `status=pass|fail` plus an error when an invariant fails.

The concurrency executable uses the separate schema
`coakka.runtime.native.concurrency-evidence.v1`. It additionally records the
thread and generation configuration, stop-race convergence, independent
lifecycle totals, runtime queue/deadletter accounting, accepted-versus-observed
snapshot counts, final snapshot state, and whether ThreadSanitizer covered only
the consumer harness or an explicitly source-instrumented runtime.

## Source Layout

The public harness is intentionally split by ownership:

| File | Responsibility |
| --- | --- |
| [`main.c`](main.c) | Process-level orchestration only. |
| [`evidence.h`](evidence.h) | Internal contract shared by the harness modules. |
| [`evidence_config.c`](evidence_config.c) | CLI parsing, mode defaults, and input limits. |
| [`evidence_platform.c`](evidence_platform.c) | Monotonic clock, process metadata, channel wait, and OS adaptation. |
| [`evidence_runtime.c`](evidence_runtime.c) | Public C ABI adapter, target path, runtime pumping, and pass invariants. |
| [`evidence_report.c`](evidence_report.c) | Environment metadata and the final JSON document. |
| [`connection_strategy_contract.c`](connection_strategy_contract.c) | Capability-aware validation, atomic apply, lifecycle, and immutability checks. |
| [`connection_strategy_report.c`](connection_strategy_report.c) | Machine-readable connection-strategy evidence without ambiguous default statuses. |
| [`concurrency_config.c`](concurrency_config.c) | Bounded race and hot-reload CLI parsing. |
| [`concurrency_runtime.c`](concurrency_runtime.c) | Multi-producer, submit-versus-stop, independent lifecycle, and atomic snapshot-replacement contracts. |
| [`concurrency_report.c`](concurrency_report.c) | Machine-readable concurrency and hot-reload evidence. |
| [`evidence_json.c`](evidence_json.c) | Shared JSON string writer used by both evidence executables. |

Raw host-handle field names stay inside the public ABI adapter. The rest of the
harness uses request, response, deadletter, and delivered-request channel
vocabulary. The source contains no private core runtime implementation.

The runtime driver owns at most one pending reply. When bounded admission
returns `WOULD_BLOCK`, it yields to the common pump so response and deadletter
channels can make progress before the next retry; it does not sleep or spin in
the handler path.

The Clang/GCC source build uses strict C11 without compiler language extensions
and enables `-Wall -Wextra -Wpedantic -Werror` by default. MSVC uses `/W4 /WX`.
Published Windows runners are built from the same source with the exported
public C ABI surface. Windows readiness uses a bounded `PeekNamedPipe` fallback
because CRT anonymous pipes are not socket-pollable; Linux and macOS use
`poll`. The selected backend is recorded in `environment.readinessWaitBackend`.

Run Clang static analysis over every available public translation unit:

```sh
CLANG=clang bash runtime-test/analyze.sh \
  /path/to/coakka-runtime-native-v2/include
```

For consumer-harness ASan/UBSan instrumentation, configure with:

```sh
cmake -S runtime-test -B build/native-evidence-sanitized \
  -DCMAKE_PREFIX_PATH=/path/to/coakka-runtime-native-v2 \
  -DCOAKKA_NATIVE_EVIDENCE_ENABLE_SANITIZERS=ON
cmake --build build/native-evidence-sanitized
```

That option instruments this public harness. It proves the public consumer code
only when the selected runtime library is an ordinary published binary. To make
a sanitizer claim about core, link the harness to an ASan/UBSan-instrumented
runtime built from the same source and record both build identities. Linux is
the leak-detection authority; macOS is an ASan/UBSan correctness lane, and no
Windows sanitizer result is implied.

ThreadSanitizer is intentionally separate from ASan/UBSan:

```sh
cmake -S runtime-test -B build/native-evidence-tsan \
  -DCMAKE_PREFIX_PATH=/path/to/coakka-runtime-native-v2 \
  -DCOAKKA_NATIVE_EVIDENCE_ENABLE_TSAN=ON
cmake --build build/native-evidence-tsan
./build/native-evidence-tsan/coakka_runtime_v2_concurrency_evidence race
./build/native-evidence-tsan/coakka_runtime_v2_concurrency_evidence hot-reload
```

That command instruments the public consumer harness only. A core race claim
requires the runtime and its linked C++ implementation to be compiled with
ThreadSanitizer in the same build graph. The Core repository provides the
release-source gate for that deeper run. Its script first verifies the runtime
implementation paths against the recorded source generation; it does not infer
that an ordinary production binary was sanitizer-instrumented.

The regular `sample-surface` workflow keeps small race and hot-reload profiles
alongside static analysis and the ASan/UBSan consumer harness. The deeper Core
ThreadSanitizer workflow is separate so its source build does not make every
sample push expensive.

## Source And Prebuilt Paths

On Linux and macOS, the Bash wrapper builds the public source modules in this
directory in `Release` mode when `cc`, CMake, and `tar` are available. Without
that toolchain, it uses a matching prebuilt evidence runner when one is
available:

```sh
COAKKA_NATIVE_EVIDENCE_USE_PREBUILT=1 \
  bash run.sh runtime-test smoke
```

Both paths execute the same public sample source. The prebuilt archive bundles
the matching published runtime shared library; it does not contain private core
source.

Windows uses the matching published prebuilt runner for the original workload
modes. The concurrency modes build the auditable C11 source against the current
published runtime DLL through `run.ps1`.

## Reproducibility

Before comparing runs:

- prefer a controlled Linux host that resembles the deployment worker;
- close noisy background workloads;
- avoid low-power mode and thermal throttling;
- record the complete JSON document;
- use the same runtime release, payload, queue capacity, and in-flight limit;
- run several repetitions and compare the distribution, not the best result;
- do not compare Docker, CI, UTM, or other VM throughput with host results;
- use deployment-owned tests for network, connector, framework, and production
  capacity conclusions.
