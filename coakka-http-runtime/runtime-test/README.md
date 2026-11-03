# CoAkka HTTP Runtime native contract tests

This is reviewable consumer test source, not the runtime implementation. It
consumes only the installed `<coakka/http/host.h>` contract and the relocatable
`CoAkkaHttp::host` CMake target. C11 and C++20 callers use the same C ABI.
The 1.1.2 train uses host ABI10. Do not combine headers, metadata or a library
from different packages. Older archive receipts describe their own contracts.

## Build and run

Verify and extract one complete native SDK for the matching host, then run:

```sh
cmake -S coakka-http-runtime/runtime-test -B build/http-tests \
  -DCMAKE_PREFIX_PATH=/absolute/path/to/extracted-sdk \
  -DCMAKE_BUILD_TYPE=Release
cmake --build build/http-tests --config Release --parallel
ctest --test-dir build/http-tests -C Release --output-on-failure
```

CMake requires SDK version1.1.2 exactly. The explicit
`COAKKA_HTTP_TEST_SDK_VERSION` cache entry permits qualification of a different
complete SDK; such a run is evidence for that SDK, not a 1.1.2 release pass.
Configure verifies the complete source inventory before compiling. Its SHA256
manifest detects source drift; it is not a publisher signature.

## What the eleven tests prove

| Case | Invariant |
| --- | --- |
| runtime | Real loopback request, parsed path/query, synchronous input copies and exact response bytes |
| contract | Accepted Core settings, invalid output preservation, empty-read timeout, stop and idempotent disposal |
| ownership | Cross-service and retired tokens refused; drain/stop do not discharge a live borrowed lease |
| pressure | One retained admission, bounded refusal, invalid response does not consume reply ownership, recovery |
| outbound | Logical selection, copied intent, response correlation and cancellation terminal |
| routes | Coherent snapshot, captured old binding, revision conflict preserves state, exact activation replay |
| stream | Ordered request chunks/trailers/end, Core writable notification, bounded response stream and trailers |
| concurrency | Four clients, one event reader, 32 distinct replies and proven worker joins |
| execution | Absent/explicit/malformed construction intent, Core CPU facts, configured versus active loops |
| cpp | Standard-layout C records and checked C++ create/inspect/destroy |
| fixture_wire | Truncation, framing conflict, overflow and wrong-body/trailer rejection in the fixture assertion |

POSIX hosts run an additional white-box fixture test: refusal at each partial
thread-creation position must release and join every child actually created.
This helper test is not a separate runtime capability.

The socket helper owns nonblocking loopback descriptors, one monotonic 10-second
budget per operation, partial I/O and interruption handling, and a 16KiB output
ceiling. These are fixture limits, not duplicated runtime defaults. The thread
helper caps groups at32, publishes immutable inputs at creation, and reads
results only after joins. Unproven coordination/join aborts the fixture process
before freeing a possibly live child context. CTest supplies a 120-second
process watchdog; a portable join is not a hard realtime guarantee.

Exactly one reader owns each service's request-event lease. Bytes borrowed from
an event expire at release. Opaque exchange tokens are returned to their owning
service and never reconstructed. The application retains business-handler
ownership. A failed destroy keeps its pointer; successful drain is not proof
that borrowed memory can be freed. Controller CPU scope entry/restore uses
Core-issued results on the same thread, after all child owners retire.

## Instrumented fixture checks

Use separate build directories for `address`, `undefined`, and `thread`.
For example, add these flags to a supported Clang/GCC configuration:

```sh
-DCMAKE_C_FLAGS="-fsanitize=undefined -fno-omit-frame-pointer"
-DCMAKE_CXX_FLAGS="-fsanitize=undefined -fno-omit-frame-pointer"
-DCMAKE_EXE_LINKER_FLAGS=-fsanitize=undefined
```

Run with `UBSAN_OPTIONS=halt_on_error=1:print_stacktrace=1`. For address use
`ASAN_OPTIONS=halt_on_error=1:detect_leaks=1`; for thread use
`TSAN_OPTIONS=halt_on_error=1`. These instrument the consumer, not a prebuilt
production library. A tool that fails before execution is blocked evidence.
`analyze.sh /absolute/sdk/include` analyzes only the owned C fixture source.

Clang users may enable `COAKKA_HTTP_TEST_FUZZ=ON` and run
`http_fixture_fuzz -max_total_time=30 -max_len=16384` with a task-owned corpus
and artifact directory. This targets the fixture's narrow wire assertion; it
is not product parser fuzz evidence.

## Evidence boundaries

This self-contained suite exercises HTTP/1.1. Product qualification separately
owns TLS/mTLS, HTTP/2, HTTP/3, static files, monitoring, fault injection, stress,
soak, artifact/export/dependency checks and instrumented runtime builds. Removing
obsolete SDK callers does not transfer their historical evidence to this suite.
A passing test does not claim complete capability coverage or release readiness.

The required matching lanes are physical macOS ARM64, physical Raspberry Pi5
Linux ARM64, Linux x86_64, physical Windows x86_64, and Windows WSL2. Windows
ARM64 through UTM has a bounded correctness/compatibility lane. WSL consumes
the Linux x86_64 image, so there are five native artifacts. Comparative
throughput/latency is measured only on Pi5 with declared identity, thermal and
throttling admission. This harness is not a benchmark.

Common documentation is authored in `coakkaCoreNativeDev/public-docs` and
projected one way. Reviewed test code is owned by the HTTP Runtime repository;
its projector preserves the separately owned README and refuses unauthenticated
replacement or deletion of an older projected source file.
