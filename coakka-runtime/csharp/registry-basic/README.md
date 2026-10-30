# C# Runtime 3.0.0: First Run From NuGet

This example uses .NET 8 or newer and **CoAkka.Runtime 3.0.0**. It is the
registry consumer for the new API; a 2.x package cannot run this source.
Run from the Samples checkout with the published package, or use the local-feed
verification below when evaluating a package before publication. See the
[Runtime sample index](../../README.md) for package availability and other examples.

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
with that feed explicitly, then run with `--no-restore`:

```sh
dotnet restore coakka-runtime/csharp/registry-basic/CoAkkaRuntimeFirstRun.csproj --source /absolute/selected/feed
dotnet run --project coakka-runtime/csharp/registry-basic/CoAkkaRuntimeFirstRun.csproj --no-restore
```

Use a fresh NuGet package cache when comparing different prepublication builds
with the same version. A local-feed check does not establish registry availability.

[Runtime integration](../../../docs/coakka-runtime/guides/runtime-integration-guide.md)
explains how to put the same lifecycle in an application.
