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

The file-transfer lane is public in native generation `2.1.0+60ddf70d` and the
matching connector artifact train. npm `2.1.1` and NuGet `2.1.0` are published
and file-lane capable. PyPI remains on its separately verified 1.4.x release
until its 2.1.0 upload completes. Do not generate file-lane calls against that
older package.

For exact package contents, matching-host execution, and known platform gaps,
use [Runtime Package And Platform Evidence](runtime-package-platform-evidence.md).

## Package Manager Entrypoints

| Channel | Runtime package | Logger package | Sample command |
| --- | --- | --- | --- |
| NuGet | [`CoAkka.Runtime` 2.1.0](https://www.nuget.org/packages/CoAkka.Runtime/2.1.0) | [`CoAkka.Logger` 1.2.2](https://www.nuget.org/packages/CoAkka.Logger/1.2.2) | `bash run.sh runtime csharp basic` |
| npm | [`coakka-v2-connector-node` 2.1.1](https://www.npmjs.com/package/coakka-v2-connector-node/v/2.1.1) | [`coakka-logger-node` 1.2.6](https://www.npmjs.com/package/coakka-logger-node/v/1.2.6) | `bash run.sh runtime node basic` |
| npm (Bun) | [`coakka-v2-connector-bun` 2.1.1](https://www.npmjs.com/package/coakka-v2-connector-bun/v/2.1.1) | [`coakka-logger-bun` 1.2.6](https://www.npmjs.com/package/coakka-logger-bun/v/1.2.6) | `bash run.sh runtime bun basic` |
| npm (Electron) | [`coakka-v2-connector-electron` 2.1.1](https://www.npmjs.com/package/coakka-v2-connector-electron/v/2.1.1) | [`coakka-logger-electron` 1.2.6](https://www.npmjs.com/package/coakka-logger-electron/v/1.2.6) | `bash run.sh runtime electron basic` |
| PyPI | [`coakka-v2-connector` 1.4.6](https://pypi.org/project/coakka-v2-connector/1.4.6/) | [`coakka-logger` 1.2.2](https://pypi.org/project/coakka-logger/1.2.2/) | `bash run.sh runtime python basic` |
| Go modules | [`coakka-runtime-go` v1.5.0](https://pkg.go.dev/github.com/phuong-tran/coakka-runtime-go@v1.5.0) | [`coakka-logger-go` v1.2.5](https://pkg.go.dev/github.com/phuong-tran/coakka-logger-go@v1.2.5) | `bash run.sh runtime go basic` |
| SwiftPM | [`coakka-runtime-swift` v2.1.0](https://github.com/phuong-tran/coakka-runtime-swift/releases/tag/v2.1.0) | [`coakka-logger-swift` v1.2.1](https://github.com/phuong-tran/coakka-logger-swift/releases/tag/v1.2.1) | `bash run.sh runtime swift basic` |

## Runtime 2.1.0 Artifact Entrypoints

| Surface | Exact coordinate |
| --- | --- |
| Native C ABI | `runtime/native/releases/2.1.0+60ddf70d/coakka-runtime-native-v2-2.1.0.tar.gz` |
| JVM | `coakka.v2:coakka-jvm-native-runtime-v2:2.1.0-g60ddf70d-4782dcd` |
| Spring Boot | `coakka.spring:coakka-spring-boot-starter:2.1.0-g60ddf70d-4782dcd` |
| Quarkus | `coakka.quarkus:coakka-quarkus-extension:2.1.0-g60ddf70d-4782dcd` |
| Node, Bun, Electron, Python, Go, Rust, Swift, Mojo, Zig, Tauri | `runtime/<lane>/releases/2.1.0+60ddf70d-4782dcd/` |
| C# | `runtime/csharp/releases/2.1.0+60ddf70d-99bb16c/` |
| coakka-client | `coakka-tools/coakka-client/releases/2.1.0+60ddf70d/` |
| coakka-runtime-inspect | `coakka-tools/coakka-runtime-inspect/releases/2.1.0+60ddf70d/` |

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
