# CoAkka Raspberry Pi Camera Sample v1.1.0

## Private P497 candidate

The current private branch binds source revision `1.1.2` to Runtime native
`2.5.1+f6b61768f60a08d81b1dc06625a211ffca8e89e2`. Candidate packaging and
matching-host execution are coordinated privately; no public tag, release
root, checksum catalog, or support claim is changed by this work.

The candidate must execute the freshly installed Linux ARM64 Pi publisher and
macOS ARM64 host gateway together. The retained run covers real V4L2 and ALSA
capture, authentication refusal, exclusive control, unsupported-profile
refusal, MJPEG viewing, slow-viewer pressure, profile change, audio/video
recording, stop, disconnect, and residue-free shutdown.

The private P497 pair now passes that installed-package gate. Its final receipt
records real MJPEG plus AAC output, bounded slow-viewer pressure, profile
confirmation, and no remaining process or listener; it remains unpublished.

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
- Control parsing now uses a sample-owned bounded JSON object boundary with
  duplicate-key, UTF-8, nesting, integer, and overflow refusal tests. The camera
  executables no longer require a separate message/reflection runtime.

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
