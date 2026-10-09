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

Live listener certificate reload is planned for a future version. The intended
experience is an explicit, checked change of credential material, with clear
effective-state reporting and failure behavior. Detailed connection, trust
transition and rollback semantics will accompany the verified API.

Today, certificate rotation uses a replacement service: prepare credentials,
start and verify the new instance, shift traffic, then drain the old instance.
That is the current operational procedure; the roadmap is not an instruction
to reload a running listener by overwriting its certificate files.

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

The language connectors do not yet expose the end-user API for declaring this
metadata. A future update will connect idiomatic connector declarations to the
existing Core capability, with Java-friendly JVM APIs, per-language samples,
documentation and artifact-backed tests. Metadata belongs to route registration,
not to every incoming request. This work extends the public integration path;
it does not redesign the metadata model or add schema work to the request path.

Downstream OpenAPI-tool verification will accompany that integration. See
[metadata and OpenAPI](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/inspect/metadata-and-openapi.md)
for current behavior and the distinction between route snapshots and schema
publication. No new connector API is implied by this roadmap entry.

## Current guides

- [Install and explore the language samples](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-http-runtime/README.md).
- [Certificate rotation procedure](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/tls-and-mtls.md#credential-operations).
- [Native baseline and language costs](https://github.com/phuong-tran/coakka-samples/blob/main/docs/coakka-http-runtime-introduction.md#native-baseline-and-language-costs).

No new runtime API, registry release or connector support is implied by this
planning document.
