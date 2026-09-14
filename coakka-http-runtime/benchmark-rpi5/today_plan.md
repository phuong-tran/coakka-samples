# Runtime Info and io_uring Benchmark Slice

Status date: 2026-09-14

## Done

- [done] Lock the Core ABI 3 runtime-info/provider source at
  `846bfdf52e71577bb321a6b9c7a81c8f2960c21c` and the connector source at
  `bede799074ccd7bab6998ad75db9dc466f9b40c9`.
- [done] Remove the temporary Linux poll-shim and public `io_uring` vocabulary
  patches from preparation. Both boundaries now belong to the locked Core;
  benchmark code does not recreate them.
- [done] Regenerate the deterministic Core and connector source archives at
  SHA-256 `5f37998ee9c2e4f369717996b3ba895c0afa8b6126eefd67f2145e3b36b92849`
  and `cf4a4699c145a4c9c67e2d46a44c6a906dab059356f98647f39083f8bb01ac0a`.
  All 7 inputs and 16 tools verify both locally and in the refreshed RPi
  staging root; the archives contain the Core provider owner and Linux arm64
  connector selector without either retired patch.
- [done] Stage and verify all 7 source inputs and 16 benchmark tools locally.
- [done] Transfer the candidate into a new Raspberry Pi 5 root at
  `/home/pi5/coakka-http-runtime-info-20260914`; verify the exact input locks
  and generate the source manifest there.
- [done] Configure the Linux arm64 candidate with HTTP/2, TLS, and `io_uring`;
  CMake found liburing 2.3 and OpenSSL 3.0.20.
- [done] Build all native and language benchmark applications. The native
  poller contract and HTTP/2 public fixture pass with both the platform backend
  and explicit `io_uring`.
- [done] Inspect the matching-host benchmark Core: ELF aarch64, 128 public
  exports, SHA-256
  `92cae3944428f20b5e4ad48fb00e61e48907e9c498d677ce82578ab3705d957c`.
  It dynamically needs liburing 2 and OpenSSL 3, so it remains benchmark-only
  and is not copied into a dependency-closed production connector package.
- [done] Keep benchmark admission temperature-driven: poll until the board is
  at or below 52 C and `vcgencmd get_throttled` is `0x0`; do not impose a
  fixed five-minute sleep.
- [done] Run the controlled short Go qualification. Core reports support;
  platform-default is requested/effective `1/1`, explicit `io_uring` is `2/2`,
  neither lane falls back, exact HTTP/2 TLS responses pass, and both stop with
  zero handler errors.
- [done] Restore host services and governors, confirm `throttled=0x0`, seal the
  124-file evidence set, copy it back intact, and verify every checksum. The
  evidence digest is
  `6983a6126cbab3c2f8a4a6929e894cae541acf980c7c1ee14e3bd38a0786ea77`.
- [done] Qualify the separate dependency-closed ABI 3 Linux arm64 package with
  and without liburing, then embed the exact Core SHA
  `78ae0a6356630117ff498455af40bd7211423580021ddea860e95e1d8803b359`
  in all four connector candidates. This production-shaped package is distinct
  from the earlier TLS benchmark image.

## Next

- [next] Run `prepare-rpi5.sh` from the refreshed source locks before widening
  the benchmark campaign; the rebuild must apply no Core system-boundary patch.
- [next] Run JVM, Python, Node.js, and Bun qualification only when widening the
  campaign beyond this initial Go/Core proof.

No registry or package-ecosystem publication is part of this slice. No ABI 2
payload is retained as a compatibility path.
