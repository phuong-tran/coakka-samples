# Start With CoAkka HTTP Runtime

**Choose your language, start a service and send one request.**

HTTP Runtime is a separate product from CoAkka Runtime. Its current 1.0.0
distribution uses repository archives. Follow one language guide to obtain the
matching archive and prepare only that language's tools.

## Choose your language

| Your application | Start with |
| --- | --- |
| Node.js or Bun / TypeScript | [Node/Bun installation and first request](https://github.com/phuong-tran/coakka-samples/blob/main/docs/coakka-http-runtime/typescript/integration.md) |
| Python | [Python installation and first request](https://github.com/phuong-tran/coakka-samples/blob/main/docs/coakka-http-runtime/python/integration.md) |
| Kotlin or Java | [JVM installation and first request](https://github.com/phuong-tran/coakka-samples/blob/main/docs/coakka-http-runtime/kotlin/integration.md) |
| Go | [Go installation and first request](https://github.com/phuong-tran/coakka-samples/blob/main/docs/coakka-http-runtime/go/integration.md) |
| C | [C installation and first request](https://github.com/phuong-tran/coakka-samples/blob/main/docs/coakka-http-runtime/c/integration.md) |
| C++ | [C++ installation and first request](https://github.com/phuong-tran/coakka-samples/blob/main/docs/coakka-http-runtime/cpp/integration.md) |

Each guide gives prerequisites, archive setup, the service command, a request
to send, its expected response and how to stop the service. Follow that complete
sequence; installing a CoAkka Runtime connector does not install HTTP Runtime.

The [HTTP sample index](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-http-runtime/README.md)
links the runnable source and feature coverage for each language.

## After your first response

- [Choose an HTTP feature](guides/README.md): files, uploads and other application tasks.
- [Read the application model](explanation/introduction.md).
- [Observe and operate a service](operations/README.md).
- [Check package/platform scope](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/README.md).

The all-language verification suite is for contributors checking a change.
It requires all corresponding tools and is not part of the first-run path.

[HTTP Runtime documentation](README.md) · [All products](../README.md).
