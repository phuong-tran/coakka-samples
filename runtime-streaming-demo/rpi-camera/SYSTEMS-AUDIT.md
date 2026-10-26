# Raspberry Pi Camera Systems Audit v1.1.0

Date: 2026-08-11

## Private P497 candidate addendum

The private P497 candidate changes no capture, Stream Lane, queue, thread,
descriptor, transport, or device-ownership law. It replaces the general
message/reflection dependency used only on the cold control path with a
sample-owned bounded JSON boundary. It binds this sample to frozen Runtime native source
`f6b61768f60a08d81b1dc06625a211ffca8e89e2`, ABI 1 with 160 exports, and
packages the Pi publisher and macOS host separately with exact Runtime images.
The packages remain private and unselected.

Matching-host evidence uses a Raspberry Pi 5 running Debian 13 ARM64 with a
real V4L2 camera and ALSA microphone, plus a macOS ARM64 host. Two pre-package
live runs cover authentication refusal, exclusive control, invalid command and
unsupported-profile refusal, bounded slow-viewer pressure, profile switching,
MJPEG delivery, audio/video recording, stop, disconnect, and natural cleanup.
The exact freshly installed candidate archives repeat that end-to-end path;
the retained recording inspection reports MJPEG video plus AAC audio and both
processes leave no listener or process residue.

Senior systems review passes: existing bounded queues, monotonic waits,
single-owner device and libuv loops, explicit cancellation, idempotent joined
shutdown, and fail-closed admission remain unchanged. The new parser performs
at most 64 linear key comparisons on the cold control path, caps input,
decoded strings and nesting, and makes no allocation proportional to anything
larger than those bounds. Expert boundary review finds no justified NUMA,
io_uring, DMA, IRQ, MMU, kernel-bypass, or hard realtime mechanism for this
userspace sample. The parser is a cohesive 694-line syntax, projection and
writer boundary with its own tests and fuzz harness; the gateway and demo
translation units remain at their existing ownership boundaries.

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
- The control JSON boundary accepts at most 16 KiB, 64 fields or array entries,
  eight nesting levels, and 8 KiB per decoded string. Duplicate top-level keys,
  malformed UTF-8, invalid escapes, non-integral profile values, and overflow
  fail closed before state changes.

At the 4 MiB profile ceiling, host camera buffers reserve roughly 36 MiB before
Runtime, libuv, socket, and FFmpeg process memory. The Pi additionally holds the
negotiated V4L2 MMAP set and Runtime window. This is bounded but not small;
operators should select 720p or below on memory-constrained devices.

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

Control JSON is parsed by the sample-owned bounded flat-object boundary rather
than a general reflection runtime. Unknown fields remain syntactically checked
and ignored; duplicate top-level keys are rejected so no component can disagree
about which command value wins.

The Windows binary is unsigned. Windows does not universally require
Authenticode, but SmartScreen or managed application-control policy may block
it. Release checksums establish artifact integrity, not publisher identity.

## Evidence And Residual Risk

Private P497 source-readiness evidence for the exact candidate includes:

- Apple Clang 17 release build with strict warnings, thin LTO, dead stripping,
  PIE, hidden visibility and an empty Mach-O export list: 4/4 tests pass;
- Raspberry Pi 5 ARM64 Debian 13, GCC 14.2 release build with strict warnings,
  LTO, PIE, RELRO/NOW, non-executable stack and stripped `.comment`: 2/2 tests
  pass;
- Cppcheck 2.20 on the parser and both control consumers: no finding;
- Homebrew Clang 22.1.5 libFuzzer with ASan, UBSan and leak detection: 200,000
  bounded inputs, no finding, 90 MiB peak RSS;
- deterministic private archive generation repeated twice with identical
  bytes, safe member verification and frozen Runtime digest verification;
- installed-package execution across the physical Pi camera/microphone and
  macOS host, including the refusal, pressure, profile, recording and cleanup
  path described above.

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
