# Current Packages

This page lists current public packages for CoAkka Runtime, CoAkka HTTP
Runtime, and CoAkka Logger.

[Runtime](#coakka-runtime) · [HTTP Runtime](#coakka-http-runtime) ·
[Logger](#coakka-logger) · [Source archives and previews](#runtime-source-archives-and-previews)

CoAkka Runtime `3.2.1` means connector revision 1 on Core generation `3.2`.
Core build `f9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45` uses ABI 1. The matching
Core comes with the package or its exact Runtime dependency. Each connector
can advance its own revision on that Core generation. CoAkka HTTP Runtime and
CoAkka Logger have their own release versions.

The [Runtime 3.2.1 changelog](../coakka-runtime/CHANGELOG.md#321)
indexes the release records, including the Android and Mojo previews.

For package contents, execution evidence and platform limits, see
[Runtime Package And Platform Evidence](../coakka-runtime/reference/runtime-package-platform-evidence.md).

## Package And Source Entrypoints

Choose your product and host. The commands below install exact versions.
Each first run shows the prerequisites, command and expected result.

<!-- package-catalog -->

### CoAkka Runtime

| Host | Install or dependency coordinate | First run |
| --- | --- | --- |
| Node.js | [`npm install coakka-v2-connector-node@3.2.1`](https://www.npmjs.com/package/coakka-v2-connector-node/v/3.2.1) | [Run the sample](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/node/registry-basic) |
| Bun | [`bun add coakka-v2-connector-bun@3.2.1`](https://www.npmjs.com/package/coakka-v2-connector-bun/v/3.2.1) | [Run the sample](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/bun/registry-basic) |
| Electron | [`npm install coakka-v2-connector-electron@3.2.1`](https://www.npmjs.com/package/coakka-v2-connector-electron/v/3.2.1) | [Run the sample](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/electron/registry-basic) |
| Python | [`python -m pip install coakka-v2-connector==3.2.1`](https://pypi.org/project/coakka-v2-connector/3.2.1/) | [Run the sample](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/python/registry-basic) |
| C# / .NET | [`dotnet add package CoAkka.Runtime --version 3.2.1`](https://www.nuget.org/packages/CoAkka.Runtime/3.2.1) | [Run the sample](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/csharp/registry-basic) |
| Java / Kotlin | [`io.github.phuong-tran.coakka:runtime:3.2.1`](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/runtime/3.2.1) | [Run the sample](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/jvm/registry-basic) |
| Spring Boot | [`io.github.phuong-tran.coakka:spring-boot-starter:3.2.1`](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/spring-boot-starter/3.2.1) | [Run the sample](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/scenarios/customer-crud/framework-registry) |
| Quarkus | [`io.github.phuong-tran.coakka:quarkus-extension:3.2.1`](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/quarkus-extension/3.2.1) | [Run the sample](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/scenarios/customer-crud/framework-registry) |
| Go | [`go get github.com/phuong-tran/coakka-runtime-go/v3@v3.2.1`](https://pkg.go.dev/github.com/phuong-tran/coakka-runtime-go/v3@v3.2.1) | [Run the sample](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/go/registry-basic) |
| Swift | [`.package(url: "https://github.com/phuong-tran/coakka-runtime-swift.git", exact: "3.2.1")`](https://github.com/phuong-tran/coakka-runtime-swift/releases/tag/v3.2.1) | [Run the sample](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/swift/registry-basic) |

### CoAkka HTTP Runtime

| Host | Install or dependency coordinate | First run |
| --- | --- | --- |
| Node.js | [`npm install coakka-http-runtime-node@2.2.1`](https://www.npmjs.com/package/coakka-http-runtime-node/v/2.2.1) | [Run the sample](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-http-runtime/protocol-listeners/README.md#nodejs-or-bun) |
| Bun | [`bun add coakka-http-runtime-bun@2.2.1`](https://www.npmjs.com/package/coakka-http-runtime-bun/v/2.2.1) | [Run the sample](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-http-runtime/protocol-listeners/README.md#nodejs-or-bun) |
| Python | [`python -m pip install coakka-http-runtime-python==2.2.1`](https://pypi.org/project/coakka-http-runtime-python/2.2.1/) | [Run the sample](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-http-runtime/protocol-listeners/README.md#python) |
| Java / Kotlin | [`io.github.phuong-tran.coakka:coakka-http-runtime-jvm:2.2.1`](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/coakka-http-runtime-jvm/2.2.1) | [Run the sample](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-http-runtime/protocol-listeners/README.md#java-and-kotlin) |
| Go | [`go get github.com/phuong-tran/coakka-http-runtime-go/v2@v2.2.1`](https://pkg.go.dev/github.com/phuong-tran/coakka-http-runtime-go/v2@v2.2.1) | [Run the sample](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-http-runtime/protocol-listeners/README.md#go) |
| C / C++ | [Native SDK 2.2.0 and SHA-256 checksums](https://github.com/phuong-tran/coakka-http-runtime-go/releases/tag/v2.2.1) | [Build a native sample](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-http-runtime/protocol-listeners/README.md#c-and-c) |

### CoAkka Logger

| Host | Install or dependency coordinate | First run |
| --- | --- | --- |
| Node.js | [`npm install coakka-logger-node@1.2.7`](https://www.npmjs.com/package/coakka-logger-node/v/1.2.7) | [Run the sample](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-logger/node/basic) |
| Bun | [`bun add coakka-logger-bun@1.2.7`](https://www.npmjs.com/package/coakka-logger-bun/v/1.2.7) | [Run the sample](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-logger/bun/basic) |
| Electron | [`npm install coakka-logger-electron@1.2.7`](https://www.npmjs.com/package/coakka-logger-electron/v/1.2.7) | [Run the sample](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-logger/electron/basic) |
| Python | [`python -m pip install coakka-logger==1.2.2`](https://pypi.org/project/coakka-logger/1.2.2/) | [Run the sample](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-logger/python/basic) |
| C# / .NET | [`dotnet add package CoAkka.Logger --version 1.2.3`](https://www.nuget.org/packages/CoAkka.Logger/1.2.3) | [Run the sample](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-logger/csharp/basic) |
| Java / Kotlin | [`io.github.phuong-tran.coakka:logger:1.2.2`](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/logger/1.2.2) | [Run the sample](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-logger/jvm/basic) |
| Go | [`go get github.com/phuong-tran/coakka-logger-go@v1.2.6`](https://pkg.go.dev/github.com/phuong-tran/coakka-logger-go@v1.2.6) | [Run the sample](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-logger/go/basic) |
| Swift | [`.package(url: "https://github.com/phuong-tran/coakka-logger-swift.git", exact: "1.2.2")`](https://github.com/phuong-tran/coakka-logger-swift/releases/tag/v1.2.2) | [Run the sample](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-logger/swift/basic) |

<!-- /package-catalog -->

## Runtime source archives and previews

| Channel | Runtime package | Logger package | Sample or guide |
| --- | --- | --- | --- |
| Rust source archive | [`coakka-runtime-rs` 3.2.1](https://raw.githubusercontent.com/phuong-tran/coakka-publish/89e183de62618085a1223c69d8f5ecadf6e7cf7b/coakka-runtime/rust/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/coakka-runtime-rs-3.2.1.tar.gz) | Use the application's Rust logging policy | [Checksum and host scope](../coakka-runtime/releases/2026-11-05-rust-tauri-runtime-3.2.1-source.md) |
| Tauri source archive | [`coakka-runtime-tauri-intents` 3.2.1](https://raw.githubusercontent.com/phuong-tran/coakka-publish/89e183de62618085a1223c69d8f5ecadf6e7cf7b/coakka-runtime/tauri/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/coakka-runtime-tauri-intents-3.2.1-source.tar.gz) | [`coakka-logger-tauri-intents` 1.2.2 source](https://github.com/phuong-tran/coakka-publish/tree/main/logger/tauri/releases/1.2.1+f50756ebff0d-7718ce6) | [Checksum and WebView scope](../coakka-runtime/releases/2026-11-05-rust-tauri-runtime-3.2.1-source.md) |
| Android preview | [coakka-runtime-android-3.2.1.aar](https://raw.githubusercontent.com/phuong-tran/coakka-publish/fe81cabbfbeb02672b02b19de8cfbe1a846630aa/coakka-runtime/android/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/coakka-runtime-android-3.2.1.aar) | Use the app's Android logging policy | [Install and verify](../coakka-runtime/releases/2026-11-05-android-runtime-3.2.1-preview.md) |
| Zig source archive | [`coakka-runtime-zig` 3.2.1](https://raw.githubusercontent.com/phuong-tran/coakka-publish/c426326567f57dc01ca0449c347779802833b66b/coakka-runtime/zig/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/coakka-runtime-zig-3.2.1-source.tar.gz) | Use the application's Zig logging policy | [Checksum and host scope](../coakka-runtime/releases/2026-11-05-zig-mojo-runtime-3.2.1-source.md) |
| Mojo source preview | [`coakka-runtime-mojo` 3.2.1 preview](https://raw.githubusercontent.com/phuong-tran/coakka-publish/c426326567f57dc01ca0449c347779802833b66b/coakka-runtime/mojo/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45/coakka-runtime-mojo-3.2.1-source.tar.gz) | Use the application's Mojo logging policy | [Checksum and preview scope](../coakka-runtime/releases/2026-11-05-zig-mojo-runtime-3.2.1-source.md) |

Zig 3.2.1 and Mojo 3.2.1 preview are checksum-pinned source archives with
embedded Core 3.2. Mojo preview execution covers macOS ARM64 and Raspberry Pi 5
Linux ARM64.

Rust 3.2.1 and Tauri Intents 3.2.1 use checksum-pinned source archives with
embedded Core 3.2. Tauri WebView execution was verified on macOS ARM64.

Node.js, Bun, and Electron are three distinct npm packages. Android 3.2.1
is a standalone preview AAR
with Core 3.2 and four ABIs: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.
Installed emulator tests and lifecycle soak passed on API 24 x86_64
(613 cycles) and API 36 ARM64 (477 cycles). API 24 ARM64 stopped before
APK installation. See the [Android preview record](../coakka-runtime/releases/2026-11-05-android-runtime-3.2.1-preview.md).

## Runtime Artifact And Source-Package Entrypoints

The standalone Core 3.2 SDK and Runtime Test archives are available for direct
download. The 3.2.1 tools below bundle Core 3.2.

| Surface | Exact coordinate |
| --- | --- |
| Native C ABI and Runtime Test | [Core 3.2 SDK, runner downloads and SHA-256](../coakka-runtime/releases/2026-11-05-core-runtime-test-3.2.md) |
| Historical connector archives | `runtime/<lane>/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be-0ba485e/` |
| coakka-client | [coakka-client 3.2.1](https://github.com/phuong-tran/coakka-publish/tree/86aa17f3d2af26bfd0faee3c02fbd9e82750592e/coakka-tools/coakka-client/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45) |
| coakka-runtime-inspect | [coakka-runtime-inspect 3.2.1](https://github.com/phuong-tran/coakka-publish/tree/86aa17f3d2af26bfd0faee3c02fbd9e82750592e/coakka-tools/coakka-runtime-inspect/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45) |

Client and Inspect 3.2.1 each bundle Core 3.2 and provide five native archives.
The [tool release record](../coakka-runtime/releases/2026-11-05-runtime-tools-3.2.1.md)
lists verified downloads and SHA-256 values. The Docker demos use their own
2.5.1 images.

## HTTP Runtime SDK

Connector version `2.2.1` means Core generation `2.2`, connector revision
`1`. It pins native SDK `2.2.0` with C ABI 10.
See the [release notes](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/release-2.2.1.md) and
[versioning guide](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/versions.md).

For C, C++ or Go, download the [2.2.0 native SDK and SHA-256 checksums](https://github.com/phuong-tran/coakka-http-runtime-go/releases/tag/v2.2.1).
[Run a first request](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-http-runtime/protocol-listeners/README.md).

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
