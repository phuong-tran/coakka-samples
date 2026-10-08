# C11 Sample

The native application uses only installed `coakka/http/http.h` and
`CoAkkaHttp::runtime`, with explicit checked create/start/stop/destroy.
The runtime schedules callbacks; this application does not build a connector
request-pump thread. Borrowed request views never escape their callback.

## Contents

- [Features](#features)
- [Run](#run)
- [Feature Recipes And Coverage](#feature-recipes-and-coverage)
- [Native Startup Tuning And Parameters](#native-startup-tuning-and-parameters)
- [Raspberry Pi 5 Benchmark](#raspberry-pi-5-benchmark)

## Features

- binary buffered echo with status 201;
- incremental `/upload` byte/trailer counting without retaining body chunks;
- `/socket` text/binary echo, runtime-owned ping/pong and close;
- a separate bounded response-stream/SSE producer with final trailers;
- a standalone logical-target outbound request/reply and HTTP 404 example;
- static frontend/index/SPA files and a confined file response;
- runtime-issued information, fresh liveness and bounded monitor reads;
- prepared, generation-checked handler activation from `v1` to `v2`;
- TLS/mTLS with explicit credential generation and test-only credential files;
- signal-driven shutdown with failures reported, not ignored.

`server.c` serves both ordinary and secure examples.
`../native/sample_options.h` shares application CLI/file declarations only;
it is not another connector or runtime implementation. The convenience server
uses its platform I/O default. It does not accept `--io-uring`: that option is
shown by the language connector samples, not invented for this API.

## Run

First set `COAKKA_PUBLISH_ROOT` and `COAKKA_HTTP_SAMPLE_WORK_ROOT` as described
in the [sample overview](../README.md), then:

```bash
bash coakka-http-runtime/c/run.sh check
bash coakka-http-runtime/c/run.sh smoke
bash coakka-http-runtime/c/run.sh security-smoke
bash coakka-http-runtime/c/run.sh outbound
bash coakka-http-runtime/c/run.sh streaming
bash coakka-http-runtime/c/run.sh run
```

CMake resolves `CoAkkaHttp 1.0.0 EXACT` from the pinned native archive. Only the
sample is compiled; warnings are errors. The smoke client uses finite waits,
checks binary echo, file bytes and the activated handler, then requires normal
process shutdown. Security smoke verifies TLS/mTLS and rejects an unidentified
client. Its automated wire test is HTTP/1.1; `--protocol http2` and `http3`
select other listeners but are not claimed as tested by that smoke.

`POST /upload` returns `bytes=N trailers=M`. For example, `curl --data-binary
'hello' http://127.0.0.1:PORT/upload` returns `bytes=5 trailers=0`. Unlike an
echo collector, this example retains only scalar counters, regardless of body
size. The runtime enforces its configured body ceiling and request deadline; each
body read has a finite five-second wait on the handler worker. Borrowed chunk
and trailer views expire on the next read. Cancellation ends the callback
without attempting another response. The smoke sends a chunked binary body
with a trailer and disconnects a second upload only after DATA was observed;
it requires cancellation and a successful later request before shutdown.

On shutdown, close admission before destroying the server. Handler contexts
remain alive until destruction succeeds; failed close is not permission to
restart or free retained state. C++ exceptions never cross the C callback.

## Feature Recipes And Coverage

### Monitor Reload And Handler Rejection

Startup runs the shared [monitor recipe](../native/monitor_example.h): read
the runtime's effective policy and generation, enable aggregates, then exercise a
stale-generation refusal and a request exceeding the runtime's immutable reservation.
Both refusals must return the unchanged accepted policy. The example restores
the original policy and reads it back; it never reconstructs effective state
from the submitted input. `native-monitor-reload=pass` marks those assertions.

Handler activation prepares `v2` before publication. A subsequent activation
with the old binding revision must return a typed revision mismatch without
changing the effective binding. The wire smoke then requires `/version` to
remain `v2`. This is not a complete structural route replacement or a test of
every delivery-ambiguous timeout/retry path. Both recipes pass on Mac and Pi.

### Response Streaming And SSE

[`streaming.c`](streaming.c) is a separate finite producer. The command above
prints `native-stream-port=PORT`; `/stream` emits `stream-ready` followed by
`x-stream-end: done`, and `/events` emits one typed SSE event with an ID, retry
interval and multiline data. Use `curl --raw -i http://127.0.0.1:PORT/stream`
to inspect HTTP/1.1 chunks/trailers, or `curl -N http://127.0.0.1:PORT/events`.

A callback selects the response head and returns before the writer can become
writable. Never wait for initial credit inside that callback. This example
hands a writer to main through one mutex-protected slot and wakes a condition
variable; it adds no application thread. One reservation covers preparation,
handoff and production. Overlap receives HTTP503 before a head is selected.
That single-producer capacity is an explicit sample policy, not a runtime limit.
Native waits remain finite and monotonic; no borrowed request escapes.

The runtime owns framing. The app passes final trailers to `finish`, which consumes
the writer only on success. A refusal retains it; this finite producer records
the typed failure and releases its local writer rather than silently retrying
or writing a second response. Release does not fabricate a last chunk. The runtime's
deadline or server stop owns remaining transport cleanup. Shutdown closes the
producer slot, releases pending writers, then stops and destroys the service
before freeing callback state.

### WebSocket Ownership

The ordinary server reserves WebSocket support at startup and accepts `/socket`.
Main is the sole event reader and echoes text/binary frames before releasing
each borrowed event exactly once. The runtime owns protocol ping/pong and session
storage. Send pressure triggers an explicit 1013 close attempt, not an unbounded
application retry queue. The smoke checks text, binary, ping/pong, ordinary close,
abrupt TCP disconnect and subsequent sessions. It is not a saturation test.

`smoke` checks both executables, including eight stream/SSE writer-reuse rounds
and repeated WebSocket sessions. Mac and Pi checks pass; these new examples do
not add Windows sample-execution evidence or a performance claim.

### Outbound Requests

[`outbound.c`](outbound.c) starts a loopback upstream on an assigned port and
declares it as the logical target `sample.upstream` on a second service. The runtime
copies that topology at create and owns transport selection, queues, timeouts
and terminal outcomes. The example passes a three-second request deadline and
uses one main-thread terminal reader with a finite five-second wait. It checks
both parts of the returned call identity before reading the response.

Every successful take borrows one terminal: inspect its typed reason, status
and body, then release exactly once before another take or shutdown. A 404
from `/missing` is an HTTP response, not a provider failure. Do not infer retry
safety from status or diagnostic text. The client closes before the upstream;
the pass marker is emitted only after both owners have closed successfully.
Cancellation, outbound TLS and timeout-fault examples remain separate coverage
work; these finite requests do not claim to verify those paths.

The example then prepares a new binding and replaces the upstream's complete
route table: `/published` becomes available and `/source` returns404. It checks
ordinary replay of the identical activation/payload, then rejects a new
activation using stale generations. The runtime's returned route, metadata and binding
sequence remain unchanged on rejection. The initial expected generation is an
explicit precondition on this isolated fresh service, not an observed snapshot.
Mac/Pi and consumer sanitizer checks pass. This is not an injected ambiguous
delivery, replay-expiry or in-flight-old-handler retention test.

Use the [feature index](../FEATURES.md) for source links, route commands,
expected responses and outstanding test coverage. Sample presence, package
capability and matching-host execution are separate claims.

## Native Startup Tuning And Parameters

The pinned native candidate uses ABI11. Keep its header and library together;
these operations are not available by mixing a new header with an ABI10 binary.
Normal startup leaves the optional tuning pointer NULL and uses the runtime defaults.
After `run.sh check`, explicitly demonstrate advanced settings with:

```sh
"$COAKKA_HTTP_SAMPLE_WORK_ROOT/c/build/coakka-http-c" \
  --assets "$PWD/coakka-http-runtime/assets" --cpu auto \
  --request-batch small --terminal-batch medium --compression gzip
```

| Option | Meaning |
| --- | --- |
| `--cpu auto` | the runtime prefers two allowed logical CPUs on Linux; one when only one is allowed. Other platforms currently inherit scheduler placement and report unknown counts. |
| `--cpu single` | Explicit one-CPU placement on Linux; unsupported elsewhere, never silently ignored. |
| `--request-batch PROFILE` | Request-notification coalescing cap. |
| `--terminal-batch PROFILE` | Independent terminal-notification coalescing cap. |
| `--compression gzip\|disabled` | Construction-fixed response compression policy. |

Profiles are `auto`, `small`, `medium`, `large`, `xlarge`, `xxlarge`,
and `ultra`. The runtime resolves AUTO and reports the effective caps; notifications
do not wait for a full batch. No loop-count CLI is exposed. CPU placement is
not a process-wide quota, exclusive CPU reservation or throughput guarantee.
The startup log prints the runtime's selected CPU count, verification state and
effective batch sizes, not copies of input preferences. Unknown is not zero
hardware CPUs. These controls cannot be hot-reloaded.

The GZIP demonstration deliberately chooses level6 and a one-byte threshold
so its small response is observable; these are sample choices, not the runtime's
default table. The remaining bounded limits are resolved by the runtime. Test it:

```sh
curl --max-time 5 --compressed -i "http://127.0.0.1:$PORT/items/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
curl --max-time 5 -i "http://127.0.0.1:$PORT/items/a%2Fb?tag=one&flag&tag=&tag=two"
```

The second request returns `a%2Fb`, `x-query-count: 4`, and
`x-query-with-value: 3`. The handler borrows the indexed path/query views the runtime
already parsed: no raw-target split, URL decoding or temporary parameter map.
Query order and duplicate keys survive; `?flag` has no value while `?tag=`
has an empty value. Indexed access is O(1); enumerating all entries is O(n).
Views expire on callback return. The response operation copies selected bytes
before local values expire.

Create/start temporarily apply the runtime's chosen placement while creating workers,
then restore the caller. A rare restoration failure after create may return a
non-NULL destroy-only owner together with failure; always clean it up.
The shared cleanup path checks the returned owner even when create fails.
No failed or stopped instance is restarted. Mac/Pi smoke checks parameters,
effective profiles, bounded GZIP decoding and graceful shutdown; Pi additionally
checks the explicit SINGLE recipe. Package platform gates remain separate from
sample execution evidence.

## Raspberry Pi 5 Benchmark

| Application | CPUs | Req/s median (range) | p50 / p95 / p99 ms | CPU % | RSS mean / sampled peak MiB | Errors / timeouts |
| --- | ---: | --- | --- | ---: | --- | --- |
| Native C + CoAkka | 1 | 88,255.1 (87,753.3–88,939.2) | 0.715 / 0.756 / 0.943 | 99.7 | 12.9 / 12.9 | 0 / 0 |
| Native C + CoAkka | 2 | 158,854.4 (158,021.3–158,903.7) | 0.375 / 0.547 / 0.640 | 195.6 | 17.1 / 17.1 | 0 / 0 |

Installed r3 native package, explicitly configured public C host-inlined
event reader (not callback defaults). Native is measured alone, without
a competing server. One/two CPUs use one/two loops, notifications 64/32
and completion batch 64; full terminal events remain enabled. Three fresh-process runs
per profile, with the required CPU cooldown before each run.
These are localhost observations, not an unconstrained capacity claim.

After preparing the Pi workspace using the shared methodology, run only
the native lane from its benchmark directory (use fresh output paths):

```sh
python3 scripts/run-rpi5.py --study-mode client-limited-loopback --lane c-coakka --config config/lanes.json --output evidence/native-1cpu
python3 scripts/run-rpi5.py --study-mode client-limited-loopback --lane c-coakka --config config/lanes-2cpu.json --output evidence/native-2cpu
python3 scripts/summarize.py evidence/native-1cpu/campaign.json --study --output evidence/native-1cpu/STUDY.md
python3 scripts/summarize.py evidence/native-2cpu/campaign.json --study --output evidence/native-2cpu/STUDY.md
```

Run the two campaigns sequentially, never concurrently. The runner owns
cooldown and refuses failed package, response or shutdown checks.

See [complete results, machine facts, all runs and evidence identities](../benchmark-rpi5/README.md#verified-package-results)
and the [methodology](../benchmark-rpi5/README.md). CPU100% means one CPU;
RSS is sampled, and resource measurements include warm-up.
