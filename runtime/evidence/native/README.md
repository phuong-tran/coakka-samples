# Native Runtime Evidence Harness

This harness exercises the published CoAkka Runtime native package through its
public C ABI. It provides a reproducible local request/reply scenario and
bounded-admission evidence without reaching into private runtime sources.

It is a native public-ABI baseline, not a claim about the absolute runtime
ceiling, cross-machine performance, connector performance, or production
capacity.

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

## Modes And Pass Invariants

| Mode | Submission path | Stop condition | Required pass evidence |
| --- | --- | --- | --- |
| `smoke` | Direct native submit API | Finite request count | Every request reaches the handler and returns one reply; no rejection, deadletter, or route miss. |
| `pressure` | Framed request channel | Finite burst | At least one bounded queue rejection becomes a deadletter; every admitted request still completes a reply. |
| `stress` | Direct native submit API | Finite request count | Every request completes a reply with no rejection, deadletter, or route miss. |
| `soak` | Direct native submit API | Submission duration or request limit | Every submitted request reaches one terminal outcome; the default expects replies without rejection. |

All passing modes require:

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

## Run

From the `coakka-samples` repository root:

```sh
bash run.sh runtime/evidence/native smoke
```

Common runs:

```sh
bash run.sh runtime/evidence/native smoke --payload 64K --requests 128
bash run.sh runtime/evidence/native pressure --requests 512 --queue-capacity 2
bash run.sh runtime/evidence/native stress --payload 128K --requests 2000
bash run.sh runtime/evidence/native soak --payload 64K --duration 30s --max-in-flight 64
```

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
bash run.sh runtime/evidence/native smoke > result.json
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

## Source Layout

The public harness is intentionally split by ownership:

| File | Responsibility |
| --- | --- |
| [`main.c`](main.c) | Process-level orchestration only. |
| [`evidence.h`](evidence.h) | Internal contract shared by the harness modules. |
| [`evidence_config.c`](evidence_config.c) | CLI parsing, mode defaults, and input limits. |
| [`evidence_runtime.c`](evidence_runtime.c) | Public C ABI adapter, target path, runtime pumping, and pass invariants. |
| [`evidence_report.c`](evidence_report.c) | Environment metadata and the final JSON document. |

Raw host-handle field names stay inside the public ABI adapter. The rest of the
harness uses request, response, deadletter, and delivered-request channel
vocabulary. The source contains no private core runtime implementation.

The runtime driver owns at most one pending reply. When bounded admission
returns `WOULD_BLOCK`, it yields to the common pump so response and deadletter
channels can make progress before the next retry; it does not sleep or spin in
the handler path.

The current Clang/GCC build uses strict C11 without compiler language
extensions and enables `-Wall -Wextra -Wpedantic`.

## Source And Prebuilt Paths

With `cc`, CMake, and `tar` available, the wrapper builds the public source
modules in this directory in `Release` mode against the published native
package.

Without a native toolchain, it uses a matching prebuilt evidence runner when
one is available:

```sh
COAKKA_NATIVE_EVIDENCE_USE_PREBUILT=1 \
  bash run.sh runtime/evidence/native smoke
```

Both paths execute the same public sample source. The prebuilt archive bundles
the matching published runtime shared library; it does not contain private core
source.

## Reproducibility

Before comparing runs:

- close noisy background workloads;
- avoid low-power mode and thermal throttling;
- record the complete JSON document;
- use the same runtime release, payload, queue capacity, and in-flight limit;
- run several repetitions and compare the distribution, not the best result;
- use deployment-owned tests for network, connector, framework, and production
  capacity conclusions.
