# npm Runtime 2.5.0

Status: published to npm and registry-verified on August 20, 2026.

This release publishes the JavaScript Runtime connectors over exact native
generation `2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a`:

- `coakka-v2-connector-node@2.5.0`
- `coakka-v2-connector-bun@2.5.0`
- `coakka-v2-connector-electron@2.5.0`

## Frozen Identity

| Field | Value |
| --- | --- |
| Connector source | `6b56a27b2139c8abb26483a04c2e26b14c4ab2fd` |
| Connector tag | `coakka-runtime-npm-v2.5.0` |
| Tag object | `277c2392974f836d7a463b890663ae4de09c0f4a` |
| Candidate staging commit | `df34c04806e2c69f1ad3afa15ea96f390a4865c8` |
| Candidate directory | `package-manager/npm/candidates/6b56a27/` |
| Payload staging source | `eb62ec8` |
| Native generation | `2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a` |

## Registry Identity

| Package | SHA-256 | npm SHA-1 | npm integrity |
| --- | --- | --- | --- |
| `coakka-v2-connector-node@2.5.0` | `119530628d084732aa6067494df4d72bffbea4bda3046b5faf37fd95c4875d5a` | `7c194d24584dd79277d28114464b3fcd4ff8494f` | `sha512-aYvmdSZjNPxNKW38EAxqmirvVdahB45Q4hsbyM5RLEGBCETnsdZGs0/u2/MVy3E6jFbY2gynqRYpbeYHPfW2Dg==` |
| `coakka-v2-connector-bun@2.5.0` | `65f179690064cd302976b30e48952e550e806963b3f884d3549b4e30e66070eb` | `f1bce5f5f202e257b3ed54561b17cadfddf046dc` | `sha512-OHeYkXSr6RCytSAvHL+nlXl++BxlQoHqJfnPHQ0Sy4coA8V5nPvKiZ22EsmkJT5/EyKkpcyZEHo0QYlWf1VPEA==` |
| `coakka-v2-connector-electron@2.5.0` | `d1efaccf3fa904217d1b2e9e7b31bd476add511f3fc559738619166da79d2953` | `c6c8e25b2a1063673a37b50d1c07c5eb3bedc81b` | `sha512-O64hw0giBECo8BcVQQs4Rp7hXkPVrAM5vZzDN1tEEvZIlJlVjxywZkWefUmEuR7YZYPxhNAWcB9+QVNhiffozg==` |

The three registry downloads are byte-identical to their frozen candidate
tarballs. The `latest` dist-tag points to `2.5.0` for all three packages.
Electron declares exact dependency `coakka-v2-connector-node@2.5.0`.

## Package Contract

Node.js and Bun contain exactly five Runtime payloads: Linux ARM64/x86-64,
macOS ARM64, and Windows ARM64/x86-64. Electron delegates native ownership to
the exact Node.js package. Each package declares
`SEE LICENSE IN PACKAGE-LICENSE.md` and carries Apache `LICENSE`, native terms
in `NATIVE-LICENSE.md`, package-scope terms in `PACKAGE-LICENSE.md`, and
`NOTICE`.

The packaged README presents `File Lane`, `Stream Lane`, and
`AI-Assisted Integration` as adjacent top-level sections in that order. Shared
documentation links target `coakka-samples/docs`; intentional package,
artifact, checksum, and release-evidence links target `coakka-publish`.

## Known README Link Defect

The `2.5.0` README License section links to `LICENSE`, `NATIVE-LICENSE.md`,
`PACKAGE-LICENSE.md`, and `NOTICE` using relative paths. npm does not expose
tarball members at those README-relative URLs, so the rendered links fail even
though all four files are present in each published tarball. npm packages are
immutable; the connector source and package gates require absolute public
license links for the next release. See
[Runtime Package Licensing](../package-licensing.md).

## Verification

- Node.js 24.13.0 and npm 11.6.2 package build, tests, tarball surface, and
  clean registry request/reply pass on macOS ARM64.
- Bun 1.3.14 build, test, tarball surface, and clean registry request/reply
  pass on macOS ARM64.
- Electron 42 installs the public Electron package and its exact public Node.js
  dependency, then passes the main/preload/hidden-renderer intent smoke on
  macOS ARM64.
- Registry version, license, dependency, dist-tag, SHA-1, integrity, and exact
  tarball-byte checks pass for all three coordinates.
- Candidate verification at publication rejected missing package-license files
  and stale metadata, but did not model registry resolution of README-relative
  links. The corrected gate rejects relative registry License links in
  addition to private metadata, install lifecycle scripts, transport codec
  leaks, invalid native matrices, and macOS deployment-target drift.

This publication reuses the sealed native payload matrix and its earlier
matching-host evidence. The release-day registry execution above is macOS
ARM64 evidence; payload presence for Linux and Windows is not presented as a
new matching-host execution run.
