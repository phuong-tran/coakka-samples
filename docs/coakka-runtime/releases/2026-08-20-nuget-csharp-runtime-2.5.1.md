# NuGet C# Runtime 2.5.1

Status: published to NuGet and registry-verified on August 20, 2026.

```text
package: CoAkka.Runtime
version: 2.5.1
connector source: 11c155586796b0fbe946df273d2bbfe8058eaec5
annotated tag: coakka-runtime-nuget-v2.5.1
annotated tag object: 45e0d0df647845907f6abbdc69d2f21d61cde6d3
native generation: 2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a
candidate SHA-256: f241064ba045b2dd4c31fd79dfea20d52e22c15096a4c04fbf150d8bd2953282
registry SHA-256: 683da8c884202515c2ae56d0a406dd76b44beafb5a240a1096ed3ebcbfd5f96d
```

The candidate is
`runtime/csharp/releases/2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a-11c1555/CoAkka.Runtime.2.5.1.nupkg`
from Publish staging commit `1b018c8fa75673f13278047baa17b70835f67454`.

The package targets `net8.0` and carries one managed library plus native RID
assets for Linux ARM64/x86-64, macOS ARM64, and Windows ARM64/x86-64. Public
APIs, ABI, protocol behavior, and native files are unchanged from 2.5.0.

## Verification

- The frozen candidate passes package readiness and request/reply plus
  route-miss deadletter consumers on .NET 8, 9, and 10.
- The public NuGet package passes `dotnet nuget verify --all` with the valid
  NuGet.org repository signature.
- NuGet.org adds only `.signature.p7s`; all 19 candidate ZIP entries remain
  byte-identical in the signed registry package.
- The public `net8.0` sample installs exact `2.5.1` from NuGet and completes
  request/reply, matched deadletter, and bounded shutdown on macOS ARM64.
- The package license endpoint and README expose Apache terms, CoAkka Native
  Artifact License 1.2, the file-scope map, and notice through stable links.

Connector C# code is Apache-2.0. Custom terms apply only to bundled native Core
files; application use remains free, including commercial and production use.
