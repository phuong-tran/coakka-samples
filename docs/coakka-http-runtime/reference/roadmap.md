# CoAkka HTTP Runtime roadmap

This roadmap describes planned work, not capabilities already shipped. Exact
versions and dates will be announced after package, platform and consumer
verification. Current installation uses the checksum-pinned repository
archives; keep following the current guides until registry coordinates are
published.

## Contents

- [Package distribution](#package-distribution)
- [Certificate lifecycle](#certificate-lifecycle)
- [More language connectors](#more-language-connectors)
- [Current guides](#current-guides)
- [Inspect integration](#inspect-integration)

## Package distribution

The next distribution phase will bring the existing language surfaces to their
usual package channels, with installation guides and matching native artifacts.

| Channel | Planned application surface |
| --- | --- |
| npm | Shared JavaScript/TypeScript package for Node.js and Bun |
| Go modules | Versioned Go module distribution with its documented native artifact integration |
| PyPI | Python packages with the corresponding platform artifacts |
| Maven Central | JVM artifacts for both Java and Kotlin |

Registry publication is more than uploading the current archives: each channel
needs its native layout, versioning, integrity and consumer installation checks.
No package name, coordinate or installation command here reserves an
unpublished registry entry.

## Certificate lifecycle

The 1.1.2 release candidate supports explicit TLS and mTLS credential reload
for secure listeners. Operators submit a new credential set with the expected
effective generation. A rejected update preserves the current generation.
New handshakes use the accepted credentials, while existing sessions follow
the listener's retirement policy. Monitoring remains off by default.

For older packages, rotate with a replacement service. See the certificate
operations guide for both procedures.

## More language connectors

After the package-distribution phase, planned connector expansion includes
**C#/.NET** and **Swift**, followed by other languages as priorities and
verification mature. Each connector should remain idiomatic and thin, with
configuration, HTTP mechanics and effective-state truth owned by the shared
Core. These are HTTP Runtime plans, separate from connector availability for
the distributed CoAkka Runtime product.

## Inspect integration

Core already supports route API metadata, including parameters, request bodies,
responses and schemas. Inspect already consumes that metadata and projects
OpenAPI 3.0.3; its standalone application has its own qualified packages.

JavaScript/TypeScript, Python, Go and JVM connectors expose route metadata
and authenticated inspection. The 1.1.2 qualification exercised the focused
native read surface and the Node/Bun OpenAPI workflow with the standalone
Inspector. See [metadata and OpenAPI](https://github.com/phuong-tran/coakka-publish/blob/main/docs/coakka-http-runtime/inspect/metadata-and-openapi.md)
for the distinction between route snapshots and schema publication.

## Current guides

- [Install and explore the language samples](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-http-runtime/README.md).
- [Certificate rotation procedure](https://github.com/phuong-tran/coakka-publish/blob/main/docs/coakka-http-runtime/operations/tls-and-mtls.md#credential-operations).
- [Native baseline and language costs](https://github.com/phuong-tran/coakka-samples/blob/main/docs/coakka-http-runtime/explanation/introduction.md#native-baseline-and-language-costs).

No new runtime API, registry release or connector support is implied by this
planning document.
