# C# Runtime 3.2.1 on NuGet

Released on 2026-11-05. Version `3.2.1` is the first C# connector release on
Core generation `3.2`, build `f9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45`, ABI 1.

## Install

Use the .NET 8 SDK or later.

```sh
dotnet add package CoAkka.Runtime --version 3.2.1
```

The package provides a `net8.0` managed assembly and native images for
macOS ARM64, Linux ARM64 and x64, and Windows ARM64 and x64. Use the
[C# first run](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/csharp/registry-basic)
to exercise request/reply. For an existing 2.x application, follow the
[migration guidance](../CHANGELOG.md#breaking-changes-and-migration).

## Registry verification

NuGet added its repository signature. All 20 original files match the
qualified package by SHA-256. Signature verification passed. A clean .NET 8
restore from NuGet.org on macOS ARM64 passed request/reply, a route-miss
deadletter and shutdown, and verified the SHA-256 of the loaded Core.
The desktop qualification includes the .NET 8 lifecycle gate on Windows
ARM64 in UTM.

| Archive | SHA-256 |
| --- | --- |
| Qualified upload | `d25366526b214f1079ec5222a30fa78635a4b7a71c9e9809f1fafcd7074539ce` |
| Repository-signed NuGet archive | `827b3f84268ba117b9b5c83acb8d0f2192413e0b7a76a4a3e34ea9dfab03b573` |

[CoAkka.Runtime 3.2.1 on NuGet](https://www.nuget.org/packages/CoAkka.Runtime/3.2.1) ·
[Current package versions](../../common/current-packages.md)
