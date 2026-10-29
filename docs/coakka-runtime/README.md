# CoAkka Runtime Documentation

**Application-owned work across languages and processes.**

CoAkka Runtime is a polyglot, cross-platform runtime powered by a shared native
core. Native-backed language connectors expose familiar APIs. The application
supplies its handlers and business rules; Runtime manages delivery and outcomes.

## Start

[Choose a language and run one example](getting-started.md). The guide separates
published packages from the unpublished 3.0.0 candidate so code and packages match.
Then [integrate Runtime into an application](guides/runtime-integration-guide.md).

## Add a capability

| Task | Guide |
| --- | --- |
| Exchange messages and understand delivery outcomes | [Messages and routing](explanation/runtime-message-and-routing-model.md) |
| Transfer files or stream data | [Files](guides/runtime-file-transfer.md) · [Streams](guides/runtime-streaming.md) |
| Integrate a JVM application framework | [Spring Boot](guides/coakka-spring-boot.md) · [Quarkus](guides/coakka-quarkus.md) |
| Connect browser-facing or optional integrations | [WebSocket](guides/runtime-websocket-integration.md) · [Addons](guides/runtime-addons.md) |

## Operate

Start with [defaults and monitoring](operations/runtime-monitoring.md): AUTO and
MULTIPLEXED are the current defaults; extra monitor collection starts OFF.
The guide explains why, when to enable it and how, using Kotlin as one example
of the same model available through other languages.

Use [Client and Inspect](operations/README.md) to inspect a participant, or
[troubleshoot a problem](operations/troubleshooting.md).

## Learn or look up details

- [Understand the model and adoption choices](explanation/README.md).
- [Find configuration, platforms and vocabulary](reference/README.md).
- [Browse all task guides](guides/README.md).
- [Read release history](releases/README.md) or [evaluate the 3.0.0 candidate](previews/README.md).

Runtime versions independently from [HTTP Runtime](../coakka-http-runtime/README.md)
and [Logger](../coakka-logger/README.md). [All product documentation](../README.md).
