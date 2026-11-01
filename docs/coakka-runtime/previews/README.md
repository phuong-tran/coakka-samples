# CoAkka Runtime Evaluation And Local-Package Checks

For a normal installation, start with the [published first-run examples](../getting-started.md).
Runtime 3.0.0 is released for Node.js, Bun, Electron, Python, C#, Kotlin/Java,
Go and Swift, together with the Spring Boot and Quarkus adapters.
The [package ledger](../../common/current-packages.md) owns exact availability.

The guides below are for checking a selected local package, including an
already-published version. They are not prerequisites for the published first
runs. C/C++, Rust, Tauri and Zig retain their own release/evaluation status;
Android and Mojo remain previews. Swift itself is released; Windows ARM64
alone is its preview path.

| Host | Local-package check |
| --- | --- |
| Node.js / Bun | [JavaScript](3.0.0/runtime-js-candidate.md) |
| Electron | [Electron](3.0.0/runtime-electron-candidate.md) |
| Python | [Python](3.0.0/runtime-python-candidate.md) |
| C# | [C#](3.0.0/runtime-csharp-candidate.md) |
| Kotlin / Java | [JVM](3.0.0/runtime-jvm-candidate.md) |
| Spring Boot / Quarkus | [Framework integrations](3.0.0/runtime-frameworks-candidate.md) |
| Go | [Go](3.0.0/runtime-go-candidate.md) |
| Swift | [Swift](3.0.0/runtime-swift-candidate.md) |
| Rust / Tauri | [Rust](3.0.0/runtime-rust-candidate.md) · [Tauri](3.0.0/runtime-tauri-candidate.md) |
| Zig | [Zig](3.0.0/runtime-zig-candidate.md) |
| Mojo preview | [Mojo](3.0.0/runtime-mojo-candidate.md) |

[Android preview](edge-iot-android.md) is evaluated with its local AAR and
sample, without a Maven publication plan. Mojo also remains a preview.
Swift is publishable; only its Windows ARM lane is preview in this release.
[Runtime documentation](../README.md).
