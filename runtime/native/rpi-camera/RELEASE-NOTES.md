# CoAkka Raspberry Pi Camera Sample v0.2.0

Release `v0.2.0` replaces the withdrawn `v0.1.0` prerelease.

## Boundary Correction

- The release is published from the public `coakka-samples` repository.
- Public source contains only the focused camera sample project.
- No archive contains a snapshot of the private Core repository.
- Prebuilt applications remain separate archives for Raspberry Pi ARM64,
  macOS ARM64, Linux x86-64, and Windows x86-64.

## CLI Contract

- Both applications require a matching logical `--camera-id`.
- The Pi selects hardware with either `--video-device PATH` or
  `--video-index N`.
- The Pi Stream endpoint uses `--port`; its control endpoint uses `port + 1`.
- The host receives `--publisher-host`, `--publisher-port`, and `--web-port`.
- Audio is independently selectable with `--audio-device PATH|off` on the Pi
  and `--audio on|off` on the host.
- Both applications read the bearer token from `CAMERA_TOKEN` by default, or
  from the variable selected by `--token-env`. Tokens are not accepted as CLI
  values and therefore are not exposed in the process argument list.
- Both applications provide `--help`, reject duplicate and unknown options,
  validate port ranges, and reject malformed camera IDs.

## Evidence Boundary

- Raspberry Pi 5 ARM64 captured the USB webcam at 1280x720 and 640x480, and
  captured its ALSA microphone at 48 kHz mono.
- macOS ARM64 completed live display, profile switching, video-only recording,
  and MJPEG plus AAC recording against that Pi.
- Linux x86-64 completed a strict native build and CLI smoke; its live-camera
  workflow remains pending.
- Windows 11 x86-64 completed native CLI validation, connected to the live Pi,
  and served its loopback UI. Windows-side WebSocket control and recording
  remain pending.
- The Windows executable is unsigned. SmartScreen, Windows Firewall, or
  managed application-control policy can require operator action or an
  organization signature.

The browser listener remains restricted to `127.0.0.1`. The Pi control token
does not provide TLS; use the sample only on a trusted private network.
