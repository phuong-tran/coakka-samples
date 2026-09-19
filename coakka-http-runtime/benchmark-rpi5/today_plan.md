# Runtime Info and io_uring Benchmark Slice

Status date: 2026-09-19

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

## Prior Follow-Up

- [done] Run `prepare-rpi5.sh` from the refreshed source locks before widening
  the benchmark campaign; the rebuild must apply no Core system-boundary patch.
- [done] Run JVM, Python, Node.js, and Bun qualification when widening the
  campaign beyond this initial Go/Core proof.

## Current Host-Inlined Campaign

- [done] Reconfirm the measurement boundary: use the connector runtime through
  the application `Builder`/`Service` path and its host-inlined native bridge;
  do not use `coakka-core.mjs`, `takeEvent()`, or the low-level Core
  `io_uring` A/B suites for this campaign.
- [done] Confirm the Raspberry Pi 5 is idle, below the 52 C admission threshold,
  and reports `throttled=0x0` before preparation.
- [done] Rebuild and verify the locked application connector inputs on the
  Raspberry Pi 5.
- [done] Qualify the Go, JVM, Python, Node.js, and Bun application pair suites;
  all 21 lanes passed identity, exact-response, lifecycle, and throughput-floor
  admission under the thermal/throttle polling policy.
- [done] Run the controlled host-inlined application campaign on the Raspberry
  Pi 5 only. Each ecosystem used a fresh reboot, one 30-second warmup, one
  30-second measurement at concurrency 8, server CPUs 0-2, and load CPU 3.
  All 21 measurement samples passed exact-response, lifecycle, thermal, and
  throttle validation; no Windows or UTM Linux VM benchmark was run.
- [done] Record the one-pass CoAkka throughput snapshots: Go 89,460.22 req/s,
  JVM 99,829.73 req/s, Python 22,146.39 req/s, Node.js 20,727.29 req/s, and Bun
  38,835.84 req/s. These are workload snapshots, not multi-round statistical
  claims.
- [done] Restore the Pi after every campaign, copy all qualification and sealed
  campaign evidence back, and verify every local checksum. Evidence digests:
  Go `2d4765cc0cabee0bbd30ad13eeff11ac5e0b83284aac11a54c12bdd9023b0dd8`,
  JVM `fe4e55cd3db5cccc280a28a151d4188305fd15149659825b947d733699a0a91b`,
  Python `406cb08008240dbbf50d8bfa2f68ac4b474392c133a04f22c189bea4b4045641`,
  Node.js `23d6a539b83fc412748ac463e9866b87efea1cf607578ea7270db4247dba5fa4`,
  and Bun `835c51b61810fbc7a9c647f5162224f5d7344cb1c628fac6d79e1e9ed97a5ede`.
- [done] Recheck the restored authority host after the final campaign: 47.7 C,
  `throttled=0x0`, all four CPU governors back on `ondemand`, and sampled host
  services active.

No registry or package-ecosystem publication is part of this slice. No ABI 2
payload is retained as a compatibility path.
