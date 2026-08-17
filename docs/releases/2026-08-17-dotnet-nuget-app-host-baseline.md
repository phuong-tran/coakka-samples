# NuGet .NET App-Host Baseline

Date: 2026-08-17

CoAkka's C# package lane now supports applications from .NET 8 onward through
these public NuGet coordinates:

```sh
dotnet add package CoAkka.Runtime --version 2.4.1
dotnet add package CoAkka.Logger --version 1.2.3
```

- [CoAkka.Runtime 2.4.1](https://www.nuget.org/packages/CoAkka.Runtime/2.4.1)
- [CoAkka.Logger 1.2.3](https://www.nuget.org/packages/CoAkka.Logger/1.2.3)

Each package contains one `lib/net8.0` managed asset. The exact immutable
candidate and the repository-signed public download execute on .NET 8, 9, and
10. Newer app hosts do not require separate packages unless a future connector
intentionally exposes a framework-specific capability.

## Release Identity

| Surface | Package version | Native generation | Connector tag |
| --- | --- | --- | --- |
| Runtime | `2.4.1` | `2.4.0+c2f53117` | `coakka-runtime-nuget-v2.4.1` |
| Logger | `1.2.3` | `1.2.1+f50756ebff0d` | `coakka-logger-nuget-v1.2.3` |

- connector source commit:
  `801a0a6e67152465568c72246b112beb299360a3`
- Runtime candidate SHA-256:
  `c62304e1011a96a0a80fc288a0d0d55683401c49efb9823f7fa38eeaca959b94`
- Runtime registry SHA-256:
  `c61d8bbc7b1c7343008988a4546cad1117aa810948bb7b61b8add00d19d2d6ae`
- Logger candidate SHA-256:
  `a929605a76631cff07c1488f8e60f09331e66bbeeb6d72fd8e1a1741922bb93c`
- Logger registry SHA-256:
  `f9501901ab71376feb027ccf97100f17de48792c45ddf5a843d88ec129a9639a`

The registry file hash differs because NuGet.org adds its repository signature.
Every candidate ZIP entry is byte-identical in the corresponding public
package, and `.signature.p7s` is the only added entry.

## Evidence

- Both public downloads pass `dotnet nuget verify --all` with a valid NuGet
  repository signature.
- Runtime has five native RID assets and completes request/reply plus
  route-miss deadletter on .NET `8.0.30`, `9.0.19`, and `10.0.8`.
- Logger has five native RID assets and completes accepted write, drain, and
  bounded-pressure drop behavior on the same three runtime versions.
- SDK package validation passes against public Runtime `2.4.0` and Logger
  `1.2.2`; this release does not change either public managed API.
- The bundled native bytes match their previous public NuGet packages. No
  runtime ABI, logger ABI, queue law, lifecycle, or native behavior changed.
- Connector CI run `31992973586` passes every job for source commit
  `801a0a6e67152465568c72246b112beb299360a3`.

## Public Source And Samples

- [C# Runtime samples](https://github.com/phuong-tran/coakka-samples/tree/main/runtime/csharp)
- [C# Logger samples](https://github.com/phuong-tran/coakka-samples/tree/main/logger/csharp)
- [Package catalog, compatibility, and release evidence](https://github.com/phuong-tran/coakka-publish)

The sample repository uses `net8.0` as the consumer baseline. The package
catalog records the minimum target separately from the tested app-host matrix
so a future host-only feature can be versioned deliberately without raising the
low-level connector baseline by accident.
