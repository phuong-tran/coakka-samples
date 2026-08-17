# Runtime Package And Platform Evidence

This page records the current CoAkka Runtime artifact train and the separately
versioned package-manager registries. It is the source of truth for developers
and AI code generators selecting an OS/CPU payload or generating file-lane
code.

Current artifact generation:

```text
native runtime:   2.4.0+c2f53117
connector source: 0afb5e9
JVM/Maven Central: 2.4.1
source artifacts: 2.4.0+c2f53117-0afb5e9
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

## Native 2.4.0 Matrix

| Platform | Library | SHA-256 | Release evidence |
| --- | --- | --- | --- |
| Linux ARM64 | `libcoakka_runtime_v2.so` | `9ccd618dbb18fb32a0d7201f13a3163de175c7037c3e5325e84824bb32e1843c` | Native build, runtime identity, exact 133-export, dependency, archive, and package verification pass on the ARM64 UTM host. |
| Linux x86-64 | `libcoakka_runtime_v2.so` | `465e831fa564cde87fe3af29390071e4241390e1edcd0153c55ce00017f2c248` | Native build, runtime identity, exact 133-export, dependency, archive, and package verification pass on the x86-64 UTM host. |
| macOS ARM64 | `libcoakka_runtime_v2.dylib` | `5ca37b5f6d5182d4bd25284785c6b386114857074c91ab9dbefecf0dedda637c` | Native build, exact 133-export and dependency gates, full connector conformance, and packaged consumer smokes pass. |
| Windows ARM64 | `libcoakka_runtime_v2.dll` | `ae26021aac51ae19d06e317b9ce5a43befa9ef1bc8997e6bbd238e09036df3f9` | Zig cross-build plus PE architecture, exact 133-export, dependency, digest, archive, and package gates pass; matching-host execution is not yet recorded for 2.4.0. |
| Windows x86-64 | `libcoakka_runtime_v2.dll` | `795615adb861b74d9c017d480a377a08cd355e1fb83648f06b43ee85c5f049d6` | Zig cross-build plus PE architecture, exact 133-export, dependency, digest, archive, and package gates pass; matching-host execution is not yet recorded for 2.4.0. |

The native archive is
`runtime/native/releases/2.4.0+c2f53117/coakka-runtime-native-v2-2.4.0.tar.gz`
with SHA-256
`e121c691833beba44a53891130d75f2032cf50c7d37020b8c98f801d13a9ad32`.
It contains all five libraries, the public headers including
`coakka/v2/file_lane.h` and `coakka/v2/stream_lane.h`, CMake metadata,
manifest, and per-file checksums.

## Runtime Tool 2.4.0 Matrix

`coakka-client` and `coakka-runtime-inspect` are published for the same five
platforms under native generation `2.4.0+c2f53117`. Each archive is immutable
and checksum-pinned in `artifacts/public-artifacts.tsv`.

| Tool | Matching-host execution | Additional verified payloads |
| --- | --- | --- |
| `coakka-client` | macOS ARM64 command and dependency gates pass. | Linux ARM64/x86-64 pass matching-architecture Docker build plus dependency, archive, and checksum gates. Windows ARM64/x86-64 pass cross-build, PE architecture, dependency, archive, and checksum gates. |
| `coakka-runtime-inspect` | macOS ARM64 and Linux ARM64/x86-64 command plus `serve` smokes pass. | Windows ARM64/x86-64 pass cross-build, PE architecture, dependency, archive, and checksum gates. |

Matching-host Linux or Windows command execution is not recorded for the
`coakka-client` archives. Matching-host Windows execution is not recorded for
the inspect archives.

## Connector Artifact Matrix

| Surface | Exact artifact coordinate | Native payloads | Current exact-artifact evidence |
| --- | --- | --- | --- |
| JVM | `io.github.phuong-tran.coakka:runtime:2.4.1` | All five | Signed Maven Central bundle validation, clean Java 8 and Java 26 consumers, JVM checks, embedded-native verification, packaged runtime smoke, Spring Boot tests, and Quarkus tests pass on macOS ARM64. |
| Node.js | `runtime/node/releases/2.4.0+c2f53117-0afb5e9/` | All five | Build, unit tests, package-surface verification, and packaged request/reply pass on macOS ARM64. |
| Bun | `runtime/bun/releases/2.4.0+c2f53117-0afb5e9/` | All five | Runtime request/reply, lane native-call tests, package-surface verification, and packaged request/reply pass on macOS ARM64. |
| Electron | `runtime/electron/releases/2.4.0+c2f53117-0afb5e9/` | All five through Node | Packaged Electron main/preload intent smoke passes on macOS ARM64. |
| Python | `runtime/python/releases/2.4.0+c2f53117-0afb5e9/` | All five | Source tests, package readiness, clean local wheel request/reply, and File/Stream Lane tests pass on macOS ARM64. |
| Go | `runtime/go/releases/2.4.0+c2f53117-0afb5e9/` | All five | Packaged request/reply, Stream Lane tests, and `go test ./...` pass; public module publication remains separate. |
| C# | `runtime/csharp/releases/2.4.0+c2f53117-0afb5e9/` | Five RID assets | Package readiness and clean local NuGet request/reply/deadletter execution pass on macOS ARM64. |
| Rust | `runtime/rust/releases/2.4.0+c2f53117-0afb5e9/` | All five | Package readiness, packaged request/reply/deadletter, and Stream Lane tests pass on macOS ARM64. |
| Swift | `runtime/swift/releases/2.4.0+c2f53117-0afb5e9/` | All five | Swift build, native-payload verification, runtime tests, and source-package smokes pass on macOS ARM64; SwiftPM publication remains separate. |
| Mojo | `runtime/mojo/releases/2.4.0+c2f53117-0afb5e9/` | All five | Strict source/platform gates and native lifecycle, request/reply, and lane checks pass. |
| Zig | `runtime/zig/releases/2.4.0+c2f53117-0afb5e9/` | All five | Cross-platform compile/link gates plus native lifecycle, request/reply, and lane checks pass. |
| Tauri | `runtime/tauri/releases/2.4.0+c2f53117-0afb5e9/` | All five through Rust | Source-package, intent-command, desktop tests, and dependency-lock gates pass. |

Every release directory has a manifest and `SHA256SUMS`. The manifest records
the five-platform matrix, native source generation, connector source
generation, and artifact name.

## Android Candidate Evidence

Android connector `1.1.0` is staged at
`maven/android/releases/1.1.0+345e97b2/`. Its AAR embeds native package
generation `2.3.0+345e97b2` and matching runtime/JNI libraries for
`arm64-v8a` and `x86_64`. The exact file passes build, unit-test, lint, intake,
checksum, and recursive package-surface gates.

This entry is packaged-file evidence only. No Android device or emulator
execution is recorded, so the AAR is absent from `artifacts/public-artifacts.tsv`
and is not a current package coordinate. Promotion requires the exact AAR
digest to complete open, route application, start, one terminal request
outcome, and close on a named Android image and ABI.

## Registry Coordinates

npm, PyPI, and NuGet are independent publication channels. Their current
published and clean-install verified coordinates are:

| Registry | Current verified coordinate | Bundled native generation |
| --- | --- | --- |
| npm | `coakka-v2-connector-{node,bun,electron}@2.4.0` | `2.4.0+c2f53117` |
| PyPI | `coakka-v2-connector==2.4.0` | `2.4.0+c2f53117` |
| NuGet | `CoAkka.Runtime==2.4.1` | `2.4.0+c2f53117` |
| Go modules | `github.com/phuong-tran/coakka-runtime-go@v1.7.1` | `2.4.0+c2f53117` |
| SwiftPM | `github.com/phuong-tran/coakka-runtime-swift@v2.4.1` | `2.4.0+c2f53117` |

npm `2.4.0`, PyPI `2.4.0`, NuGet `2.4.1`, Go `v1.7.1`, and Swift `v2.4.0`
expose File Lane and Stream Lane. Select an exact coordinate whose release
receipt records the required native generation.

NuGet Runtime `2.4.1` and Logger `1.2.3` each expose one `lib/net8.0` managed
asset and five native RID assets. The same repository-signed packages execute
their consumer smokes on .NET 8, 9, and 10; `net8.0` is the minimum supported
application target, while newer hosts are compatibility targets rather than
separate packages.

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
