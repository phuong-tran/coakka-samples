# Go: installation and integration

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

- [Inspect, metadata and OpenAPI](#inspect-metadata-and-openapi)

- [1. Install and prepare](#1-install-and-prepare)
- [2. Run your first server](#2-run-your-first-server)
- [3. Read and adapt the application](#3-read-and-adapt-the-application)
- [Request and response in Go](#request-and-response-in-go)
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

**Tools for this lane:** Go 1.23+, a C compiler with cgo enabled, Python 3.11+ for package resolution, and curl. Security recipes also need OpenSSL CLI. Use the archive for the Go process architecture.

The runner stages `go.mod` and adds a local `replace` pointing to the verified archive's `go/` module. It selects the paired native include/library directories for cgo and keeps Go caches under the work directory. For your own module, use the package guide's `go mod edit` and cgo settings; keep the local replacement in your deployment/build configuration rather than publishing a developer path.

[Package installation and platform details](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/go/README.md).

## 2. Run your first server

```sh
bash coakka-http-runtime/go/run.sh check
bash coakka-http-runtime/go/run.sh smoke
bash coakka-http-runtime/go/run.sh run
```

Run the commands sequentially. `check` checks/builds the sample; `smoke`
runs finite checks and exits. `run` keeps the server alive. Wait for the
`go-sample=http://127.0.0.1:...` line, then in another terminal:

```sh
PORT=12345 # replace with the printed port
curl --max-time 5 -i -H 'x-sample-caller: smoke' \
  "http://127.0.0.1:$PORT/hello/reader?title=hello"
```

Expect HTTP 200 and `hello reader from smoke`. Stop the server with Ctrl-C
when finished, and let its shutdown path complete. A startup message is not
a substitute for the actual HTTP request.

## 3. Read and adapt the application

Read `run`, `newService`, and the handler declarations in [main.go](main.go); use [control.go](control.go), [adverse.go](adverse.go) and [security.go](security.go) for focused recipes. The import is `github.com/phuong-tran/coakka-http-runtime-go`, aliased as `coakkahttp`.

```go
// Inside the existing builder chain, before Start().
Post("/echo", func(request *coakkahttp.Request) (coakkahttp.Response, error) {
    return coakkahttp.Bytes(201, request.Bytes())
}).
```

This is a builder-chain excerpt, not a standalone Go file. Keep `Start()` error handling, port lookup, signal waiting and checked `Close()` from the complete sample. Ordinary handlers return `(Response, error)`; this is not a `net/http.ResponseWriter` adapter.

For a first integration, keep the listener, one route and the lifecycle code.
Add features one at a time; you do not need the demonstration upstream, monitor,
static mount or test assertions in every application. Re-run the request above
after changing a route. Keep business validation, authorization and data codecs
in your handlers; protocol parsing and effective runtime settings stay in Core.

| Feature | Where to adapt the source |
| --- | --- |
| Streaming | `PostStream`, the bounded reader in `main.go`, and response/SSE producers; propagate read/write errors. |
| WebSocket | `NewWebSocketResponse` and its typed callback; no unbounded frame queue. |
| Outbound HTTP | The startup logical-target example in `main.go`; match the complete call identity and inspect the typed terminal. HTTP 404 is still a received response. |
| Files | `StaticMount`, `FileAuthority`, and `ServeFile`; authority ID and relative path are separate. |

## Request and response in Go

Read the [URL grammar and glossary](../glossary.md) first. A route pattern
is `/hello/{name}`, not a full URL or `/hello/:name`. Register the
method with `Get`/`Post`; the builder associates the handler with that route.

| Input | Go projection |
| --- | --- |
| Captured path | `request.PathParameters`; entries contain `EncodedValue`. |
| Query | `request.QueryParameters`; `EncodedKey`, `EncodedValue`, `HasValue` preserve order and presence. |
| Header | `request.Headers.Get("x-sample-caller")` returns value and presence; `GetAll` preserves duplicates. |
| Buffered body | `request.Bytes()`. |
| Streamed body | `request.Body`; handle read failure, enforce a byte ceiling, inspect `request.Trailers` after EOF. |

The request is supplied by Core through the connector. For a custom binary
response, this is a handler-body excerpt using `request` and the imports
from `main.go`:

```go
header, err := coakkahttp.NewHeader("content-type", "application/octet-stream")
if err != nil {
    return coakkahttp.Response{}, err
}
headers, err := coakkahttp.NewHeaders(header)
if err != nil {
    return coakkahttp.Response{}, err
}
return coakkahttp.NewResponse(201, headers, request.Bytes())
```

For text use `coakkahttp.Text(status, text)`; for JSON serialize with
`encoding/json`, handle serialization failure, and pass the bytes with
`application/json`. The existing `parameterHandler` shows that sequence.
Do not assemble HTTP framing manually.

**Outbound request construction:** after declaring the upstream target, the
existing `demonstrateOutbound` submits `coakkahttp.ClientRequest` with
`LogicalTarget: outboundTarget`, `Method: "GET"`, `Target: "/source"`
and `TimeoutMillis: 3000`. It checks submit errors, calls
`TakeOutbound(5 * time.Second)`, matches `terminal.Call` to the returned
call and checks `OutboundResponse` before using status/body. Copy that
complete lifecycle, not only the request struct. The target path contains
actual encoded values, never `{name}` placeholders.

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

```go
// Inside newService: root is an application-controlled absolute asset path.
StaticMount(coakkahttp.StaticMount{
    URLPrefix: "/app", RootPath: root,
    IndexFile: "index.html", HasIndexFile: true,
    SPAFallbackFile: "index.html", HasSPAFallbackFile: true,
}).
FileAuthority(coakkahttp.FileAuthority{
    ID: 82, RootPath: root, MaxActiveFiles: 2, MaxFileBytes: 1 << 20,
}).
Get("/download", func(_ *coakkahttp.Request) (coakkahttp.Response, error) {
    return coakkahttp.ServeFile(200, textHeaders, 82, "/sample.txt")
}).
```

These are builder/control excerpts using the imports and checked lifecycle in `main.go`. `textHeaders` is the checked `NewHeaders` value created there. The complete existing feature sample registers `/version` as route 8; the small standalone ordering above uses route 1 deliberately.

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

```go
// Control-plane excerpt inside a function returning error.
// service is fresh; /version was the first registered route (ID 1).
before, err := service.Routes()
if err != nil { return err }
if len(before.Routes) == 0 || before.Routes[0].RouteID != 1 {
    return errors.New("version route missing")
}
version := before.Routes[0]
if err := service.PrepareHandler(100, func(_ *coakkahttp.Request) (coakkahttp.Response, error) {
    return coakkahttp.Text(200, "v2")
}); err != nil { return err }
outcome, err := service.RebindHandler(coakkahttp.RebindRequest{
    ActivationID: 1, ExpectedRouteGeneration: before.RouteGeneration,
    RouteID: version.RouteID, ExpectedBindingRevision: version.BindingRevision,
    NewHandlerBindingID: 100,
})
if err != nil { return err }
if outcome.Code != coakkahttp.RebindApplied || !outcome.Changed {
    return fmt.Errorf("handler switch refused: %+v", outcome)
}
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

Read [control.go](control.go). `RuntimeInfo().CPU`, `.Limits` and `.Execution` are runtime-issued observations. `Service.Routes()` returns the coherent route snapshot; inspect errors instead of retaining an assumed success. Compression is demonstrated by `Builder.Compression` and the runner's `smoke`/`tuning-smoke` commands; it transforms eligible buffered responses, not SSE or streams.

```sh
bash coakka-http-runtime/go/run.sh tuning-smoke
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
bash coakka-http-runtime/go/run.sh security-smoke
```

It generates development-only certificates under the work directory and checks
verified TLS plus rejection of a mutual-TLS client without an identity. The
security source also demonstrates runtime-owned outbound TLS/mTLS. Do not
disable peer verification or deploy the generated private keys.

For a secure HTTP/2 or HTTP/3 listener, run one of these at a time:

```sh
bash coakka-http-runtime/go/run.sh http2
# Stop it before trying the other listener:
bash coakka-http-runtime/go/run.sh http3
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
bash coakka-http-runtime/go/run.sh adverse-smoke
```

This isolated finite recipe holds application work to test handler expiry,
pressure and outbound cancellation/deadline followed by reuse. Do not copy its
deliberately blocked handlers into production. HTTP 503 alone does not tell you
which admission owner refused work; inspect typed outcomes where available.

The sample uses `signal.NotifyContext` and retains close errors alongside earlier errors with `errors.Join`. Close the outbound owner before its upstream. On a retained close failure, keep the owner alive and finish/cancel application work before retrying cleanup; never restart that instance.

## Inspect, metadata and OpenAPI

The optional standalone Inspect application shows an explicitly enabled
target's route/schema snapshots and exports OpenAPI 3.0.3. It does not infer
business schemas from handlers, types or traffic. These language samples do
not currently enable the inspection target or declare route API metadata.
Snapshot APIs alone are not metadata-publication APIs.

Read [Inspect, metadata and Swagger UI](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/inspect/metadata-and-openapi.md)
for its purpose, connection settings, metadata checklist, export semantics
and the current public-package integration path. Do not copy private schema
or imagined builder calls into your application.

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
