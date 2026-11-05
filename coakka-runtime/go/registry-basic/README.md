# Go Runtime 3.2.1: First Run

Send one request to a Go handler and print its reply.

**You need:** Go 1.22.12 or later, a C compiler and a
[Samples checkout](../../../docs/coakka-runtime/getting-started.md#get-the-examples).
Native Windows ARM64 uses Go 1.25.5 and a matching ARM64 C compiler.

## Run

From the Samples repository root:

~~~sh
cd coakka-runtime/go/registry-basic
go run -mod=readonly .
~~~

Expected output:

~~~text
accepted-hello
~~~

Go resolves the exact public /v3 module declared in [go.mod](go.mod).
No separate Core installation is needed.

If macOS reports a malformed system library or an unknown architecture while
linking, select the SDK from the active Xcode installation and retry.

```sh
export SDKROOT="$(xcrun --sdk macosx --show-sdk-path)"
```

## Read the code

Open [main.go](main.go). It starts a local host, registers a handler, submits
a request and checks its reply. Deferred close preserves cleanup errors
alongside any operation error.

This is a one-process example. [Choose a use case](../../../docs/coakka-runtime/guides/examples-and-use-cases.md)
or [integrate the same steps into your application](../../../docs/coakka-runtime/guides/runtime-integration-guide.md).

[Platform and build requirements](../../../docs/coakka-runtime/reference/runtime-package-platform-evidence.md) ·
[Local-module evaluation](../../../docs/coakka-runtime/previews/README.md).
