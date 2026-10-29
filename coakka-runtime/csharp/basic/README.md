# C# runtime basic candidate

Use exactly the private NuGet in
[the candidate guide](../../../docs/coakka-runtime/previews/3.0.0/runtime-csharp-candidate.md), not public2.5.2
or2.5.3. Choose .NET8, a matching RID and explicit external work/cache roots;
then run `bash run.sh` here or `bash run.sh coakka-runtime csharp basic` at repo root.
Native Windows uses the guide's ordinary PowerShell/dotnet workflow.

The actually ILLink-published app loads the bundled Core normally, checks its
identity/effective startup/delivery facts, receives `hello Ada`, checks a typed
route miss with the original target and leaves no pending ask. Both requests
keep their two-second budget. Core owns queue normalization and AUTO/MULTIPLEX
selection; no queue size or delivery hint is repeated in business code.

App-scope cleanup awaits `DisposeAsync` exactly once; an operation and cleanup failure are
both retained. Focused tests exercise all four combinations without native
resources. The normal consumer's diagnostics contain product facts, not private
paths, schemas or dependency identities. This sample does not claim a benchmark,
minimum-OS matrix, signing status or coordinated release completion.
