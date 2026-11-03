# CoAkka HTTP Runtime roadmap

HTTP Runtime 1.1.2 is available through public package channels. This page records what shipped and outlines future work.

## Contents

- [Package distribution](#package-distribution)
- [Certificate lifecycle](#certificate-lifecycle)
- [More language connectors](#more-language-connectors)
- [Current guides](#current-guides)
- [Inspect integration](#inspect-integration)

## Package distribution

Version 1.1.2 ships Node.js and Bun packages on npm, Python wheels on PyPI,
JVM artifacts for Java and Kotlin on Maven Central, and a tagged Go module.
The native SDK is available as checksum-pinned archives. See the
[package catalog](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/README.md#version-112)
and [first-request samples](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-http-runtime/registry-basic/README.md).

## Certificate lifecycle

HTTP Runtime 1.1.2 supports explicit TLS and mTLS credential reload
for secure listeners. Operators submit a new credential set with the expected
effective generation. A rejected update preserves the current generation.
New handshakes use the accepted credentials, while existing sessions follow
the listener's retirement policy. Monitoring remains off by default.

For older packages, rotate with a replacement service. See the certificate
operations guide for both procedures.

## More language connectors

Planned connector expansion includes
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
