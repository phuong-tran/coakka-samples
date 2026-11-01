# Swift Runtime 3.0.0: First Run

Send one request to a Swift handler and print its reply.

**You need:** a [Samples checkout](../../../docs/coakka-runtime/getting-started.md#get-the-examples)
and a supported Swift installation: Apple Swift 6.2 on macOS, or Swift 6.0.3
on Linux/Windows x64. The package's language minimum is Swift 6.0.
Windows ARM64 alone remains preview.

## Run

From the Samples repository root:

~~~sh
cd coakka-runtime/swift/registry-basic
swift run -c release CoAkkaFirstRun
~~~

Expected output:

~~~text
accepted-hello
~~~

Swift Package Manager resolves the public 3.0.0 tag declared in
[Package.swift](Package.swift). No separate Core installation is needed.

## Read the code

Open [FirstRun.swift](Sources/CoAkkaFirstRun/FirstRun.swift). It starts a local
host, registers a handler, asks for work and checks its reply. It handles
throwing close and preserves both operation and cleanup failures.

When deploying an application, include its dynamic library and resource bundle.
This is a one-process example; use the [integration guide](../../../docs/coakka-runtime/guides/runtime-integration-guide.md)
and [use-case index](../../../docs/coakka-runtime/guides/examples-and-use-cases.md) for the next step.

[Platform requirements](../../../docs/coakka-runtime/reference/runtime-package-platform-evidence.md) ·
[Local-package evaluation](../../../docs/coakka-runtime/previews/README.md).
