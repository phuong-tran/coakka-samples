# PyPI Python Runtime 2.5.1

Status: published to PyPI and registry-verified on August 20, 2026.

The public coordinate is `coakka-v2-connector==2.5.1`. It embeds unchanged
native generation `2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a`
for Linux ARM64/x86-64, macOS ARM64, and Windows ARM64/x86-64.

## Frozen Identity

| Field | Value |
| --- | --- |
| Connector source | `11c155586796b0fbe946df273d2bbfe8058eaec5` |
| Candidate staging commit | `1b018c8fa75673f13278047baa17b70835f67454` |
| Candidate directory | `runtime/python/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a-11c1555/` |
| Wheel | `coakka_v2_connector-2.5.1-py3-none-any.whl` |
| Size | `15395972` bytes |
| SHA-256 | `6e2a3ef527f866bd334f535705a23dc4a37186d89eac3c2e3cd047655dd75aab` |

PyPI JSON reports the exact filename, size, and digest above, `yanked=false`,
Python `>=3.11`, and license expression
`Apache-2.0 AND LicenseRef-CoAkka-Native-Artifact-1.2`. The wheel downloaded
from `files.pythonhosted.org` is byte-identical to the candidate.

## Verification

- The package suite passes 33 tests, 9 platform skips, and 4 subtests on macOS
  ARM64; wheel metadata, native payload, license, and `twine check` gates pass.
- A clean environment installs exact `2.5.1` from PyPI, loads Runtime 2.5.0 at
  git `4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a`, and completes request/reply.
- Public basic, deadletter, and hot-reload samples install `2.5.1` from PyPI
  and complete their expected paths.
- The README license section uses stable absolute URLs for Apache terms, Native
  Artifact License 1.2, the file-scope map, and notice.

Public APIs, ABI, protocol, and native bytes are unchanged. Connector Python
code remains Apache-2.0; custom terms apply only to bundled native Core files.
