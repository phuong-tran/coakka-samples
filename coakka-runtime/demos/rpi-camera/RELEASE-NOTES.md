# CoAkka Raspberry Pi Camera Sample v1.1.0

## Current Source After v1.1.0

Current public source uses the released CoAkka HTTP Runtime `1.1.2` native host
ABI 10 for the loopback UI, MJPEG stream, WebSocket control, normal outbound
profile request, and Pi profile endpoint. The application no longer links libuv
or owns an HTTP/WebSocket listener or general wire parser. The native HTTP Core
owns protocol, listener, session, bounded pressure and drain mechanics; camera
policy, Stream Lane, V4L2/ALSA and recording remain application-owned.

Source commit `46462b32026ceeaf599f4c1bb5e504451411dd1f` has focused
exact-package build and loopback HTTP/WebSocket evidence on macOS ARM64 and
physical Raspberry Pi 5 Linux ARM64, plus ASan/UBSan and TSan evidence on
macOS. The same source also passes physical Pi USB-camera and ALSA delivery to
the macOS host, live MJPEG display, WebSocket control, and MJPEG/AAC recording.
Remote profile switching additionally passes through a temporary, bounded
numeric-target workaround because HTTP Runtime native `1.1.2` rejects that LAN
target before sending the outbound request. The owner defect remains open. The
workaround must be disabled and deleted after a corrected HTTP Runtime release
passes the same physical `1280x720 -> 800x600 -> 1280x720` replay. This does not
change, rebuild, or requalify the published camera `v1.1.0` archives. The
libuv statements below describe that historical binary release.

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

- Current-source Raspberry Pi 5 ARM64 and macOS ARM64: strict exact-package
  builds plus live V4L2/ALSA capture, Stream Lane delivery, MJPEG display,
  WebSocket control, and MJPEG/AAC recording.
- Current-source remote profile switching: physical LAN replay passes through
  the default-on, version-named `1.1.2` numeric-target workaround. It is not
  evidence that the released HTTP Runtime owner defect is fixed.
- Workaround removal gate: install a corrected HTTP Runtime release, build with
  `COAKKA_CAMERA_HTTP_RUNTIME_1_1_2_NUMERIC_TARGET_WORKAROUND=OFF`, replay the
  physical profile cycle through Runtime outbound, then delete the workaround
  module, option, test, and call site.
- Previous private candidate: live profile switching passed before the HTTP
  Runtime migration; that historical evidence cannot qualify the current edge.
- Linux x86-64: strict native build and CLI smoke; live camera workflow pending.
- Windows 11 x86-64: native CLI, live Pi connection, and loopback UI smoke;
  WebSocket control and recording pending. The executable is unsigned.

See [`SYSTEMS-AUDIT.md`](SYSTEMS-AUDIT.md) for ownership, memory, shutdown, and
residual-risk details.
