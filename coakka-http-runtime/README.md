# CoAkka HTTP Runtime Samples

**Polyglot applications should not require polyglot HTTP infrastructure.**

A team may build an API in Go, an image endpoint in Python and a web service
in TypeScript. Maintaining a separate HTTP stack for each language repeats
decisions about request limits, timeouts, streaming and shutdown.

CoAkka HTTP Runtime supplies a shared HTTP engine through language-specific
packages. Each service runs its own instance, and handlers stay ordinary
application functions in the language the team already uses. Teams can reuse
their understanding of HTTP behavior and adopt shared fixes through package
updates.

Use it for HTTP APIs, web frontends, file delivery, streams and WebSocket
sessions. CoAkka Runtime's named application work is a separate product; a
handler can use both when the application needs them. Read the
[HTTP Runtime introduction](../docs/coakka-http-runtime/explanation/introduction.md)
for the fuller story.

Choose one language below, run its server and send one request. Each Start here
page gives the tools you need, the package archive and the run command. You do
not need all language toolchains to try one example.

The examples use checksum-pinned HTTP Runtime 1.0.0 archives from
[Publish](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-http-runtime).
They do not rebuild a private Core or connector. This HTTP service product is
separate from CoAkka Runtime's distributed application model.

## Languages

| Language and host | Start here | Details and evidence |
| --- | --- | --- |
| C11 | [C integration](../docs/coakka-http-runtime/c/integration.md) | [C sample](c/README.md) |
| C++20 | [C++ integration](../docs/coakka-http-runtime/cpp/integration.md) | [C++ sample](cpp/README.md) |
| Go 1.23 or newer | [Go integration](../docs/coakka-http-runtime/go/integration.md) | [Go sample](go/README.md) |
| JVM: Kotlin and Java; Java 8 bytecode, JDK 17 sample compilation | [JVM integration](../docs/coakka-http-runtime/kotlin/integration.md) | [Kotlin and Java samples](kotlin/README.md) |
| Python 3.11 or newer | [Python integration](../docs/coakka-http-runtime/python/integration.md) | [Python sample](python/README.md) |
| TypeScript on Node.js 22+ and Bun 1.2.22+ | [Node/Bun integration](../docs/coakka-http-runtime/typescript/integration.md) | [TypeScript sample](typescript/README.md) |

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
