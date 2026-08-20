# PyPI Python Runtime 2.5.2

Status: published to PyPI and registry-verified on August 20, 2026.

The public coordinate is `coakka-v2-connector==2.5.2`. It exposes typed File
receive and Stream publish owner grants, trusted control-plane reconstruction,
and exact replica-owner endpoint pinning. It embeds unchanged native generation
`2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a` for Linux ARM64/x86-64,
macOS ARM64, and Windows ARM64/x86-64.

## Frozen Identity

| Field | Value |
| --- | --- |
| Connector source | `3ae74f43d061904d3bdc38a1d84d0479cd6c43bf` |
| Candidate staging commit | `d28f1f13469f378f20a0ce772c522439fe3f0b5f` |
| Candidate directory | `runtime/python/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a-3ae74f4/` |
| Wheel | `coakka_v2_connector-2.5.2-py3-none-any.whl` |
| Size | `15399813` bytes |
| SHA-256 | `d28123f38e60ee76fec8fb545e37b75ec94bf56ba7d2cbccd028f907aa1ae819` |

PyPI JSON reports the exact filename, size, and digest above, `yanked=false`,
Python `>=3.11`, and license expression
`Apache-2.0 AND LicenseRef-CoAkka-Native-Artifact-1.2`. The wheel downloaded
from `files.pythonhosted.org` is byte-identical to the candidate.

## Verification

- The package suite passes 36 tests, 11 platform skips, and 4 subtests on
  macOS ARM64; wheel metadata, native payload, license, and `twine check` gates
  pass.
- A clean environment installs exact `2.5.2` from PyPI, loads Runtime 2.5.0 at
  git `4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a`, and completes request/reply.
- File and Stream owner-grant live tests validate replica-owner pinning and
  reject invalid reconstructed grants.
- The README license section uses stable absolute URLs for Apache terms,
  Native Artifact License 1.2, the file-scope map, and notice.

Connector Python code remains Apache-2.0; custom terms apply only to bundled
native Core files.
