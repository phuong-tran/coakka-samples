# Runtime Package And Platform Evidence

This page records the current CoAkka Runtime artifact train and the separately
versioned package-manager registries. It is the source of truth for developers
and AI code generators selecting an OS/CPU payload or generating file-lane
code.

Current artifact generation:

```text
native runtime:   2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a
non-JVM source:   11c1555
JVM source:       f36c396
payload staging:  eb62ec8
non-JVM artifacts: 2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a-11c1555
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

## Native 2.5.0 Matrix

| Platform | Library | SHA-256 | Release evidence |
| --- | --- | --- | --- |
| Linux ARM64 | `libcoakka_runtime_v2.so` | `bf32ebb908cde7ab7eade427356365ad561c1a4222a950d73097ff92329b79c1` | Debian 12/glibc 2.36 native build, runtime identity, exact 139-export, dependency, archive, package, public C lifecycle, File/Stream Lane, pressure, Client, and Inspect gates pass on Raspberry Pi 5. |
| Linux x86-64 | `libcoakka_runtime_v2.so` | `07b246b97bad301b81cc90bb9d6f02d9ed425227bc302bc4b9039489b60d1727` | Debian 12/glibc 2.36 native build, runtime identity, exact 139-export, dependency, archive, package, Client, and Inspect gates pass in matching-architecture Actions. |
| macOS ARM64 | `libcoakka_runtime_v2.dylib` | `391d2256bd5276f7b9001ae9afa8900dd82c5d29e2d81bc0edc1949c509dc4c1` | Native build, exact 139-export and dependency gates, full connector conformance, and packaged consumer smokes pass. |
| Windows ARM64 | `libcoakka_runtime_v2.dll` | `5662cd77be9e5446bf530c7aedbeccd4b22e5a08b3c96acd92825014abba020f` | Zig cross-build, PE architecture, exact 139-export, dependency, archive, package, and matching-host execution gates pass. |
| Windows x86-64 | `libcoakka_runtime_v2.dll` | `45e4832d0a4c05cce36ec2dea9cc3e32695159b6bc8c741fce9d0bee583a938f` | Zig cross-build plus PE architecture, exact 139-export, dependency, digest, archive, and package gates pass. Matching-host packaged DLL command, snapshot, and bounded Inspect-server execution pass on Windows Server 2025; the focused native `6/6` suite also passes under Windows-on-ARM x64 emulation. |

The native archive is
`runtime/native/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a/coakka-runtime-native-v2-2.5.0.tar.gz`
with SHA-256
`1a7c33f167e03554e7eaa137b92d87f697c8dcec7186fa42b12b70460006055c`.
It contains all five libraries, the public headers including
`coakka/v2/file_lane.h` and `coakka/v2/stream_lane.h`, CMake metadata,
manifest, and per-file checksums.

The Linux payloads were restaged after Raspberry Pi 5 execution rejected the
first candidate for requiring `GLIBC_2.38`. Both replacements fail closed above
the glibc 2.36 baseline. Raspberry Pi 5 Debian 12 execution covers the exact
ARM64 native and tool packages; Core Actions run `32131181333` supplies the
corresponding Debian 12 x86-64 evidence.

## Runtime Tool 2.5.0 Matrix

`coakka-client` and `coakka-runtime-inspect` are published for the same five
platforms under native generation
`2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a`. Each archive is immutable
and checksum-pinned in `artifacts/public-artifacts.tsv`.

| Tool | Matching-host execution | Additional verified payloads |
| --- | --- | --- |
| `coakka-client` | Command execution passes on macOS ARM64, Linux ARM64/x86-64, and Windows ARM64/x86-64. | All five archives pass dependency, architecture, archive, and checksum gates. |
| `coakka-runtime-inspect` | Command execution passes on all five platforms; macOS ARM64, Linux ARM64/x86-64, and Windows x86-64 also pass `serve` smokes. | All five archives pass dependency, architecture, archive, and checksum gates. |

Windows x86-64 matching-host tool execution is recorded by Core Actions run
`32115663861` on Microsoft Windows Server 2025. It verifies the exact Publish
commit `d5cff2a7922470b4b33bd48cac2b472bb75acbc4`, checksums and loads both
packaged DLL sets, executes both command surfaces, and proves bounded inspect
HTTP serving and shutdown.
Those Windows archives are byte-identical to the files retained by Publish
artifact commit `53ade103faf819f180c6cb518d5d4d8c4e855861`; the Linux-only
restage did not regenerate them.

## Connector Artifact Matrix

| Surface | Exact artifact coordinate | Native payloads | Current exact-artifact evidence |
| --- | --- | --- | --- |
| JVM | `runtime/jvm/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a-f36c396/` | All five | Java 8 through current JVM checks, embedded-native verification, packaged runtime smoke, Spring Boot tests, and Quarkus tests pass. |
| Node.js | `runtime/node/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a-11c1555/` | All five | Frozen tarball, registry bytes, request/reply, File Lane, and Stream Lane gates pass. |
| Bun | `runtime/bun/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a-11c1555/` | All five | Frozen tarball, registry bytes, request/reply, File Lane, and bounded Stream Lane gates pass. |
| Electron | `runtime/electron/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a-11c1555/` | All five through Node | Frozen tarball, registry bytes, and main/preload/hidden-renderer execution pass. |
| Python | `runtime/python/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a-11c1555/` | All five | Frozen wheel, file-scope license metadata, byte-identical PyPI download, and clean registry-installed consumers pass. |
| Go | `runtime/go/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a-11c1555/` | All five | Native payload, package consumer, remote-tag request/reply, File Lane, and Stream Lane gates pass. |
| C# | `runtime/csharp/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a-11c1555/` | Five RID assets | Frozen NuGet candidate, repository signature, entry equality, and .NET 8/9/10 consumers pass. |
| Rust | `runtime/rust/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a-11c1555/` | All five | Format, Clippy, rustdoc, package readiness, and packaged File/Stream Lane execution pass. |
| Swift | `runtime/swift/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a-11c1555/` | All five | Swift build, exact payload, remote-tag runtime, File Lane, Stream Lane, and source-package gates pass. |
| Mojo | `runtime/mojo/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a-11c1555/` | All five | Strict source/platform gates and native lifecycle, request/reply, File Lane, and Stream Lane checks pass. |
| Zig | `runtime/zig/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a-11c1555/` | All five | Cross-platform compile/link plus native lifecycle, request/reply, File Lane, and Stream Lane gates pass. |
| Tauri | `runtime/tauri/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a-11c1555/` | All five through Rust | Runtime `2.5.1-source` command-source and Tauri v2 host tests pass. |

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
| npm | `coakka-v2-connector-{node,bun,electron}@2.5.1` | `2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a` |
| PyPI | `coakka-v2-connector==2.5.1` | `2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a` |
| NuGet | `CoAkka.Runtime==2.5.1` | `2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a` |
| Go modules | `github.com/phuong-tran/coakka-runtime-go@v1.8.1` | `2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a` |
| SwiftPM | `github.com/phuong-tran/coakka-runtime-swift@v2.5.1` | `2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a` |

npm `2.5.1`, PyPI `2.5.1`, NuGet `2.5.1`, Go `v1.8.1`, and Swift `v2.5.1`
expose File Lane and Stream Lane. Select an exact coordinate whose release
receipt records the required native generation.

NuGet Runtime `2.5.1` and Logger `1.2.3` each expose one `lib/net8.0` managed
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
