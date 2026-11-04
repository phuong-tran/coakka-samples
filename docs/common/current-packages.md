# Current Packages

This page lists current public packages for CoAkka Runtime, CoAkka HTTP
Runtime, and CoAkka Logger.

Every runtime package below is a connector into the same polyglot,
multi-language, multi-platform CoAkka Runtime ecosystem. A Python, Go, C#,
Swift, or JavaScript package is not a separate language-specific CoAkka
runtime; each projects the same native core and public runtime contract into
its host language.

Package versions are independent across the ecosystem. A NuGet package, npm
package, PyPI package, Go module, and SwiftPM tag do not need to share the same
version number. Each lane follows the release cadence of its connector,
packaging surface, and native payload.

Maven Central carries JVM Runtime, Spring Boot starter and Quarkus extension
`3.2.1`. Both adapters depend exactly on Runtime `3.2.1`, which bundles
Core `3.2+f9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45` (ABI 1).
See the [Maven release record](../coakka-runtime/releases/2026-11-05-jvm-spring-quarkus-runtime-3.2.1-maven-central.md).
The frozen checksum-pinned 2.x connector archives retain native generation
`2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be`.
Logger JVM remains `1.2.2` over native logger `1.2.1+f50756ebff0d`;
NuGet Logger remains `1.2.3`. Logger and other products keep separate versions.

NuGet `CoAkka.Runtime 3.2.1` includes Core generation 3.2,
build `f9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45`, ABI 1, for .NET 8 or later.
See the [C# 3.2.1 release](../coakka-runtime/releases/2026-11-05-nuget-csharp-runtime-3.2.1.md).

PyPI `coakka-v2-connector==3.2.1` includes Core generation 3.2, build
`f9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45`, ABI 1. Five platform wheels support
GIL-enabled CPython 3.11 or later. See the [Python 3.2.1 release](../coakka-runtime/releases/2026-11-05-pypi-python-runtime-3.2.1.md).

Node.js, Bun and Electron Runtime `3.2.1` are available on npm. Node.js and
Bun include Core `3.2`, build `f9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45`, ABI 1.
Electron installs Node.js `3.2.1` as its exact dependency.
See the [npm release record](../coakka-runtime/releases/2026-11-05-npm-node-bun-electron-runtime-3.2.1.md).

Go is published as `github.com/phuong-tran/coakka-runtime-go/v3@v3.2.1`, with
bundled Core generation 3.2, build `f9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45`, ABI 1.
Use the existing `/v3` import path and a C11 cgo compiler. Go 1.22.12 is the
baseline; native Windows ARM64 uses Go 1.25.5 and a matching C compiler.
See the [Go release record](../coakka-runtime/releases/2026-11-05-go-runtime-3.2.1-github.md).

SwiftPM Runtime 3.2.1 is [released on GitHub](../coakka-runtime/releases/2026-11-05-swift-runtime-3.2.1-github.md),
with bundled Core generation 3.2, build `f9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45`, ABI 1.
Use product `CoAkkaRuntime` and Swift 6.0 or later. The package includes five
native images; Windows ARM64 remains an evaluation preview.

The `2.5.3` connector source is `0ba485e8ff19f3ce23902345cb445a1f652fe3f3`;
the non-JVM archive mirrors are checksum-pinned independently.

Historical Runtime connector release `2.5.3` introduced the C1-backed replica-owner API. The supported
host connectors expose typed File receive and Stream publish grants, reconstruct
trusted grants from a control plane, and pin the resulting lane to the exact
replica endpoint that prepared it. Fan-out to all replicas therefore means one
independent owner grant, transfer or session, outcome, retry budget, and
pressure state per replica; it is not repeated submission through a
load-balancing Service.

For exact package contents, matching-host execution, and known platform gaps,
use [Runtime Package And Platform Evidence](../coakka-runtime/reference/runtime-package-platform-evidence.md).

## Runtime Addons

The [Runtime 3.0.0 native addon compatibility check](../coakka-runtime/guides/native-addons-runtime3.md)
records all twelve providers on WSL2 and the eleven Windows packages, including
the Windows WebDAV/SFTP limitations. No new addon version was published by this check.

[`runtime-addons/`](../coakka-runtime/guides/runtime-addons.md) is the independent release family for
optional native capabilities that compose with Runtime without entering the
default runtime package. Addons carry their own versions, compatibility
manifests, native dependency closure, and matching-host evidence.

The current Artifact Source Addons acquire a pinned external file, verify and
stage it locally, then compose with File Lane for bounded peer delivery. They
exist because File Lane transfers an already-realized file but intentionally
does not embed S3, Hugging Face, SFTP, or other provider clients in runtime
core. High-level language addon connectors are ready to wrap over the public C
ABI but remain demand-driven and are not currently released.

The 11-addon artifact-source wave is published at native
`1.1.0+d1032f6d`. HTTPS, S3/MinIO, Azure Blob, GCS, WebDAV, OCI Distribution,
Hugging Face Hub, GitHub release assets, Google Drive, and Dropbox carry five
native targets and require Runtime `2.4.0+`. Local Drop carries the three POSIX
targets. SFTP is independently published at replacement native
`1.2.0+88b9a047` and requires Runtime `2.3.0+`.

```text
coakka-runtime/addons/artifact-publisher-<source>/native/releases/1.1.0+d1032f6d/
runtime-addons/artifact-publisher-sftp/native/releases/1.2.0+88b9a047/
  coakka-runtime-addon-artifact-publisher-sftp-native-1.2.0.tar.gz
```

Run one exact native consumer with `bash run.sh coakka-runtime addons <addon>` or the
complete matrix with `bash run.sh coakka-runtime addons all published`.

## Package And Source Entrypoints

| Channel | Runtime package | Logger package | Sample or guide |
| --- | --- | --- | --- |
| Maven Central | [`runtime` 3.2.1](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/runtime/3.2.1) | [`coakka.logger` 1.2.2](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/logger/1.2.2) | [Java/Kotlin 3.2.1 first run](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/jvm/registry-basic); `bash run.sh coakka-logger jvm basic` |
| NuGet | [`CoAkka.Runtime` 3.2.1](https://www.nuget.org/packages/CoAkka.Runtime/3.2.1) | [`CoAkka.Logger` 1.2.3](https://www.nuget.org/packages/CoAkka.Logger/1.2.3) | [C# 3.2.1 install](../coakka-runtime/releases/2026-11-05-nuget-csharp-runtime-3.2.1.md#install); `bash run.sh coakka-logger csharp basic` |
| npm | [`coakka-v2-connector-node` 3.2.1](https://www.npmjs.com/package/coakka-v2-connector-node/v/3.2.1) | [`coakka-logger-node` 1.2.7](https://www.npmjs.com/package/coakka-logger-node/v/1.2.7) | [3.2.1 first run](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/node/registry-basic) |
| npm (Bun) | [`coakka-v2-connector-bun` 3.2.1](https://www.npmjs.com/package/coakka-v2-connector-bun/v/3.2.1) | [`coakka-logger-bun` 1.2.7](https://www.npmjs.com/package/coakka-logger-bun/v/1.2.7) | [3.2.1 first run](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/bun/registry-basic) |
| npm (Electron) | [`coakka-v2-connector-electron` 3.2.1](https://www.npmjs.com/package/coakka-v2-connector-electron/v/3.2.1) | [`coakka-logger-electron` 1.2.7](https://www.npmjs.com/package/coakka-logger-electron/v/1.2.7) | [Electron 3.2.1 install](../coakka-runtime/releases/2026-11-05-npm-node-bun-electron-runtime-3.2.1.md#install) |
| PyPI | [`coakka-v2-connector` 3.2.1](https://pypi.org/project/coakka-v2-connector/3.2.1/) | [`coakka-logger` 1.2.2](https://pypi.org/project/coakka-logger/1.2.2/) | [CPython3.11 first run](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/python/registry-basic) |
| Go modules | [`coakka-runtime-go/v3` v3.2.1](https://github.com/phuong-tran/coakka-runtime-go/releases/tag/v3.2.1) | [`coakka-logger-go` v1.2.6](https://pkg.go.dev/github.com/phuong-tran/coakka-logger-go@v1.2.6) | [Go 3.2.1 first run](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/go/registry-basic) |
| SwiftPM | [`coakka-runtime-swift` v3.2.1](https://github.com/phuong-tran/coakka-runtime-swift/releases/tag/v3.2.1) | [`coakka-logger-swift` v1.2.2](https://github.com/phuong-tran/coakka-logger-swift/releases/tag/v1.2.2) | [Swift 3.2.1 first run](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/swift/registry-basic) |
| Tauri source archive | [`coakka-runtime-tauri-intents` 2.5.3 source](https://github.com/phuong-tran/coakka-publish/tree/main/runtime/tauri/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be-0ba485e) | [`coakka-logger-tauri-intents` 1.2.2 source](https://github.com/phuong-tran/coakka-publish/tree/main/logger/tauri/releases/1.2.1+f50756ebff0d-7718ce6) | [Tauri integration guide](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/tauri/README.md) |
| Android preview | [coakka-runtime-android-3.2.1.aar](https://raw.githubusercontent.com/phuong-tran/coakka-publish/fe81cabbfbeb02672b02b19de8cfbe1a846630aa/coakka-runtime/android/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/coakka-runtime-android-3.2.1.aar) | Use the app's Android logging policy | [Install and verify](../coakka-runtime/releases/2026-11-05-android-runtime-3.2.1-preview.md) |
| Zig source archive | [`coakka-runtime-zig` 3.2.1](https://raw.githubusercontent.com/phuong-tran/coakka-publish/c426326567f57dc01ca0449c347779802833b66b/coakka-runtime/zig/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/coakka-runtime-zig-3.2.1-source.tar.gz) | Use the application's Zig logging policy | [Checksum and host scope](../coakka-runtime/releases/2026-11-05-zig-mojo-runtime-3.2.1-source.md) |
| Mojo source preview | [`coakka-runtime-mojo` 3.2.1 preview](https://raw.githubusercontent.com/phuong-tran/coakka-publish/c426326567f57dc01ca0449c347779802833b66b/coakka-runtime/mojo/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/coakka-runtime-mojo-3.2.1-source.tar.gz) | Use the application's Mojo logging policy | [Checksum and preview scope](../coakka-runtime/releases/2026-11-05-zig-mojo-runtime-3.2.1-source.md) |

Zig 3.2.1 and Mojo 3.2.1 preview are checksum-pinned source archives with
embedded Core 3.2. Mojo preview execution covers macOS ARM64 and Raspberry Pi 5
Linux ARM64.

Node.js, Bun, and Electron are three distinct npm packages. Tauri uses a
checksum-pinned source archive. Android 3.2.1 is a standalone preview AAR
with Core 3.2 and four ABIs: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.
Installed emulator tests and lifecycle soak passed on API 24 x86_64
(613 cycles) and API 36 ARM64 (477 cycles). API 24 ARM64 stopped before
APK installation. See the [Android preview record](../coakka-runtime/releases/2026-11-05-android-runtime-3.2.1-preview.md).

Java 17 app hosts can use the independently versioned Maven Central framework
adapters: [`spring-boot-starter`
3.2.1](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/spring-boot-starter/3.2.1)
or [`quarkus-extension`
3.2.1](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/quarkus-extension/3.2.1).
Both depend exactly on public Runtime `3.2.1`; applications select their own verified
Spring Boot or Quarkus platform line.

The current source coordinates are
`github.com/phuong-tran/coakka-runtime-go/v3@v3.2.1` and
`github.com/phuong-tran/coakka-runtime-swift@v3.2.1`. Go uses semantic major `v3` through its `/v3` module suffix; historical
unsuffixed v1 tags remain unchanged.

## Runtime Artifact And Current Source-Package Entrypoints

| Surface | Exact coordinate |
| --- | --- |
| Native C ABI | `runtime/native/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be/coakka-runtime-native-v2-2.5.1.tar.gz` |
| JVM | `io.github.phuong-tran.coakka:runtime:3.2.1` |
| Spring Boot | `io.github.phuong-tran.coakka:spring-boot-starter:3.2.1` |
| Quarkus | `io.github.phuong-tran.coakka:quarkus-extension:3.2.1` |
| Connector archives | `runtime/<lane>/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be-0ba485e/` |
| JVM signed public tree | `https://repo1.maven.org/maven2/io/github/phuong-tran/coakka/runtime/3.2.1/` |
| npm | `coakka-v2-connector-{node,bun,electron}@3.2.1` |
| PyPI | `coakka-v2-connector`, exact `3.2.1` |
| NuGet | `CoAkka.Runtime`, exact `3.2.1` |
| Go module | `github.com/phuong-tran/coakka-runtime-go/v3@v3.2.1` |
| SwiftPM | `https://github.com/phuong-tran/coakka-runtime-swift.git`, exact `3.2.1` |
| coakka-client | [coakka-client 3.2.1](https://github.com/phuong-tran/coakka-publish/tree/86aa17f3d2af26bfd0faee3c02fbd9e82750592e/coakka-tools/coakka-client/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45) |
| coakka-runtime-inspect | [coakka-runtime-inspect 3.2.1](https://github.com/phuong-tran/coakka-publish/tree/86aa17f3d2af26bfd0faee3c02fbd9e82750592e/coakka-tools/coakka-runtime-inspect/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45) |

Client and Inspect 3.2.1 each bundle Core 3.2 and provide five native archives.
The [tool release record](../coakka-runtime/releases/2026-11-05-runtime-tools-3.2.1.md)
lists verified downloads and SHA-256 values. The Docker demos use their own
2.5.1 images.

## CoAkka HTTP Runtime

HTTP Runtime has its own packages and release cadence. Version `2.0.1`
means Core generation `2.0` and connector revision `1`. The native SDK pin
is `2.0.1` with C ABI 10. Version `1.1.2` is deprecated for new installs.
See [the versioning
guide](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/versions.md).

| Host | HTTP Runtime 2.0.1 |
| --- | --- |
| Node.js | [`coakka-http-runtime-node`](https://www.npmjs.com/package/coakka-http-runtime-node/v/2.0.1) |
| Bun | [`coakka-http-runtime-bun`](https://www.npmjs.com/package/coakka-http-runtime-bun/v/2.0.1) |
| Python | [`coakka-http-runtime-python`](https://pypi.org/project/coakka-http-runtime-python/2.0.1/) |
| JVM | [`io.github.phuong-tran.coakka:coakka-http-runtime-jvm:2.0.1`](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/coakka-http-runtime-jvm/2.0.1) |
| Go | [`github.com/phuong-tran/coakka-http-runtime-go/v2@v2.0.1`](https://github.com/phuong-tran/coakka-http-runtime-go/releases/tag/v2.0.1) |

For C, C++, or Go, download the [2.0.1 native SDK for your platform](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-http-runtime/native/releases/2.0.1).
[Run a first request](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-http-runtime/registry-basic/README.md).

## Main Public Repositories

| Repository | Use it for |
| --- | --- |
| [`coakka-samples`](https://github.com/phuong-tran/coakka-samples) | Runnable examples and code you can inspect first. |
| [`coakka-publish`](https://github.com/phuong-tran/coakka-publish) | Released packages, native archives, manifests, checksums, compatibility matrix, and release notes. |
| [`coakka-runtime-go`](https://github.com/phuong-tran/coakka-runtime-go) | Public Go module for CoAkka Runtime. |
| [`coakka-logger-go`](https://github.com/phuong-tran/coakka-logger-go) | Public Go module for CoAkka Logger. |
| [`coakka-runtime-swift`](https://github.com/phuong-tran/coakka-runtime-swift) | Public SwiftPM runtime package with five verified native payloads and macOS ARM64 Swift execution. |
| [`coakka-logger-swift`](https://github.com/phuong-tran/coakka-logger-swift) | Public SwiftPM logger package for macOS ARM64. |

For direct archive downloads, checksums, compatibility status, and release
notes, use
[`coakka-publish`](https://github.com/phuong-tran/coakka-publish).
