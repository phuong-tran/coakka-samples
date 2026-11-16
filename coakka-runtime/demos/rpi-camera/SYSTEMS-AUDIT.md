# Raspberry Pi Camera Systems Audit

## Camera 1.1.2 archive verification

Samples commit `eddff0c596de0f431daab94515b86b01fbfff5a4` installs the Pi
publisher and host as separate components. Each component carries the
executable, the matching CoAkka Runtime image and the HTTP Runtime image
under the name the loader expects. The archive file lists and SHA-256
checks were verified before extracting into fresh directories. The running
processes loaded the copies inside those directories. The inputs were the
published Runtime native `2.5.1+26f7944d` and HTTP Runtime native `2.0.1`
archives, verified against the published checksums.

| Scope | Evidence |
| --- | --- |
| macOS ARM64 strict source build | 6/6 tests passed |
| Physical Pi 5 Linux ARM64 strict source build | 7/7 tests passed |
| macOS ARM64 sample ASan, UBSan and leak detection | 6/6 tests passed with LLVM 22 |
| Freshly extracted Pi publisher and macOS host archives | UI and health, WebSocket control, a complete MJPEG frame, three confirmed profiles, recordings with and without AAC, disconnect and exit 0 |

The install change reuses the libraries staged by the build and adds no
running process, thread, queue or borrowed lifetime. The live test covers
the two archive targets above. A Windows or Linux x86-64 camera archive
would require its own matching-host evidence. The packaged runtime images
were consumed as released binaries in the sample sanitizer build.

## HTTP SDK 2.0.1: LAN profile replay and workaround removal

The gateway submits one fixed POST through the installed host ABI. Core owns
address admission, connection, terminal event and cancellation; the gateway
serializes requests under its existing outbound mutex, releases the event, and
retains no borrowed bytes. The sample owns authorization input, profile intent
and confirmation by a received JPEG. There are no new threads, queues or
allocations on the frame path. The change removes the sample's private socket
path and leaves the existing stop and drain order intact.

Strict builds and installed-SDK tests passed on macOS ARM64 (6/6) and physical
Pi 5 Linux ARM64 (7/7). The sample-owned macOS suite also passed 6/6 with
LLVM 22 ASan/UBSan and leak detection, and 6/6 in a separate TSan build.
On these machines, the real camera supplied frames and confirmed `1280x720 → 800x600 → 1280x720` over `192.168.1.13`; both
processes exited cleanly. The macOS loader reported the HTTP library from
the exact staged SDK. The replay used video with audio disabled. It does not
requalify the previously published camera binaries or add target support.

The P561 and P559 sections below are historical. In particular, their blocked
numeric-LAN row describes the HTTP SDK 1.1.2 path.

## Historical: P561 Temporary HTTP 1.1.2 Numeric-Target Workaround

Source commit `46462b32026ceeaf599f4c1bb5e504451411dd1f`
keeps CoAkka HTTP Runtime `1.1.2` as the owner of the loopback UI, MJPEG,
WebSocket, Pi profile endpoint, and normal outbound requests. It adds one
version-named compatibility path for the released provider defect that treats a
non-loopback numeric target as resolver-dependent and rejects it before I/O.
Only that numeric profile-control target bypasses Runtime outbound; loopback
and hostname targets retain the ordinary Runtime path.

The workaround owns one synchronous fixed plaintext `POST /v1/profile` on the
application control-call path. It performs numeric-only address admission with
no DNS, allows no retry, uses a two-second monotonic deadline, limits both the
request and response head to 4 KiB, validates the bearer token before request
construction, and accepts only HTTP `202`. It creates no production thread and
does not add a listener, general HTTP parser, WebSocket implementation, or
application network event loop. Errors fail the profile request closed and the
existing state machine cannot report a requested profile as observed until a
matching JPEG arrives.

Strict workaround-ON and workaround-OFF builds pass 7/7 and 6/6 focused tests.
The workaround test passes 100 normal repetitions, 20 LLVM 22
ASan/UBSan/LeakSanitizer repetitions, and 20 separate LLVM 22 TSan repetitions;
cppcheck and Clang static analysis pass. The production source cross-compiles
for Linux and Windows x86_64 and ARM64. A physical Raspberry Pi 5 LAN replay
reached browser states `active:800x600` and then `active:1280x720`, with matching
Pi camera profile logs and joined host/Pi shutdown.

This is not a fix or release of HTTP Runtime. The removal gate is an exact
corrected HTTP Runtime package, an
`COAKKA_CAMERA_HTTP_RUNTIME_1_1_2_NUMERIC_TARGET_WORKAROUND=OFF` build, and the
same physical `1280x720 -> 800x600 -> 1280x720` replay through Runtime outbound.
After that gate passes, delete the CMake option, workaround module, focused
test, and gateway call site together. No camera package, binary, version,
support, performance, or HTTP Runtime release claim is made by this slice.

## P559 HTTP Runtime Native Edge Migration

The P559 migration source uses the released CoAkka HTTP Runtime `1.1.2` native
host ABI 10
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
profile-state ownership, and concurrent idempotent stop. A physical Pi 5 and
macOS ARM64 host additionally pass migrated V4L2/ALSA capture, Stream Lane
delivery, loopback MJPEG display, WebSocket control, MJPEG/AAC recording, and
joined shutdown. Remote profile switching does not pass in that migration
source: HTTP Runtime native
`1.1.2` rejects a non-loopback numeric LAN target as resolver-dependent before
request bytes are sent. Linux x86-64, Windows, sustained stress, and soak remain
open. This is source-readiness evidence, not a new camera package, release,
support, or performance claim; published camera `v1.1.0` binaries retain their
historical implementation and evidence.

## P533 Profile Status Correction

The current private source corrects a caught startup status defect: the host
previously presented its 1280x720 recording preference as an active Pi profile.
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
socket loop, or general HTTP parser exists. The P561 workaround owns only one
bounded synchronous numeric-target profile POST on the control-call path. Stop
is idempotent and keeps the service owner alive until both reader threads have
exited and all leases are released.
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
`062d5856d7747f80e834f7c18fbfab9886327636`. P561 workaround and physical
profile-switch evidence uses source commit
`46462b32026ceeaf599f4c1bb5e504451411dd1f`.

| Control | Result | Evidence and promotion impact |
| --- | --- | --- |
| Ownership, borrowing, lifecycle, cancellation, shutdown | pass | One owner per HTTP event reader; every successful event lease is released; ordinary outbound identity is matched; stop is drain/stop/join/destroy. The temporary numeric-target request retains its owned bytes through one synchronous call and closes its descriptor on every outcome. Focused lifecycle and package tests pass. |
| Bounds and exhaustion | pass | Connection, active-request, queue, header, body, WebSocket payload, MJPEG chunk, retained-byte, display, recorder, timeout, and thread bounds are explicit. The workaround adds 4 KiB request/response-head ceilings, one two-second monotonic deadline, and no retry or retained state. Capacity/stale results fail closed or retire the affected stream/session. |
| Senior systems review | pass | Reviewed allocation, event ownership, mutex/atomic handoff, monotonic deadlines, wakeups, descriptor/service lifetime, and failure paths. No app-owned network poller or general protocol parser remains; the temporary fixed POST is isolated from the HTTP/Stream event loops and adds no thread. |
| Expert systems review | not applicable | The slice does not change MMAP/DMA, IRQ, kernel, MMU, NUMA, io_uring, or hard-realtime behavior. Existing V4L2/ALSA ownership remains unchanged; no kernel-adjacent mechanism is justified. |
| Strict compilation | pass | AppleClang 17 on macOS ARM64 passes `-Wall -Wextra -Wpedantic -Werror`; GCC 14.2 on physical Pi 5 ARM64 passes the same strict policy for host and Pi targets. P561 passes both workaround-ON and workaround-OFF builds and the production workaround source cross-compiles for Linux and Windows x86_64/ARM64. |
| Static analysis | pass | LLVM `clang-tidy` 22.1.5 passes the P559 implementation and tests with analyzer, bugprone, performance, and portability families as errors. P561 cppcheck and LLVM 22 Clang analyzer runs are clean. |
| Public/package black box | pass | Exact package-tree build passes 7/7 with the workaround on and 6/6 with it off on macOS; the P559 Pi build passes 7/7. Coverage includes health, rejected Origin, duplicate control session, accepted WebSocket, MJPEG, bearer rejection, malformed profile, loopback native outbound, and the isolated temporary POST fixture. Physical LAN replay is recorded separately. |
| ASan and UBSan | pass | LLVM 22.1.5 combined ASan/UBSan build passes the P559 6/6 suite with halt-on-error. The P561 focused workaround test passes 20 repetitions under LLVM 22 ASan/UBSan. AppleClang 17's sanitizer runtime stalls after interceptor initialization, so it is not used as evidence. |
| LeakSanitizer | blocked | The focused P561 LLVM 22 run passes 20 repetitions with leak detection, but the AppleClang matching-host whole-package run reports `detect_leaks is not supported on this platform` before `main`. Publication still requires a working whole-package matching-host leak run. |
| TSan | pass | Separate LLVM 22.1.5 TSan evidence passes the P559 6/6 suite and 20 P561 focused repetitions, including concurrent gateway stop, real HTTP Runtime event readers, and the temporary fixed POST fixture. |
| Bounded parser fuzzing | pass | Existing camera JSON owner passes 10,000 libFuzzer runs with ASan/UBSan, `max_len=8192`, and a two-second per-input timeout. HTTP and WebSocket wire parsing is HTTP Runtime-owned and is not duplicated here. |
| Physical Raspberry Pi 5 ARM64 | pass for build/package and live capture | Debian GCC 14.2 builds both ARM64 apps and passes 7/7 loopback/package tests. `coakka_camera_pi` records the P559 source commit, has SHA-256 `b69f7f8c4ad820e223cad70a6f5fa65de41c66503fc20701a219a12762258dd5`, and imports Runtime, HTTP Runtime, ALSA, and system libraries; it has no libuv/uSockets dependency. A fresh source build opened the USB MJPEG camera at 1280x720/30 and mono 48 kHz ALSA input, then delivered 8,385 video frames and 26,873 audio frames before joined cancellation. P561 physical replay additionally logged `CAMERA_PROFILE profile=800x600@30` and restoration to `1280x720@30`. |
| macOS ARM64 installed-package identity and live host | pass | Runtime archive SHA-256 is `da1e5713de30efb841896670dbeb310ac9e91547e9a2a25ecea3315c7d812e93`; HTTP Runtime archive SHA-256 is `620b419cbb4c6f00c32697aca28584fd30dc6ccb8a5177806fdb900a667ecac0`. The host imports `libcoakka_runtime_v2.dylib` and `libcoakka_http_host.1.dylib`, with no libuv/uSockets dependency. P559 received 8,384 video frames and finalized a 9.962-second Matroska recording containing 1280x720 MJPEG and mono 48 kHz AAC; its SHA-256 is `a307409f3c9f5565eb3953143653e9c3f40dc77e896c27509a78a1a1c4110634`. P561 WebSocket state reached `active:800x600` and restored `active:1280x720`. |
| Remote outbound profile switching | blocked | P559 proves released HTTP Runtime `1.1.2` rejects `192.168.1.13` as resolver-dependent before sending bytes. P561 physical LAN replay passes only because the version-named numeric-target workaround handles that fixed profile POST. A corrected HTTP Runtime release, workaround-OFF replay, and workaround deletion remain mandatory; this row does not reclassify the owner defect as fixed. |
| Linux x86-64 and Windows matching-host qualification | blocked | Not run in this slice. No new support or release claim may use this source until the applicable host lanes pass. |
| Stress, pressure, fault injection, and soak | blocked | Focused malformed input, auth refusal, Origin refusal, duplicate session, bounded queue, and concurrent stop cases pass; sustained slow-network, disconnect, disk-full, allocation failure, camera removal, and soak evidence remain release gates. |
| Performance benchmark | not applicable | No performance mechanism or claim is introduced. The physical Pi remains the only allowed comparative baseline if a later performance gate is opened. |
| Release binary hardening and publication | not applicable | No package or binary is released by P559 or P561. Export, stripping, signing, final dependency closure, and release-matrix gates remain mandatory for a future artifact. |

The current live V4L2/ALSA and temporary profile path are matching-source
evidence, but they do not erase the HTTP Runtime owner defect or broaden release
claims. Open production gates therefore include a corrected and released HTTP
Runtime remote-target provider, workaround-OFF physical profile replay and
workaround deletion, Linux x86-64 and Windows
matching-host tests, working leak evidence, TLS/mTLS, typed CoAkka control,
sustained browser soak, camera removal/reconnect, disk-full and quota tests,
slow-network shaping, HTTP/stream pressure fault injection, FFmpeg failure
drills, signed Windows distribution, and codec-safe production fan-out. NUMA
pinning, io_uring, lock-free queues, and kernel bypass are not justified by
current cost centers and are deliberately absent.
