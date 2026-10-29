# Native Runtime Test

The rolling harness requires a Runtime SDK exposing `coakka_v2_host_handles_close`.
An older SDK is rejected before the harness is built. Historical Runtime
archives remain unchanged; use their matching recorded harness snapshot rather
than pairing them with this current rolling source. Candidate verification
uses the exact SDK identified by the selected candidate catalog.

This harness exercises the published CoAkka Runtime native package through its
public C ABI. It provides a reproducible local request/reply scenario and
bounded-admission, concurrency, lifecycle, and live route-snapshot evidence
without reaching into private runtime sources.

It is a native public-ABI baseline, not a claim about the absolute runtime
ceiling, cross-machine performance, connector performance, or production
capacity.

The published/default profile below is the historical v1 harness. The explicit
private Native candidate profile uses Core readers/tickets and v2 accounting;
see [Current-Engine Controls](#current-engine-controls--source-candidate).
Do not apply the v1 framed-channel description to the new response engine.

For loader, unsigned-library, Windows, macOS, Linux, and TLS/mTLS diagnosis,
see [Troubleshooting](../../docs/coakka-runtime/troubleshooting.md).

## Contents

- [Measured Target Path](#measured-target-path)
- [Choose The Measurement Environment](#choose-the-measurement-environment)
- [Modes And Pass Invariants](#modes-and-pass-invariants)
- [Concurrency And Snapshot Hot Reload](#concurrency-and-snapshot-hot-reload)
- [Connection Strategy Contract](#connection-strategy-contract)
- [Native Startup Candidate](#native-startup-candidate)
- [Current-Engine Controls](#current-engine-controls--source-candidate)
- [Run](#run)
- [Timing And Throughput](#timing-and-throughput)
- [Output Contract](#output-contract)
- [Source Layout](#source-layout)
- [Source And Prebuilt Paths](#source-and-prebuilt-paths)
- [Reproducibility](#reproducibility)

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
| `file-lane-simple` | Stable Simple File Lane API over loopback | One 9 MiB direct-profile transfer | Application-managed endpoint fields drive a transfer whose peers complete with matching SHA-256, terminal state, progress, and counters. |
| `file-lane-owner-aware` | Additive native owner-grant API over loopback | The same bounded transfer | Feature detection, exact owner projection, fixed-size grant handoff, token cleanup, and both peer terminal results pass. |
| `stream-lane-simple` | Stable Simple Stream Lane API over loopback | 97 deterministic variable-size frames | Application-managed endpoint fields drive ordered frames, pressure observation, terminal state, counters, and forget lifecycle. |
| `stream-lane-owner-aware` | Additive native owner-grant API over loopback | The same bounded frame sequence | Feature detection, exact publisher projection, single-admission grant handoff, token cleanup, pressure, and terminal results pass. |

`file-lane` and `stream-lane` remain command aliases for their `-simple`
profiles. Simple is supported, not deprecated. Use it for one stable lane
instance or when the application already owns endpoint selection. Use
Owner-aware when a logical service has multiple replicas or an endpoint may be
reassigned; the grant must reach the exact process or pod retaining prepared
state.

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

In the default historical profile, the second executable exercises connection strategy
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

## Standalone Lane Source Candidate

The current source candidate also runs both standalone lane profiles against
the exact selected SDK:

```sh
./build/coakka_runtime_v2_file_lane_evidence
./build/coakka_runtime_v2_file_lane_evidence --owner-aware
./build/coakka_runtime_v2_stream_lane_evidence
./build/coakka_runtime_v2_stream_lane_evidence --owner-aware
```

Build the two lane targets in the configured external build directory. These
APIs are independent of actor Runtime startup/response delivery; they do not
use its old response pipe or need a Native fixture. Owner-aware preparation
must return Core's copied exact owner endpoint and bound port. File grants
retain their bounded resume/idempotency law; Stream grants are single-admission.
The deterministic loopback tokens are test-only and never printed. Both simple
and owner-aware paths require exact terminal/counter checks,9MiB+731-byte File
digest/receipt and97 ordered Stream frames. File failure JSON emits null for
unobserved Core snapshots, digests and statistics; a failed getter is not zero.

These current sources require SDK headers exposing owner-grant APIs. Historical
prebuilt runners remain unchanged; do not claim this candidate source was built
against an older SDK because an old version appears elsewhere in this guide.
Windows derives a consumer import library when the package contract supplies
only a DLL; its checked-in list contains the harness's public C call closure,
not private exports or an expanded Core ABI.

## Native Startup Candidate

`native-startup` is an opt-in source-candidate check, not a capability of a
previously published package or prebuilt runner. It uses only an explicitly
selected installed `CoAkkaV2` C package containing `runtime_startup.h`.
It never downloads a package, changes a pin, builds Core implicitly or falls
back to an older constructor.

Create an external, caller-owned build directory first, then select the exact
installed candidate:

```sh
mkdir -p /external/owned/native-startup-build
COAKKA_NATIVE_EVIDENCE_RUNTIME_PREFIX=/exact/installed/native-candidate \
COAKKA_NATIVE_EVIDENCE_BUILD_DIR=/external/owned/native-startup-build \
  bash run.sh coakka-runtime runtime-test native-startup
```

Both directories must already exist and must not contain each other. The build
directory cannot be a source checkout or contain this sample repository.
Bash resolves physical directory paths; PowerShell refuses reparse-point
paths. The explicit reusable build and its evidence remain caller-owned.
Temporary package-admission metadata is removed on success and failure.

The runner checks CMake's selected config and imported Release runtime image
beneath the resolved prefix, including the runtime image hash. After execution,
it also checks the image path observed by the process loader and its content
hash against that selection. A compatible library loaded from another prefix
is a failure, even if the child itself passed its startup checks. A small
metadata-only preflight rejects a missing header or foreign image before the
existing build cache is changed. Each invocation clears a stale Core-source
override and explicitly sets consumer sanitizer preferences. Sanitizer
environment values accept only `0` or `1`; ASan/UBSan and TSan are separate
builds. Windows candidates must supply their current import library rather
than deriving it from the older compatibility export file.

The four bounded cases cover omitted groups, explicit OFF values, monitor ON
and optional I/O ON. They check copied startup intent, defaulted-group facts,
invalid/short input refusal, unchanged getter output on failure, immutable
connection and monitor settings, host-handle cleanup, and create/start/stop/
destroy. Legacy constructor objects report unavailable startup facts; the
harness does not fabricate AUTO, MULTIPLEXED or OFF values for them.

Each case reports actual Core-issued configuration in CREATED, STARTED and
STOPPED states. AUTO CPU selection is not a thread count or utilization
measurement. The cases deliberately use EMBEDDED network policy: optional I/O
may be requested and supported while effective remains zero because there is
no TCP receiver. This is not active receiver, TLS or network-traffic evidence.

Stdout contains one JSON document using
`coakka.runtime.native.startup-evidence.v1`. Core supplies ABI, version, source
commit, build identity, profile and capability facts; unchecked operations
remain marked unchecked. The child execution has a 30-second ceiling. Raw
stdout/stderr and the selected image/hash receipt remain in the explicit build
directory, including after timeout or invalid JSON. Only loader-verified output
is saved as `native-startup-validated.json`; each invocation removes that
task-owned file first so a failed run cannot leave an older pass behind.
No benchmark is produced.
Sanitizer options instrument the consumer only unless an independently
qualified instrumented Core artifact is deliberately supplied; a successful
consumer run is not a whole-Core sanitizer claim.

Direct CMake consumers can select the same boundary with
`COAKKA_NATIVE_EVIDENCE_RUNTIME_PACKAGE=CoAkkaV2`, an explicit
`COAKKA_NATIVE_EVIDENCE_RUNTIME_PREFIX`, and
`COAKKA_NATIVE_EVIDENCE_REQUIRE_NATIVE_STARTUP=ON`. Missing startup headers fail
that required gate. With the gate OFF, older published packages retain their
existing workload and connection-strategy modes without the new target.

## Current-Engine Controls — Source Candidate

On macOS/Linux, an explicit installed-candidate prefix and caller-owned build
directory now select the current engine for every source mode, not only
`native-startup`:

```sh
COAKKA_NATIVE_EVIDENCE_RUNTIME_PREFIX=/exact/installed/native-candidate \
COAKKA_NATIVE_EVIDENCE_BUILD_DIR=/external/owned/native-engine-controls \
  bash run.sh coakka-runtime runtime-test smoke
```

Create that external build directory first; it must not overlap the SDK or
source checkout. Replace `smoke` with `pressure`, `stress`, `soak`, `race`,
`hot-reload`, `connection-strategies`, either lane's `-simple`/`-owner-aware`
profile, or `native-startup`. Workload/concurrency arguments pass unchanged to
their owning CLI. This selection refuses historical/prebuilt and Core-source
build conflicts; it never falls back after admission/build/probe failure.
Without explicit candidate intent, versioned historical runner behavior stays
unchanged until coordinated promotion.

Metadata admission precedes reusable-cache writes. The existing bounded startup
driver verifies loader-observed Core image identity; every real candidate target
uses the same selected image path. CMake supplies the executable's exact path,
including custom/multi-config layouts, instead of probing guessed directories.
The shell removes its temporary admission directory before direct exec, clears
only its process's loader overrides and preserves workload exit/signal ownership
and original deadlines. Native Windows uses the explicit CMake controls below;
this paragraph does not claim a Windows Bash frontend.

The private native candidate `2.5.1+f6b61768` additionally has focused C11
fixture-owner controls. They use Core's opaque request reader and ask tickets,
not framed response channels. From the repository root, configure an explicit
installed `CoAkkaV2` prefix and external, caller-owned build directory:

```sh
cmake -S runtime-test -B /external/owned/native-engine-controls \
  -DCMAKE_BUILD_TYPE=Release \
  -DCOAKKA_NATIVE_EVIDENCE_RUNTIME_PACKAGE=CoAkkaV2 \
  -DCOAKKA_NATIVE_EVIDENCE_RUNTIME_PREFIX=/exact/installed/native-candidate \
  -DCOAKKA_NATIVE_EVIDENCE_REQUIRE_NATIVE_STARTUP=ON
cmake --build /external/owned/native-engine-controls --config Release \
  --target coakka_runtime_v2_native_engine_owner_tests \
           coakka_runtime_v2_native_engine_owner_consumer \
           coakka_runtime_v2_native_ticket_window_tests \
           coakka_runtime_v2_native_ticket_publication_tests \
           coakka_runtime_v2_native_concurrency_fixture_tests \
           coakka_runtime_v2_connection_strategy_evidence \
           coakka_runtime_v2_concurrency_evidence \
           coakka_runtime_v2_native_evidence
ctest --test-dir /external/owned/native-engine-controls -C Release \
  -R '^native-(engine|ticket|concurrency|connection)' --output-on-failure
```

The white-box control checks exact status/fact projection, partial startup,
stop-refusal retention and cleanup order. The SDK-only consumer verifies16
complete lifecycles, empty/binary roundtrips and Core-issued owned request and
response modes. Assertions remain active in Release; disabling them is a
compile error. The fixture's pre-start snapshots are not live applied-state
observations. Stop refusal never permits destroying a still-borrowed Runtime.

This explicit profile selects `native_workload.c` for `smoke`, `pressure`,
`stress` and `soak`. Run `coakka_runtime_v2_native_evidence` in the build's
configuration directory with one of those mode names. Historical SDK/profile
still selects unchanged `evidence_runtime.c`; no runtime fallback or pipe-based
response parser is used by the Native workload. The same explicit profile now
selects Native `race`/`hot-reload` sources; historical/default SDK profiles keep
their unchanged legacy adapters.

The Native report schema is `coakka.runtime.native.evidence.v2`:

```text
attempted == submitted + nativeProfile.synchronouslyRefused
submitted == completed + rejected
handlerReceived == repliesSubmitted == completed
deadletters == rejected
queueRejected == synchronouslyRefused + rejected + replySubmitBackpressure
```

`submitted` counts Core-accepted tickets, `rejected` counts their actual matched
deadletters, and synchronous `WOULD_BLOCK` has no ticket or fabricated terminal
result. Pressure is a finite Native admission burst before handler pumping;
it is not the old pipe-paced scenario. It requires actual bounded refusal,
queue capacity/high-water bounds and complete terminal conservation. Other
modes require zero refusal/deadletter/route miss and verify every reply payload.
The original1000ms request intent and5000ms final drain are unchanged.

One caller-owned FIFO window retains tickets; Core still matches replies.
Storage is bounded by finite request count and/or max-in-flight, at most500K
slots, and allocated once. An unbounded/oversized request is explicitly refused
before Runtime construction. The default soak still retains at most64 tickets,
not one slot per completed request. The window test covers ownership transfer,
overflow refusal, single-slot/empty behavior and4096 wrap rounds without Core.
Pre-start Core facts are marked known only after getters succeed; CLI refusal
does not report a zero-filled runtime as an observed fact. This is source
candidate evidence, not a newly published package or whole-train readiness.

A separate pure publication control checks finite one-producer/controller
ticket ownership with4×4096 transfers and no Core load. It uses release/acquire
publication, not ticket-state inference. The Native concurrency adapter uses
that handoff and Core's request reader/ask client, never a response pipe parser:

```sh
/external/owned/native-engine-controls/coakka_runtime_v2_concurrency_evidence race
/external/owned/native-engine-controls/coakka_runtime_v2_concurrency_evidence hot-reload
```

Native concurrency emits `coakka.runtime.native.concurrency-evidence.v2`.
`attempted` is logical request intent; `admissionBackpressure` counts actual
synchronous refusals retried within the existing phase. Core-accepted requests
must reach an actual response or matched deadletter. Full snapshot replacement,
every-generation observation, stale/invalid preservation, active/inactive route
probes, submit-versus-stop `CLOSED` and independent lifecycle checks remain.
Request intent5000ms/default phase30000ms are unchanged. It does not claim TLS
credential or connection-policy reload.

One contiguous publication table is allocated once: default512 pointers/4KiB
on64-bit, at most64×100000 pointers/51.2MB for advanced controls, excluding
Core-owned ticket/result storage. Only the controller observes/destroys tickets;
threads join before abort cleanup. `producerObservationsDiscarded` is local
observation cleanup, not revoked Core execution. Admission remains recorded if
Core accepts but fixture handoff fails. Core RESPONSE is not reclassified when
the echo payload is wrong; `responseValidation` separately records that
application check and Core's exact projection status. Unknown/unperformed Core
states, counters and rejection checks are null. Final state is the Core stats
snapshot before fixture close, not a local started/stopped flag.

The explicit Native profile also selects a separate connection constructor
contract, emitting `coakka.runtime.native.connection-strategy-evidence.v2`:

```sh
/external/owned/native-engine-controls/coakka_runtime_v2_connection_strategy_evidence
```

Seven finite cases exercise omitted connection intent, four explicit modes,
explicit pool tuning and an invalid mode. Core capabilities decide expected
availability; Core validation and constructor must agree with that contract.
Omission passes NULL startup intent and verifies Core-issued AUTO/MULTIPLEXED
defaults; no connector-side default table resolves them. Unsupported or invalid
construction must return the exact typed refusal and no partial instance.
Only successful getters make accepted startup/delivery/connection facts known;
unperformed or failed observations are null, not zero-filled successful facts.
Snapshots are accepted pre-start configuration, not live placement observations.

Native connection intent is immutable from construction. Valid old setters
must return BAD_STATE both in CREATED and STARTED; malformed setters preserve
the original validation refusal. Both the atomic apply result and a separate
getter must retain the actual accepted configuration, not an assumed default.
The legacy profile keeps its original mutable-before-start contract. Both
profiles retire exported descriptors through Core's close API, including native
Windows where an executable's separate CRT cannot close DLL-owned descriptors.
No business exchange, response pipe parser, certificate or TLS handshake is
exercised by this connection configuration lane.

## Run

From the `coakka-samples` repository root:

```sh
bash run.sh coakka-runtime runtime-test smoke
```

On Windows PowerShell:

```powershell
./runtime-test/run.ps1 smoke
```

Common runs:

```sh
bash run.sh coakka-runtime runtime-test smoke --payload 64K --requests 128
bash run.sh coakka-runtime runtime-test pressure --requests 512 --queue-capacity 2
bash run.sh coakka-runtime runtime-test stress --payload 128K --requests 2000
bash run.sh coakka-runtime runtime-test soak --payload 64K --duration 30s --max-in-flight 64
bash run.sh coakka-runtime runtime-test connection-strategies
bash run.sh coakka-runtime runtime-test file-lane-simple
bash run.sh coakka-runtime runtime-test file-lane-owner-aware
bash run.sh coakka-runtime runtime-test stream-lane-simple
bash run.sh coakka-runtime runtime-test stream-lane-owner-aware
bash run.sh coakka-runtime runtime-test race --threads 4 --requests 256
bash run.sh coakka-runtime runtime-test hot-reload --threads 4 --requests 256 --generations 64
```

The sealed `2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a` candidate contains
the Simple and owner-aware File/Stream Lane C ABIs. These release-branch
commands resolve that checksum-pinned candidate; they do not claim it is
published. For development against a local Core change, override the package
with an exact source tree:

```sh
COAKKA_NATIVE_EVIDENCE_RUNTIME_SOURCE_DIR=../coakkaCoreNativeDev/v2 \
  bash run.sh coakka-runtime runtime-test file-lane-owner-aware
```

The CMake target is conditional for compatibility with older packages. Setting
`COAKKA_NATIVE_EVIDENCE_REQUIRE_FILE_LANE=ON` makes absence of the ABI a
configure error.

Run both Stream Lane profiles against the exact candidate with:

```sh
bash run.sh coakka-runtime runtime-test stream-lane-simple
bash run.sh coakka-runtime runtime-test stream-lane-owner-aware
```

The command builds the evidence consumer against the packaged public boundary,
then verifies ordered variable-size frames, metadata, source-reported drops,
terminal state, pressure snapshots, nonblocking pressure wait behavior, stats,
forget, and stop. Setting
`COAKKA_NATIVE_EVIDENCE_REQUIRE_STREAM_LANE=ON` makes absence of the packaged
contract a configure error.

macOS ARM64 and Linux ARM64/x86-64 package-built lane modes use native
generation `2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a`. The prebuilt
`1.3.4+dc6ec284` evidence runner remains a
separate compatibility path for workload modes that do not require the newer
connection-strategy ABI.

For the original workload modes, the Windows PowerShell entrypoint executes the
checksum-verified `1.3.4+dc6ec284` compatibility evidence runner. For
File/Stream Lane profiles, `race`, and `hot-reload`, it builds this public C11
source against the checksum-verified candidate package. File Lane and Stream
Lane select Windows ARM64 or x86-64 to match the host; the concurrency harness
remains x86-64-only.
The archive contains runtime DLLs but no MSVC import library, so the harness
generates a consumer-only `.lib` from its checked-in public export definition
during CMake configure. It does not alter or relink the published DLL.

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
bash run.sh coakka-runtime runtime-test smoke > result.json
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
| [`file_lane_main.c`](file_lane_main.c) | Receiver-first File Lane transfer, progress, digest, terminal, and stats evidence. |
| [`stream_lane_main.c`](stream_lane_main.c) | Ordered frame, pressure, terminal, lifecycle, and stats evidence for the selected Stream Lane package. |
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
CLANG=clang bash coakka-runtime/runtime-test/analyze.sh \
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

The regular `sample-surface` workflow may keep small non-sanitized race and
hot-reload profiles. Static analysis and sanitizer profiles are intentionally
local or evaluator-run so hosted CI does not pay for instrumented builds or a
source-level protobuf toolchain.

## Source And Prebuilt Paths

On Linux and macOS, the Bash wrapper builds the public source modules in this
directory in `Release` mode when `cc`, CMake, and `tar` are available. Without
that toolchain, it uses a matching prebuilt evidence runner when one is
available:

```sh
COAKKA_NATIVE_EVIDENCE_USE_PREBUILT=1 \
  bash run.sh coakka-runtime runtime-test smoke
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
