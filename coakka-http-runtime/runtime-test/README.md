# CoAkka HTTP Runtime Installed-Package Tests

This public harness consumes the installed application package through
`<coakka/http/http.h>` and `CoAkkaHttp::runtime`. It contains test code only,
not the HTTP Runtime implementation. The same reviewed test source is projected
into `coakka-samples` and `coakka-publish`; public documentation has a separate
owner and is synchronized with those copies.

## Build and run

Verify the selected archive with the warehouse admission command, then extract
that archive into a new directory and pass its absolute path to CMake:

```sh
cmake -S coakka-http-runtime/runtime-test -B build/http-tests \
  -DCMAKE_PREFIX_PATH=/absolute/path/to/extracted-package \
  -DCOAKKA_HTTP_RUNTIME_TEST_EXPECT_TLS=ON \
  -DCOAKKA_HTTP_RUNTIME_TEST_EXPECT_OUTBOUND=ON
cmake --build build/http-tests --config Release --parallel
ctest --test-dir build/http-tests -C Release --output-on-failure
```

Configure verifies the exact source inventory before compilation. Eight tests
exercise C/C++ consumption, request/trailer delivery, configuration and effective
runtime information, request lifetime, concurrency, pressure, graceful shutdown
and outbound requests. A test without required outbound capability fails when
the option above is enabled; optional omission is a reported skip, never a pass.

## Instrumented consumer tests

Keep each sanitizer in its own build directory. On a supported Clang/GCC host,
for example:

```sh
cmake -S coakka-http-runtime/runtime-test -B build/http-asan \
  -DCMAKE_PREFIX_PATH=/absolute/path/to/extracted-package \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo \
  -DCMAKE_C_FLAGS="-fsanitize=address -fno-omit-frame-pointer" \
  -DCMAKE_CXX_FLAGS="-fsanitize=address -fno-omit-frame-pointer" \
  -DCMAKE_EXE_LINKER_FLAGS=-fsanitize=address \
  -DCOAKKA_HTTP_RUNTIME_TEST_EXPECT_OUTBOUND=ON
cmake --build build/http-asan --parallel
ASAN_OPTIONS=halt_on_error=1:detect_leaks=1 ctest --test-dir build/http-asan --output-on-failure
```

Use `undefined` with `UBSAN_OPTIONS=halt_on_error=1:print_stacktrace=1`, or
`thread` with `TSAN_OPTIONS=halt_on_error=1`, in separate configurations.
These flags instrument the consumer harness, not an ordinary production
library. Full-runtime sanitizer evidence belongs to the instrumented product
build. A tool failure before execution is unavailable evidence, not a pass.

## Verification scope

The package qualification also checks architecture, checksums, public exports,
dependencies, legal material, matching-host lifecycle and bounded failure.
The recorded full installed suite has nine tests, repeated three times, on
macOS ARM64, Linux ARM64/x64 and Windows ARM64/x64. Windows x64 execution uses
Windows ARM64 emulation, not physical x64 performance evidence.

The ninth test needs separate HTTP/2 and HTTP/3 client fixtures and test
identities supplied by the product qualification environment. Those fixtures
are not published here. Public CI runs the eight self-contained tests; it does
not claim to rerun the ninth or the complete product sanitizer matrix.
Benchmark results are a separate form of evidence.

Use `scripts/verify-http-runtime-release.sh --all-candidates` in the warehouse
to verify package bytes. Checksum admission never substitutes for execution.
