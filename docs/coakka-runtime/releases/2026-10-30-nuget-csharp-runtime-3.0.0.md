# C# Runtime 3.0.0 on NuGet

Released on 2026-10-30: [CoAkka.Runtime 3.0.0](https://www.nuget.org/packages/CoAkka.Runtime/3.0.0).
This release covers the C# connector and its bundled Core; other language
registries, HTTP Runtime and Logger keep their independent release status.

## Install and first run

Use .NET 8 or newer:

```sh
dotnet add package CoAkka.Runtime --version 3.0.0
```

Run the [matching sample](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/csharp/registry-basic) from the Samples checkout:

```sh
dotnet run --project coakka-runtime/csharp/registry-basic/CoAkkaRuntimeFirstRun.csproj
```

Expected output: `accepted-hello`. No separate native installation is needed.
Core owns the normal CPU/connection/monitor defaults; application handlers
remain application-owned.

## Compatibility

3.0.0 is a new .NET API baseline. Migrate and recompile 2.x applications;
binary/source compatibility with 2.x is not promised. The package targets
`net8.0`, bundles Core `3.0.0+1522b4a9f30da1039ef8c7d198bbecfc749a2456` / ABI1, and includes:

- macOS ARM64 (`osx-arm64`)
- Linux ARM64 and x64 (`linux-arm64`, `linux-x64`)
- Windows ARM64 and x64 (`win-arm64`, `win-x64`)

The qualified executable payload is unchanged from the final selected package.
The registry archive adds only `.signature.p7s`; every original ZIP member
matches byte-for-byte. NuGet repository signature verification passes. Fresh
NuGet-only .NET8/macOS ARM64 execution prints `accepted-hello`; existing
five-target qualification remains tied to the identical managed/native bytes.
This is not a new post-publication execution claim on every OS/.NET version.

## Package identities

| File | SHA256 |
| --- | --- |
| Qualified unsigned package | `9ebb8c12b598b0dca1301998993c8173320a5ba927ea6ee5d3c01dfd91a61d61` |
| NuGet repository-signed package | `32b0371db8221d490efd74ee1acde4e075c8e9d697e5081a6fa3be51ff8db15c` |

The unsigned package is 4152259 bytes; the registry
package is 4165351 bytes. The added repository signature
explains their different archive hashes.

[Current coordinates](../../common/current-packages.md) ·
[Package/platform evidence](../reference/runtime-package-platform-evidence.md) ·
[Defaults and monitoring](../operations/runtime-monitoring.md).
