# CoAkka Runtime Documentation

**Application-owned work across languages and processes.**

CoAkka Runtime is a polyglot, cross-platform distributed runtime backed by a
shared native core. Your application supplies handlers and business rules;
Runtime connects their named work and reports replies or delivery failures.

## Start

1. [Choose your language and run one example](getting-started.md).
2. [Choose a business use case](guides/examples-and-use-cases.md).
3. [Put the same steps in your application](guides/runtime-integration-guide.md).

Start with one local request. The first-run guides use published 3.2.1 packages
and point to real source for each language, including separate Java/Kotlin commands.

## Add a capability

| Need | Guide |
| --- | --- |
| Exchange work and understand replies/failures | [Messages and routing](explanation/runtime-message-and-routing-model.md) |
| Transfer files or stream data | [Files](guides/runtime-file-transfer.md) · [Streams](guides/runtime-streaming.md) |
| Integrate a JVM framework | [Spring Boot](guides/coakka-spring-boot.md) · [Quarkus](guides/coakka-quarkus.md) |
| Connect browser-facing or optional integrations | [HTTP/WebSocket edge](guides/runtime-websocket-integration.md) · [Addons](guides/runtime-addons.md) |

## Operate and look up details

- [Defaults and monitoring](operations/runtime-monitoring.md): AUTO, MULTIPLEXED and monitoring-off.
- [Troubleshooting](operations/troubleshooting.md) and [operations](operations/README.md).
- [Task guides](guides/README.md), [reference](reference/README.md) and [explanations](explanation/README.md).
- [Changelog and migration](CHANGELOG.md), [release history](releases/) and [local evaluation](previews/README.md).

[HTTP Runtime](../coakka-http-runtime/README.md) and [Logger](../coakka-logger/README.md)
have independent versions. [All product documentation](../README.md).
