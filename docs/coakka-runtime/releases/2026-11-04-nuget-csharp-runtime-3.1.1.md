# C# Runtime 3.1.1 on NuGet

Released on 2026-11-04. C# connector 3.1.1 is its first release on Core
generation 3.1. The package bundles Core 3.1 r2, build `6789dd16210fc01f17cca4d2b66a592d1ac79121`, ABI 1.
The managed API baseline introduced in 3.0.0 still applies. Migrate and
recompile 2.x applications.

## Install

Use the .NET 8 SDK or later. Add the package to your project.

```sh
dotnet add package CoAkka.Runtime --version 3.1.1
```

Run the Quick Start in the package README. Its text request returns
`accepted-Ada Lovelace`. The package includes a `net8.0` managed asset and
native images for macOS ARM64, Linux ARM64/x64 and Windows ARM64/x64.

## Verification

The NuGet archive adds the repository signature. Every original archive
member matches the qualified package byte for byte. Signature verification
and a fresh registry-only .NET 8 consumer passed on macOS ARM64. The
consumer verified request/reply, a route-miss deadletter and the exact
loaded Core. Five matching-host .NET 8 runs qualify the identical executable
assets. ASan/LSan ran on Mac and physical Pi 5, with a separate TSan run on
Mac for the Core and Commons source used by this release.

| Package | SHA-256 |
| --- | --- |
| Qualified upload | `e51a0b7ffc02bb126dcb857ac1d65b47e259606b128d28107a60977017119155` |
| NuGet repository-signed archive | `5206902c9696d12f570afb9df68678cf283b7af0dc891eaf297fd9fe4ffed140` |

[CoAkka.Runtime 3.1.1 on NuGet](https://www.nuget.org/packages/CoAkka.Runtime/3.1.1) ·
[Current package versions](../../common/current-packages.md) ·
[Package and platform evidence](../reference/runtime-package-platform-evidence.md)
