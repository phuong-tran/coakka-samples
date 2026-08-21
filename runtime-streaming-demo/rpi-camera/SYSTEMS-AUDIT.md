# Raspberry Pi Camera Systems Audit v1.1.0

Date: 2026-08-11

## Architecture And Ownership

The Pi application owns V4L2, optional ALSA, one Stream Lane publisher per
enabled medium, and one private libuv control loop. The host owns Stream Lane
subscriber workers, one loopback-only libuv web loop, and one recorder worker.
Libuv is an application dependency and does not enter the Runtime C ABI.

Each libuv handle is created, used for ordinary I/O, closed, and drained by its
owning loop thread. Cross-thread producers use `uv_async_send`; the display
queue mutex also fences handle detach/close. Startup result publication uses a
release/acquire handshake. Stop is idempotent, wakes the loop, drains close
callbacks, and joins its owner thread. The application stops Stream Lane
callbacks before final gateway destruction; a concurrent final callback still
cannot dereference a detached libuv handle.

## Bounds And Memory Behavior

- V4L2 maps two to four kernel buffers once and publishes only the newest ready
  MJPEG frame. It performs no per-frame heap allocation.
- Stream Lane admits frames up to 4 MiB and uses bounded byte credit.
- The host display queue has four preallocated 4 MiB slots and one 4 MiB
  in-flight browser buffer. Old display frames are dropped for freshness.
- The recorder has four preallocated video slots and 16 fixed 1,920-byte audio
  slots. It drops oldest queued data under pressure.
- The web gateway admits at most eight clients, one MJPEG viewer, one control
  WebSocket, eight pending control writes, 16 KiB HTTP input, and 4 KiB control
  payloads.

At the 4 MiB profile ceiling, host camera buffers reserve roughly 36 MiB before
Runtime, Protobuf, libuv, socket, and FFmpeg process memory. The Pi additionally
holds the negotiated V4L2 MMAP set and Runtime window. This is bounded but not
small; operators should select 720p or below on memory-constrained devices.

## Device And Kernel Boundary

The V4L2 adapter requests MJPEG, validates negotiated format and `sizeimage`,
uses a 100 ms poll bound, drains ready buffers, records sequence gaps, and
copies one selected JPEG across the Runtime ownership boundary. Profile changes
stop streaming, release mappings, and reopen the device under the same single
owner. ALSA runs nonblocking with one fixed pending buffer and explicit xrun
recovery. These are userspace adapters; no hard-realtime or IRQ-latency promise
is made.

## Recording And Time

Accepted frames are written by one recorder worker. FFmpeg finalization is a
cold subprocess path with a 15-second deadline and one-second termination grace;
it never runs on the libuv or capture loop. Capture and mux duration use
monotonic timestamps. Wall clock is used only for a human-readable filename,
so an unset Pi/host clock may produce an inaccurate name but cannot change
timeouts or ordering.

## Security Boundary

The browser listener is hard-restricted to `127.0.0.1`. The Pi Stream and
profile-control listeners use one bearer token read from an environment
variable, but transport is plaintext. Use only a trusted private network or a
separately secured tunnel. There is no TLS, Internet exposure, browser user
authentication, multi-user authorization, or token rotation service.

The Windows binary is unsigned. Windows does not universally require
Authenticode, but SmartScreen or managed application-control policy may block
it. Release checksums establish artifact integrity, not publisher identity.

## Evidence And Residual Risk

The release matrix distinguishes strict builds from matching-host runtime
smokes. Raspberry Pi ARM64 and macOS ARM64 have the complete live workflow
evidence listed in the release notes. Linux x86-64 remains build/CLI-only.
Windows 11 x86-64 has live connection and local UI evidence, while WebSocket
control and recording remain unverified on that host.

Open production gates include TLS/mTLS, typed CoAkka control, sustained browser
soak, camera removal/reconnect, disk-full and quota tests, slow-network shaping,
FFmpeg failure drills, signed Windows distribution, and codec-safe production
fan-out. NUMA pinning, io_uring, lock-free queues, and kernel bypass are not
justified by current cost centers and are deliberately absent.
