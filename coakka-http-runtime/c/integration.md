# C11: installation and integration

Start here for a native CoAkka HTTP Runtime application. Use the complete
artifact-backed sample before adapting callbacks or resource ownership.
C and C++ share the installed C contract; all fallible operations return explicit results.

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
- [3. Adapt the native application](#3-adapt-the-native-application)
- [Native request and response values](#native-request-and-response-values)
- [URL grammar and glossary](../glossary.md)
- [4. Try upload, files and WebSocket](#4-try-upload-files-and-websocket)
- [Static files and an SPA frontend](#static-files-and-an-spa-frontend)
- [Swap a native handler](#swap-a-native-handler)
- [5. Add response streaming and SSE](#5-add-response-streaming-and-sse)
- [6. Make outbound requests and update routes](#6-make-outbound-requests-and-update-routes)
- [7. Configure, observe and secure the server](#7-configure-observe-and-secure-the-server)
- [8. Handle failures and shutdown](#8-handle-failures-and-shutdown)
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

**Tools for this lane:** a C11 compiler, CMake 3.24+,
Python 3.11+, curl and OpenSSL CLI for the security recipe.
The runner builds only the sample using the native package selected by the pins.

For your own application, follow the
[native package installation guide](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/native/README.md)
and [sample CMakeLists.txt](CMakeLists.txt). Use the package's exported CMake
target and matching installed headers rather than including development-tree
headers or selecting a library from another connector bundle. These instructions
describe the pinned r3 package, not a newer development API.

## 2. Run your first server

```sh
bash coakka-http-runtime/c/run.sh check
bash coakka-http-runtime/c/run.sh smoke
bash coakka-http-runtime/c/run.sh run
```

Run sequentially. `check` compiles; `smoke` runs finite assertions and
exits; `run` remains alive. Wait for `native-sample-port=`, then use the
printed port in a second terminal:

```sh
PORT=12345 # replace with the printed port
curl --max-time 5 -i --data-binary 'payload' "http://127.0.0.1:$PORT/echo"
curl --max-time 5 -i "http://127.0.0.1:$PORT/items/a%2Fb?tag=one&flag&tag=&tag=two"
```

Echo returns HTTP 201 with `payload`. The parameter response preserves
`a%2Fb`, reports `x-query-count: 4` and `x-query-with-value: 3`.
An encoded slash is not automatically decoded into a path separator.
The runtime has already parsed these values; use its accessors instead of
splitting the raw target or losing duplicate/absent query values.

## 3. Adapt the native application

Read [server.c](server.c) in this order:

1. The result-checking helper and callback handlers.
2. Initialized options and route declarations in the startup owner.
3. Create/start, followed by port and runtime-info queries.
4. The signal-driven owner loop.
5. Stop/destroy and the failure cleanup path.

The shared [sample options](../native/sample_options.h) helper contains
application CLI and file declarations; it is not an installed connector API.
For a first application, retain the explicit lifecycle and one echo handler,
then add the other declarations only when needed.

Initialize every public options/value structure with its initializer, and check every returned result before reading output. On create failure, inspect whether an owner was returned for destruction; failure does not imply a NULL owner. Keep callback state alive until destruction actually succeeds.

Request views are borrowed. Do not retain request/body/header/parameter pointers
after their documented lifetime. Indexed parameter access is O(1); walking
all parameters is O(n). Copy only data the application genuinely needs after
the callback, within an explicit bound.

## Native request and response values

Read the [URL grammar and glossary](../glossary.md). Initialize a route,
set `method` to `GET` and `path` to `/items/{id}`, then attach its
callback and application context as shown in `server.c`.
The route path is not a full URL or a query expression.

| Value | Public sample operation |
| --- | --- |
| Matched route | `coakka_http_request_route_id(request)`. |
| Path capture | `coakka_http_request_path_parameter(request, index, &path)`; check success before reading `path.encoded_value`. |
| Query entry | Count and indexed `coakka_http_request_query_parameter`; preserve `has_value`, order and duplicates. |
| Buffered body | `coakka_http_request_body(request)` returns borrowed bytes. |
| Streamed body | `coakka_http_request_body_read` returns typed DATA/TRAILERS/END/cancellation observations. |
| Response | Initialize `coakka_http_response_t`, set `status_code`, `headers`, `header_count`, `body`, then submit and check its result. |

Inside the existing callback, this response excerpt uses the sample's
`sample_bytes` utility (a string-to-byte-view helper, not an installed API):

```c
coakka_http_response_t response;
coakka_http_header_t header;
coakka_http_response_init(&response);
header.name = sample_bytes("content-type");
header.value = sample_bytes("application/octet-stream");
response.status_code = 201;
response.headers = &header;
response.header_count = 1;
response.body = coakka_http_request_body(request);
(void)check(coakka_http_request_respond(request, &response), "echo response");
```

Use the existing checked-result helper.
The accepted buffered response copies the selected bytes before local storage
expires. Keep all views valid until the call returns; do not assume ownership
of incoming buffers. JSON encoding is application work: serialize bounded
bytes and set `application/json`, rather than treating a C struct as wire JSON.

For an outbound request, [outbound.c](outbound.c)
initializes `coakka_http_client_request_t`, sets `logical_target` to
`sample.upstream`, `method` to `GET`, `target` to an actual origin-form
path such as `/source`, and `timeout_ms` to 3000. Follow submit, finite
terminal take, full call-identity matching, typed reason/status handling and
exact terminal release. The logical target must already have an endpoint
declaration; the path field is not a place for a route template or arbitrary URL.

## 4. Try upload, files and WebSocket

With the ordinary server still running:

```sh
curl --max-time 5 -i --data-binary 'hello' "http://127.0.0.1:$PORT/upload"
curl --max-time 5 -i "http://127.0.0.1:$PORT/download"
curl --max-time 5 -i -H 'Range: bytes=0-3' "http://127.0.0.1:$PORT/download"
curl --max-time 5 -H 'Accept: text/html' "http://127.0.0.1:$PORT/app/client/route"
curl --max-time 5 -i "http://127.0.0.1:$PORT/version"
```

| Feature | Expected result | Integration rule |
| --- | --- | --- |
| Upload | `bytes=5 trailers=0` | Native example counts chunks, not an unbounded body collector. Borrowed views expire at the next read. |
| File response | Sample file, or 206 for the range | Use an application-granted root and relative path. |
| Static SPA | Index file for HTML navigation | Keep SPA fallback an explicit application choice. |
| Handler activation | 200 and `v2` | The startup recipe has already prepared and activated the replacement. |
| WebSocket | Text/binary echo at `/socket` | Connect with a WebSocket client and subprotocol `coakka.sample`, not a plain HTTP GET. |

Reuse the download's `ETag` in `If-None-Match` for a bodyless 304.
Unsatisfiable ranges return 416. Core owns validator/range mechanics.
The main owner reads WebSocket events and releases each borrowed event exactly
once. Send pressure is handled explicitly, not by an unbounded retry queue.

## Static files and an SPA frontend

The following startup excerpt uses `sample_bytes` from the existing sample.
`assets` must name the trusted directory containing the built frontend.
Keep these configuration values alive through server creation, which copies
the declarations; do not publish a request-selected root.

```c
coakka_http_static_mount_t mount;
coakka_http_file_authority_t files;
coakka_http_static_mount_init(&mount);
mount.mount_id = 1;
mount.url_prefix = sample_bytes("/app");
mount.root_path = sample_bytes(assets);
mount.index_file = sample_bytes("index.html");
mount.has_index_file = 1;
mount.spa_fallback_file = sample_bytes("index.html");
mount.has_spa_fallback_file = 1;
options.static_mounts = &mount;
options.static_mount_count = 1;

coakka_http_file_authority_init(&files);
files.authority_id = 82;
files.root_path = sample_bytes(assets);
files.max_active_files = 2;
files.max_file_bytes = 1048576;
options.file_authorities = &files;
options.file_authority_count = 1;
```

`options` is an initialized `coakka_http_server_options_t`, as in
the full sample. For static files without SPA fallback, leave its fallback
fields at their initialized values. Use an API path such as `/api/items`
outside `/app`; build the frontend with the matching asset base `/app/`.
Core serves the built files; it does not run the frontend build or dev server.

A static mount serves files directly, so mount only public assets. A private
download should instead go through an authorized application handler, which
selects a file in the granted authority:

```c
coakka_http_file_response_t file;
coakka_http_file_response_init(&file);
file.authority_id = 82;
file.encoded_path = sample_bytes("/sample.txt");
(void)check(coakka_http_request_respond_file(request, &file), "file response");
```

This is a callback excerpt using the sample's checked-result helper.
It is not permission to pass arbitrary OS paths. Test `/app/sample.txt`,
`/app/client/route` with `Accept: text/html`, and `/download` with
`Range: bytes=0-3`; expect asset bytes, the SPA index, and HTTP206 respectively.

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

## Swap a native handler

The runnable server declares route ID3 for `/version` and an initial
`v1` context. This excerpt is its **fresh isolated startup** operation;
the generation/revision1 values are explicit preconditions, not a method
of discovering current state in a long-lived service:

```c
coakka_http_route_rebind_t change;
coakka_http_route_rebind_outcome_t outcome;
if (!check(coakka_http_server_prepare_handler(server, 10, (void *)"v2", handle), "prepare")) goto cleanup;
coakka_http_route_rebind_init(&change);
change.activation_id = 1;
change.expected_route_generation = 1;
change.route_id = 3;
change.expected_binding_revision = 1;
change.new_handler_binding_id = 10;
coakka_http_route_rebind_outcome_init(&outcome);
if (!check(coakka_http_server_rebind_handler(server, &change, 2000, &outcome), "rebind")) goto cleanup;
if (outcome.code != COAKKA_HTTP_ROUTE_REBIND_APPLIED) {
    atomic_store(&failed, 1);
    goto cleanup;
}
```

Use this inside the sample's startup owner with its existing `cleanup` label and failure recording.
Check both the operation result and typed apply outcome. Production control
must use Core-issued effective state for subsequent changes and handle stale or
ambiguous outcomes according to the installed contract; do not guess revision
increments or reissue with new IDs merely because a wait expired.

This changes the handler, not the path or method. Already captured requests keep
the old binding, so both callback contexts must remain valid until safely retired.
The full sample retains contexts through destruction and exercises stale-revision
refusal; `/version` still returns `v2`. It swaps before printing readiness.
Use [outbound.c](outbound.c) for a separate complete route-table
publication example.

## 5. Add response streaming and SSE

Stop the ordinary server, then start the separate producer example:

```sh
bash coakka-http-runtime/c/run.sh streaming
```

Read `native-stream-port=` and replace `PORT` with that new port:

```sh
curl --max-time 5 --raw -i "http://127.0.0.1:$PORT/stream"
curl --max-time 5 -N "http://127.0.0.1:$PORT/events"
```

Expect `stream-ready` plus final `x-stream-end: done` for the first
request and a finite multiline SSE event for the second.

Read [streaming.c](streaming.c) before moving this pattern into an
application. The callback chooses a response head and hands a writer to main,
then returns. Only afterward may the producer wait for writable credit;
waiting inside that callback would prevent head admission. One bounded slot
covers preparation, handoff and production; overlap receives HTTP 503. That
one-producer limit is sample policy, not the runtime's universal capacity.

Finishing consumes the writer only on success. Refusal retains it; local
release does not manufacture successful wire completion. Preserve finite
waits, error handling and shutdown ordering.

## 6. Make outbound requests and update routes

```sh
bash coakka-http-runtime/c/run.sh outbound
```

This finite example starts its own loopback upstream and client, submits requests
to logical target `sample.upstream`, checks response and 404 outcomes, then
closes both. It does not require the ordinary server from step 2.

Read [outbound.c](outbound.c): declare the target before startup,
submit with a finite deadline, match the complete returned call identity,
inspect the typed terminal, and release it exactly once. HTTP 404 remains a
received HTTP response, not a transport failure or automatic retry permission.

The same example publishes a complete replacement route set and checks replay
and stale-generation refusal. Handler-only replacement in `server.c`
changes a binding, not the structural route table. Do not republish the whole
table just to select another prepared handler.

## 7. Configure, observe and secure the server

Normal startup leaves tuning omitted. For the existing advanced example, after
`check`, run:

```sh
"$COAKKA_HTTP_SAMPLE_WORK_ROOT/c/build/coakka-http-c" \
  --assets "$PWD/coakka-http-runtime/assets" --cpu auto \
  --request-batch small --terminal-batch medium --compression gzip
```

Use its printed port to check eligible buffered compression:

```sh
curl --max-time 5 --compressed -i \
  "http://127.0.0.1:$PORT/items/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
```

These options are explicit demonstration values, not a required production
configuration. Core reports effective CPU placement and notification caps;
AUTO is not a process-wide CPU quota. There is no loop-count CLI here.
The convenience sample retains its platform I/O default and has no
`--io-uring` option. Do not copy another connector's CLI into it.

Startup also reads health/liveness and runs the
[monitor policy recipe](../native/monitor_example.h): apply within the startup
reservation, reject stale/oversized intent atomically, then restore the original
effective policy. Monitoring is optional; do not enable all retention for a
minimal server. Runtime-info refusal leaves output unchanged, not freshly valid.

For verified TLS/mTLS:

```sh
bash coakka-http-runtime/c/run.sh security-smoke
```

The runner generates development identities, verifies TLS and checks rejection
without a mutual-TLS client identity. Never deploy those keys. The native
executable also accepts explicit secure HTTP/2/HTTP/3 listener selection; see
[the native sample notes](README.md#run). This HTTP/1.1 security smoke is not
HTTP/2/3 wire qualification. Optional route/schema inspection has a separate
[Inspect guide](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/inspect/README.md);
it is not automatically served by these samples.

## 8. Handle failures and shutdown

Ctrl-C requests shutdown; wait for the process to finish. Stop admission,
release pending application producers/events, stop the server, then destroy it
before freeing callback state. Do not restart a stopped or failed owner.

Keep pressure, cancellation, deadline expiry, stale intent and closed ownership
distinct. A wait timeout does not prove drain completion or authorize freeing
live state. Transport expiry cannot forcibly stop business work.
The sample's explicit body/read/response/shutdown bounds are teaching choices;
select deployment budgets deliberately rather than removing bounds.

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
| Package or checksum failure | Use the exact pinned native archive; do not mix headers and libraries. |
| CMake cannot find the package | Use the runner, or the installation prefix from the package guide. |
| `/stream` is missing | Start the separate `streaming` executable and use its port. |
| TLS handshake fails | Use the generated CA and `localhost`; mTLS needs a client identity. |
| Stop/destroy refuses | Retain ownership, finish application work and inspect the typed result. |

See [README](README.md) for detailed contracts and benchmark observations,
[feature coverage](../FEATURES.md) for evidence boundaries, and
[benchmark methodology](../benchmark-rpi5/README.md) for performance work.
C++ uses the native C benchmark reference, not an independently measured C++
number. No new execution or sanitizer result is claimed by this guide.
After every sample has exited, clean only your dedicated work directory.
