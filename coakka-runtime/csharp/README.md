# C# Runtime samples

`CoAkka.Runtime 3.1.1` is [published on NuGet](https://www.nuget.org/packages/CoAkka.Runtime/3.1.1) with Core 3.1 r2.
Use .NET 8 or later and follow the [3.1.1 install guide](../../docs/coakka-runtime/releases/2026-11-04-nuget-csharp-runtime-3.1.1.md#install).

The [.NET 8 sample for 3.0.0](registry-basic/README.md) retains its original
package pin. Run it from the Samples checkout.

```sh
dotnet run --project coakka-runtime/csharp/registry-basic/CoAkkaRuntimeFirstRun.csproj
```

The 3.0.0 sample includes its matching Core. Migrate and recompile 2.x
applications when adopting the 3.x managed API.

## Local-feed qualification

The separate `basic` runner verifies an exact local package/catalog. Use the
[admitted candidate guide](../../docs/coakka-runtime/previews/3.0.0/runtime-csharp-candidate.md) for the
standard .NET8/C#12/ILLink build and matching-host scope.

```sh
bash run.sh coakka-runtime csharp basic
```

Set the explicit candidate/work/cache roots in the guide first. The basic
example starts one host, registers a text handler, sends an ask, observes a
matched route miss and awaits asynchronous disposal after success or failure. No application
queue/transport/capability default is guessed; the package projects Core truth.
Keep ASP.NET Core/HTTP at the external edge and application business policy
above the runtime. File/Stream and other integrations require the exact package
API and their separate ownership/evidence, not translated identifiers.
