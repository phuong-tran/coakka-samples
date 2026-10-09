# Python: installation and integration

Start here to run your first CoAkka HTTP Runtime application, then add only
the features you need. The full sample remains the executable reference;
this guide explains its reading order. Handlers stay host-inlined in your
application language while Core owns HTTP transport and runtime policy.

For monitor startup, policy changes, event retention and exporter ownership,
see [Monitoring an HTTP service](../monitoring.md), including this language's
complete source recipe.

For the rationale and lifecycle behind the APIs, read
[generations and drain](../glossary.md#why-generations-are-needed),
[shutdown hooks and trade-offs](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/operations.md#application-hooks),
and [certificate rotation versus hot reload](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/tls-and-mtls.md#what-can-be-hot-reloaded).

## Contents

- [1. Install and prepare](#1-install-and-prepare)
- [2. Run your first server](#2-run-your-first-server)
- [3. Read and adapt the application](#3-read-and-adapt-the-application)
- [Request and response in Python](#request-and-response-in-python)
- [URL grammar and glossary](../glossary.md)
- [4. Try the HTTP features](#4-try-the-http-features)
- [5. Configure and observe the service](#5-configure-and-observe-the-service)
- [Static files, frontend and downloads](#static-files-frontend-and-downloads)
- [Swap a handler without replacing routes](#swap-a-handler-without-replacing-routes)
- [6. Enable security and other protocols](#6-enable-security-and-other-protocols)
- [7. Handle failures and shutdown](#7-handle-failures-and-shutdown)
- [Troubleshooting and next steps](#troubleshooting-and-next-steps)
- [Sendfile and upload are different](#sendfile-and-upload-are-different)

## 1. Install and prepare

These instructions use the exact repository archives selected by
[`package-pins.json`](../scripts/package-pins.json), currently the
`2026-10-08-r3` train. HTTP Runtime is **not installed from npm, PyPI,
Maven Central or a Go module tag** in this guide. CoAkka Runtime's registry
packages are a different product.

If you do not already have the two public checkouts, clone them side by side
in a workspace with enough storage:

```sh
git clone https://github.com/phuong-tran/coakka-samples.git
git clone https://github.com/phuong-tran/coakka-publish.git
cd coakka-samples
export COAKKA_PUBLISH_ROOT="$(cd ../coakka-publish && pwd)"
export COAKKA_HTTP_SAMPLE_WORK_ROOT=/absolute/path/to/build-volume/http-samples
```

Replace the build-volume path with a dedicated writable directory before running
anything. All commands below start in the **coakka-samples repository root**.
For existing checkouts, set the same two variables to their actual paths;
do not clone over an existing directory. Re-export them in each new terminal.

The runner selects the current host's archive, verifies its pinned SHA-256,
extracts it under the work directory, and verifies the extracted files before
reuse. It does not build Core or the connector. Build tools may download their
own dependencies. Do not bypass a checksum failure or mix native files from
different archives.

These shell commands support macOS ARM64 and Linux ARM64/x86-64. Windows
package use is documented in the linked package guide; these POSIX commands
are not a Windows walkthrough. Package availability and matching-host execution
are separate facts; see [coverage](../FEATURES.md#final-r3-package-checkpoint).

**Tools for this lane:** CPython 3.11+ with `venv`, and curl. Security recipes also need OpenSSL CLI. No native compiler is required to load the paired Python archive.

The runner creates a virtual environment under the work directory and adds the verified archive's `python/` to `PYTHONPATH`; the module selects its paired native image. It does not install CoAkka from PyPI. `check` installs pinned Ruff/mypy tools. For an existing application, retain the extracted bundle layout and set `PYTHONPATH` as shown in the package guide; do not copy only the Python files.

[Package installation and platform details](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/python/README.md).

## 2. Run your first server

```sh
bash coakka-http-runtime/python/run.sh check
bash coakka-http-runtime/python/run.sh smoke
bash coakka-http-runtime/python/run.sh run
```

Run the commands sequentially. `check` checks/builds the sample; `smoke`
runs finite checks and exits. `run` keeps the server alive. Wait for the
`python-sample=http://127.0.0.1:...` line, then in another terminal:

```sh
PORT=12345 # replace with the printed port
curl --max-time 5 -i -H 'x-sample-caller: smoke' \
  "http://127.0.0.1:$PORT/hello/reader?title=hello"
```

Expect HTTP 200 and `hello reader from smoke`. Stop the server with Ctrl-C
when finished, and let its shutdown path complete. A startup message is not
a substitute for the actual HTTP request.

## 3. Read and adapt the application

Begin with `main`, `create_service` and the named handlers in [main.py](main.py). Control, timeout and TLS examples are split into [control.py](control.py), [adverse.py](adverse.py) and [security.py](security.py). Import application types from `coakka_http`.

```python
# Inside the existing Builder chain, before .start().
.post("/echo", lambda request: Response(status=201, body=request.body))
```

This is an excerpt from the complete application, not a Python file to run by itself. Handlers return typed response values; they are not ASGI callables. Preserve the main-thread signal/Event wait and the checked `finally` cleanup when replacing sample handlers.

For a first integration, keep the listener, one route and the lifecycle code.
Add features one at a time; you do not need the demonstration upstream, monitor,
static mount or test assertions in every application. Re-run the request above
after changing a route. Keep business validation, authorization and data codecs
in your handlers; protocol parsing and effective runtime settings stay in Core.

| Feature | Where to adapt the source |
| --- | --- |
| Streaming | `upload_response` reads within 64 KiB; `streaming_response` writes bounded chunks and returns trailers. |
| SSE / WebSocket | `event_response` and `socket_response`; use typed events and propagate producer failures. |
| Outbound HTTP | The logical-target startup request; match call identity and inspect `OutboundReason`, not exception text or status alone. |
| Files | `StaticMount` and `FileResponse` use a declared `FileAuthority`. |

## Request and response in Python

Read the [URL grammar and glossary](../glossary.md). Register
`.get("/hello/{name}", handler)` or `.post("/echo", handler)`;
the pattern is not a full URL and does not include query keys.

| Input | Python projection |
| --- | --- |
| Captured path | `request.path_parameters`; `encoded_value` remains percent-encoded. |
| Query | `request.query_parameters`; `encoded_key`, `encoded_value`, `has_value` preserve order and presence. |
| Header | `request.headers.get("x-sample-caller", "")` in the hello example. |
| Buffered body | `request.body` bytes. |
| Streamed body | `request.body_reader`; use bounded reads and inspect `request.trailers` after EOF. |

For example, this handler uses the same public values as `main.py`:

```python
from coakka_http import Header, Headers, Request, Response

def echo_binary(request: Request) -> Response:
    return Response(
        status=201,
        headers=Headers((Header("content-type", "application/octet-stream"),)),
        body=request.body,
    )
```

Register it with `.post("/echo", echo_binary)` in the existing builder.
Use `Response.text(...)` for text. For JSON, serialize with `json.dumps`,
encode to UTF-8, set `application/json`, and keep the resulting byte size
bounded. Returning a dict is not an implicit JSON response contract.

**Outbound request construction:** the startup recipe declares a target and calls
`service.outbound_submit(OutboundRequest(OUTBOUND_TARGET, "GET", "/source",
timeout_ms=3_000))`. It then reads `service.take_outbound(5_000)`, rejects
a missing terminal, matches `terminal.call`, and checks
`OutboundReason.RESPONSE` before status/body. See `demonstrate_outbound`
in [main.py](main.py) for the complete sequence. The reader wait is not the
call's deadline, and HTTP404 is not a transport failure.

## 4. Try the HTTP features

Keep the server from step 2 running. Use its printed port, not a guessed default.
These requests use ordinary HTTP clients; CoAkka owns the server transport.

```sh
curl --max-time 5 -i --data-binary 'payload' "http://127.0.0.1:$PORT/echo"
curl --max-time 5 -i --data-binary 'streamed' "http://127.0.0.1:$PORT/upload"
curl --max-time 5 --raw -i "http://127.0.0.1:$PORT/stream"
curl --max-time 5 -N "http://127.0.0.1:$PORT/events"
curl --max-time 5 -i "http://127.0.0.1:$PORT/download"
curl --max-time 5 -i -H 'Range: bytes=0-3' "http://127.0.0.1:$PORT/download"
curl --max-time 5 -H 'Accept: text/html' "http://127.0.0.1:$PORT/app/client/route"
curl --max-time 5 -i "http://127.0.0.1:$PORT/version"
```

| Request | Expected observation | What to preserve in your app |
| --- | --- | --- |
| POST `/echo` | 201 and the same bytes | Choose an explicit body ceiling for buffered business data. |
| POST `/upload` | 200 and the same bytes | This sample collects at most 64 KiB; consume bounded chunks for larger uploads. Read trailers after EOF. |
| GET `/stream` | `stream-ready`, final `x-stream-end: done` trailer | Honor writer pressure; do not accumulate an unbounded producer queue. |
| GET `/events` | A finite `state` event with multiline data, ID and retry | This example ends; implement a bounded, cancellable producer for a long-lived feed. |
| GET `/download` | Confined sample file; range request returns 206 | Grant only an application-controlled root. Never turn an incoming path into an arbitrary OS path. |
| GET `/app/client/route` | Static index through SPA navigation fallback | SPA fallback is an explicit app choice, not business routing. |
| GET `/version` | 201 and `v2` | The startup recipe already activated the new handler. |

For WebSocket, connect to `ws://127.0.0.1:$PORT/socket` with subprotocol
`coakka.sample` using a WebSocket client, then send a text or binary message.
The sample echoes it. Plain `curl GET` is not a WebSocket handshake.
Use `smoke` for the lane's automated checks and the
[feature index](../FEATURES.md) for the exact wire/adverse coverage.

Static/file responses also support validators: copy the returned `ETag` into
`If-None-Match` to observe a bodyless 304. An out-of-file range returns 416.
Core owns these protocol decisions.

## Static files, frontend and downloads

Use a static mount when Core should serve assets directly. For an SPA, set an
index and a navigation fallback. Keep APIs outside that prefix, for example
`/api/hello/{name}` beside `/app`. The runtime serves the frontend's **built
output**; the frontend toolchain still owns building it and setting an asset
base such as `/app/`. A dev server and its live reload are separate tools.

```python
# Inside create_service: root is a trusted absolute directory.
.static_mount(StaticMount(
    "/app", root,
    index_file="index.html",
    spa_fallback_file="index.html",
))
.file_authority(FileAuthority(82, root, 1 << 20, 2))
.get("/download", lambda _request: FileResponse(
    82, "/sample.txt",
    headers=Headers((Header("content-type", "text/plain"),)),
))
```

The builder excerpt uses imports from `main.py`; the swap excerpt is its `activate_replacement` operation and must replace that startup call, not run a second time after it. `FileAuthority` takes max file bytes before active-file count in Python; do not copy Kotlin's positional order.

A file authority grants only its declared root. Returning a file from a handler
lets the application perform authorization before choosing a relative path;
a static mount is directly served and must contain only files meant for public
access. Do not put secrets in the mount and expect an API handler to protect it.
To serve static files without SPA fallback, omit the fallback declaration.

With the main sample running, verify:

```sh
curl --max-time 5 -i "http://127.0.0.1:$PORT/app/sample.txt"
curl --max-time 5 -H 'Accept: text/html' "http://127.0.0.1:$PORT/app/client/route"
curl --max-time 5 -i -H 'Range: bytes=0-3' "http://127.0.0.1:$PORT/download"
```

Expect the asset, the index document for HTML navigation, and HTTP206 with four
bytes respectively. This does not install an authentication policy or a
frontend development server. File snapshot/reload and deployment changes follow
the package contract; do not assume a production directory behaves like hot
module reload.

## Sendfile and upload are different

The file-response recipe above gives Core a confined file reference instead of
reading the entire file into a host-owned response buffer. For eligible
plaintext HTTP/1.1 delivery, Core can use sendfile to avoid the application
buffer round trip. Other transports, including TLS, use their appropriate
bounded delivery path; your handler keeps the same file-response API.

This is **server-to-client download**, not client-to-server upload. Uploads
still need bounded request consumption and application-owned validation and
storage policy. Sendfile does not parse multipart or save incoming files.
Do not assume every file response is zero-copy or turn off TLS to choose a
fast path. See [file delivery, sendfile and uploads](../file-delivery.md) for
the motivation, protocol conditions and responsibilities.

## Swap a handler without replacing routes

Start by registering `GET /version` with a handler returning `v1`.
Prepare the replacement before publication, read the current Core snapshot,
then apply using the observed route generation and binding revision:

```python
# Reuse the existing feature sample's service; /version is route 8.
snapshot = service.routes
version = next(route for route in snapshot.routes if route.route_id == 8)
service.prepare_handler(100, lambda _request: Response.text("v2", status=201))
outcome = service.rebind_handler(RouteRebind(
    1, snapshot.identity.route_generation, version.route_id,
    version.binding_revision, 100,
))
if outcome.code is not RouteRebindCode.APPLIED or not outcome.changed:
    raise RuntimeError(f"handler switch refused: {outcome.code.name}")
```

This runs on application control flow, not inside every HTTP request. Binding
and activation IDs are application-chosen identities; generations/revisions
come from Core. Reserve enough handler capacity before startup for staged and
draining bindings. The feature sample already reserves that capacity.

On APPLIED, later admissions use the new binding; previously captured work
retains its old binding. Refusal leaves effective state unchanged. A stale
result calls for rereading state and deciding whether the original intent is
still wanted, not blind retries with incremented numbers. Keep old application
state alive until its users retire.

Try `GET /version` before and after activation to observe `v1` then `v2`.
The complete feature sample performs activation **before** readiness, so its
normal `run` command already returns `v2`. Handler swap keeps the route
pattern unchanged; adding/removing routes requires complete route publication
as demonstrated separately in the source.

## 5. Configure and observe the service

Read [control.py](control.py). Use `runtime_info()`, `effective_limits()` and `service.routes` for accepted state. The `--compression` sample option enables buffered GZIP; `smoke` exercises it. Native CPU placement cannot remove the Python GIL and must not be reported as Python parallelism.

```sh
bash coakka-http-runtime/python/run.sh tuning-smoke
```

Core owns effective configuration, capability probes, routing, transport
deadlines and causal failures. The connector submits intent and projects facts.
Start with omitted tuning; select CPU AUTO/SINGLE only when the deployment
needs it. Loop count is connector tuning, **not an application setting**.
CPU placement is not a process-wide quota or a promise of parallel handlers.

Batch profiles and transport deadlines are startup settings in these examples.
Monitoring alone has a demonstrated generation-checked live policy: reserve
bounded storage before start, pull the effective generation, apply, check the
returned state. Monitoring is disabled by default; the feature sample explicitly
enables it. Do not infer a capability or CPU count from an input option.

Keep header/body/keep-alive/idle deadlines separate from application-handler
deadlines and outbound call deadlines. A transport timeout cannot forcibly stop
application work. Release application resources separately, and never retry
business operations solely because an HTTP status or elapsed time suggests it.

Handler replacement prepares a binding before activation and uses the current
revision. Structural route publication replaces the complete route set against
the observed generation. Read the runtime snapshot, not the local handler list;
a stale update must leave the accepted state intact.

The optional inspection application is separate. Follow the
[Inspect guide](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/inspect/README.md)
to enable target reads and connect it; these samples do not automatically start
an inspector or expose a hidden documentation endpoint.

## 6. Enable security and other protocols

First run the finite TLS/mTLS recipe:

```sh
bash coakka-http-runtime/python/run.sh security-smoke
```

It generates development-only certificates under the work directory and checks
verified TLS plus rejection of a mutual-TLS client without an identity. The
security source also demonstrates runtime-owned outbound TLS/mTLS. Do not
disable peer verification or deploy the generated private keys.

For a secure HTTP/2 or HTTP/3 listener, run one of these at a time:

```sh
bash coakka-http-runtime/python/run.sh http2
# Stop it before trying the other listener:
bash coakka-http-runtime/python/run.sh http3
```

Use the newly printed port and a curl build supporting the selected protocol:

```sh
curl --http2 --max-time 5 \
  --cacert "$COAKKA_HTTP_SAMPLE_WORK_ROOT/tls/identities/ca.pem" \
  "https://localhost:$PORT/protocol"
# For the HTTP/3 listener, replace --http2 with --http3-only.
```

Expect `protocol-ready`. If curl lacks protocol support, use a capable client;
do not describe that as a server failure. Keep peers progressing during drain.

## 7. Handle failures and shutdown

```sh
bash coakka-http-runtime/python/run.sh adverse-smoke
```

This isolated finite recipe holds application work to test handler expiry,
pressure and outbound cancellation/deadline followed by reuse. Do not copy its
deliberately blocked handlers into production. HTTP 503 alone does not tell you
which admission owner refused work; inspect typed outcomes where available.

The signal handler tells the main thread to stop; it does not destroy native state itself. The sample attempts both closes in reverse order and retains independent failures in an exception group. A timeout cannot kill a Python callable; release held application work before retrying retained cleanup.

## Troubleshooting and next steps

| Symptom | Check first |
| --- | --- |
| Required environment variable missing | Set both paths in step 1 in this terminal. |
| Archive missing or checksum mismatch | Confirm the warehouse has the exact pinned train; do not substitute a different library. |
| Library load failure | Match process architecture and keep the bundle's native files together. |
| Connection refused | Wait for readiness and use the current process's printed port. |
| TLS verification failure | Use the generated CA and `localhost`; mTLS also requires the client identity. |
| Shutdown refuses or times out | Finish/cancel application work, retain ownership, inspect the typed failure and retry cleanup safely. |

See [README](README.md) for this lane's evidence and benchmark table,
[feature coverage](../FEATURES.md) for known limits, and
[benchmark methodology](../benchmark-rpi5/README.md) for the separate performance
workflow. No benchmark or new platform qualification is implied by this guide.
After all sample processes exit, remove only the dedicated work directory you
created to clean generated builds, dependencies and test credentials.
