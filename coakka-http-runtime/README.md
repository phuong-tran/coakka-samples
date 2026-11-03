# CoAkka HTTP Runtime Samples

HTTP Runtime runs HTTP services and sends outbound requests from C, C++, Go,
Kotlin, Java, Python, Node.js and Bun. Each service runs its own instance.
Application handlers use their language API while the native runtime handles
HTTP delivery, limits and shutdown.

[HTTP documentation](../docs/coakka-http-runtime/README.md) · [Pi 5 benchmark](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/benchmark/README.md)

## Version 1.1.2

[Run your first request](registry-basic/README.md) · [C and C++ examples](native/host-server/README.md)

| Host | HTTP Runtime 1.1.2 |
| --- | --- |
| Node.js | [`coakka-http-runtime-node`](https://www.npmjs.com/package/coakka-http-runtime-node/v/1.1.2) |
| Bun | [`coakka-http-runtime-bun`](https://www.npmjs.com/package/coakka-http-runtime-bun/v/1.1.2) |
| Python | [`coakka-http-runtime-python`](https://pypi.org/project/coakka-http-runtime-python/1.1.2/) |
| JVM | [`io.github.phuong-tran.coakka:coakka-http-runtime-jvm:1.1.2`](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/coakka-http-runtime-jvm/1.1.2) |
| Go | [`github.com/phuong-tran/coakka-http-runtime-go@v1.1.2`](https://pkg.go.dev/github.com/phuong-tran/coakka-http-runtime-go@v1.1.2) |

The feature guides below include recipes that use pinned 1.0.0 archives.

## HTTP Inspect

Inspect can browse a service’s published routes, try requests and export
OpenAPI. Its packages target macOS ARM64 and Linux ARM64. The service must
enable inspection before the companion can connect.
[Walkthrough](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/inspect/walkthrough.md) · [Integration status](https://github.com/phuong-tran/coakka-publish/blob/main/docs/coakka-http-runtime/inspect/metadata-and-openapi.md#current-package-integration)

## Languages

| Language and host | Feature guide | Archived feature sample |
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
