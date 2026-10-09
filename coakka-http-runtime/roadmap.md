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

## Current guides

- [Install and explore the language samples](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-http-runtime/README.md).
- [Certificate rotation procedure](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/tls-and-mtls.md#credential-operations).
- [Native baseline and language costs](https://github.com/phuong-tran/coakka-samples/blob/main/docs/coakka-http-runtime-introduction.md#native-baseline-and-language-costs).

No new runtime API, registry release or connector support is implied by this
planning document.
