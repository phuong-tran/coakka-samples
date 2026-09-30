# C Sample

The C11 lane uses only the installed host header and shared library. One
connector thread owns the inbound event lane and releases every borrowed event
after completing or observing it.

## What It Shows

- buffered echo through the host-inlined request lane;
- static frontend files with SPA fallback;
- a file response confined to a declared authority root;
- health, fresh liveness, and a bounded monitoring page;
- a generation-checked handler activation;
- default platform I/O and explicit `--io-uring` preference reporting;
- signal-aware, ordered stop and destroy.

`security.c` declares TLS or mutual TLS, credential identity and generation,
certificate/key paths, optional client trust, and HTTP/1.1, HTTP/2, or HTTP/3.
The automated smoke uses HTTP/1.1 and verifies that mutual TLS rejects a client
without an identity.

`io_uring` is off unless `--io-uring` is present. The sample only forwards
that preference. Native startup checks whether the backend is usable and falls
back to `epoll` when it is not. Requested, effective, and fallback states are
reported separately.

## Run

```bash
bash coakka-http-runtime/c/run.sh check
bash coakka-http-runtime/c/run.sh smoke
bash coakka-http-runtime/c/run.sh security-smoke
bash coakka-http-runtime/c/run.sh run
```

The CMake project resolves `CoAkkaHttpHost 1.0.0 EXACT` from the externally
built source candidate. Builds are C11 with strict warnings enabled.

## Ownership Rules

- Event views are borrowed only until `coakka_http_host_release_request`.
- Exactly one connector thread reads the inbound event lane.
- Response values and file paths are copied by a successful completion call.
- Every created service is drained and stopped before destroy.
