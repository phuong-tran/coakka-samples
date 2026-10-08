# C++20 Sample

The native application uses only installed `coakka/http/http.h` and
`CoAkkaHttp::runtime`, with a non-copyable RAII server, explicit checked close, and exception containment at callbacks.
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
- incremental upload counting and WebSocket text/binary echo;
- a separate RAII response-stream/SSE producer with final trailers;
- a standalone RAII logical-target outbound client and HTTP 404 example;
- static frontend/index/SPA files and a confined file response;
- runtime-issued information, fresh liveness and bounded monitor reads;
- prepared, generation-checked handler activation from `v1` to `v2`;
- TLS/mTLS with explicit credential generation and test-only credential files;
- signal-driven shutdown with failures reported, not ignored.

`server.cpp` serves both ordinary and secure examples.
`../native/sample_options.h` shares application CLI/file declarations only;
it is not another connector or runtime implementation. The convenience server
uses its platform I/O default. It does not accept `--io-uring`: that option is
shown by the language connector samples, not invented for this API.

## Run

First set `COAKKA_PUBLISH_ROOT` and `COAKKA_HTTP_SAMPLE_WORK_ROOT` as described
in the [sample overview](../README.md), then:

```bash
bash coakka-http-runtime/cpp/run.sh check
bash coakka-http-runtime/cpp/run.sh smoke
bash coakka-http-runtime/cpp/run.sh security-smoke
bash coakka-http-runtime/cpp/run.sh streaming
bash coakka-http-runtime/cpp/run.sh outbound
bash coakka-http-runtime/cpp/run.sh run
```

CMake resolves `CoAkkaHttp 1.0.0 EXACT` from the pinned native archive. Only the
sample is compiled; warnings are errors. The smoke client uses finite waits,
checks binary echo, file bytes and the activated handler, then requires normal
process shutdown. Security smoke verifies TLS/mTLS and rejects an unidentified
client. Its automated wire test is HTTP/1.1; `--protocol http2` and `http3`
select other listeners but are not claimed as tested by that smoke.

On shutdown, close admission before destroying the server. Handler contexts
remain alive until destruction succeeds; failed close is not permission to
restart or free retained state. C++ exceptions never cross the C callback.

## Feature Recipes And Coverage

### Monitor Reload And Handler Rejection

Startup executes the shared C-first [monitor recipe](../native/monitor_example.h)
through the public API: accept a policy, reject a stale generation, reject a
request beyond the runtime's reservation, then restore and read back the original
policy. Rejection must preserve the runtime's returned generation and policy; no
sample-owned default table or inferred effective state is used.

After activating `v2`, the sample attempts a prepared binding with a stale
revision and requires typed rejection with the previous binding unchanged.
The wire smoke requires `/version` to remain `v2`. Mac/Pi checks pass; these
examples do not establish structural route replacement, monitor cursor loss,
or delivery-ambiguous timeout/retry coverage.

### Response Streaming And SSE

[`streaming.cpp`](streaming.cpp) uses a `unique_ptr` writer, one bounded optional
job and a standard mutex/condition variable. It prints `native-stream-port=PORT`.
`/stream` returns `stream-ready` plus final `x-stream-end: done`; `/events`
returns one typed SSE event with ID, retry interval and multiline data.

The handler reserves the producer slot, selects a head, transfers its writer
and returns. Main waits for writable credit only after that handoff: waiting
inside the callback would prevent head admission. Only one writer may be
reserved/queued/active, and overlap returns HTTP503. This is example business
producer capacity, not a runtime-wide limit. No extra application thread or
unbounded work queue is created.

`finish` consumes the writer on success; failure restores it to the RAII owner.
Local release does not manufacture successful HTTP completion. The runtime owns
deadline/transport cleanup. Shutdown closes slot admission, releases pending
work, joins callbacks through the runtime stop, then destroys the server before the
producer. Exceptions never cross callbacks, and a retained owner is not silently
discarded during destructor unwinding.

### WebSocket Ownership

The ordinary server accepts `/socket`. Main owns the sole event reader, echoes
text/binary before exact event release and handles send pressure through an
explicit 1013 close attempt. The runtime owns ping/pong and connection storage. The
sample adds no frame-retention queue and releases an event before throwing on a
send error. Wire smoke checks handshake, text/binary, ping/pong, normal close,
peer TCP abort and subsequent sessions.

`smoke` also runs eight stream/SSE writer-reuse rounds. Mac/Pi strict builds and
wire tests pass; new Windows sample execution and saturation tests remain open.

`POST /upload` incrementally counts bytes and trailers and returns
`bytes=N trailers=M`. It retains no body chunks: borrowed views expire at the
next read, while scalar counters remain callback-local. The runtime owns the body
ceiling and request deadline; each read waits at most five seconds on the
handler worker. Cancellation returns without a new response. Exceptions are
contained by the callback boundary. The smoke verifies a binary chunked upload
with a trailer, an observed partial-body disconnect and continued service.

### Outbound Requests

[`outbound.cpp`](outbound.cpp) starts a loopback upstream and declares its
assigned port as a logical target on the client service. The runtime copies the
topology and owns transport/defaults. Each submitted call has a three-second
deadline; main is the sole completion reader with a finite five-second wait.

The non-copyable terminal owner releases its borrowed response exactly once,
including exception unwinding, before another request or service close. Both
call slot and generation must match. HTTP404 is an ordinary response outcome,
not a transport failure or an instruction to retry. Non-copyable service owners
close client before upstream; an unreleased native owner is never discarded
silently during unwinding. Callback exceptions cannot cross the C boundary.

Mac/Pi checks cover 200/body, 404 and successful close. Mac consumer-only
ASan/LSan/UBSan, separate TSan and analyzer pass. Outbound timeout, cancellation
and TLS fault scenarios remain unverified by this short example.

The same executable next publishes a complete replacement route table. It
prepares the callback before publication, checks the runtime's acceptance and exact
activation replay, then requires stale-generation refusal with unchanged
effective route/metadata/binding generations. Wire requests verify the new
`/published` route and404 for removed `/source`. Stack-owned route declarations
are borrowed only during the synchronous call. Callback context stays valid
until service destruction. Mac/Pi and consumer sanitizer checks pass; ambiguous
delivery, replay expiry and retention of already-running old handlers are not
covered by this finite demonstration.

Use the [feature index](../FEATURES.md) for source links, route commands,
expected responses and outstanding test coverage. Sample presence, package
capability and matching-host execution are separate claims.

## Native Startup Tuning And Parameters

The pinned native candidate uses ABI11. Keep its header and library together;
these operations are not available by mixing a new header with an ABI10 binary.
Normal startup leaves the optional tuning pointer NULL and uses the runtime defaults.
After `run.sh check`, explicitly demonstrate advanced settings with:

```sh
"$COAKKA_HTTP_SAMPLE_WORK_ROOT/cpp/build/coakka-http-cpp" \
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
The RAII constructors handle this explicitly because a throwing constructor does not run its destructor.
No failed or stopped instance is restarted. Mac/Pi smoke checks parameters,
effective profiles, bounded GZIP decoding and graceful shutdown; Pi additionally
checks the explicit SINGLE recipe. Package platform gates remain separate from
sample execution evidence.

## Raspberry Pi 5 Benchmark

| Application | CPUs | Req/s median (range) | p50 / p95 / p99 ms | CPU % | RSS mean / sampled peak MiB | Errors / timeouts |
| --- | ---: | --- | --- | ---: | --- | --- |
| Native C + CoAkka | 1 | 61,248.1 (60,692.4–61,400.1) | 1.051 / 1.302 / 1.411 | 99.6 | 9.2 / 9.2 | 0 / 0 |
| Native C + CoAkka | 2 | 93,072.7 (91,968.4–93,349.7) | 0.679 / 0.726 / 0.988 | 151.7 | 9.2 / 9.2 | 0 / 0 |

Installed r3 native package, public host-inlined C callbacks. Native is
measured alone, without a competing server. Both CPU profiles report
one event loop and notification batches 8/8. Three fresh-process runs
per profile, with the required CPU cooldown before each run.
These are localhost observations, not an unconstrained capacity claim.

This is the **C baseline reference**, not a separate C++ measurement.

See [complete results, machine facts, all runs and evidence identities](../benchmark-rpi5/README.md#verified-package-results)
and the [methodology](../benchmark-rpi5/README.md). CPU100% means one CPU;
RSS is sampled, and resource measurements include warm-up.
