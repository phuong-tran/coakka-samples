# Runtime Package And Platform Evidence

This page records the current CoAkka Runtime artifact train and the separately
versioned package-manager registries. It is the source of truth for developers
and AI code generators selecting an OS/CPU payload or generating file-lane
code.

Current artifact generation:

```text
native runtime:   2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be
connector source: 0ba485e8ff19f3ce23902345cb445a1f652fe3f3
connector artifacts: 2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be-0ba485e
```

## Evidence Terms

| Term | Exact meaning |
| --- | --- |
| Published artifact | The immutable file is in `coakka-publish`, checksum-pinned by `artifacts/public-artifacts.tsv`. |
| Registry-published | The exact coordinate is available from npm, PyPI, NuGet, Maven, or a Git tag. |
| Contains | The package includes a native payload for the named OS and CPU. |
| Verified | Binary format, architecture, exports, dependency policy, and digest gates pass. |
| Executed | The exact binary or package completed the named workload on the stated host. |

`Contains` and `Verified` do not imply `Executed`. Windows x86-64 execution on
the Windows 11 ARM64 test VM uses Microsoft x64 emulation and is identified as
such; it is not presented as x86-64 hardware evidence.

## Native 2.5.1 Matrix

| Platform | Library | SHA-256 | Release evidence |
| --- | --- | --- | --- |
| Linux ARM64 | `libcoakka_runtime_v2.so` | `dedacfa666c398b01e0aefa0bd9f649a6a63722645e5b822252d3e505e7fda43` | Debian 12/glibc 2.36 build and matching physical ARM64-host RuntimeInfo and focused execution pass. |
| Linux x86-64 | `libcoakka_runtime_v2.so` | `0ce69740cff0a5f7d5b2f002340ecff645c3c82f4f50d6dfdb9fb8a19e90a38b` | Debian 12/glibc 2.36 build, RuntimeInfo, exports, dependencies, and Docker/QEMU execution pass; matching x86-64 hardware remains open. |
| macOS ARM64 | `libcoakka_runtime_v2.dylib` | `277d9ff36b017f2eef2e630ac82bb9ba68f112879297e8067521fe665f82368a` | Native build, RuntimeInfo, exact 139-export, dependency, archive, and installed-package consumer gates pass. |
| Windows ARM64 | `libcoakka_runtime_v2.dll` | `0ee49c59de50dad40fa403ce2f32b59e0da05ab7677bf3d1ca8a9ccfe2f9b545` | PE architecture, exact 139-export, dependency, archive, and matching ARM64-guest lifecycle/RuntimeInfo gates pass. |
| Windows x86-64 | `libcoakka_runtime_v2.dll` | `a54e8a43089adf68f9275c83d0a4495bf8deb384c25f993cd13ef42233da573b` | PE architecture, exact 139-export, dependency, archive, and Windows-on-ARM x64 compatibility execution pass; matching x86-64 hardware remains open. |

The native archive is
`runtime/native/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be/coakka-runtime-native-v2-2.5.1.tar.gz`
with SHA-256
`da1e5713de30efb841896670dbeb310ac9e91547e9a2a25ecea3315c7d812e93`.
It contains all five libraries, the public headers including
`coakka/v2/file_lane.h` and `coakka/v2/stream_lane.h`, CMake metadata,
manifest, and per-file checksums.

Both Linux payloads fail closed above the glibc 2.36 baseline. The archive and
all five libraries pass architecture, dependency, exact 139-export, public
header, legal-bundle, checksum, and embedded build-identity verification. The
separately versioned public tool train remains at `2.4.0` below.

## Runtime Tool 2.4.0 Matrix

`coakka-client` and `coakka-runtime-inspect` are published for the same five
platforms under native generation `2.4.0+c2f53117`. Each archive is immutable
and checksum-pinned in `artifacts/public-artifacts.tsv`.

| Tool | Matching-host execution | Additional verified payloads |
| --- | --- | --- |
| `coakka-client` | Command execution passes on macOS ARM64. Linux ARM64/x86-64 pass matching-architecture Docker build and dependency gates. | All five archives pass dependency, architecture, archive, and checksum gates. |
| `coakka-runtime-inspect` | Command and `serve` smokes pass on macOS ARM64 and Linux ARM64/x86-64. | All five archives pass dependency, architecture, archive, and checksum gates. |

Matching-host Linux command execution is not recorded for `coakka-client`, and
matching-host Windows execution is not recorded for either tool. Windows
ARM64/x86-64 pass Zig cross-build, PE architecture, native dependency, archive,
and checksum gates.

## Connector Artifact Matrix

| Surface | Exact artifact coordinate | Native payloads | Current exact-artifact evidence |
| --- | --- | --- | --- |
| JVM | `io.github.phuong-tran.coakka:runtime:2.5.3` | All five | Maven Central signed publication, native identity gate, Java package checks, and clean consumers pass. |
| Node.js, Bun, Electron, Python, Go, C#, Rust, Swift, Mojo, Zig, Tauri | `runtime/<lane>/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be-0ba485e/` | All five where supported | Frozen connector packages pass the public artifact-surface verifier against the exact C1 native archive. Registry versions remain independently listed below. |
| Android | signed internal `1.2.0` candidate; not published | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` | Exact tagged AAR passed clean build, package/ABI checks, R8 name verification, and the release-minified API 36 ARM64 Runtime/request/File/Stream smoke. |

Every release directory has a manifest and `SHA256SUMS`. The manifest records
the five-platform matrix, native source generation, connector source
generation, and artifact name.

Connector source `0ba485e8ff19f3ce23902345cb445a1f652fe3f3` makes the native owner-grant contract usable from
the host-language surfaces. File transfers use transfer-scoped grants with
bounded resume while the exact receiver owner retains the record. Stream
grants admit one valid OPEN. A multi-replica fan-out enumerates owners and
uses one independently observable grant per owner; a load-balancing Service
is not a substitute for owner enumeration.

## Android Candidate Evidence

Android connector `1.2.0` is frozen at annotated Samples tag
`android-runtime-1.2.0` (`53d39fd9b6dd417374662a25437af106198aff7a`)
over Core `2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be`. The AAR
SHA-256 is `edadc38b61a47ad70de6e0a52afeec14cdac467b3cac1c7a15c1d6aa9fd4ad29`
and it contains matching runtime/JNI libraries for all four Android ABIs.

The exact release-minified AAR passed on
`google/sdk_gphone64_arm64/emu64a:16/BE2A.250530.026.D1/13818094:user/release-keys`
(API 36, `arm64-v8a`). R8 evidence checks four exact JNI/callback class names in
the mapping and 34 native plus two callback members in the final APK DEX before
executing Runtime, request/reply, File Lane, and Stream Lane. The signed bundle
is retained as internal reproducibility evidence. No Maven Central publication
is planned, so no Android coordinate is claimed or listed below.

## Registry Coordinates

Maven Central, npm, PyPI, NuGet, Go modules, and SwiftPM are independent
publication channels. Their current published and clean-install verified
coordinates are:

| Registry | Current verified coordinate | Bundled native generation |
| --- | --- | --- |
| Maven Central | `io.github.phuong-tran.coakka:runtime:2.5.3` | `2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be` |
| npm | `coakka-v2-connector-{node,bun,electron}@2.5.2` | `2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a` |
| PyPI | `coakka-v2-connector==2.5.2` | `2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a` |
| NuGet | `CoAkka.Runtime==2.5.2` | `2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a` |
| Go modules | `github.com/phuong-tran/coakka-runtime-go@v1.8.3` | `2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be` |
| SwiftPM | `github.com/phuong-tran/coakka-runtime-swift@v2.5.3` | `2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be` |

Maven Runtime `2.5.3`, npm `2.5.2`, PyPI `2.5.2`, NuGet `2.5.2`, Go `v1.8.3`,
and Swift `v2.5.3` expose typed replica-owner File and Stream Lane grants.
Select an exact coordinate whose release receipt records the required native
generation. The signed JVM audit is recorded in
[JVM Runtime Maven Central 2.5.2](releases/2026-08-20-jvm-runtime-2.5.2-maven-central.md).

NuGet Runtime `2.5.2` and Logger `1.2.3` each expose one `lib/net8.0` managed
asset and five native RID assets. The same repository-signed packages execute
their consumer smokes on .NET 8, 9, and 10; `net8.0` is the minimum supported
application target, while newer hosts are compatibility targets rather than
separate packages.

## File-Lane Generation Rule

Generated integrations must preserve the complete workflow:

1. Service B authorizes the operation, chooses the destination, and prepares
   the receiver.
2. Service A receives a transfer-scoped grant, hashes the source, and submits
   the sender job.
3. Both sides wait through the notification API instead of busy-polling.
4. Service B uses the file only after receiver `COMPLETED + OK` and digest
   verification.
5. Both sides handle timeout, cancellation, queue-full, storage, integrity,
   TLS/mTLS, and source-change failures.
6. Terminal records are forgotten after the application records the result.

Direct mode may use Linux/macOS `sendfile` or Windows `TransmitFile`. TLS and
mTLS use encrypted streaming. The file lane does not claim end-to-end zero
copy because the receiver persists data to storage.

For code and ownership details, read [Runtime File Transfer](runtime-file-transfer.md),
[Envelope And Deadletter Map](envelope-deadletter-map.md), and
[TLS And mTLS](tls-and-mtls.md).
