# CoAkka Runtime Documentation

CoAkka Runtime connects distributed application targets across host languages.
Start with a normal request/reply example, then add only the features your
application needs. [Runnable projects](../../coakka-runtime/README.md) stay
beside their code; this directory contains the product guides.

## Start and integrate

- [How it works](how-it-works.md) and [Runtime story](coakka-story.md)
- [Integration guide](runtime-integration-guide.md)
- [Network modes](runtime-network-modes.md) and [connection strategies](connection-strategies.md)
- [Message and routing model](runtime-message-and-routing-model.md)
- [Envelope and deadletter outcomes](envelope-deadletter-map.md)
- [Spring Boot](coakka-spring-boot.md) and [Quarkus](coakka-quarkus.md)
- [Python configuration](python-configuration.md)

## Operate and monitor

- [Defaults and monitoring](runtime-monitoring.md): AUTO and MULTIPLEXED are the defaults; monitoring is off until enabled.
- [Field guide](runtime-field-guide.md)
- [Logging and observability](runtime-logging-observability.md)
- [Client](coakka-runtime-client.md) and [Inspect](coakka-runtime-inspect.md)
- [TLS and mTLS](tls-and-mtls.md)
- [Troubleshooting](troubleshooting.md)
- [Production readiness](production-readiness.md) and [evidence](production-evidence.md)
- [Package and platform evidence](runtime-package-platform-evidence.md)
- [Signing and platform trust](runtime-release-signing-and-platform-trust.md)

## Extend an application

- [File transfer](runtime-file-transfer.md)
- [Streaming](runtime-streaming.md) and [WebSocket integration](runtime-websocket-integration.md)
- [Replica-owner grants](runtime-lane-owner-grants.md) and [cluster routing](runtime-cluster-routing.md)
- [Addons](runtime-addons.md)
- [Containers](containerized-runtime.md)
- [Edge and Android preview](edge-iot-android.md)
- [Glossary](runtime-glossary.md)

## Private 3.0.0 release candidate

These guides describe a prepared candidate, not an already published release.
[Published coordinates](../common/current-packages.md) remain the installation
reference until the release is opened.

- [Node.js and Bun](runtime-js-candidate.md), [Electron](runtime-electron-candidate.md)
- [Python](runtime-python-candidate.md), [C#](runtime-csharp-candidate.md)
- [JVM](runtime-jvm-candidate.md), [Spring Boot and Quarkus](runtime-frameworks-candidate.md)
- [Go](runtime-go-candidate.md), [Swift](runtime-swift-candidate.md)
- [Rust](runtime-rust-candidate.md), [Tauri](runtime-tauri-candidate.md), [Zig](runtime-zig-candidate.md)
- [Mojo preview](runtime-mojo-candidate.md)

Runtime versions independently from [HTTP Runtime](../coakka-http-runtime/README.md)
and [Logger](../coakka-logger/README.md). Return to the [ecosystem docs](../README.md).
