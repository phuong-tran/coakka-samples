# CoAkka HTTP Runtime Samples

[Choose your language](#languages) · [Next steps](#after-one-request) · [HTTP Inspect](#coakka-http-runtime-inspect) · [Documentation](../docs/coakka-http-runtime/README.md) · [Downloads](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/README.md) · [All products](../README.md)

**HTTP services in your language. One shared native core.**

CoAkka HTTP Runtime provides **polyglot, cross-platform HTTP services and
outbound HTTP requests** backed by a **shared native HTTP core**. Build APIs,
serve files or stream responses through familiar language APIs. Each application
runs its own instance and owns its handlers and business rules.

HTTP Runtime is independent of CoAkka Runtime, which connects named
application work across processes. Read the
[HTTP introduction](../docs/coakka-http-runtime/explanation/introduction.md)
for the fuller story.

## Why a shared HTTP core

A Go API and a Python upload service can use the same HTTP core through their
own language APIs. Common fixes and improvements can be delivered through
updated language packages. Teams can learn one product's HTTP behavior across
services while keeping application handlers natural to each language.

Outbound requests belong to HTTP Runtime's documented package scope. The
planned standalone **CoAkka HTTP Client** is a separate future product; read
[current client scope and roadmap](../docs/coakka-http-runtime/explanation/introduction.md#outbound-http-and-the-planned-http-client).
Runtime remains the product for named application work; an HTTP handler can
call it when needed.

## CoAkka HTTP Runtime Inspect

**Swagger-like API exploration for an inspection-enabled HTTP service.**
Browse the route descriptions a service publishes, try HTTP requests in a
browser and export an **OpenAPI 3.0.3** document. Inspect provides its own UI;
the exported document can also be used by OpenAPI tools such as Swagger UI.

Current Inspect archives target **macOS ARM64** and **Linux ARM64**. Service
inspection must be explicitly enabled, and language-connector APIs for declaring
route/schema metadata are still being completed. Follow the current integration
status before adding inspection to a language example.

[Explore Inspect and OpenAPI](https://github.com/phuong-tran/coakka-publish/blob/main/docs/coakka-http-runtime/inspect/metadata-and-openapi.md) ·
[Current integration status](https://github.com/phuong-tran/coakka-publish/blob/main/docs/coakka-http-runtime/inspect/metadata-and-openapi.md#current-package-integration) ·
[Inspect packages and setup](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/inspect/README.md).

## Version 1.1.2

| Host | HTTP Runtime 1.1.2 |
| --- | --- |
| Node.js | [`coakka-http-runtime-node`](https://www.npmjs.com/package/coakka-http-runtime-node/v/1.1.2) |
| Bun | [`coakka-http-runtime-bun`](https://www.npmjs.com/package/coakka-http-runtime-bun/v/1.1.2) |
| Python | [`coakka-http-runtime-python`](https://pypi.org/project/coakka-http-runtime-python/1.1.2/) |
| JVM | [`io.github.phuong-tran.coakka:coakka-http-runtime-jvm:1.1.2`](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/coakka-http-runtime-jvm/1.1.2) |
| Go | [`github.com/phuong-tran/coakka-http-runtime-go@v1.1.2`](https://pkg.go.dev/github.com/phuong-tran/coakka-http-runtime-go@v1.1.2) |

[C and C++ SDK downloads](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-http-runtime/native/releases/1.1.2) · [C and C++ examples](native/host-server/README.md) · [Raspberry Pi 5 benchmark](benchmark-rpi5/README.md)

Start with the [1.1.2 registry samples](registry-basic/README.md) for Node.js, Bun, Python, Go, and JVM. The language guides below also document the historical archive-backed feature samples.

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

For the installed SDK 1.1.2, see the [C and C++ server and streaming examples](native/host-server/README.md).

## After one request

- [Choose a feature](../docs/coakka-http-runtime/reference/FEATURES.md) such as streaming, WebSocket or frontend delivery.
- [Read the HTTP model](../docs/coakka-http-runtime/explanation/introduction.md) and [file/upload guide](../docs/coakka-http-runtime/guides/file-delivery.md).
- [Observe a service](../docs/coakka-http-runtime/operations/monitoring.md).
- [HTTP Runtime Inspect and OpenAPI](https://github.com/phuong-tran/coakka-publish/blob/main/docs/coakka-http-runtime/inspect/metadata-and-openapi.md).

## Verify a change

For contributors verifying all language examples, `bash coakka-http-runtime/run.sh verify`
runs the full suite and needs all corresponding tools. This is separate from
running your first language example. See [feature coverage](../docs/coakka-http-runtime/reference/FEATURES.md)
and the [recorded package scope](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/README.md).

[HTTP documentation](../docs/coakka-http-runtime/README.md) · [Other products](../README.md).
