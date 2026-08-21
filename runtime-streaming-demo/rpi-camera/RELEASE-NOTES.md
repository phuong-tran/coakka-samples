# CoAkka Raspberry Pi Camera Sample v1.1.0

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

- Raspberry Pi 5 ARM64: strict build plus live V4L2/ALSA capture and profile
  switching.
- macOS ARM64: strict build plus live display, control, video-only recording,
  and MJPEG/AAC recording.
- Linux x86-64: strict native build and CLI smoke; live camera workflow pending.
- Windows 11 x86-64: native CLI, live Pi connection, and loopback UI smoke;
  WebSocket control and recording pending. The executable is unsigned.

See [`SYSTEMS-AUDIT.md`](SYSTEMS-AUDIT.md) for ownership, memory, shutdown, and
residual-risk details.
