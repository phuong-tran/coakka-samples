# CoAkka HTTP Runtime 2.0.1 first request

Choose one language below. Each example starts a local server, sends one
request, prints `HTTP 200 hello` and exits.

Clone [coakka-samples](https://github.com/phuong-tran/coakka-samples) and open
a separate application directory. Set `SAMPLE` to the absolute path of
`coakka-http-runtime/registry-basic` in your checkout.

[HTTP package and platform guide](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/README.md)
 · [HTTP Runtime documentation](../../docs/coakka-http-runtime/README.md)

## Node.js and Bun

Copy the sample once.

```sh
cp "$SAMPLE/hello.mjs" .
```

Run the commands for Node.js or Bun.

```sh
npm install coakka-http-runtime-node@2.0.1
node hello.mjs
```

```sh
bun add coakka-http-runtime-bun@2.0.1
bun hello.mjs
```

## Python

Use CPython 3.11 or newer with the GIL enabled.

```sh
python3 -m venv .venv
.venv/bin/python -m pip install coakka-http-runtime-python==2.0.1
.venv/bin/python "$SAMPLE/hello.py"
```

## Go

Download and verify the [native SDK 2.0.1](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-http-runtime/native/releases/2.0.1). Set `SDK` to the
absolute path of the extracted SDK. Use Go 1.23 or newer and a C compiler.
On macOS or Linux:

```sh
cp "$SAMPLE/hello.go" .
go mod init example.com/http-first-run
go get github.com/phuong-tran/coakka-http-runtime-go/v2@v2.0.1
CGO_ENABLED=1 CGO_CFLAGS="-I$SDK/include" \
  CGO_LDFLAGS="-L$SDK/lib -Wl,-rpath,$SDK/lib" go run hello.go
```

Use the [Go package guide](https://github.com/phuong-tran/coakka-http-runtime-go/blob/v2.0.1/README.md)
for Windows setup and macOS system SDK troubleshooting.

If macOS reports a malformed system library or an unknown architecture while
linking, select the SDK from the active Xcode installation and retry.

```sh
export SDKROOT="$(xcrun --sdk macosx --show-sdk-path)"
```

## JVM

Use JDK 17 and Maven. The example selects the macOS ARM64 native ZIP in
`pom.xml`. On another platform, change the ZIP classifier to match your JVM
and use the [JVM package guide](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/jvm/README.md)
for the library setup.

```sh
cp "$SAMPLE/pom.xml" "$SAMPLE/Hello.java" .
mvn org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies
unzip -q target/dependency/coakka-http-runtime-jvm-2.0.1-macos-aarch64.zip
javac -cp 'target/dependency/*' Hello.java
java "-Djava.library.path=$PWD/native" -cp '.:target/dependency/*' Hello
```

For Kotlin, follow the [2.0.1 first-request example](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/jvm/README.md#first-request-in-kotlin)
using the same managed JAR and matching native ZIP.

## C and C++

Use the [server and streaming examples](../native/host-server/README.md)
with the same native SDK 2.0.1.
