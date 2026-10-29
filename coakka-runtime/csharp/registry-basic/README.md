# C# Runtime 3.0.0: First Run From NuGet

This example uses .NET 8 or newer and **CoAkka.Runtime 3.0.0**. It is the
registry consumer for the new API; a 2.x package cannot run this source.
3.0.0 is currently awaiting publication. Until it appears on NuGet, use the
[local candidate guide](../../../docs/coakka-runtime/previews/3.0.0/runtime-csharp-candidate.md)
or the released 2.x sample linked from the [Runtime sample index](../../README.md).

After 3.0.0 is published, run from the Samples checkout:

```sh
dotnet run --project coakka-runtime/csharp/registry-basic/CoAkkaRuntimeFirstRun.csproj
```

Expected output:

```text
accepted-hello
```

The application starts a local host, registers an echo handler and sends one
request to it. `await using` closes the host when the program ends; no separate
server or Core build is required. CPU, connection and monitor defaults stay
owned by Core.

To verify an unpublished package from a local NuGet feed, restore this project
with that feed explicitly, then run with `--no-restore`. This is an evaluation
step, not a different source/API or a registry availability claim.

[Runtime integration](../../../docs/coakka-runtime/guides/runtime-integration-guide.md)
explains how to put the same lifecycle in an application.
