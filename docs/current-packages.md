# Current Packages

This page lists the current public package-manager entrypoints for CoAkka
Runtime and CoAkka Logger.

Every runtime package below is a connector into the same polyglot,
multi-language, multi-platform CoAkka Runtime ecosystem. A Python, Go, C#,
Swift, or JavaScript package is not a separate language-specific CoAkka
runtime; each projects the same native core and public runtime contract into
its host language.

Package versions are independent across the ecosystem. A NuGet package, npm
package, PyPI package, Go module, and SwiftPM tag do not need to share the same
version number. Each lane follows the release cadence of its connector,
packaging surface, and native payload.

The promoted repository artifact generation is `2.4.0+c2f53117`. Its native,
JVM, Spring Boot, Quarkus, and connector archives expose File Lane, Stream
Lane, and explicit runtime network participation modes. The JVM distribution,
Spring Boot starter, and Quarkus extension are published to Maven Central as
`2.4.1` while retaining that exact native generation. Logger JVM distribution
`1.2.2` is published independently over native logger generation
`1.2.1+f50756ebff0d`. NuGet publishes Runtime
`2.4.1` and Logger `1.2.3` as `net8.0` packages verified on .NET 8, 9, and 10
without changing either native generation. npm, PyPI, Go, and Swift packages
use the separately listed coordinates; version numbers remain independent by
channel.

Go `v1.8.0` and SwiftPM `v2.5.0` independently embed sealed native generation
`2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a`. This scoped source-package
release does not advance Maven Central, npm, PyPI, NuGet, or the promoted
repository artifact mirror.

For exact package contents, matching-host execution, and known platform gaps,
use [Runtime Package And Platform Evidence](runtime-package-platform-evidence.md).

## Runtime Addons

[`runtime-addons/`](runtime-addons.md) is the independent release family for
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
runtime-addons/artifact-publisher-<source>/native/releases/1.1.0+d1032f6d/
runtime-addons/artifact-publisher-sftp/native/releases/1.2.0+88b9a047/
  coakka-runtime-addon-artifact-publisher-sftp-native-1.2.0.tar.gz
```

Run one exact native consumer with `bash run.sh runtime-addons <addon>` or the
complete matrix with `bash run.sh runtime-addons all published`.

## Package Manager Entrypoints

| Channel | Runtime package | Logger package | Sample command |
| --- | --- | --- | --- |
| Maven Central | [`coakka.runtime` 2.4.1](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/runtime/2.4.1) | [`coakka.logger` 1.2.2](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/logger/1.2.2) | `bash run.sh logger jvm basic` |
| NuGet | [`CoAkka.Runtime` 2.4.1](https://www.nuget.org/packages/CoAkka.Runtime/2.4.1) | [`CoAkka.Logger` 1.2.3](https://www.nuget.org/packages/CoAkka.Logger/1.2.3) | `bash run.sh runtime csharp basic`; `bash run.sh logger csharp basic` |
| npm | [`coakka-v2-connector-node` 2.4.1](https://www.npmjs.com/package/coakka-v2-connector-node/v/2.4.1) | [`coakka-logger-node` 1.2.7](https://www.npmjs.com/package/coakka-logger-node/v/1.2.7) | `bash run.sh runtime node basic` |
| npm (Bun) | [`coakka-v2-connector-bun` 2.4.1](https://www.npmjs.com/package/coakka-v2-connector-bun/v/2.4.1) | [`coakka-logger-bun` 1.2.7](https://www.npmjs.com/package/coakka-logger-bun/v/1.2.7) | `bash run.sh runtime bun basic` |
| npm (Electron) | [`coakka-v2-connector-electron` 2.4.1](https://www.npmjs.com/package/coakka-v2-connector-electron/v/2.4.1) | [`coakka-logger-electron` 1.2.7](https://www.npmjs.com/package/coakka-logger-electron/v/1.2.7) | `bash run.sh runtime electron basic` |
| PyPI | [`coakka-v2-connector` 2.4.0](https://pypi.org/project/coakka-v2-connector/2.4.0/) | [`coakka-logger` 1.2.2](https://pypi.org/project/coakka-logger/1.2.2/) | `bash run.sh runtime python basic` |
| Go modules | [`coakka-runtime-go` v1.8.0](https://github.com/phuong-tran/coakka-runtime-go/tree/v1.8.0) | [`coakka-logger-go` v1.2.6](https://pkg.go.dev/github.com/phuong-tran/coakka-logger-go@v1.2.6) | `bash run.sh runtime go basic` |
| SwiftPM | [`coakka-runtime-swift` v2.5.0](https://github.com/phuong-tran/coakka-runtime-swift/tree/v2.5.0) | [`coakka-logger-swift` v1.2.2](https://github.com/phuong-tran/coakka-logger-swift/releases/tag/v1.2.2) | `bash run.sh runtime swift basic` |

Java 17 app hosts can use the independently versioned Maven Central framework
adapters: [`spring-boot-starter`
2.4.1](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/spring-boot-starter/2.4.1)
or [`quarkus-extension`
2.4.1](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/quarkus-extension/2.4.1).
Both depend on public Runtime `2.4.1`; applications select their own verified
Spring Boot or Quarkus platform line.

The current source coordinates are
`github.com/phuong-tran/coakka-runtime-go@v1.8.0` and
`github.com/phuong-tran/coakka-runtime-swift@v2.5.0`. Go remains on semantic
major `v1` because its established module path does not carry a `/v2` suffix.

## Runtime 2.4.0 Artifact Entrypoints

| Surface | Exact coordinate |
| --- | --- |
| Native C ABI | `runtime/native/releases/2.4.0+c2f53117/coakka-runtime-native-v2-2.4.0.tar.gz` |
| JVM | `io.github.phuong-tran.coakka:runtime:2.4.1` |
| Spring Boot | `io.github.phuong-tran.coakka:spring-boot-starter:2.4.1` |
| Quarkus | `io.github.phuong-tran.coakka:quarkus-extension:2.4.1` |
| Connector archives | `runtime/<lane>/releases/2.4.0+c2f53117-0afb5e9/` |
| JavaScript/Tauri patch archives | `runtime/{node,bun,electron,tauri}/releases/2.4.0+c2f53117-7718ce6/` |
| npm | `coakka-v2-connector-{node,bun,electron}@2.4.1` |
| PyPI | `coakka-v2-connector`, exact `2.4.0` |
| NuGet | `CoAkka.Runtime`, exact `2.4.1` |
| Go module | `github.com/phuong-tran/coakka-runtime-go@v1.8.0` |
| SwiftPM | `https://github.com/phuong-tran/coakka-runtime-swift.git`, exact `2.5.0` |
| coakka-client | `coakka-tools/coakka-client/releases/2.4.0+c2f53117/` |
| coakka-runtime-inspect | `coakka-tools/coakka-runtime-inspect/releases/2.4.0+c2f53117/` |

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
