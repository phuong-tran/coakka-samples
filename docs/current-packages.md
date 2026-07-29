# Current Packages

This page lists the current public package-manager entrypoints for CoAkka
Runtime and CoAkka Logger.

Package versions are independent across the ecosystem. A NuGet package, npm
package, PyPI package, Go module, and SwiftPM tag do not need to share the same
version number. Each lane follows the release cadence of its connector,
packaging surface, and native payload.

## Package Manager Entrypoints

| Channel | Runtime package | Logger package | Sample command |
| --- | --- | --- | --- |
| NuGet | [`CoAkka.Runtime` 1.3.3](https://www.nuget.org/packages/CoAkka.Runtime/1.3.3) | [`CoAkka.Logger` 1.2.2](https://www.nuget.org/packages/CoAkka.Logger/1.2.2) | `bash run.sh runtime csharp basic` |
| npm | [`coakka-v2-connector-node` 1.3.9](https://www.npmjs.com/package/coakka-v2-connector-node/v/1.3.9) | [`coakka-logger-node` 1.2.6](https://www.npmjs.com/package/coakka-logger-node/v/1.2.6) | `bash run.sh runtime node basic` |
| PyPI | [`coakka-v2-connector` 1.3.4](https://pypi.org/project/coakka-v2-connector/1.3.4/) | [`coakka-logger` 1.2.2](https://pypi.org/project/coakka-logger/1.2.2/) | `bash run.sh runtime python basic` |
| Go modules | [`coakka-runtime-go` v1.3.10](https://pkg.go.dev/github.com/phuong-tran/coakka-runtime-go@v1.3.10) | [`coakka-logger-go` v1.2.5](https://pkg.go.dev/github.com/phuong-tran/coakka-logger-go@v1.2.5) | `bash run.sh runtime go basic` |
| SwiftPM | [`coakka-runtime-swift` v1.3.2](https://github.com/phuong-tran/coakka-runtime-swift/releases/tag/v1.3.2) | [`coakka-logger-swift` v1.2.1](https://github.com/phuong-tran/coakka-logger-swift/releases/tag/v1.2.1) | `bash run.sh runtime swift basic` |

## Main Public Repositories

| Repository | Use it for |
| --- | --- |
| [`coakka-samples`](https://github.com/phuong-tran/coakka-samples) | Runnable examples and code you can inspect first. |
| [`coakka-publish`](https://github.com/phuong-tran/coakka-publish) | Released packages, native archives, manifests, checksums, compatibility matrix, and release notes. |
| [`coakka-runtime-go`](https://github.com/phuong-tran/coakka-runtime-go) | Public Go module for CoAkka Runtime. |
| [`coakka-logger-go`](https://github.com/phuong-tran/coakka-logger-go) | Public Go module for CoAkka Logger. |
| [`coakka-runtime-swift`](https://github.com/phuong-tran/coakka-runtime-swift) | Public SwiftPM runtime package for macOS ARM64. |
| [`coakka-logger-swift`](https://github.com/phuong-tran/coakka-logger-swift) | Public SwiftPM logger package for macOS ARM64. |

For direct archive downloads, checksums, compatibility status, and release
notes, use
[`coakka-publish`](https://github.com/phuong-tran/coakka-publish).
