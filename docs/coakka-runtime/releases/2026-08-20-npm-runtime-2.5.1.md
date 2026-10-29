# npm Runtime 2.5.1

Status: published to npm and registry-verified on August 20, 2026.

This documentation-and-licensing patch publishes:

- `coakka-v2-connector-node@2.5.1`
- `coakka-v2-connector-bun@2.5.1`
- `coakka-v2-connector-electron@2.5.1`

Public APIs, ABI, protocol behavior, and the five native payloads are unchanged
from Runtime 2.5.0. The packages embed native generation
`2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a`.

## Frozen Identity

| Field | Value |
| --- | --- |
| Connector source | `11c155586796b0fbe946df273d2bbfe8058eaec5` |
| Annotated tag | `coakka-runtime-npm-v2.5.1` |
| Tag object | `37566f2c8c61bf378f8da6d7eb12c37228b62190` |
| Candidate staging commit | `1b018c8fa75673f13278047baa17b70835f67454` |
| Candidate directory | `package-manager/npm/candidates/11c1555/` |

## Registry Identity

| Package | SHA-256 | npm SHA-1 | npm integrity |
| --- | --- | --- | --- |
| `coakka-v2-connector-node@2.5.1` | `67af94b331b57e9ec4b791eae27380465ec5008319cf34afe677ae06e6465f55` | `e949c03a2a19e56b0e7dfd36123ff68b37d6e617` | `sha512-WqqBEELkkUv6U7OF9LJfc3RSYYF4zkkUN/rolx1e5yyRGjJUqxX5EedTQ9e7iesSGuBy9W5vkcIuiHIv7qLVZg==` |
| `coakka-v2-connector-bun@2.5.1` | `c2f54ce2ead17649514248e0fd3efc17f92cea1ed42307dc7474df6b7a27ac35` | `57a6b50b3cb3e2bfb86841a66f90c1139be71a7b` | `sha512-laydfgGhF7mvenXvCBo2enWsNPSAie4rKVCAm9/GEzhdZXPfIdD/j9XzzimE7BsMBFKt2PIcyr+kAZaSrANE1g==` |
| `coakka-v2-connector-electron@2.5.1` | `b8c6ebb48bcd898f99212517d99faa931672c51d41f2185b1503f0c7ca8f34b5` | `d327ea2e696ef4b59ac7b6d3407c93b133ddf65b` | `sha512-Lpl6wbglX2F1akZ40j2T8yKMw7mPuxWGXzWkJgTOf9FwkJWoa5m4d3xRxpFuBK8E5xNjGjRGuzVOV2V1JbYYFQ==` |

All three registry downloads are byte-identical to their frozen tarballs. The
`latest` dist-tag resolves to `2.5.1`, and Electron depends on exact Node.js
`2.5.1`.

## Verification

- Registry version, license, dependency, dist-tag, SHA-1, integrity, and exact
  archive-byte gates pass.
- Clean npm Node.js request/reply and deadletter consumers pass on macOS ARM64.
- Clean Bun request/reply and Electron main/preload/hidden-renderer intent
  consumers pass on macOS ARM64.
- Every package carries Apache `LICENSE`, CoAkka Native Artifact License 1.2,
  `PACKAGE-LICENSE.md`, and `NOTICE`; registry README license links use stable
  absolute public URLs.

Connector source is Apache-2.0. The Native Artifact License 1.2 applies only to
bundled native Core files and permits application use, including commercial
and production use, while reserving managed-runtime and infrastructure resale.
