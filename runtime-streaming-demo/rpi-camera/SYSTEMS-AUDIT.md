# Raspberry Pi Camera Systems Audit v1.1.1

Date: 2026-08-20

## Architecture And Ownership

The Pi application owns V4L2, optional ALSA, one Stream Lane publisher per
enabled medium, and one private libuv control loop. The host owns Stream Lane
subscriber workers, one loopback-only libuv web loop, and one recorder worker.
Libuv is an application dependency and does not enter the Runtime C ABI.

Each lane is started before its publisher record is prepared. Preparation is a
lane-owned state transition; failure prevents camera or audio capture from
starting. Each libuv handle is created, used for ordinary I/O, closed, and
drained by its owning loop thread. Cross-thread producers use `uv_async_send`;
the display queue mutex also fences handle detach/close. Startup result
publication uses a release/acquire handshake. Stop is idempotent, wakes the
loop, drains close callbacks, and joins its owner thread. The application stops
Stream Lane callbacks before final gateway destruction; a concurrent final
callback still cannot dereference a detached libuv handle. The scoped lane
owner itself always calls stop before destroy, including prepare, port lookup,
and control-server startup failures. Its focused fake-policy test verifies call
order and idempotent reset independently from Runtime destroy implementation.

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

The expert boundary review found no reason to add NUMA placement, io_uring,
kernel bypass, custom drivers, DMA ownership, or IRQ control. Those mechanisms
would not address the observed lifecycle defect or the bounded sample workload.

## Artifact Provenance

Every bundled Runtime library must be byte-identical to the corresponding file
inside native release
`2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a`. The staging script validates
the structured native manifest, its checksum file, archive SHA-256, and each
platform library before packaging. The regression gate proves that one changed
Linux x86-64 library aborts staging.

The producer requires the public `camera-demo-v1.1.1` tag to peel to the named
commit, exports only `runtime-streaming-demo/rpi-camera` from that Git object,
and builds in a separate temporary directory. Every executable embeds the full
commit and receives a machine-readable receipt recording its SHA-256, target
format, exact Runtime byte, public Git tree and archive objects, CMake cache,
Ninja graph, C/C++ compiler bytes, and current producer-script hash. The bounded
archive/receipt contract helper has a separate recorded hash. Native targets
accept no extra CMake arguments. Windows accepts only an explicit
toolchain file and Runtime import library and records both hashes. The producer
also clears compiler-launcher, project-include, toolchain, compiler, and flags
environment overrides before configuration. The stager recreates the exact Git
archive and checks those fields, hashes, binary formats, and compiled marker. It
rejects arbitrary build directories, post-build tampering, a foreign source
identity, stale Runtime bytes, or extra files; receipts are preserved at release
root and inside each platform archive.

The receipt is deliberately labeled `unsigned-local-build-receipt`. It provides
fail-closed integrity and auditable input identities under the trusted local
producer boundary, but it is not a signed or remotely attested proof that a
binary came from those source bytes. Publication still trusts the reviewed
producer, host dependencies, compilers, Windows toolchain, and release operator.
The receipt makes those identities reviewable and prevents the stager from
claiming stronger source-to-binary provenance than the evidence supports.

Runtime and source tar extraction accepts only bounded regular files and
directories below the one expected root. Absolute and parent paths, links,
special files, duplicates, excessive member counts, and expanded-size overflow
fail before a file is written. Runtime manifests, checksum sets, receipts,
executables, individual archive members, expanded archives, and member counts
also have explicit admission bounds. The final release path must be an absent
`releases/1.1.1` directory; content is prepared in a same-parent temporary
directory and promoted with one atomic rename. Existing release content is
never recursively replaced. The structured manifest is generated only after
these gates. `LICENSE`, `NATIVE-LICENSE.md`, `PACKAGE-LICENSE.md`, and `NOTICE`
are copied byte-for-byte from canonical legal authority into the release root
and every platform archive.

`test_build_camera_demo_release_input.sh` proves that the producer ignores dirty
public worktree bytes and rejects native/Windows CMake option injection,
compiler-launcher environment injection, a foreign public remote, and mutable
output. `test_stage_camera_demo_release.sh` covers executable, source-archive,
Runtime, traversal, link, extra-file, and output tampering. Bash syntax,
ShellCheck, Python byte compilation, strict C++ ownership compilation, and the
canonical-to-Samples byte comparison pass on macOS ARM64. The focused owner
test also passes an ASan/UBSan halt-on-error run; Apple leak detection reports
that it is unsupported on this host and is not claimed. TSan is not applicable
to this ownership helper because it adds no shared or concurrent state.

The Raspberry Pi and macOS matching-host drill used the exact Linux ARM64 and
macOS ARM64 released Runtime bytes with the freshly built `v1.1.1` programs.
It covered V4L2 video publication, host subscription, loopback browser render,
and clean disconnect; it did not cover audio or recording. Linux x86-64 and
Windows x86-64 retain the named verification limits in the release notes;
cross-compilation is not represented as matching-host execution.

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
The build receipts are deterministic local provenance records, not signed
remote attestations; they do not defend against a malicious release operator
who can replace both producer and receipt before review.

## Evidence And Residual Risk

Raspberry Pi ARM64 and macOS ARM64 pass the exact-runtime live workflow and
clean disconnect described in the release notes. One first capture attempt
returned an invalid initial frame; an immediate bounded V4L2 device check and
the release drill then passed. Treat repeated first-frame failures as a camera
or adapter investigation trigger rather than an unbounded retry condition.

Open production gates remain TLS/mTLS, typed CoAkka control, sustained browser
soak, camera removal/reconnect, disk-full and quota tests, slow-network shaping,
FFmpeg failure drills, signed Windows distribution, and codec-safe production
fan-out. They do not block this explicitly bounded evaluation sample, but no
broader production-support claim is made.
