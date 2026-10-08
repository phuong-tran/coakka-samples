# CoAkka Ecosystem Overview

CoAkka is a polyglot, multi-language, multi-platform distributed runtime
ecosystem. Its central product, **CoAkka Runtime**, routes application-owned
work by stable target name across processes, languages, containers and hosts.
It provides bounded admission, request/reply, deadletters and delivery
diagnostics through idiomatic host-language connectors.

## Products And Boundaries

| Product | Owns | Start here |
| --- | --- | --- |
| CoAkka Runtime | Distributed target-based delivery, replies, deadletters, File Lane and Stream Lane. | [Runtime integration](runtime-integration-guide.md), [published packages](current-packages.md) |
| CoAkka Logger | Bounded logging with explicit admission, delivery and pressure outcomes. | [Logging and observability](runtime-logging-observability.md) |
| Runtime Addons | Optional integration capabilities composed with Runtime, such as acquiring a file before File Lane delivery. | [Runtime addons](runtime-addons.md) |
| CoAkka HTTP Runtime | Independent HTTP server/client: routing, static files, streaming, SSE, WebSocket, TLS/mTLS, monitoring and graceful shutdown. | [HTTP samples](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-http-runtime), [archive catalog](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-http-runtime) |

HTTP Runtime is not a new name for CoAkka Runtime or a required layer around
it. An HTTP handler can call a Runtime target, but each product keeps its own
lifecycle, configuration, package identity and release cadence. Applications
may retain their existing HTTP framework and adopt only Runtime.

## Distribution

Runtime and Logger have published package-manager lanes and exact native or
source archives, depending on the language. Use [Current Packages](current-packages.md)
for the actual coordinate; matching version numbers across products are not
required.

HTTP Runtime is newer and currently distributed as checksum-pinned repository
archives on `coakka-publish/main`, with archive-backed samples. It has no npm,
PyPI, Maven Central or tagged Go-module release in this distribution. Installing
a Runtime/Logger package does not install HTTP Runtime.

## Repository Boundaries

| Surface | Responsibility |
| --- | --- |
| `coakka-samples` | Runnable consumer examples, integration workflows and documentation. |
| `coakka-publish` | Versioned packages and archives, checksums, manifests, compatibility and release evidence. |
| Language package repositories | Product-specific Go/Swift package coordinates and consumption guides. |
| Product implementation repositories | Build and qualify the product; not prerequisites for running published samples. |

Use the [samples repository](https://github.com/phuong-tran/coakka-samples)
to run an integration and the [publish repository](https://github.com/phuong-tran/coakka-publish)
when exact artifact identity matters. Neither is an aggregate ecosystem
release; see [Repository Boundaries](repository-boundaries.md).

## Language And Platform Scope

The ecosystem includes native C/C++, JVM and framework adapters, Node.js/Bun,
Python, Go, C#, Rust, Swift and additional source or desktop/device lanes.
That inventory does not mean every product supports every host. Check the
selected product's exact version, OS, CPU, package contents and matching-host
execution evidence. A packaged binary alone does not prove connector execution.

Kubernetes is a first-class deployment lane, not a prerequisite. Runtime also
operates on standalone services, containers, VMs, bare-metal hosts and supported
edge devices. See [Runtime package and platform evidence](runtime-package-platform-evidence.md)
for product-specific limits; Android support is never inferred from Linux.

## Shared Design Principles

- Explicit lifecycle ownership and bounded resources.
- Idiomatic connectors that expose the owning product's effective configuration,
  capabilities and failures rather than guessing them.
- Business routing policy, authorization and retry decisions remain application-owned.
- Product-specific contracts stay distinct: Runtime targets/envelopes are not
  HTTP routes/requests, and Runtime File/Stream Lane grants are not HTTP streams.
- Availability and performance claims follow exact artifact and workload evidence.

Start with [New to CoAkka](new-to-coakka.md), then the
[Runtime learning path](integration-path.md). For an HTTP-only task, go
directly to the HTTP sample guide above. Operational help is in
[Troubleshooting](troubleshooting.md) and [Contact and Support](contact-and-support.md).
