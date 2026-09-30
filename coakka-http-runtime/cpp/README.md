# C++ Sample

The C++20 lane wraps the host-inlined public C host API with a small RAII
owner. Exceptions stay in foreground control flow and never cross the inbound
reader thread.

## What It Shows

- ordinary text routes, static frontend delivery, and a confined file response;
- health, liveness, and bounded monitoring reads;
- generation-checked handler activation;
- default platform I/O and explicit `--io-uring` preference reporting;
- reader error recording through atomics;
- explicit close for reportable errors plus a no-throw destructor fallback;
- signal-aware shutdown.

`security.cpp` is a separate RAII service for TLS and mutual TLS. It supports
HTTP/1.1, HTTP/2, and HTTP/3 listener selection. The automated smoke verifies
the server chain, verifies the client chain, and proves that an unidentified
client cannot enter a mutual-TLS listener.

`io_uring` is off unless `--io-uring` is present. The sample only forwards
that preference. Native startup checks whether the backend is usable and falls
back to `epoll` when it is not. Requested, effective, and fallback states are
reported separately.

## Run

```bash
bash coakka-http-runtime/cpp/run.sh check
bash coakka-http-runtime/cpp/run.sh smoke
bash coakka-http-runtime/cpp/run.sh security-smoke
bash coakka-http-runtime/cpp/run.sh run
```

The CMake project resolves `CoAkkaHttpHost 1.0.0 EXACT` and compiles as C++20
with strict warnings enabled.
