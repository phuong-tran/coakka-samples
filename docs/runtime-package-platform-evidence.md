# Runtime Package And Platform Evidence

This page records the current CoAkka Runtime artifact train and the separately
versioned package-manager registries. It is the source of truth for developers
and AI code generators selecting an OS/CPU payload or generating file-lane
code.

Current artifact generation:

```text
native runtime:   2.1.0+60ddf70d
connector source: 4782dcd
JVM/Maven:        2.1.0-g60ddf70d-4782dcd
source artifacts: 2.1.0+60ddf70d-4782dcd
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

## Native 2.1.0 Matrix

| Platform | Library | SHA-256 | Release evidence |
| --- | --- | --- | --- |
| Linux ARM64 | `libcoakka_runtime_v2.so` | `3c0cc47250e3c4ebb71633af85d205adb7bf2606d58abba0bf893a770dfde48c` | Native build, exports, dependencies, TLS/mTLS gates, file-lane runtime test, and public sample pass on the ARM64 UTM host. |
| Linux x86-64 | `libcoakka_runtime_v2.so` | `7d8781b8eae6948eee968e422dd2097dfee43d788c4cb4a3fb3e8936bd214815` | Native build, exports, dependencies, TLS/mTLS gates, file-lane runtime test, and public sample pass on the x86-64 UTM host. |
| macOS ARM64 | `libcoakka_runtime_v2.dylib` | `e95cda46cd8e5d31633d005bb8af9093b2a93c9c2d0cefc90148e188f31da6d7` | Native build, dependency gate, file-lane runtime test, and connector package smokes pass. |
| Windows ARM64 | `libcoakka_runtime_v2.dll` | `e932f870f6dd15fd36612f0ce404e4906faff47766f6ed40c328d4e12a69ebf0` | MSVC build, 115-export gate, dependencies, TLS/mTLS tests, and file-lane tests pass on Windows 11 ARM64. |
| Windows x86-64 | `libcoakka_runtime_v2.dll` | `dc9d352144fefb2d6789bc3ea49dd6fe1b3bb627be4f1277944bc51d49e2f3f9` | MSVC build, 115-export gate, dependencies, TLS/mTLS tests, and file-lane tests pass under Windows 11 ARM64 x64 emulation. |

The native archive is
`runtime/native/releases/2.1.0+60ddf70d/coakka-runtime-native-v2-2.1.0.tar.gz`
with SHA-256
`01fb5a0cb09c648391bc90171bfd49940d88febc3020770acca57352c82ae5a6`.
It contains all five libraries, the public headers including
`coakka/v2/file_lane.h`, CMake metadata, manifest, and per-file checksums.

## Connector Artifact Matrix

| Surface | Exact artifact coordinate | Native payloads | Current exact-artifact evidence |
| --- | --- | --- | --- |
| JVM/JNA/JNI | `coakka.v2:coakka-jvm-native-runtime-v2:2.1.0-g60ddf70d-4782dcd` | All five | JVM checks, embedded-native verification, packaged runtime smoke, Spring Boot tests, and Quarkus tests pass. The implementation uses JNA over the C ABI; JNI names the JVM native-bridge category. |
| Node.js | `runtime/node/releases/2.1.0+60ddf70d-4782dcd/` | All five | Build, unit tests, package-surface verification, and packaged request/reply pass on macOS ARM64. |
| Bun | `runtime/bun/releases/2.1.0+60ddf70d-4782dcd/` | All five | Runtime request/reply, file-lane FFI test, package-surface verification, and packaged request/reply pass on macOS ARM64. |
| Electron | `runtime/electron/releases/2.1.0+60ddf70d-4782dcd/` | Via exact Node package | Packaged Electron main/preload intent smoke passes. |
| Python | `runtime/python/releases/2.1.0+60ddf70d-4782dcd/` | All five | 29 tests plus four subtests pass; the packaged wheel loads runtime 2.1.0 and completes request/reply on macOS ARM64. |
| Go | `runtime/go/releases/2.1.0+60ddf70d-4782dcd/` | All five | Packaged request/reply and `go test ./...` pass. |
| C# | `runtime/csharp/releases/2.1.0+60ddf70d-4782dcd/` | Five RID assets | NuGet readiness and packaged request/reply/deadletter pass on macOS ARM64. |
| Rust | `runtime/rust/releases/2.1.0+60ddf70d-4782dcd/` | All five | Package readiness and packaged request/reply/deadletter pass on macOS ARM64. The historical Linux loader constant defect is corrected. |
| Swift | `runtime/swift/releases/2.1.0+60ddf70d-4782dcd/` | All five | Swift build, tests, runtime request/reply, transport, and packaged-consumer smokes pass on macOS ARM64. |
| Mojo | `runtime/mojo/releases/2.1.0+60ddf70d-4782dcd/` | All five | Strict source/platform gates and native lifecycle, request/reply, deadletter smoke pass. |
| Zig | `runtime/zig/releases/2.1.0+60ddf70d-4782dcd/` | All five | Linux ARM64 and Windows x86-64 compile/link gates plus native lifecycle, request/reply, and deadletter smoke pass. |
| Tauri | `runtime/tauri/releases/2.1.0+60ddf70d-4782dcd/` | All five through Rust | Source package generation and dependency lock complete; Electron and Tauri keep UI code outside the native runtime owner. |

Every release directory has a manifest and `SHA256SUMS`. The manifest records
the five-platform matrix, native source generation, connector source
generation, and artifact name.

## Registry Coordinates

npm, PyPI, and NuGet are independent publication channels. Until their 2.1.0
uploads and clean-registry installs complete, their current registry
coordinates remain:

| Registry | Current verified coordinate | Bundled native generation |
| --- | --- | --- |
| npm | `coakka-v2-connector-{node,bun,electron}@1.4.6` | `1.4.1+9e02a51d` |
| PyPI | `coakka-v2-connector==1.4.6` | `1.4.1+9e02a51d` |
| NuGet | `CoAkka.Runtime==1.4.7` | `1.4.1+9e02a51d` |

Do not generate file-lane calls against those 1.4.x registry packages. Use the
2.1.0 artifact mirror or a later registry coordinate whose release receipt
explicitly records native `2.1.0+60ddf70d` or a compatible successor.

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
