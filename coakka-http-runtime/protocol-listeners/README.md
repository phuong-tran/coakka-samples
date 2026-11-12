# Run the HTTP version samples

The 2.2.1 registry rollout starts with Node.js and Bun on npm. Use the
Go, Python and JVM instructions once the matching package is available
in its registry. The native examples require SDK 2.2.0.

Each sample serves `GET /hello` and returns `hello`. Start one mode,
run the matching curl command, then stop the sample before starting a
different mode. After curl completes, press Enter to close the C or C++
example.

| Mode | Address | Listener |
| --- | --- | --- |
| [`http1`](../../docs/coakka-http-runtime/guides/http-versions/http1.md) | `http://127.0.0.1:8080/hello` | HTTP/1.1, plain TCP |
| [`http2`](../../docs/coakka-http-runtime/guides/http-versions/http2.md) | `https://127.0.0.1:8443/hello` | HTTP/2, TLS over TCP |
| [`http3`](../../docs/coakka-http-runtime/guides/http-versions/http3.md) | `https://127.0.0.1:8443/hello` | HTTP/3, QUIC over UDP |
| [`multi`](../../docs/coakka-http-runtime/guides/http-versions/multi.md) | `https://127.0.0.1:8443/hello` | Listener V1, Node.js or Bun in these samples |

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
the status and the negotiated version. `multi` is accepted by the
Node.js and Bun examples; the C and C++ samples here use the standalone
API.

## Node.js or Bun

Install the connector from npm using the package manager for your runtime.
Both runtimes use [the same sample](server.mjs).

```sh
npm install --no-save coakka-http-runtime-node@2.2.1
node server.mjs http1 "$certs"
```

```sh
bun add coakka-http-runtime-bun@2.2.1
bun server.mjs multi "$certs"
```

## Python

Use CPython 3.11 or newer and install the wheel from PyPI.
[Python source](server.py)

```sh
python3 -m venv .venv
.venv/bin/python -m pip install coakka-http-runtime-python==2.2.1
.venv/bin/python server.py http1 "$certs"
```

## Go

Install the platform SDK 2.2.0 and set `sdk` to its absolute extracted
directory. Go resolves the 2.2.1 module from the module path in
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

Resolve the JVM 2.2.1 JAR and matching platform classifier ZIP from Maven
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

Set `sdk` to the absolute directory of the extracted SDK 2.2.0.
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
