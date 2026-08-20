# CoAkka Raspberry Pi Camera Sample v1.1.1

Release `v1.1.1` moves every bundled Runtime library to exact native generation
`2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a`. It preserves the camera,
control, browser, and recording contracts from `v1.1.0`.

## Runtime Correction

- The Pi publisher now starts each Stream Lane before preparing its bounded
  publisher record. Runtime `2.5.0` rejects the former prepare-before-start
  order instead of retaining state for a lane that is not running.
- Video and optional audio preparation report their exact status separately and
  fail closed before camera capture starts.
- The sample lane owner now calls the public stop contract before destroy on
  every normal and early-return path. A focused policy-seam regression locks the
  `stop` then `destroy` order without relying on destroy's current internals.
- Release staging validates the native release manifest and checksum set, then
  compares every bundled Runtime library byte-for-byte with the audited native
  archive. A stale scratch build cannot be substituted for a released library.
- Each platform archive carries the four canonical offline legal files. The
  generated release manifest binds their SHA-256 values, the public source
  commit, the native archive, and every platform Runtime-library hash.
- Staging also requires the public `camera-demo-v1.1.1` tag to peel to the
  recorded Samples commit; local-only source cannot enter the release.
- Each platform executable is built by one producer from `git archive` of that
  tagged tree and embeds the full source commit. Its unsigned local build
  receipt records the executable hash and format, exact Runtime byte, Git tree
  and archive hashes, CMake/Ninja inputs, C/C++ compiler bytes, and producer
  script plus its archive/receipt contract helper. Native targets accept no
  extra CMake arguments; Windows accepts only a hashed toolchain file and
  Runtime import library. Staging rejects an
  arbitrary build directory, a tampered executable, or a receipt for another
  source archive. This is auditable local evidence under a trusted build-host,
  compiler, toolchain, and operator boundary, not cryptographic
  source-to-binary attestation.
- Runtime tar members are bounded and validated before extraction; absolute or
  parent paths, links, devices, FIFOs, duplicates, and oversized members fail
  closed. Final staging uses an absent `releases/1.1.1` destination and one
  same-filesystem atomic rename, never recursive deletion of a caller path.

## Runtime Identity

| Platform | Runtime library SHA-256 |
| --- | --- |
| Raspberry Pi / Linux ARM64 | `bf32ebb908cde7ab7eade427356365ad561c1a4222a950d73097ff92329b79c1` |
| macOS ARM64 | `391d2256bd5276f7b9001ae9afa8900dd82c5d29e2d81bc0edc1949c509dc4c1` |
| Linux x86-64 | `07b246b97bad301b81cc90bb9d6f02d9ed425227bc302bc4b9039489b60d1727` |
| Windows x86-64 | `45e4832d0a4c05cce36ec2dea9cc3e32695159b6bc8c741fce9d0bee583a938f` |

The source archive is
`coakka-runtime-native-v2-2.5.0.tar.gz`, SHA-256
`1a7c33f167e03554e7eaa137b92d87f697c8dcec7186fa42b12b70460006055c`.

## Evidence Boundary

- Raspberry Pi 5 ARM64 / Debian 12: strict GCC build against the exact released
  Runtime, live V4L2 MJPEG capture, and Stream Lane publication.
- macOS ARM64: strict AppleClang build against the exact released Runtime, live
  subscription, loopback browser rendering at 1280x720, and explicit clean
  session disconnect.
- The live correction drill published 360 frames / 15,008,531 bytes; the host
  accepted 57 frames / 2,386,144 bytes and the browser accepted three frames /
  131,555 bytes. Freshness pressure dropped one source frame.
- Linux x86-64 remains strict native build and CLI smoke only.
- Windows x86-64 passes the exact `v1.1.1` cross-build, PE architecture,
  dependency, and archive gates. The previous `v1.1.0` binary has native CLI,
  live Pi connection, and loopback UI evidence, but that is historical context,
  not matching-host execution of the new bytes. The new executable remains
  unsigned and its live WebSocket-control and recording gates remain pending.

See [`SYSTEMS-AUDIT.md`](SYSTEMS-AUDIT.md) for ownership, bounds, failure law,
and residual risk.
