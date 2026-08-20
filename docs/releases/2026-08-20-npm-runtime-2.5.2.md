# npm Runtime 2.5.2

Status: published to npm and registry-verified on August 20, 2026.

This connector correction publishes:

- `coakka-v2-connector-node@2.5.2`
- `coakka-v2-connector-bun@2.5.2`
- `coakka-v2-connector-electron@2.5.2`

The packages expose typed File receive and Stream publish owner grants,
trusted control-plane reconstruction, and exact replica-owner endpoint
pinning. Native generation remains
`2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a`.

## Frozen Identity

| Field | Value |
| --- | --- |
| Connector source | `3ae74f43d061904d3bdc38a1d84d0479cd6c43bf` |
| Annotated tag | `coakka-runtime-npm-v2.5.2` |
| Tag object | `95900d8200d1c7773d926d60f999beaadf1331a9` |
| Candidate staging commit | `d28f1f13469f378f20a0ce772c522439fe3f0b5f` |
| Candidate directory | `package-manager/npm/candidates/3ae74f4/` |

## Registry Identity

| Package | SHA-256 | npm SHA-1 | npm integrity |
| --- | --- | --- | --- |
| `coakka-v2-connector-node@2.5.2` | `d102314d75ab90603d42b4708e670665c8e46ead81cb08eaff68540dedd18b3a` | `acbe1b870615cc390ba091f6441dcf3b0c32ed04` | `sha512-smy05lQVrGy4XHxASC4tasWlWahNR2TNBjXhUy5p6/TVAB54vpq/xg4c34u4zJuani/FcaXkPEkC1mimhOkV+g==` |
| `coakka-v2-connector-bun@2.5.2` | `537524c5d6cc6f8a1a877d8f340a6c22c419a5421fffc8e6434a14b8b8f24c1e` | `7bc90137b8f168a91ef2f0bd6011142d6f62805d` | `sha512-chD74QsvY+wa6LyeVpWQHJp0Y0z83LKSpdqyLJu6+pT0ERiSgcFFUb4wDQu1TLSZ9eNnqDoLl9z+q5CFhP76gw==` |
| `coakka-v2-connector-electron@2.5.2` | `e68e10ae0b934a9f195113dae824c87400753edaa684d99fdcbe3bfdbaedf5ea` | `6744ba130804643800c136307801999228f09540` | `sha512-pXSxZ1PJtC2hcTYtR0iWwwJoWjbXb10pX5Zi/cI4bwuhUgeWs6sPH0TCEPJxDTKkYEG6BPDieFFYfA+iBZgTXg==` |

All three registry downloads are byte-identical to their frozen candidates.
The `latest` dist-tag resolves to `2.5.2`, and Electron depends on exact Node.js
`2.5.2`.

## Verification

- Registry version, license, dependency, dist-tag, SHA-1, integrity, and exact
  archive-byte gates pass.
- Clean registry installs complete Node.js and Bun package-surface plus
  request/reply execution on macOS ARM64.
- A clean Electron install completes the trusted main/preload/renderer intent
  path and keeps lane capability out of the renderer surface.
- Every package contains Apache `LICENSE`, CoAkka Native Artifact License 1.2,
  `PACKAGE-LICENSE.md`, and `NOTICE`; registry README license links use stable
  absolute public URLs.

Connector source remains Apache-2.0. The Native Artifact License 1.2 applies
only to bundled native Core files.
