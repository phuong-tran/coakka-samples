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
Lane, and explicit runtime network participation modes. npm, NuGet, and PyPI
have been promoted to `2.4.0`; Go modules and SwiftPM remain on the separately
listed coordinates until their next publication steps.

For exact package contents, matching-host execution, and known platform gaps,
use [Runtime Package And Platform Evidence](runtime-package-platform-evidence.md).

## Runtime Addons

[`runtime-addons/`](runtime-addons.md) is the independent release family for
optional native capabilities that compose with Runtime without entering the
default runtime package. Addons carry their own versions, compatibility
manifests, native dependency closure, and matching-host evidence.

SFTP artifact publisher `1.1.0+42841ae2` is published as an independent native
archive for Linux ARM64/x86-64, macOS ARM64, and Windows 11 ARM64/x86-64. It
requires Runtime native `2.3.0` or newer and the `file_lane` feature. It is not
part of the Runtime archive and does not change any connector coordinate.

```text
runtime-addons/artifact-publisher-sftp/native/releases/1.1.0+42841ae2/
  coakka-runtime-addon-artifact-publisher-sftp-native-1.1.0.tar.gz
```

Use the isolated native sample with
`bash runtime-addons/artifact-publisher-sftp/native/run.sh published`. The root
main sample lane remains reserved for Runtime and Logger packages.

## Package Manager Entrypoints

| Channel | Runtime package | Logger package | Sample command |
| --- | --- | --- | --- |
| NuGet | [`CoAkka.Runtime` 2.4.0](https://www.nuget.org/packages/CoAkka.Runtime/2.4.0) | [`CoAkka.Logger` 1.2.2](https://www.nuget.org/packages/CoAkka.Logger/1.2.2) | `bash run.sh runtime csharp basic` |
| npm | [`coakka-v2-connector-node` 2.4.0](https://www.npmjs.com/package/coakka-v2-connector-node/v/2.4.0) | [`coakka-logger-node` 1.2.6](https://www.npmjs.com/package/coakka-logger-node/v/1.2.6) | `bash run.sh runtime node basic` |
| npm (Bun) | [`coakka-v2-connector-bun` 2.4.0](https://www.npmjs.com/package/coakka-v2-connector-bun/v/2.4.0) | [`coakka-logger-bun` 1.2.6](https://www.npmjs.com/package/coakka-logger-bun/v/1.2.6) | `bash run.sh runtime bun basic` |
| npm (Electron) | [`coakka-v2-connector-electron` 2.4.0](https://www.npmjs.com/package/coakka-v2-connector-electron/v/2.4.0) | [`coakka-logger-electron` 1.2.6](https://www.npmjs.com/package/coakka-logger-electron/v/1.2.6) | `bash run.sh runtime electron basic` |
| PyPI | [`coakka-v2-connector` 2.4.0](https://pypi.org/project/coakka-v2-connector/2.4.0/) | [`coakka-logger` 1.2.2](https://pypi.org/project/coakka-logger/1.2.2/) | `bash run.sh runtime python basic` |
| Go modules | [`coakka-runtime-go` v1.6.0](https://github.com/phuong-tran/coakka-runtime-go/releases/tag/v1.6.0) | [`coakka-logger-go` v1.2.5](https://pkg.go.dev/github.com/phuong-tran/coakka-logger-go@v1.2.5) | `bash run.sh runtime go basic` |
| SwiftPM | [`coakka-runtime-swift` v2.3.0](https://github.com/phuong-tran/coakka-runtime-swift/releases/tag/v2.3.0) | [`coakka-logger-swift` v1.2.1](https://github.com/phuong-tran/coakka-logger-swift/releases/tag/v1.2.1) | `bash run.sh runtime swift basic` |

The `2.3.0` source coordinates are
`github.com/phuong-tran/coakka-runtime-go@v1.6.0` and
`github.com/phuong-tran/coakka-runtime-swift@v2.3.0`. Go remains on semantic
major `v1` because its established module path does not carry a `/v2`
suffix.

## Runtime 2.4.0 Artifact Entrypoints

| Surface | Exact coordinate |
| --- | --- |
| Native C ABI | `runtime/native/releases/2.4.0+c2f53117/coakka-runtime-native-v2-2.4.0.tar.gz` |
| JVM | `coakka.v2:coakka-jvm-native-runtime-v2:2.4.0-gc2f53117-0afb5e9` |
| Spring Boot | `coakka.spring:coakka-spring-boot-starter:2.4.0-gc2f53117-0afb5e9` |
| Quarkus | `coakka.quarkus:coakka-quarkus-extension:2.4.0-gc2f53117-0afb5e9` |
| Connector archives | `runtime/<lane>/releases/2.4.0+c2f53117-0afb5e9/` |
| npm | `coakka-v2-connector-{node,bun,electron}@2.4.0` |
| PyPI | `coakka-v2-connector`, exact `2.4.0` |
| NuGet | `CoAkka.Runtime`, exact `2.4.0` |
| Go module | `github.com/phuong-tran/coakka-runtime-go@v1.6.0` |
| SwiftPM | `https://github.com/phuong-tran/coakka-runtime-swift.git`, exact `2.3.0` |
| coakka-client | `coakka-tools/coakka-client/releases/2.3.0+a83ab412/` |
| coakka-runtime-inspect | `coakka-tools/coakka-runtime-inspect/releases/2.3.0+a83ab412/` |

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
