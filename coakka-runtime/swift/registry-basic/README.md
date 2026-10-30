# Swift 3.0.0 first run

Use the supported Swift profile for your platform: Apple Swift 6.2/macOS,
Swift 6.0.3/Linux or Windows x64. The manifest/language floor is Swift 6.0.
Windows ARM64 alone is preview.

```sh
cd coakka-runtime/swift/registry-basic
swift run -c release CoAkkaFirstRun
```

Expected output: `accepted-hello`. SwiftPM resolves the public GitHub tag 3.0.0.
The app owns its handler and preserves operation/close errors. The dynamic
library and resource bundle must be deployed together; Core supplies CPU,
connection and monitor defaults. No separate Core installation is needed.

[Current packages](../../../docs/common/current-packages.md) ·
[Integration](../../../docs/coakka-runtime/guides/runtime-integration-guide.md).
