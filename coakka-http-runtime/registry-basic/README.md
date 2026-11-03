# CoAkka HTTP Runtime 1.1.2 first request

Choose one host below. From a checkout of the samples repository, set the
source path and create a new working directory for that host.

```sh
SAMPLE="$(pwd)/coakka-http-runtime/registry-basic"
mkdir -p /absolute/path/to/http-first-run
cd /absolute/path/to/http-first-run
```

Each sample starts a loopback server on an OS-selected port, sends one
request, checks the status and body, and closes the server. Success prints
`HTTP 200 hello`.

## Node.js

```sh
cp "$SAMPLE/hello.mjs" .
npm install coakka-http-runtime-node@1.1.2
node hello.mjs
```

## Bun

```sh
cp "$SAMPLE/hello.mjs" .
bun add coakka-http-runtime-bun@1.1.2
bun hello.mjs
```

## Python

Use CPython 3.11 or newer with the GIL enabled.

```sh
python3 -m venv .venv
.venv/bin/python -m pip install coakka-http-runtime-python==1.1.2
.venv/bin/python "$SAMPLE/hello.py"
```

## Go

Install the matching [native SDK 1.1.2](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-http-runtime/native/releases/1.1.2)
and set `SDK` to the absolute path of the extracted SDK. The following
commands use Go with cgo and a C compiler on macOS or Linux.

```sh
cp "$SAMPLE/hello.go" .
go mod init example.com/http-first-run
go get github.com/phuong-tran/coakka-http-runtime-go@v1.1.2
CGO_ENABLED=1 CGO_CFLAGS="-I$SDK/include" \
  CGO_LDFLAGS="-L$SDK/lib -Wl,-rpath,$SDK/lib" go run hello.go
```

On macOS, use the SDK belonging to the selected Xcode toolchain if the
system compiler and linker resolve different SDK versions.
Set `SDKROOT="$(xcrun --sdk macosx --show-sdk-path)"` before running Go.

## JVM

Use a JDK and Maven. The example below selects the Maven Central native
classifier for macOS ARM64. The other classifiers are `linux-aarch64`,
`linux-x86_64`, `windows-aarch64`, and `windows-x86_64`. Select the matching
classifier in `pom.xml` and use the matching native filenames on that OS.

```sh
cp "$SAMPLE/pom.xml" "$SAMPLE/Hello.java" .
mvn org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies
unzip -q target/dependency/coakka-http-runtime-jvm-1.1.2-macos-aarch64.zip
javac -cp 'target/dependency/*' Hello.java
java -Dcoakka.http.host.path="$PWD/native/libcoakka_http_host.dylib" \
  -Dcoakka.http.bridge.path="$PWD/native/libcoakka_http_jvm.dylib" \
  -cp '.:target/dependency/*' Hello
```

## Native C and C++

Use the [SDK 1.1.2 server and streaming samples](../native/host-server/README.md).
