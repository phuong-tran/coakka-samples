# CoAkka Raspberry Pi Camera Sample

This sample has two native applications:

- `coakka_camera_pi` owns the Raspberry Pi V4L2 camera and optional ALSA
  microphone, then publishes bounded MJPEG and PCM Stream Lane sessions.
- `coakka_camera_host` runs on the operator computer, subscribes to the Pi,
  serves the local camera UI, and records Matroska video with optional AAC
  audio.

The web listener is intentionally restricted to `127.0.0.1`. The Pi listener
uses a bearer token and is intended for a trusted private network. This sample
does not provide TLS, Internet exposure, or multi-user browser authentication.

Release `v1.1.0` is the first audited stable sample line. Its detailed thread,
memory, device, shutdown, and evidence boundaries are in
[`SYSTEMS-AUDIT.md`](SYSTEMS-AUDIT.md).

## Distribution Boundary

The focused public source belongs in
[`coakka-samples`](https://github.com/phuong-tran/coakka-samples/tree/main/runtime-streaming-demo/rpi-camera).
Prebuilt evaluation binaries belong in the versioned
[`coakka-publish`](https://github.com/phuong-tran/coakka-publish/tree/main/samples/runtime/native/rpi-camera/releases/1.1.0)
lane. Neither repository uses GitHub Releases for this sample; merging reviewed
content to `main` is the publication event.

- Treat the source commit recorded in the Publish manifest as the authority for
  review, rebuilds, driver adaptation, and license compliance.
- Consume one architecture-specific binary archive directly from the Publish
  lane; do not use GitHub Release attachments.
- Verify `SHA256SUMS` before extraction. The manifest records the source commit,
  platform, verification level, and Windows signing state.
- Do not put binaries in `coakka-samples`, publish a Core repository snapshot,
  or ship tokens, device paths, IP addresses, recordings, or signing secrets.

Binary-only delivery is a poor fit for Raspberry Pi camera work because V4L2,
ALSA device names, distributions, and system library versions vary. Source-only
delivery is auditable but creates unnecessary setup friction. A source release
plus verified convenience binaries provides both properties.

## Supported Artifact Matrix

| Application | Platform | Artifact | Current gate |
| --- | --- | --- | --- |
| Pi publisher | Raspberry Pi OS/Debian 12, ARM64 | `coakka_camera_pi` | GCC strict build and live Pi 5 smoke |
| Host gateway | macOS, ARM64 | `coakka_camera_host` | AppleClang strict build and live camera smoke |
| Host gateway | Linux, x86_64 | `coakka_camera_host` | GCC strict native build and CLI smoke; camera workflow pending |
| Host gateway | Windows, x86_64 | `coakka_camera_host.exe` | Zig/LLVM strict build; Windows 11 CLI and live-connection/UI smoke |

An artifact whose native smoke is pending must be labeled that way. Do not
promote compile-only evidence to runtime support.

### Windows Release Status

The Windows archive is an unsigned convenience build. Windows does not require
an Authenticode signature to execute it, but Microsoft Defender SmartScreen may
show an `Unknown publisher` warning. An operator may need to select
`More info` and then `Run anyway`; Windows Firewall may also request private
network access on first use. Managed corporate devices can reject unsigned
applications entirely according to local policy.

The release manifest states the exact Windows verification level. Release
`v1.1.0` carries forward the Windows 11 native smoke: CLI validation passed,
the host connected
to a live Pi publisher, and the loopback UI was fetched locally. Windows-side
WebSocket control and recording remain pending and are not implied by that
smoke. Verify `SHA256SUMS` before running the archive.

## Runtime Dependencies

Raspberry Pi OS or Debian:

```sh
sudo apt update
sudo apt install -y libasound2 libprotobuf32 libuv1 ffmpeg v4l-utils alsa-utils
```

Ubuntu 24.04 host:

```sh
sudo apt update
sudo apt install -y libprotobuf32t64 libuv1t64 ffmpeg
```

macOS host:

```sh
brew install protobuf libuv ffmpeg
```

Windows host requires a recent `ffmpeg.exe` on `PATH`. Keep
`libcoakka_runtime_v2.dll` beside `coakka_camera_host.exe` in a binary bundle.

## Find The Camera And Microphone

Run these commands on the Raspberry Pi after plugging in the camera:

```sh
v4l2-ctl --list-devices
ls -l /dev/v4l/by-id/ 2>/dev/null || true
arecord -L
```

Use a `/dev/v4l/by-id/...` path when one exists because it remains stable when
USB enumeration order changes. Otherwise pass `--video-index N`, which maps to
`/dev/videoN`. `--camera-id` is a logical ID shared by the Pi and host apps; it
does not select a Linux device node.

Choose a Stream port from `1..65534`. The control endpoint automatically uses
the next port. For example, `--port 39092` requires both TCP `39092` and
`39093` to be free:

```sh
sudo ss -ltnp | grep -E ':(39092|39093)[[:space:]]' || true
hostname -I
```

## Run The Pi App

Generate one fresh token on a trusted machine and transfer it out of band. Do
not use a token shown in documentation or commit it to a script.

```sh
export CAMERA_TOKEN="$(openssl rand -hex 32)"
./coakka_camera_pi \
  --camera-id rpi-camera-live \
  --video-device /dev/v4l/by-id/usb-YOUR_CAMERA-video-index0 \
  --audio-device plughw:CARD=Camera,DEV=0 \
  --bind-host 0.0.0.0 \
  --port 39092 \
  --width 1280 --height 720 --fps 30
```

To run without a microphone or audio lane, pass `off` to `--audio-device`:

```sh
./coakka_camera_pi \
  --camera-id rpi-camera-live \
  --video-index 0 \
  --audio-device off \
  --bind-host 0.0.0.0 \
  --port 39092
```

Pi options:

```text
--camera-id ID           required logical ID shared with the host
--video-device PATH      stable V4L2 path; mutually exclusive with --video-index
--video-index N          maps to /dev/videoN; mutually exclusive with --video-device
--port PORT              required Stream port; control is PORT + 1
--audio-device PATH|off  ALSA device, default off
--bind-host HOST         default 0.0.0.0
--token-env NAME         default CAMERA_TOKEN
--width N                default 1280
--height N               default 720
--fps N                  default 30
```

The private profile-control endpoint uses `port + 1`, so both ports must be
available. Supported profiles are `640x480`, `800x600`, `1280x720`, and
`1920x1080`, all at a requested 30 FPS. Actual delivery rate depends on the
camera.

## Run The Host App

The host audio mode must match the Pi command. `on` subscribes to the `.audio`
session and enables the Audio checkbox for each recording. The UI can still
record without audio. `off` runs one video session and one subscriber worker.

macOS:

```sh
export CAMERA_TOKEN='<same token used on the Pi>'
mkdir -p "$HOME/Movies/CoAkka Camera"
./coakka_camera_host \
  --camera-id rpi-camera-live \
  --publisher-host 192.168.1.13 \
  --publisher-port 39092 \
  --web-port 8091 \
  --audio on \
  --recording-directory "$HOME/Movies/CoAkka Camera" \
  --ffmpeg-binary /opt/homebrew/bin/ffmpeg
```

Linux:

```sh
export CAMERA_TOKEN='<same token used on the Pi>'
mkdir -p "$HOME/Videos/CoAkka Camera"
LD_LIBRARY_PATH="$(pwd)${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" \
  ./coakka_camera_host \
  --camera-id rpi-camera-live \
  --publisher-host 192.168.1.13 \
  --publisher-port 39092 \
  --web-port 8091 \
  --audio on \
  --recording-directory "$HOME/Videos/CoAkka Camera" \
  --ffmpeg-binary /usr/bin/ffmpeg
```

Windows PowerShell:

```powershell
$expected = (Get-Content .\SHA256SUMS |
  Select-String 'coakka-camera-host-windows-x86_64.zip').Line.Split()[0]
$actual = (Get-FileHash .\coakka-camera-host-windows-x86_64.zip -Algorithm SHA256).Hash.ToLower()
if ($actual -ne $expected) { throw 'camera archive checksum mismatch' }

Expand-Archive .\coakka-camera-host-windows-x86_64.zip -DestinationPath .\coakka-camera-host
Set-Location .\coakka-camera-host\host-windows-x86_64
$env:CAMERA_TOKEN = '<same token used on the Pi>'
$recordings = Join-Path $env:USERPROFILE 'Videos\CoAkka Camera'
New-Item -ItemType Directory -Force $recordings | Out-Null
.\coakka_camera_host.exe `
  --camera-id rpi-camera-live `
  --publisher-host 192.168.1.13 `
  --publisher-port 39092 `
  --web-port 8091 `
  --audio on `
  --recording-directory $recordings `
  --ffmpeg-binary ffmpeg.exe
```

Open `http://127.0.0.1:8091/`. Use the UI to start or stop live display,
select a resolution, start or stop recording, choose audio per recording, or
disconnect the Stream Lane session. `Ctrl-C` also cancels the host session.

Host options:

```text
--camera-id ID             required; must match the Pi
--publisher-host HOST      required Pi IP address or hostname
--publisher-port PORT      required Pi Stream port
--web-port PORT            local browser port, default 8091
--token-env NAME           default CAMERA_TOKEN
--max-frame-bytes BYTES    default and required build value 4194304
--audio on|off             default off; must match Pi audio publication
--recording-directory PATH default platform temporary directory
--ffmpeg-binary PATH       default platform FFmpeg location
```

Both apps read the bearer token directly from the named environment variable;
there is intentionally no `--token` option that would expose the token in the
process list. Run either binary with `--help` to print its exact CLI contract.

## Build From Public Source

Clone the public samples repository. For macOS or Linux x86-64, unpack CoAkka
Runtime native `2.3.0` or newer and set `RUNTIME_ROOT` to that package:

```sh
git clone https://github.com/phuong-tran/coakka-samples.git
cd coakka-samples/runtime-streaming-demo/rpi-camera
export RUNTIME_ROOT=/absolute/path/to/coakka-runtime-native-v2-2.3.0
```

The generic Runtime `2.3.0` Linux ARM64 archive was built against glibc 2.38;
Raspberry Pi OS 12 provides glibc 2.36. Do not link that generic library on Pi
OS 12. Instead, unpack this release's Pi archive and use its Pi-compatible
runtime library with the public Runtime package headers:

```sh
export PI_BUNDLE_ROOT=/absolute/path/to/pi-linux-arm64
cmake -S . -B build/pi -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCOAKKA_RUNTIME_LIBRARY="$PI_BUNDLE_ROOT/libcoakka_runtime_v2.so" \
  -DCOAKKA_RUNTIME_INCLUDE_DIR="$RUNTIME_ROOT/include" \
  -DCOAKKA_CAMERA_BUILD_HOST=OFF \
  -DCOAKKA_CAMERA_BUILD_PI=ON
cmake --build build/pi --target coakka_camera_pi -j4
ctest --test-dir build/pi --output-on-failure
```

Build the host app on macOS or Linux:

```sh
cmake -S . -B build/host -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCoAkkaRuntimeNativeV2_DIR="$RUNTIME_ROOT/cmake" \
  -DCOAKKA_CAMERA_BUILD_HOST=ON \
  -DCOAKKA_CAMERA_BUILD_PI=OFF
cmake --build build/host --target coakka_camera_host -j4
ctest --test-dir build/host --output-on-failure
```

Windows source builds require target-matching Protobuf and libuv packages plus
`COAKKA_RUNTIME_IMPORT_LIBRARY`, an import library generated from the exact
Runtime DLL in the native package. The prebuilt Windows archive is the normal
evaluation path. It remains unsigned and must not be represented as natively
verified until the release matrix says so.

## Download And Verify Published Binaries

Download `SHA256SUMS` and the archive for the target machine directly from the
Publish repository. For example, on macOS ARM64:

```sh
base='https://raw.githubusercontent.com/phuong-tran/coakka-publish/main/samples/runtime/native/rpi-camera/releases/1.1.0'
curl -fLO "$base/SHA256SUMS"
curl -fLO "$base/coakka-camera-host-macos-arm64.tar.gz"
shasum -a 256 -c SHA256SUMS --ignore-missing
```

The command must report `OK` for every downloaded archive. Source review and
source builds use the `coakka-samples/runtime-streaming-demo/rpi-camera/` directory;
there is no generated GitHub Release source archive and no private Core
snapshot in the public binary lane.

The public implementation files are under this directory's `src/` folder:

- `stream_lane_camera_web_demo.cc`: role-specific CLI and Stream Lane wiring;
- `v4l2_mjpeg_camera_source.c`: bounded V4L2 MMAP source;
- `alsa_pcm_audio_source.c`: bounded ALSA PCM source;
- `stream_lane_camera_control_server.cc`: Pi profile-control listener;
- `stream_lane_camera_web_gateway.cc`: local libuv HTTP/MJPEG/WebSocket bridge;
- `stream_lane_camera_recorder.cc`: bounded recorder and FFmpeg finalizer;
- `stream_lane_camera_web_ui.cc`: embedded browser UI.

The sample is bounded to one logical camera per process. Run another Pi process
with a different `--camera-id`, video device, and port pair for a second camera.
