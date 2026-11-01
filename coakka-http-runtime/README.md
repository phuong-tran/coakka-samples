# CoAkka HTTP Runtime Samples

[Choose your language](#languages) · [Next steps](#after-one-request) · [Documentation](../docs/coakka-http-runtime/README.md) · [Downloads](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/README.md) · [All products](../README.md)

**HTTP services in your language. One shared native core.**

CoAkka HTTP Runtime is a **polyglot, cross-platform HTTP server and client**
backed by a **shared native HTTP core**. Build APIs, serve files or stream
responses through familiar, native-backed language APIs.

A Python upload service and a Go API can use the same HTTP core while keeping
their own handlers. Each application runs its own instance and owns its
business rules. HTTP Runtime handles the HTTP work around those handlers.

HTTP Runtime is independent of CoAkka Runtime, which connects named
application work across processes. Read the
[HTTP introduction](../docs/coakka-http-runtime/explanation/introduction.md)
for the fuller story.

## Why a shared HTTP core

A Go API and a Python upload service can use the same HTTP core through their
own language APIs. Common fixes and improvements can be delivered through
updated language packages. Teams can learn one product's HTTP behavior across
services while keeping application handlers natural to each language.

HTTP Runtime gives the ecosystem an HTTP server/client surface for browser and
HTTP integrations. Runtime remains the product for named application work; an
HTTP handler can call it when needed. Each product can be used on its own.

## Package-manager releases in preparation

Distribution through **npm**, **Maven Central**, **Go modules** and **PyPI** is
planned. The currently available installation path uses the 1.0.0 archives and
matching examples below. The [package ledger](../docs/common/current-packages.md)
records what has actually been released.

Choose one language below, install its matching **HTTP Runtime 1.0.0 archive**,
start the server and send one request. Each guide supplies prerequisites,
commands, the expected response and shutdown steps. You only need the tools for
your chosen language. [Archives are available in Publish](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/README.md).

## Languages

Follow the guide's exact language version and platform requirements.

| Your language | Setup and first request | Runnable example |
| --- | --- | --- |
| C | [C integration](../docs/coakka-http-runtime/c/integration.md) | [C sample](c/README.md) |
| C++ | [C++ integration](../docs/coakka-http-runtime/cpp/integration.md) | [C++ sample](cpp/README.md) |
| Go | [Go integration](../docs/coakka-http-runtime/go/integration.md) | [Go sample](go/README.md) |
| Kotlin / Java | [JVM integration](../docs/coakka-http-runtime/kotlin/integration.md) | [Kotlin and Java samples](kotlin/README.md) |
| Python | [Python integration](../docs/coakka-http-runtime/python/integration.md) | [Python sample](python/README.md) |
| Node.js / Bun / TypeScript | [Node/Bun integration](../docs/coakka-http-runtime/typescript/integration.md) | [TypeScript sample](typescript/README.md) |

## After one request

- [Choose a feature](../docs/coakka-http-runtime/reference/FEATURES.md) such as streaming, WebSocket or frontend delivery.
- [Read the HTTP model](../docs/coakka-http-runtime/explanation/introduction.md) and [file/upload guide](../docs/coakka-http-runtime/guides/file-delivery.md).
- [Observe a service](../docs/coakka-http-runtime/operations/monitoring.md).
- [Use Inspect](https://github.com/phuong-tran/coakka-publish/blob/main/docs/coakka-http-runtime/inspect/metadata-and-openapi.md).

## Verify a change

For contributors verifying all language examples, `bash coakka-http-runtime/run.sh verify`
runs the full suite and needs all corresponding tools. This is separate from
running your first language example. See [feature coverage](../docs/coakka-http-runtime/reference/FEATURES.md)
and the [recorded package scope](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/README.md).

[HTTP documentation](../docs/coakka-http-runtime/README.md) · [Other products](../README.md).
