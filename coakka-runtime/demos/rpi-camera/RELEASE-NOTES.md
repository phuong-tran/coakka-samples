# CoAkka Raspberry Pi Camera Sample v1.1.0

## Current Source After v1.1.0

Current source uses HTTP Runtime native SDK `2.0.1` (C ABI 10) for
UI, MJPEG, WebSocket control and both sides of profile control. The released
SDK fixes outbound classification of numeric LAN targets, so the temporary
camera workaround was removed. The source passed the exact-package test suite
on macOS ARM64 and physical Raspberry Pi 5. The physical camera delivered
frames and confirmed `1280x720 → 800x600 → 1280x720` over `192.168.1.13`
through the native outbound path.

This source update leaves the published camera v1.1.0 archives unchanged.
The binary release and previous candidate evidence below describe their own
builds and test scope.

## Previous 1.1.2 Candidate Evidence

Source revision `1.1.2` is qualified privately against Runtime native
`2.5.1+f6b61768f60a08d81b1dc06625a211ffca8e89e2`. Freshly installed
Linux ARM64 and macOS ARM64 archives run the physical Raspberry Pi 5 USB-camera
and ALSA audio workflow together. The gate covers authentication and exclusive
control refusal, invalid commands and profiles, MJPEG display, slow-viewer
pressure, profile switching, audio/video recording, disconnect and cleanup.
This evidence does not change the published v1.1.0 release or broaden the
remaining platform and production-support claims below.

That live workflow predates the HTTP Runtime migration and is not matching-host
evidence for the current HTTP edge.

The application owns bounded JSON control parsing: 16 KiB input, at most
64 entries, depth 8 and 8 KiB decoded strings. Duplicate keys, invalid UTF-8
and escapes, fractional numbers and integer overflow reject. The current
source build requires the Runtime package and the application dependencies
listed in its README.

Release `v1.1.0` promotes the focused public camera sample after a systems
audit. It supersedes the `v0.2.0` prerelease without changing the Stream Lane
wire contract.

## Runtime Corrections

- The host display queue now uses its mutex as the lifetime fence around
  cross-thread `uv_async_send`, so a final Stream Lane callback cannot race a
  closing libuv handle.
- The host gateway and Pi control server publish their startup result with an
  explicit release/acquire handshake before stop may wake the loop.
- Both applications retain bounded queues, one libuv owner thread per server,
  monotonic deadlines, and idempotent joined shutdown.

## Operator Contract

- Pi device selection remains explicit through `--video-device PATH` or
  `--video-index N`; `--camera-id` is the shared logical identity.
- `--port` selects the Stream endpoint and reserves `port + 1` for profile
  control. The host independently selects `--publisher-port` and `--web-port`.
- Audio remains optional at capture (`--audio-device PATH|off`), subscription
  (`--audio on|off`), and recording time (Audio checkbox).
- Resolution profiles are 640x480, 800x600, 1280x720, and 1920x1080 at a
  requested 30 FPS, subject to the actual V4L2 device.
- Tokens are read only from the environment named by `--token-env`.

## Evidence Boundary

- HTTP SDK 2.0.1 source update: macOS ARM64 and physical Pi 5 exact-package
  tests, plus a live video and profile-control replay with no workaround.
- Previous private candidate: live profile switching passed before the HTTP
  Runtime migration; that historical evidence cannot qualify the current edge.
- Linux x86-64: strict native build and CLI smoke; live camera workflow pending.
- Windows 11 x86-64: native CLI, live Pi connection, and loopback UI smoke;
  WebSocket control and recording pending. The executable is unsigned.

See [`SYSTEMS-AUDIT.md`](SYSTEMS-AUDIT.md) for ownership, memory, shutdown, and
residual-risk details.
