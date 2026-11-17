# Run the HTTP version samples

Use connector 2.3.1 or Core SDK 2.3.0 for these exercises.

Each sample serves `GET /hello` and returns `hello`. Start one mode,
run the matching curl command, then stop the sample before starting a
different mode. After curl completes, press Enter to close the C or C++
example.

| Mode | Address | Listener |
| --- | --- | --- |
| [`http1`](../../docs/coakka-http-runtime/guides/http-versions/http1.md) | `http://127.0.0.1:8080/hello` | HTTP/1.1, plain TCP |
| [`http2`](../../docs/coakka-http-runtime/guides/http-versions/http2.md) | `https://127.0.0.1:8443/hello` | HTTP/2, TLS over TCP |
| [`http3`](../../docs/coakka-http-runtime/guides/http-versions/http3.md) | `https://127.0.0.1:8443/hello` | HTTP/3, QUIC over UDP |
| [`multi`](../../docs/coakka-http-runtime/guides/http-versions/multi.md) | `https://127.0.0.1:8443/hello` | One HTTPS port for HTTP/1.1, HTTP/2 and HTTP/3 |

Start in the root of this repository. For HTTPS, generate short-lived
loopback credentials and keep the absolute directory for the launch commands:

```sh
bash coakka-http-runtime/scripts/generate-test-certificates.sh ./http-test-tls
certs="$PWD/http-test-tls"
cd coakka-http-runtime/protocol-listeners
```

For the plain HTTP/1.1 case, you may set `certs="$PWD/http-test-tls"` and
skip certificate generation. The sample reads certificates only for HTTPS.
In the commands below, change `http1` to `http2` or `http3` to run the
corresponding single-protocol example. The startup message prints the
address. Use the curl command in the mode's linked guide to check both
the status and the negotiated version. The Go, Node.js, Bun, Python, Java,
Kotlin, C# and Swift examples also accept `multi`. The C and C++ examples below
use the standalone API.

## Node.js or Bun

Install the connector from npm using the package manager for your runtime.
Both runtimes use [the same sample](server.mjs).

```sh
npm install --no-save coakka-http-runtime-node@2.3.1
node server.mjs http1 "$certs"
```

```sh
bun add coakka-http-runtime-bun@2.3.1
bun server.mjs multi "$certs"
```

## Python

Use CPython 3.11 or newer and install the wheel from PyPI.
[Python source](server.py)

```sh
python3 -m venv .venv
.venv/bin/python -m pip install coakka-http-runtime-python==2.3.1
.venv/bin/python server.py http1 "$certs"
```

## Go

Install the platform SDK 2.3.0 and set `sdk` to its absolute extracted
directory. Go resolves the 2.3.1 module from the module path in
[`go.mod`](go/go.mod). The following linker flags work on Linux and macOS.
Use the platform-specific instructions for Windows.
[Go installation guide](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/go/README.md)

```sh
cd go
CGO_ENABLED=1 CGO_CFLAGS="-I$sdk/include" \
  CGO_LDFLAGS="-L$sdk/lib -Wl,-rpath,$sdk/lib" go build -o server .
./server http1 "$certs"
```

## Java and Kotlin

Use Maven coordinate `io.github.phuong-tran.coakka:coakka-http-runtime-jvm:2.3.1`
and its matching native classifier.

Resolve the JVM 2.3.1 JAR and matching platform classifier ZIP from Maven
Central, using either Maven or Gradle. The connector requires Kotlin
stdlib 2.3.10 at runtime. Extract the ZIP and set `native_dir` to the
directory containing both native libraries. Set `connector_jar` and
`kotlin_stdlib` to the absolute paths of the JARs.
[JVM installation guide](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/jvm/README.md)

With JDK 17 or newer, run [the Java sample](Server.java):

```sh
java -Djava.library.path="$native_dir" \
  -cp "$connector_jar:$kotlin_stdlib" Server.java http1 "$certs"
```

With the Kotlin compiler on your PATH, compile and run
[the Kotlin sample](Server.kt):

```sh
kotlinc -jvm-target 17 -cp "$connector_jar:$kotlin_stdlib" Server.kt -d classes
java -Djava.library.path="$native_dir" \
  -cp "classes:$connector_jar:$kotlin_stdlib" ServerKt http1 "$certs"
```

These commands use Unix shell syntax. On Windows use `;` between JARs
and use the Python executable under `.venv\Scripts`.

## C and C++

Set `sdk` to the absolute directory of the extracted SDK 2.3.0.
CMake uses its installed C ABI. The C++ example compiles the same
listener and route with the C++ compiler.
[C source](server.c) · [C++ entry point](server.cpp)

```sh
cmake -S . -B build -DCMAKE_PREFIX_PATH="$sdk"
cmake --build build
./build/coakka-http-c http1 "$certs"
# Run the C++ executable separately.
./build/coakka-http-cpp http1 "$certs"
```

## C#

From the `protocol-listeners` directory, run the [C# project](csharp/) with
.NET 8 or later. The project restores `CoAkka.Http.Runtime` 2.3.1
from NuGet. Use one mode at a time.

```sh
dotnet run --project csharp -- http1
dotnet run --project csharp -- multi "$certs"
```

Use `http2 "$certs"` or `http3 "$certs"` for the corresponding standalone
listener. The example exits after serving one request in a standalone
mode or three requests in `multi` mode, then drains the listener.

## Swift

Run the [SwiftPM project](swift/) with Swift 6.0 or later. Its manifest
resolves `coakka-http-runtime-swift` 2.3.1 and imports `CoAkkaHttp`.
On macOS 14 or newer on Apple silicon, run the project from the `protocol-listeners` directory.

```sh
swift run --package-path swift Server http1
swift run --package-path swift Server multi "$certs"
```

On Linux, have LLD on PATH and build with
`swift build --package-path swift -Xswiftc -use-ld=lld`. On Windows, build
with `swift build --package-path swift`. Locate the executable directory
with `swift build --package-path swift --show-bin-path`. From the resolved package checkout under
`swift/.build/checkouts/coakka-http-runtime-swift`, copy the Core and bridge
libraries from the matching directory to the executable directory.

| Host | Package directory | Copy to the executable directory |
| --- | --- | --- |
| Linux ARM64 | `Native/linux-arm64/` | `libcoakka_http_host.so.1`, `libcoakka_http_managed_host.so` |
| Linux x86_64 | `Native/linux-x64/` | `libcoakka_http_host.so.1`, `libcoakka_http_managed_host.so` |
| Windows ARM64 | `Native/win-arm64/` | `coakka_http_host.dll`, `coakka_http_managed_host.dll` |
| Windows x86_64 | `Native/win-x64/` | `coakka_http_host.dll`, `coakka_http_managed_host.dll` |

On Windows keep the SwiftPM-built `CoAkkaHttp.dll` alongside `Server.exe`
and have the Swift runtime on PATH. Run the built `Server` or `Server.exe`
with `http1`, `http2 "$certs"`, `http3 "$certs"` or `multi "$certs"`.
The sample exits after serving one request in a standalone mode or three
in `multi` mode. For local candidate testing, set
`COAKKA_HTTP_SWIFT_PACKAGE` to the absolute path of the unpacked SwiftPM
package and use the native files from that package instead of the checkout.
