# CoAkka HTTP Runtime 2.0.1 first request

Run these commands from a separate application directory. Set `SAMPLE` to
the absolute path of this directory in your samples checkout. Each registry
example starts a loopback server, sends one request, and prints `HTTP 200 hello`.

## Node.js and Bun

Copy `hello.mjs` from `$SAMPLE`, then run one of these pairs.

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

## C and C++

Use the [server and streaming examples](../native/host-server/README.md)
with the same native SDK 2.0.1.
