# CoAkka Runtime Changelog

CoAkka Runtime is a polyglot, native-backed distributed runtime for
application-owned work. This page explains what changed for application
developers; the [current package ledger](../common/current-packages.md) owns
installation versions, and [release history](releases/README.md) retains the
individual release records and package details.

## 3.0.0

Released **2026-10-30**. This consolidated changelog was added on 2026-11-01;
the published packages and release dates have not changed.

### Released packages

| Channel | Version 3.0.0 packages | Publication receipt |
| --- | --- | --- |
| NuGet | CoAkka.Runtime | [C#](releases/2026-10-30-nuget-csharp-runtime-3.0.0.md) |
| PyPI | coakka-v2-connector | [Python](releases/2026-10-30-pypi-python-runtime-3.0.0.md) |
| npm | coakka-v2-connector-node, coakka-v2-connector-bun, coakka-v2-connector-electron | [Node.js, Bun and Electron](releases/2026-10-30-npm-node-bun-electron-runtime-3.0.0.md) |
| Go modules / GitHub | github.com/phuong-tran/coakka-runtime-go/v3 at v3.0.0 | [Go](releases/2026-10-30-go-runtime-3.0.0-github.md) |
| SwiftPM / GitHub | coakka-runtime-swift at v3.0.0 | [Swift](releases/2026-10-30-swift-runtime-3.0.0-github.md) |
| Maven Central | io.github.phuong-tran.coakka:runtime, spring-boot-starter, quarkus-extension | [JVM, Spring Boot and Quarkus](releases/2026-10-30-jvm-spring-quarkus-runtime-3.0.0-maven-central.md) |

Core and the released connectors share version **3.0.0** for this Runtime
release. The normal package installation includes the required Runtime support.

### Changed

- Updated application APIs make accepted settings, available capabilities and
  request outcomes easier to inspect consistently across languages.
- Refined Python integration to reduce overhead when submitting requests and
  receiving results. Each language keeps a familiar application API.
- Updated Runtime, file-transfer and streaming APIs clarify when work has
  finished and how to shut down safely. File transfer and streaming already
  existed in 2.x.
- Spring Boot and Quarkus 3.0.0 depend exactly on JVM Runtime 3.0.0. The
  application continues to own HTTP, handlers and business data.
- Published packages include the matching Runtime components. Normal package
  installation does not require a separate Core installation.
- Samples now include examples using released Java/Kotlin, Spring/Quarkus, Node.js,
  Bun, Electron, Python, C#, Go and Swift entrypoints. Documentation and
  navigation are grouped by product under docs/coakka-runtime,
  docs/coakka-http-runtime, docs/coakka-logger and docs/common. Historical
  versioned artifact paths and receipts remain available.

### Breaking changes and migration

3.0.0 changes parts of the application API. Review existing code against the
matching 3.0.0 examples and rebuild compiled applications. Calls that remain
supported may work unchanged; compatibility with every earlier API is not
promised. Keep the matching package components together when deploying.

| Host | Upgrade action |
| --- | --- |
| Java / Kotlin | Use io.github.phuong-tran.coakka:runtime:3.0.0 and rebuild. Review the updated configuration, status and reply APIs in the current examples. |
| Spring Boot / Quarkus | Upgrade the adapter and Runtime together to 3.0.0. Use Java 17 and follow the framework guide for supported deployments. |
| C# | Upgrade CoAkka.Runtime to 3.0.0 and rebuild. Review the updated configuration and result types in the current examples. |
| Python | Upgrade to the matching 3.0.0 package and current APIs. Use a supported CPython build and follow the current examples for waiting on work and closing the application. |
| Node.js / Bun / Electron | Upgrade the matching package and application API together. Electron 3.0.0 depends exactly on the Node 3.0.0 connector; main-process ownership and the renderer boundary still apply. |
| Go | Change the old v1 import and module requirement to github.com/phuong-tran/coakka-runtime-go/v3 at v3.0.0. Recompile against the updated APIs. |
| Swift | Use an exact 3.0.0 SwiftPM requirement and rebuild. Host close is throwing; preserve both an operation failure and a cleanup failure. Deploy the application's dynamic library and resource bundle together. |

#### A short upgrade checklist

**Why update application code?** The updated APIs make startup settings,
request results and safe shutdown clearer and more consistent across languages.
Your application continues to own its business rules; review the integration
code that creates the Runtime, handles requests/replies and closes it.

1. Install the matching 3.0.0 packages, including any framework adapter, and
   compare your integration with the current example for your language.
2. Update changed imports, method calls and request/result handling, then
   rebuild or run the application. For example, Python message values are now
   read-only; use the sample's reply-building helpers instead of changing the
   incoming message.
3. Check a successful request/reply, a failed or timed-out request and clean
   shutdown before deploying. Successful startup alone does not exercise all
   of these paths.

**What if I only change the package version?** Some existing code may still
work. Code using a removed API or an older request/result type may instead
produce a build/import error, a missing-method error or an exception when a
handler runs. These indicate a call or type to update using the matching
example; changing CPU, connection or monitor settings does not fix them.

No monitor integration is required. When the corresponding options are omitted,
3.0.0 uses **AUTO**, **MULTIPLEXED** and **monitoring OFF**. Explicit settings
remain application choices. These defaults do not make every older API
compatible.

Start from the [getting-started guide](getting-started.md) and the
[matching source examples](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/README.md).
The per-channel receipts above contain the install command, qualification scope
and exact package identities.

### Defaults retained

**AUTO** CPU placement, **MULTIPLEXED** connections and **monitoring OFF**
remain the defaults. The normal path needs no advanced tuning.
Monitoring is opt-in; see
[defaults, monitoring and examples](operations/runtime-monitoring.md).

Application handlers and business rules remain application-owned. This release
does not move them into Core.

### Platform and application baselines

The package family includes **macOS ARM64, Linux ARM64/x64 and Windows ARM64/x64**.
No 32-bit or macOS Intel target is added. Consult the
[platform guide](reference/runtime-package-platform-evidence.md) for minimum
system versions and the checks completed for your language.

| Host | Application requirements and tested versions |
| --- | --- |
| JVM Runtime | Java 8; Windows ARM64 uses an ARM64 JDK 11+. |
| Spring Boot / Quarkus | Java 17. Published consumers cover Spring Boot 3.5.16 and Quarkus 3.35.2; the receipts distinguish earlier qualification from post-publication runs. |
| C# | .NET 8 package/application baseline. |
| Python | Standard CPython 3.11+. PyPy and experimental free-threaded builds are outside this release's qualification. |
| Node.js | Node 22.0.0+. |
| Bun | Bun 1.3.14+; native Windows ARM64 uses the 1.4.0+ profile. |
| Electron | Electron 42+, with its exact Node connector dependency. |
| Go | Go 1.22.12+; Windows ARM64 uses Go 1.25.5+. Follow the Go package's build prerequisites. |
| Swift | Swift 6.0 minimum. Tested profiles use Apple Swift 6.2 on macOS and Swift 6.0.3 on Linux/Windows x64. |

**Swift on Windows ARM64 alone remains an evaluation preview.** Swift itself
is a published release package. Support and test scope are recorded per platform.

### Verification and scope

Publication receipts record package downloads, installation checks and their
platform scope. This changelog backfill does not change the released packages
or introduce new performance or platform claims.

HTTP Runtime and Logger keep independent versions. Android and Mojo remain
previews and are not Maven Central releases. A 3.0.0 source/candidate directory
does not by itself publish the native SDK, Client/Inspect/runtime-test, Rust,
Tauri or Zig. Follow the [package ledger](../common/current-packages.md) for
those surfaces.

## Earlier releases

Use the [dated release history](releases/README.md) for 2.x and earlier package
identities. Existing tags, assets, package-manager archives, checksums and signed
publication receipts remain unchanged.
