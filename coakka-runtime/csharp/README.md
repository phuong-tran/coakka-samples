# C# Runtime samples

Use the [3.2.1 first run](registry-basic/README.md) and see the
[package ledger](../../docs/common/current-packages.md) for registry availability.

Run the .NET 8 first run from the Samples checkout.

```sh
dotnet run --project coakka-runtime/csharp/registry-basic/CoAkkaRuntimeFirstRun.csproj
```

The 3.2.1 package includes Core 3.2. Migrate and recompile 2.x
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
