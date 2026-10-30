# Swift Runtime 3.0.0 on GitHub

Released 2026-10-30: [GitHub Release v3.0.0](https://github.com/phuong-tran/coakka-runtime-swift/releases/tag/v3.0.0).

```swift
.package(url: "https://github.com/phuong-tran/coakka-runtime-swift.git", exact: "3.0.0")
```

Use product `CoAkkaRuntime`. Start with [the remote SwiftPM app](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/swift/registry-basic).
Migrate/recompile 2.x callers and preserve both operation and throwing-close
failures. The app's dynamic library and resource bundle deploy together.

Core is `3.0.0+1522b4a9f30da1039ef8c7d198bbecfc749a2456` / ABI1. Five native payloads are included:
macOS ARM64, Linux ARM64/x64, Windows ARM64/x64. **Windows ARM64 alone is an
evaluation preview**; Swift itself is a released package. Supported profiles
retain Apple Swift 6.2/macOS and Swift 6.0.3/Linux/Windows x64, with manifest/language
floor 6.0. Native floors remain macOS 13+, Linux ARM64 glibc 2.35 and Linux x64
glibc 2.36. AUTO/MULTIPLEXED/monitor OFF remain Core-owned.

Tagged source commit: `0a91a0f3288b51fa84972a7c530cbd27e0e3e452`.
Archive `coakka-runtime-swift-3.0.0.tar.gz`: 4147599 bytes,
SHA256 `6ecb9ec06cfe13ccd007b562de6fbb1c8315fa5b4c411592317cb1a2dd7ea931`. Anonymous GitHub asset download matches.
The tag's 73 source files include 46 unchanged library/C/header/native members;
reviewed docs, CI and test integration are the differences. No runtime/native
redesign or additional supported architecture is introduced.

Local and GitHub Mac ARM source tests pass 49/49 without skips. Fresh remote
SwiftPM resolution downloads 3.0.0, pins the exact revision and matches every
tagged file. Apple Swift 6.2 compiles with warnings as errors. Its real installed
consumer passes after executable, dynamic library and resource bundle are moved
away from hidden build roots: request/reply, deadletter, File 9MiB+731,
Stream 2MiB+731, callbacks/ARC retirement, refusal/retry, concurrent frames and
shutdown. The ordinary first-run app also passes. Prior supported matching-host
memory/race/resource/minimum-OS profiles remain tied to the unchanged source;
this is not a new post-upload execution on every OS, and makes no new Windows
ARM qualification claim.

Go3.0.0 remains released independently; JVM and other channels retain their
separate publication state. Historical 2.x tags and package-manager warehouse
receipts remain immutable. No signing/notarization claim is added.

[Current packages](../../common/current-packages.md) ·
[Package/platform evidence](../reference/runtime-package-platform-evidence.md).
