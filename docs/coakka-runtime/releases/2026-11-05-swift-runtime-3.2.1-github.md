# Swift Runtime 3.2.1 on GitHub

Released 2026-11-05: [GitHub Release v3.2.1](https://github.com/phuong-tran/coakka-runtime-swift/releases/tag/v3.2.1).

```swift
.package(url: "https://github.com/phuong-tran/coakka-runtime-swift.git", exact: "3.2.1")
```

Use the `CoAkkaRuntime` product and Swift 6.0 or later. The first two
version numbers identify bundled Core generation 3.2; the last identifies
Swift connector revision 1. No separate Core install is required.
Deploy the executable, dynamic library and resource bundle together.

- Tagged source commit: `f029f3786b3e20c30655e1f838ce3e2c9b376a95`.
- Core build: `f9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45`, ABI 1.
- Qualified Core SDK manifest SHA-256: `16d8239b6f6f0d113e7059429400a055a7a79500bc5ab38970328c030b5fc677`.
- Swift header manifest SHA-256: `e31cd48f78d3c09199ea840ea957490a8a9fda50186ffc85fa02914fe62b8926`.
- Tagged file manifest SHA-256: `6795a43def236c6f21811a5f6f137e85e75e1a0450dbbf3d2a7bea560c018f01`.
- GitHub archive: `coakka-runtime-swift-3.2.1.tar.gz` (4363684 bytes),
  SHA-256 `bd313b7dc929bd59d74f81ca5db0b3343d235c4f5a80d63974e9762ef9c8667f`.

An anonymous download verified both GitHub release assets. A clean SwiftPM
app resolved the exact tag from the public repository, pinned its revision in
`Package.resolved`, and ran with the bundled Core on macOS ARM64. The first
run printed `accepted-hello`. A second app built against the same tag and ran
after its executable, dynamic library, and resource bundle were moved away
from the build tree. It verified the loaded Core build and exercised
request/reply, deadletters, File and Stream transfer, callback ownership and
shutdown. All 49 source tests passed locally on macOS ARM64 with the bundled
Core. The [GitHub Actions run](https://github.com/phuong-tran/coakka-runtime-swift/actions/runs/41888048785)
passed on macOS 15 ARM64 after publication.

The five native images match the qualified SDK. Prepublication installed
checks covered macOS ARM64, Raspberry Pi Linux ARM64, Linux x64 (WSL2), and
physical Windows x64. Windows ARM64 remains an evaluation preview. This
release adds no publisher signing or notarization.

[SwiftPM first run](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/swift/registry-basic) ·
[Current packages](../../common/current-packages.md) ·
[Platform evidence](../reference/runtime-package-platform-evidence.md).
