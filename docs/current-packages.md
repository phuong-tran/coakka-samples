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

The file-transfer lane begins with native source generation `2.1.0`. The
package coordinates below remain the current public generation and do not
contain that ABI. Do not generate or enable file-lane calls against these
packages. Use the [Runtime File Transfer guide](runtime-file-transfer.md) for
the `2.1.0` contract and enable it only when the compatibility matrix names an
exact promoted `2.1.0+<core-commit>` artifact.

For exact package contents, matching-host execution, and known platform gaps,
use [Runtime Package And Platform Evidence](runtime-package-platform-evidence.md).

## Package Manager Entrypoints

| Channel | Runtime package | Logger package | Sample command |
| --- | --- | --- | --- |
| NuGet | [`CoAkka.Runtime` 1.4.7](https://www.nuget.org/packages/CoAkka.Runtime/1.4.7) | [`CoAkka.Logger` 1.2.2](https://www.nuget.org/packages/CoAkka.Logger/1.2.2) | `bash run.sh runtime csharp basic` |
| npm | [`coakka-v2-connector-node` 1.4.6](https://www.npmjs.com/package/coakka-v2-connector-node/v/1.4.6) | [`coakka-logger-node` 1.2.6](https://www.npmjs.com/package/coakka-logger-node/v/1.2.6) | `bash run.sh runtime node basic` |
| npm (Bun) | [`coakka-v2-connector-bun` 1.4.6](https://www.npmjs.com/package/coakka-v2-connector-bun/v/1.4.6) | [`coakka-logger-bun` 1.2.6](https://www.npmjs.com/package/coakka-logger-bun/v/1.2.6) | `bash run.sh runtime bun basic` |
| npm (Electron) | [`coakka-v2-connector-electron` 1.4.6](https://www.npmjs.com/package/coakka-v2-connector-electron/v/1.4.6) | [`coakka-logger-electron` 1.2.6](https://www.npmjs.com/package/coakka-logger-electron/v/1.2.6) | `bash run.sh runtime electron basic` |
| PyPI | [`coakka-v2-connector` 1.4.6](https://pypi.org/project/coakka-v2-connector/1.4.6/) | [`coakka-logger` 1.2.2](https://pypi.org/project/coakka-logger/1.2.2/) | `bash run.sh runtime python basic` |
| Go modules | [`coakka-runtime-go` v1.4.1](https://pkg.go.dev/github.com/phuong-tran/coakka-runtime-go@v1.4.1) | [`coakka-logger-go` v1.2.5](https://pkg.go.dev/github.com/phuong-tran/coakka-logger-go@v1.2.5) | `bash run.sh runtime go basic` |
| SwiftPM | [`coakka-runtime-swift` v1.4.1](https://github.com/phuong-tran/coakka-runtime-swift/releases/tag/v1.4.1) | [`coakka-logger-swift` v1.2.1](https://github.com/phuong-tran/coakka-logger-swift/releases/tag/v1.2.1) | `bash run.sh runtime swift basic` |

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
