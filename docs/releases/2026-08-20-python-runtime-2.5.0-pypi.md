# PyPI Python Runtime 2.5.0

Status: published to PyPI and registry-verified on August 20, 2026.

The public coordinate is `coakka-v2-connector==2.5.0`. It bundles exact native
generation `2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a` for Linux
ARM64/x86-64, macOS ARM64, and Windows ARM64/x86-64.

## Frozen Identity

| Field | Value |
| --- | --- |
| Connector source | `6b56a27b2139c8abb26483a04c2e26b14c4ab2fd` |
| Payload staging source | `eb62ec8a0d1cfa31e973ceca80db8016ff8c3a26` |
| Candidate staging commit | `54b71d56e3d284d792a9518b59d36f62e11561b0` |
| Candidate directory | `runtime/python/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a-6b56a27/` |
| Wheel | `coakka_v2_connector-2.5.0-py3-none-any.whl` |
| Size | `15395528` bytes |
| SHA-256 | `b92fba60480eb611bdb414ea848da350c3be283cb337b3b6e856941260a86a13` |

## Registry Identity

PyPI JSON reports version `2.5.0`, the exact filename, size, and SHA-256 above,
`yanked=false`, Python `>=3.11`, and license expression
`Apache-2.0 AND LicenseRef-CoAkka-Public-Artifact-1.1`. The wheel downloaded
from `files.pythonhosted.org` compares byte-for-byte with the staged candidate.

The wheel carries Apache `LICENSE` for connector material,
`NATIVE-LICENSE.md` for bundled native files, `PACKAGE-LICENSE.md` for the
file-scope map, and `NOTICE`. These are simultaneous file-scoped terms rather
than alternative package-wide licenses.

## Known README Link Defect

The `2.5.0` project description links those four embedded files using relative
paths. PyPI resolves them below the project page and currently redirects them
back to that page instead of serving the files. The wheel itself still contains
the verified license bytes and metadata above. PyPI release files are
immutable, so corrected absolute links require a later package release. The
connector source and readiness gate now reject relative registry License links. See
[Runtime Package Licensing](../package-licensing.md).

## Verification

- The dependency-local package suite passes 33 tests, 9 platform skips, and 4
  subtests on macOS ARM64.
- Wheel metadata, the exact five-platform native matrix, native digests,
  non-License documentation links, File Lane, Stream Lane, and embedded
  license files passed the publication gate and `twine check`. The gate did
  not model PyPI resolution of README-relative License links; that omission is
  corrected for later packages.
- A disposable environment installs exact `coakka-v2-connector==2.5.0` from
  `https://pypi.org/simple`, loads native runtime `2.5.0` at git
  `4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a`, and completes request/reply.
- The aggregate public artifact surface, Python artifact intake scanner, and
  staged `SHA256SUMS` pass before and after upload.

This release-day connector execution is macOS ARM64 evidence. The packaged
Linux and Windows payloads retain the matching-host native evidence recorded in
the Runtime 2.5.0 platform ledger; their presence in the wheel is not reported
as a new Python matching-host execution run.

## Install

```sh
python -m pip install coakka-v2-connector==2.5.0
```

Runnable basic, deadletter, and hot-reload consumers live in
[`coakka-samples`](https://github.com/phuong-tran/coakka-samples/tree/main/runtime/python).
