# Runtime Registry Packages 2.5.3

Status: published and registry-verified on 2026-08-21.

This release aligns the npm, PyPI, and NuGet Runtime connectors with native
Runtime `2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be` and connector source
`0ba485e8ff19f3ce23902345cb445a1f652fe3f3`.

## Coordinates

- npm `coakka-v2-connector-node@2.5.3`
- npm `coakka-v2-connector-bun@2.5.3`
- npm `coakka-v2-connector-electron@2.5.3`
- PyPI `coakka-v2-connector==2.5.3`
- NuGet `CoAkka.Runtime@2.5.3`

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
