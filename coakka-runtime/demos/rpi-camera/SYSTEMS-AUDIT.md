# Raspberry Pi Camera Systems Audit

## P559 HTTP Runtime Native Edge Migration

Current source uses the released CoAkka HTTP Runtime `1.1.2` native host ABI 10
instead of an application-owned libuv listener and custom HTTP/WebSocket wire
parser. HTTP Runtime Core now owns inbound and outbound HTTP mechanics,
listener I/O, route and body admission, WebSocket upgrade/session/framing,
response-stream pressure, terminal events, and drain. The application retains
only camera command policy, JSON values, a four-frame latest-value display
queue, Stream Lane handoff, recorder policy, V4L2, and ALSA.

The host has one request-event reader and one WebSocket-event reader, and the
Pi control endpoint has one request-event reader. Every successful native event
lease is released by its reader before the next take. Shutdown begins drain
while readers still progress, closes application streams/sessions, stops Core,
joins readers, then destroys the native service. The outbound profile call
copies request bytes synchronously and consumes its matching terminal event
before return; a timeout cancels once and a missing terminal fails the outbound
reader closed rather than guessing success.

Source commit `062d5856d7747f80e834f7c18fbfab9886327636` passes strict
C++20 builds against exact installed Runtime native `2.5.1` and HTTP Runtime
native `1.1.2` packages on macOS ARM64 and physical Raspberry Pi 5 Linux ARM64.
The focused package tests cover loopback health, accepted and rejected Origins,
single-session WebSocket control, MJPEG response streaming, bearer-protected
profile control, native outbound profile submission, malformed input,
profile-state ownership, and concurrent idempotent stop. Linux x86-64, Windows,
live V4L2/ALSA capture through the migrated edge, sustained stress, and soak
remain open. This is source-readiness evidence, not a new camera package,
release, support, or performance claim; published camera `v1.1.0` binaries
retain their historical implementation and evidence.

## P533 Profile Status Correction

The current private source corrects a caught startup status defect: the host
previously presented its1280x720 recording preference as an active Pi profile.
The gateway now owns an unknown initial observation, and the browser shows
resolution unconfirmed. Only the existing accepted-request/matching-JPEG
handoff confirms dimensions; a frame before the Pi queue-admission response
cannot bypass that handoff. FPS is explicitly requested intent, never a measured
or effective fact, because neither a queued response nor JPEG dimensions proves
frame rate.

No Core ABI, protocol, poller, thread, queue or buffer is added. Existing mutexes
protect request/observation transitions, borrowed JPEG bytes are inspected only
within the callback, and failed/expired requests cannot confirm later. Focused
status tests cover unknown, untaken, pre-acknowledgement, malformed, mismatching,
confirmed and expired/late-frame states. The real gateway lifecycle and ordinary
source tests remain separate. Source readiness and exact matching-host package
evidence are recorded privately with the Runtime3.0.0 train; publication remains
closed. This correction does not extend camera platform or production claims.

## P497 Private Candidate Qualification

Source revision `1.1.2` is qualified privately against Runtime native
`2.5.1+f6b61768f60a08d81b1dc06625a211ffca8e89e2`. Freshly installed
Linux ARM64 and macOS ARM64 archives run the physical Raspberry Pi 5 USB-camera
and ALSA audio workflow together. The gate covers authentication and exclusive
control refusal, invalid commands and profiles, MJPEG display, slow-viewer
pressure, profile switching, audio/video recording, disconnect and cleanup.
This evidence does not change the published v1.1.0 release or broaden the
remaining platform and production-support claims below.

The application owns bounded JSON control parsing: 16 KiB input, at most
64 entries, depth 8 and 8 KiB decoded strings. Duplicate keys, invalid UTF-8
and escapes, fractional numbers and integer overflow reject. The current
source build requires the Runtime package and the application dependencies
listed in its README.

Date: 2026-08-11

## Architecture And Ownership

The Pi application owns V4L2, optional ALSA, one Stream Lane publisher per
enabled medium, profile validation, and one HTTP Runtime request-event reader.
The host owns Stream Lane subscriber workers, one HTTP request-event reader,
one WebSocket-event reader, the bounded display handoff, and one recorder
worker. HTTP Runtime Core is the authoritative owner of protocol, listener,
session, queue, pressure and drain state; the camera adapter does not infer or
duplicate those facts.

Frame producers copy borrowed Stream Lane bytes into preallocated application
slots. A transition from an empty display queue wakes the sole HTTP request
reader through the public interrupt contract; no extra application poller,
socket loop, or HTTP parser exists. Stop is idempotent and keeps the service
owner alive until both reader threads have exited and all leases are released.
The application normally stops Stream Lane callbacks before final gateway
destruction; a concurrent final callback sees the stopping state and cannot
dereference a destroyed HTTP service.

## Bounds And Memory Behavior

- V4L2 maps two to four kernel buffers once and publishes only the newest ready
  MJPEG frame. It performs no per-frame heap allocation.
- Stream Lane admits frames up to 4 MiB and uses bounded byte credit.
- The host display queue has four preallocated 4 MiB slots and one 4 MiB
  in-flight browser buffer. Old display frames are dropped for freshness.
- The recorder has four preallocated video slots and 16 fixed 1,920-byte audio
  slots. It drops oldest queued data under pressure.
- The host HTTP service admits at most eight connections and active requests,
  one MJPEG viewer, one control WebSocket, 8 KiB aggregate request headers, a
  4 KiB application control payload, two bounded response-stream chunk slots,
  and a two-frame native retained-byte ceiling sized to the configured maximum
  frame.
- The Pi HTTP service admits at most four connections and active requests. Its
  route body policy rejects profile bodies above 8 KiB; request, completion,
  and terminal queues are each bounded to four.

At the 4 MiB profile ceiling, host camera buffers reserve roughly 36 MiB before
Runtime, HTTP Runtime Core, socket, and FFmpeg process memory. The Pi additionally
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
it never runs on an HTTP event reader or capture loop. Capture and mux duration use
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

P559 evidence was collected on 2026-11-04 from source commit
`062d5856d7747f80e834f7c18fbfab9886327636`.

| Control | Result | Evidence and promotion impact |
| --- | --- | --- |
| Ownership, borrowing, lifecycle, cancellation, shutdown | pass | One owner per HTTP event reader; every successful event lease is released; outbound identity is matched; stop is drain/stop/join/destroy. Focused lifecycle and package tests pass. |
| Bounds and exhaustion | pass | Connection, active-request, queue, header, body, WebSocket payload, MJPEG chunk, retained-byte, display, recorder, timeout, and thread bounds are explicit. Capacity/stale results fail closed or retire the affected stream/session. |
| Senior systems review | pass | Reviewed allocation, event ownership, mutex/atomic handoff, monotonic deadlines, wakeups, descriptor/service lifetime, and failure paths. No app-owned network poller or protocol parser remains. |
| Expert systems review | not applicable | The slice does not change MMAP/DMA, IRQ, kernel, MMU, NUMA, io_uring, or hard-realtime behavior. Existing V4L2/ALSA ownership remains unchanged; no kernel-adjacent mechanism is justified. |
| Strict compilation | pass | AppleClang 17 on macOS ARM64 passes `-Wall -Wextra -Wpedantic -Werror`; GCC 14.2 on physical Pi 5 ARM64 passes the same strict policy for host and Pi targets. |
| Static analysis | pass | LLVM `clang-tidy` 22.1.5 passes changed implementation and tests with analyzer, bugprone, performance, and portability families as errors. The intentional white-box `.cc` include and two unrelated existing style checks are explicitly excluded. |
| Public/package black box | pass | Exact package-tree build passes 6/6 on macOS and 7/7 on Pi. The HTTP test traverses the native package for health, rejected Origin, duplicate control session, accepted WebSocket, MJPEG, bearer rejection, malformed profile, and native outbound profile delivery. |
| ASan and UBSan | pass | LLVM 22.1.5 combined ASan/UBSan build passes 6/6 with halt-on-error. AppleClang 17's sanitizer runtime stalls after interceptor initialization, so it is not used as evidence. |
| LeakSanitizer | blocked | macOS reports `detect_leaks is not supported on this platform` before `main`. Publication still requires a working matching-host leak run. |
| TSan | pass | Separate LLVM 22.1.5 TSan build passes 6/6, including concurrent gateway stop and real HTTP Runtime event readers. |
| Bounded parser fuzzing | pass | Existing camera JSON owner passes 10,000 libFuzzer runs with ASan/UBSan, `max_len=8192`, and a two-second per-input timeout. HTTP and WebSocket wire parsing is HTTP Runtime-owned and is not duplicated here. |
| Physical Raspberry Pi 5 ARM64 | pass for build/package smoke | Debian GCC 14.2 builds both ARM64 apps and passes 7/7 loopback/package tests. `coakka_camera_pi` records the exact source commit, has SHA-256 `b69f7f8c4ad820e223cad70a6f5fa65de41c66503fc20701a219a12762258dd5`, and imports Runtime, HTTP Runtime, ALSA, and system libraries; it has no libuv/uSockets dependency. Live camera/audio migration remains open. |
| macOS ARM64 installed-package identity | pass | Runtime archive SHA-256 is `da1e5713de30efb841896670dbeb310ac9e91547e9a2a25ecea3315c7d812e93`; HTTP Runtime archive SHA-256 is `620b419cbb4c6f00c32697aca28584fd30dc6ccb8a5177806fdb900a667ecac0`. The host imports `libcoakka_runtime_v2.dylib` and `libcoakka_http_host.1.dylib`, with no libuv/uSockets dependency. |
| Linux x86-64 and Windows matching-host qualification | blocked | Not run in this slice. No new support or release claim may use this source until the applicable host lanes pass. |
| Stress, pressure, fault injection, soak, live camera/audio | blocked | Focused malformed input, auth refusal, Origin refusal, duplicate session, bounded queue, and concurrent stop cases pass; sustained slow-network, disconnect, disk-full, allocation failure, camera removal, and live V4L2/ALSA evidence remain release gates. |
| Performance benchmark | not applicable | No performance mechanism or claim is introduced. The physical Pi remains the only allowed comparative baseline if a later performance gate is opened. |
| Release binary hardening and publication | not applicable | No package or binary is released by P559. Export, stripping, signing, final dependency closure, and release-matrix gates remain mandatory for a future artifact. |

The older P497 Raspberry Pi/macOS live workflow predates the HTTP Runtime
migration and cannot qualify the current edge. Open production gates therefore
include live V4L2/ALSA requalification, Linux x86-64 and Windows matching-host
tests, working leak evidence, TLS/mTLS, typed CoAkka control, sustained browser
soak, camera removal/reconnect, disk-full and quota tests, slow-network shaping,
HTTP/stream pressure fault injection, FFmpeg failure drills, signed Windows
distribution, and codec-safe production fan-out. NUMA pinning, io_uring,
lock-free queues, and kernel bypass are not justified by current cost centers
and are deliberately absent.
