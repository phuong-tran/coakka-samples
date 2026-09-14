# Runtime Info and io_uring Benchmark Slice

Status date: 2026-09-14

## Done

- [done] Lock the Core ABI 3 runtime-info source at
  `8ecb1704b8dafbf485f37c79f53ba5fcf4fa4416` and the connector source at
  `dc019153b09542eb3ba5fd657989613610e7a1ff`.
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

## Next

- [next] Run JVM, Python, Node.js, and Bun qualification only when widening the
  campaign beyond this initial Go/Core proof.
- [next] Build a separate dependency-closed Linux arm64 ABI 3 package before
  enabling the target in a production connector package.

No registry or package-ecosystem publication is part of this slice. No ABI 2
payload is retained as a compatibility path.
