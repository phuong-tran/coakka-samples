# Runtime Registry Packages 2.5.3

Status: published and registry-verified on 2026-08-21.

This release aligns the Maven Central, npm, PyPI, and NuGet Runtime connectors
with native Runtime `2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be` and
connector source `0ba485e8ff19f3ce23902345cb445a1f652fe3f3`.

## Coordinates

- Maven Central `io.github.phuong-tran.coakka:runtime:2.5.3`
- Maven Central `io.github.phuong-tran.coakka:spring-boot-starter:2.5.3`
- Maven Central `io.github.phuong-tran.coakka:quarkus-extension:2.5.3`
- npm `coakka-v2-connector-node@2.5.3`
- npm `coakka-v2-connector-bun@2.5.3`
- npm `coakka-v2-connector-electron@2.5.3`
- PyPI `coakka-v2-connector==2.5.3`
- NuGet `CoAkka.Runtime@2.5.3`

## Maven Central Redownload Audit

The Runtime, Spring Boot starter, and Quarkus extension were downloaded again
directly from `https://repo1.maven.org/maven2` on 2026-08-21. Their public main
JAR SHA-256 values reproduce the published receipts:

| Coordinate | Main JAR SHA-256 |
| --- | --- |
| `runtime:2.5.3` | `36a3ca8d0f04a5dc41de9647c665dc80c4839dd6be974b010e6df096b11f8068` |
| `spring-boot-starter:2.5.3` | `528978b1cb3f71cedc0aa9b588a56f2a94aed7a887677efa3cbf282bd8fb9cd9` |
| `quarkus-extension:2.5.3` | `0d7f6e2f259d6777d2f6e83d651b9d7d1bf09a77906639ecad2bcbc37314626a` |

For all three coordinates, the Central JAR, POM, and Gradle module metadata are
byte-identical to the signed publication mirror. Their detached signatures
verify with OpenPGP fingerprint
`2FBD20F919F251E8D984A5EBF90740BDDBBE6638`. Both adapter POMs and module
metadata require exactly `io.github.phuong-tran.coakka:runtime:2.5.3`; neither
adapter embeds a second native Runtime.

The Central Runtime JAR contains all five advertised native libraries. Each
library matches the exact Core archive digest listed in the independent
registry audit below. A fresh Java 8 consumer resolves only Maven Central,
reports RuntimeInfo
`2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be`, and completes request/reply.
Fresh Java 17 adapter consumers resolve only Maven Central and pass on Spring
Boot `3.2.7`, `3.4.13`, and `3.5.16`, plus Quarkus `3.20.4`, `3.27.4`, and
`3.35.2`. The Quarkus matrix also verifies that its BOM does not downgrade the
Runtime dependency floor.

## Registry Integrity

Registry-downloaded npm tarballs are byte-identical to their candidates:

- Node.js SHA-256: `f4509b5ccd17580103e9d948fa78bec812861f33d1c3f001d938e520cc1aad7f`
- Bun SHA-256: `277e51baaef4c7f9676bd2a92297e53e8d18d09e7d1a259ac1202272501e6581`
- Electron SHA-256: `7f1a64a4c6bf523717979abe94a442a84dacf9b21e2726592f162acc7d847429`

The registry-downloaded PyPI wheel is byte-identical to the candidate:

- `coakka_v2_connector-2.5.3-py3-none-any.whl`
- SHA-256: `18f87998c33ba6e5bf43e0981f39f97e229e2dc74d44b3f762698ac46d6deff5`

NuGet.org repository-signs the package. The registry download therefore has a
different outer digest, but all 19 candidate ZIP entries remain byte-identical;
the only added entry is `.signature.p7s`:

- candidate SHA-256: `6d16602a5291b0d00351df122a01f4e028832f1c4b27e5827d1c557ecce781da`
- registry SHA-256: `07790498c5933f034792be540c656b542e5c529489982ef54a3a57c944a34ca7`
- `dotnet nuget verify --all`: pass

## Consumer Evidence

- clean Node.js and Bun registry installs load exact RuntimeInfo and complete
  request/reply;
- the Electron registry package resolves exact Node.js `2.5.3` and its public
  package surface;
- a clean PyPI install loads exact RuntimeInfo and completes request/reply;
- the immutable NuGet candidate passes packaged consumers on .NET 8, 9, and
  10, while the repository-signed registry package passes the clean .NET 8
  consumer with the same RuntimeInfo and request/reply result.

Android Runtime `1.2.0` is not part of this registry release. It remains a
signed internal candidate, and no Maven Central publication is planned.

## Independent Registry Redownload Recheck

The packages were downloaded again from their public package managers on
2026-08-21 and checked without using the local publication candidates:

```text
npm:   coakka-v2-connector-node@2.5.3
       coakka-v2-connector-bun@2.5.3
       coakka-v2-connector-electron@2.5.3
PyPI:  coakka-v2-connector==2.5.3
NuGet: CoAkka.Runtime@2.5.3
```

Package version `2.5.3` is the connector/package version. The native Core
embedded by these packages is the newer and current Core generation
`2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be`; there is no native Core
`2.5.3` in this release train.

The direct registry downloads reproduce the recorded outer SHA-256 values:

- Node.js: `f4509b5ccd17580103e9d948fa78bec812861f33d1c3f001d938e520cc1aad7f`
- Bun: `277e51baaef4c7f9676bd2a92297e53e8d18d09e7d1a259ac1202272501e6581`
- Electron: `7f1a64a4c6bf523717979abe94a442a84dacf9b21e2726592f162acc7d847429`
- PyPI wheel: `18f87998c33ba6e5bf43e0981f39f97e229e2dc74d44b3f762698ac46d6deff5`
- NuGet registry package: `07790498c5933f034792be540c656b542e5c529489982ef54a3a57c944a34ca7`

Every native library extracted from npm Node, npm Bun, PyPI, and NuGet matches
the exact Core archive by platform:

| Platform | SHA-256 |
| --- | --- |
| Linux ARM64 | `dedacfa666c398b01e0aefa0bd9f649a6a63722645e5b822252d3e505e7fda43` |
| Linux x86-64 | `0ce69740cff0a5f7d5b2f002340ecff645c3c82f4f50d6dfdb9fb8a19e90a38b` |
| macOS ARM64 | `277d9ff36b017f2eef2e630ac82bb9ba68f112879297e8067521fe665f82368a` |
| Windows ARM64 | `0ee49c59de50dad40fa403ce2f32b59e0da05ab7677bf3d1ca8a9ccfe2f9b545` |
| Windows x86-64 | `a54e8a43089adf68f9275c83d0a4495bf8deb384c25f993cd13ef42233da573b` |

Fresh registry consumers pass on macOS ARM64 with Node `26.0.0`, npm
`11.12.1`, Bun `1.3.14`, Python `3.12.4`, and .NET `8.0`, `9.0`, and `10.0`.
Node, Bun, Python, and all three .NET consumers report native RuntimeInfo
version `2.5.1`, git commit
`26f7944de4a4e0598845a54e4775f9463a9e33be`, backend `tcp`, and complete a
request/reply exchange. Electron resolves exact Node connector `2.5.3` from
npm and completes its main-process/renderer request/reply smoke. The NuGet
repository signature verifies successfully. No package mismatch or republish
is required.
