# NuGet C# Runtime 2.5.0

Date: 2026-08-20

The public NuGet coordinate is:

```text
package: CoAkka.Runtime
version: 2.5.0
connector source: 6b56a27b2139c8abb26483a04c2e26b14c4ab2fd
annotated tag: coakka-runtime-nuget-v2.5.0
annotated tag object: a46224711a1217c37422a34fa563bbf6e21cb3b9
native generation: 2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a
candidate SHA-256: fc2247aac2b406a0a28e2679fb89f02127d43a0d25c087e61fed9a84067dd9a6
registry SHA-256: 80a45afd9e223bbaf5cb9ca2f4dc8ca72473ab8744d19682e556b2f206bb0962
```

The package targets `net8.0` and contains one managed library plus exact native
RID assets for Linux ARM64/x86-64, macOS ARM64, and Windows ARM64/x86-64. It
exposes request/reply, deadletters, File Lane, and Stream Lane over the sealed
Runtime 2.5.0 native generation.

The candidate passes package readiness and request/reply plus route-miss
deadletter consumers on .NET 8, 9, and 10. The downloaded NuGet.org package
passes `dotnet nuget verify --all`. NuGet.org adds only `.signature.p7s`; all
19 candidate ZIP entries remain byte-identical in the repository-signed
registry package. These app-host executions are on macOS ARM64. Linux and
Windows retain the matching-host native and package-shape evidence recorded in
the Runtime 2.5.0 platform ledger; no new .NET app-host execution is claimed
for those systems by this receipt.

Connector material is Apache-2.0. Bundled CoAkka native files retain the CoAkka
Public Artifact License 1.1. `PACKAGE-LICENSE.md` maps the terms by file scope,
and `NOTICE` summarizes the aggregate. Publisher signing remains absent; the
downloaded public package carries the NuGet.org repository signature.
