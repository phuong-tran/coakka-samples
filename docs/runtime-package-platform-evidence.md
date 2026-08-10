# Runtime Package And Platform Evidence

This page records the current CoAkka Runtime artifact train and the separately
versioned package-manager registries. It is the source of truth for developers
and AI code generators selecting an OS/CPU payload or generating file-lane
code.

Current artifact generation:

```text
native runtime:   2.3.0+a83ab412
connector source: 3a84c7b
JVM/Maven:        2.3.0-ga83ab412-3a84c7b
source artifacts: 2.3.0+a83ab412-3a84c7b
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

## Native 2.3.0 Matrix

| Platform | Library | SHA-256 | Release evidence |
| --- | --- | --- | --- |
| Linux ARM64 | `libcoakka_runtime_v2.so` | `7e450b4a76ca612cf181083443d35f8c50b851d294973eb1ed34fd7868876b5e` | Native build, exact 130-export and dependency gates, File/Stream Lane tests, sanitizer checks, and public package verification pass on the ARM64 UTM host. |
| Linux x86-64 | `libcoakka_runtime_v2.so` | `2a0750f96410a035d27b9b83cfdcca574afc3c8d071af837bbf7db73e8b446a3` | Native build, exact 130-export and dependency gates, File/Stream Lane tests, sanitizer checks, and public package verification pass on the x86-64 UTM host. |
| macOS ARM64 | `libcoakka_runtime_v2.dylib` | `ce141677ebd913537dce13805ce184d71a543a1b4cfd56a24df36c51378acb8b` | Native build, export and dependency gates, File/Stream Lane public consumers, and connector package smokes pass. |
| Windows ARM64 | `libcoakka_runtime_v2.dll` | `fd2cd782acaabb70467df6c34d7f812d87415c7dee9a9898a1c335a6a16ebe97` | MSVC build, exact 130-export and dependency gates, and focused runtime tests pass on Windows 11 ARM64. |
| Windows x86-64 | `libcoakka_runtime_v2.dll` | `1f4019b285ddbd2745b52fac223dc1b7526c86a1e6e0a2a4b9a50fbf5b256403` | MSVC build, exact 130-export and dependency gates, and focused runtime tests pass under Windows 11 ARM64 x64 compatibility. |

The native archive is
`runtime/native/releases/2.3.0+a83ab412/coakka-runtime-native-v2-2.3.0.tar.gz`
with SHA-256
`7d1c58a17c0b24b547fe6339886387d4deb3f778c987970db2f85b0d9921e1ab`.
It contains all five libraries, the public headers including
`coakka/v2/file_lane.h` and `coakka/v2/stream_lane.h`, CMake metadata,
manifest, and per-file checksums.

## Connector Artifact Matrix

| Surface | Exact artifact coordinate | Native payloads | Current exact-artifact evidence |
| --- | --- | --- | --- |
| JVM | `coakka.v2:coakka-jvm-native-runtime-v2:2.3.0-ga83ab412-3a84c7b` | All five | JVM checks, embedded-native verification, packaged runtime smoke, Spring Boot tests, and Quarkus tests pass. |
| Node.js | `runtime/node/releases/2.3.0+a83ab412-3a84c7b/` | All five | Build, unit tests, package-surface verification, and packaged request/reply pass on macOS ARM64. |
| Bun | `runtime/bun/releases/2.3.0+a83ab412-3a84c7b/` | All five | Runtime request/reply, lane native-call tests, package-surface verification, and packaged request/reply pass on macOS ARM64. |
| Electron | `runtime/electron/releases/2.3.0+a83ab412-3a84c7b/` | All five | Packaged Electron main/preload intent smoke passes. |
| Python | `runtime/python/releases/2.3.0+a83ab412-3a84c7b/` | All five | Source tests, package-surface verification, and packaged request/reply pass; PyPI remains separately current at `2.1.0`. |
| Go | `runtime/go/releases/2.3.0+a83ab412-3a84c7b/` | All five | Packaged request/reply, Stream Lane tests, `go test ./...`, and public module `v1.6.0` pass. |
| C# | `runtime/csharp/releases/2.3.0+a83ab412-3a84c7b/` | Five RID assets | Package readiness, packaged request/reply/deadletter, File/Stream Lane smokes, and NuGet `2.3.0` clean-install execution pass on macOS ARM64. |
| Rust | `runtime/rust/releases/2.3.0+a83ab412-3a84c7b/` | All five | Package readiness, packaged request/reply/deadletter, and Stream Lane tests pass on macOS ARM64. |
| Swift | `runtime/swift/releases/2.3.0+a83ab412-3a84c7b/` | All five | Swift build, native-payload verification, runtime and package smokes, Stream Lane tests, and SwiftPM `v2.3.0` pass. |
| Mojo | `runtime/mojo/releases/2.3.0+a83ab412-3a84c7b/` | All five | Strict source/platform gates and native lifecycle, request/reply, and lane checks pass. |
| Zig | `runtime/zig/releases/2.3.0+a83ab412-3a84c7b/` | All five | Cross-platform compile/link gates plus native lifecycle, request/reply, and lane checks pass. |
| Tauri | `runtime/tauri/releases/2.3.0+a83ab412-3a84c7b/` | All five through Rust | Source-package, intent-command, desktop tests, and dependency-lock gates pass. |

Every release directory has a manifest and `SHA256SUMS`. The manifest records
the five-platform matrix, native source generation, connector source
generation, and artifact name.

## Registry Coordinates

npm, PyPI, and NuGet are independent publication channels. npm and NuGet
`2.3.0` are published and clean-install verified. PyPI `2.1.0` is also
published and clean-install verified:

| Registry | Current verified coordinate | Bundled native generation |
| --- | --- | --- |
| npm | `coakka-v2-connector-{node,bun,electron}@2.3.0` | `2.3.0+a83ab412` |
| PyPI | `coakka-v2-connector==2.1.0` | `2.1.0+60ddf70d` |
| NuGet | `CoAkka.Runtime==2.3.0` | `2.3.0+a83ab412` |
| Go modules | `github.com/phuong-tran/coakka-runtime-go@v1.6.0` | `2.3.0+a83ab412` |
| SwiftPM | `github.com/phuong-tran/coakka-runtime-swift@v2.3.0` | `2.3.0+a83ab412` |

npm `2.3.0`, NuGet `2.3.0`, Go `v1.6.0`, and Swift `v2.3.0` expose File Lane
and Stream Lane. PyPI `2.1.0` exposes File Lane but not Stream Lane. Select an
exact coordinate whose release receipt records the required native generation.

## File-Lane Generation Rule

Generated integrations must preserve the complete workflow:

1. Service B authorizes the operation, chooses the destination, and prepares
   the receiver.
2. Service A receives a one-use transfer grant, hashes the source, and submits
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
