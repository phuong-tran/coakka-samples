# NuGet C# Runtime 2.5.2

Status: published to NuGet and registry-verified on August 20, 2026.

```text
package: CoAkka.Runtime
version: 2.5.2
connector source: 3ae74f43d061904d3bdc38a1d84d0479cd6c43bf
annotated tag: coakka-runtime-nuget-v2.5.2
annotated tag object: 0c4a9d146925f57dba07ccf86572c610a687111f
native generation: 2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a
candidate SHA-256: 7e618e43d4405e94f4f21009bdab67f0e88ccae7e6459ba07fc2680ec2c3fed4
registry SHA-256: 9b72897fc5fc916e45a191b43ab21fee5c585bd2dd351ce04cfaef38953d0ab0
```

The candidate is
`runtime/csharp/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a-3ae74f4/CoAkka.Runtime.2.5.2.nupkg`
from Publish staging commit `d28f1f13469f378f20a0ce772c522439fe3f0b5f`.

The package exposes typed File receive and Stream publish owner grants,
validated trusted reconstruction, and exact replica-owner endpoint pinning.
It targets `net8.0` and carries one managed library plus native RID assets for
Linux ARM64/x86-64, macOS ARM64, and Windows ARM64/x86-64.

## Verification

- The frozen candidate passes package readiness and request/reply plus
  route-miss deadletter consumers on .NET 8, 9, and 10.
- The public package passes `dotnet nuget verify --all` with the valid
  NuGet.org repository signature.
- NuGet.org adds only `.signature.p7s`; all 19 candidate ZIP entries remain
  byte-identical in the signed registry package.
- The registry download completes request/reply and route-miss deadletter on
  .NET 8, 9, and 10 on macOS ARM64.
- The public `net8.0` sample installs exact `2.5.2` from NuGet and completes
  request/reply, matched deadletter, and bounded shutdown.

Connector C# code remains Apache-2.0. Custom terms apply only to bundled native
Core files; application use remains free, including commercial and production
use.
