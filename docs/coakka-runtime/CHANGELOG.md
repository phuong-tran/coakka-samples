# CoAkka Runtime Changelog

CoAkka Runtime is a polyglot, native-backed distributed runtime for
application-owned work. This page explains what changed for application
developers; the [current package ledger](../common/current-packages.md) owns
installation versions, and [release history](releases/README.md) retains the
individual publication receipts and artifact hashes.

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

All of these packages carry or depend on Core **3.0.0**, ABI **1**, generation
1522b4a9f30da1039ef8c7d198bbecfc749a2456. Runtime and connector version 3.0.0
does not mean a new native ABI or a redesign of Core's processing engine.

### Changed

- A new application-facing API baseline presents Core's accepted settings,
  capabilities and typed outcomes. Connectors submit application intent and
  report Core-issued facts; they do not reconstruct runtime state from local
  settings or infer an outcome from timing.
- Language-specific adapters handle values, callbacks, buffers and application
  scheduling while Core keeps shared delivery and lifecycle decisions.
  Python's adapter consolidates value conversion and request begin/await work;
  JVM retains direct-buffer integration. These are host-boundary refinements,
  not separate routing or transport implementations.
- Runtime, File and Stream APIs expose their current ownership and completion
  contracts. Applications must retain callbacks and data owners until work has
  retired and must await the documented shutdown. File and Stream capabilities
  already existed in 2.x; this entry does not introduce them for the first time.
- Spring Boot and Quarkus 3.0.0 depend exactly on JVM Runtime 3.0.0. The
  application continues to own HTTP, handlers and business data; the adapters
  integrate framework discovery and application lifetime.
- Published packages include the matching native payloads. Normal package
  installation does not require a separate Core installation.
- Samples now include registry-first Java/Kotlin, Spring/Quarkus, Node.js,
  Bun, Electron, Python, C#, Go and Swift entrypoints. Documentation and
  navigation are grouped by product under docs/coakka-runtime,
  docs/coakka-http-runtime, docs/coakka-logger and docs/common. Historical
  versioned artifact paths and receipts remain available.

### Breaking changes and migration

3.0.0 requires source migration and recompilation from the previous application
API. Do not combine a frozen 2.x example with a 3.0.0 dependency, or substitute a
different same-version native image.

| Host | Upgrade action |
| --- | --- |
| Java / Kotlin | Use io.github.phuong-tran.coakka:runtime:3.0.0 and rebuild. Use the current transport/allocator metadata names and grouped startup/delivery snapshots. Replace the removed startupConnectionResult() inspection with startupConfig().connection; check deliveryConfig() separately. Review the updated replyTextTo result. |
| Spring Boot / Quarkus | Upgrade the adapter and Runtime together to 3.0.0. Keep the application's chosen, qualified framework platform. Java 17 is the framework application baseline; this does not add native-image/AOT support. |
| C# | Upgrade CoAkka.Runtime to 3.0.0 and rebuild. Consume grouped accepted configuration and typed outcomes; unavailable observations remain nullable. Preserve generated JSON metadata when trimming an application. |
| Python | Upgrade to the matching 3.0.0 wheel and current APIs. Use the supported CPython build, retain callback/data ownership and await retirement; do not reuse a 2.x adapter or an unrelated native library. |
| Node.js / Bun / Electron | Upgrade the matching package and application API together. Electron 3.0.0 depends exactly on the Node 3.0.0 connector; main-process ownership and the renderer boundary still apply. |
| Go | Change the old v1 import and module requirement to github.com/phuong-tran/coakka-runtime-go/v3 at v3.0.0. Recompile; update TransportProvider, AllocatorProvider, TransportBindHost and TransportBindPort metadata access. |
| Swift | Use an exact 3.0.0 SwiftPM requirement and rebuild. Host close is throwing; preserve both an operation failure and a cleanup failure. Deploy the application's dynamic library and resource bundle together. |

Start from the [getting-started guide](getting-started.md) and the
[matching source examples](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/README.md).
The per-channel receipts above contain the install command, qualification scope
and exact package identities.

### Defaults retained

**AUTO** CPU placement, **MULTIPLEXED** connections and **monitoring OFF**
remain Core-owned defaults. Omit advanced settings for the normal path and
inspect what Core accepted. Monitoring is opt-in; see
[defaults, monitoring and examples](operations/runtime-monitoring.md).

Application handlers and business rules remain application-owned. This release
does not move them into Core.

### Platform and application baselines

The native family includes **macOS ARM64, Linux ARM64/x64 and Windows ARM64/x64**.
No 32-bit or macOS Intel target is added. Native floors are macOS 13+, Linux
ARM64 glibc 2.35+ and Linux x64 glibc 2.36+; consult the
[platform evidence](reference/runtime-package-platform-evidence.md) for each
host and OS profile.

| Host | Qualified application/toolchain baseline |
| --- | --- |
| JVM Runtime | Java 8; native Windows ARM64 uses an ARM64 JDK 11+. Owned bytecode remains Java-8 compatible. |
| Spring Boot / Quarkus | Java 17. Published consumers cover Spring Boot 3.5.16 and Quarkus 3.35.2; the receipts distinguish earlier qualification from post-publication runs. |
| C# | .NET 8 package/application baseline. |
| Python | GIL-enabled CPython 3.11+. PyPy and free-threaded Python are outside this release's qualification. |
| Node.js | Node 22.0.0+. |
| Bun | Bun 1.3.14+; native Windows ARM64 uses the 1.4.0+ profile. |
| Electron | Electron 42+, with its exact Node connector dependency. |
| Go | Go 1.22.12+ and a C11 cgo compiler; native Windows ARM64 uses Go 1.25.5/cgo. |
| Swift | Manifest/language floor 6.0; qualified profiles use Apple Swift 6.2 on macOS and Swift 6.0.3 on Linux/Windows x64. |

**Swift on Windows ARM64 alone remains an evaluation preview.** Swift itself
is a published release package. A matching CPU architecture on another OS does
not establish matching-host execution.

### Verification and scope

Publication receipts record actual registry/tag downloads and installed
consumers. They retain the qualified native/managed executable identities and
distinguish fresh publication checks from earlier platform campaigns.
This changelog backfill makes no new benchmark, sanitizer or all-platform test
claim. In particular, a throughput comparison is not an isolated measurement
of connector overhead.

HTTP Runtime and Logger keep independent versions. Android and Mojo remain
previews and are not Maven Central releases. A 3.0.0 source/candidate directory
does not by itself publish the native SDK, Client/Inspect/runtime-test, Rust,
Tauri or Zig. Follow the [package ledger](../common/current-packages.md) for
those surfaces.

## Earlier releases

Use the [dated release history](releases/README.md) for 2.x and earlier package
identities. Existing tags, assets, package-manager archives, checksums and signed
publication receipts remain unchanged.
