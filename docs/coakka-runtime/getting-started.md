# Start With CoAkka Runtime

Choose a language in the [Runtime sample guide](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/README.md).
Run its smallest request/reply example before adding files, streams or remote peers.

## Using a published package

Use the [published sample snapshot](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime) and follow that snapshot's README,
runner and exact package version together. Download or check out the snapshot
before running it; do not copy a 2.x install command into current 3.0.0 source.
The [published package ledger](../common/current-packages.md) records available releases.

A basic example starts a host, registers one handler, sends a request, checks
its reply and closes the host. Its README gives prerequisites, the command
and expected output. Keep those steps together in the selected example.

## C# Runtime 3.0.0 from NuGet

C# 3.0.0 is published. Use .NET 8 or newer and the
[matching first-run example](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/csharp/registry-basic):

```sh
dotnet run --project coakka-runtime/csharp/registry-basic/CoAkkaRuntimeFirstRun.csproj
```

Run from the Samples checkout; expected output is `accepted-hello`. The package
includes its matching native Core. Existing applications install with
`dotnet add package CoAkka.Runtime --version 3.0.0`; migrate and recompile 2.x
callers against the new API.

## Evaluating 3.0.0

Other main-branch Runtime examples still use **local 3.0.0 candidates**.
They require the matching artifact and catalog from the
[candidate index](previews/README.md). Their registry releases remain separate.
Android and Mojo are evaluation previews. Swift is a release package; only
Swift on Windows ARM has preview scope for this train.

## After the first reply

Read the [integration guide](guides/runtime-integration-guide.md) to move the
same lifecycle into your application. For default settings, monitor use and
examples, read [monitoring](operations/runtime-monitoring.md).
