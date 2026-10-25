# Private C# runtime candidate sample

The sample uses exactly `CoAkka.Runtime` `0.0.0-p454.1` from the trusted local
feed described in `artifacts/runtime-csharp-candidate.props`. This is a private
candidate, not a public NuGet release. It carries Core2.5.1+f6b61768/ABI1 for
macOS ARM64, Linux ARM64 and native Windows x64. No separate native dependency
installation, source reference or native-library override is needed.

Use .NET SDK8.0.4xx/C#12; `runtime/csharp/global.json` restricts the build to
that feature band. The consumer targets .NET8 and is actually published with
ILLink. The library is trim-compatible; a library package cannot discard public
APIs that an unknown application may use. Wider architectures, minimum OS and
coordinated publication are separate gates.

## Standard .NET build

Obtain the trusted candidate directory with exactly one package under `feed/`.
Choose existing absolute work, NuGet and SDK-home directories. On the development
Mac, large work/cache trees belong on the mounted external APFS volume. Keep
these trusted trees stable during a build. For example:

```sh
export COAKKA_CSHARP_CANDIDATE_HOME="/absolute/candidate"
export COAKKA_SAMPLE_WORK_ROOT="/absolute/work"
export NUGET_PACKAGES="/absolute/nuget-cache"
export DOTNET_CLI_HOME="/absolute/dotnet-home"
export COAKKA_SAMPLE_RID="osx-arm64" # linux-arm64 on Pi
bash runtime/csharp/basic/run.sh
```

The wrapper calls ordinary `dotnet publish`, then executes the self-contained
installed app. Outputs remain under the selected work root for inspection/reuse;
there is no shell trap recursively deleting caller directories. MSBuild admits
the single bounded NuGet by exact name, size and streamed SHA-256 before restore
and build. The shared NuGet config maps only `CoAkka.Runtime` to that local feed;
only `Microsoft.*` SDK/runtime/ILLink tools can resolve from the official tools
source. A pre-existing product cache must contain the same admitted NuGet.
Trusted SDK/tool/cache trees are not a hostile-filesystem sandbox. Hashes prove
integrity against the catalog, not a publisher signature; do not use an untrusted
catalog or concurrently mutate caches.

Native Windows uses native `dotnet.exe`, not WSL ELF. This candidate's generated
Windows launcher is qualified with SDK8.0.426. A build-only Python3.11+ helper
uses the exact Core-owned artifact utility to remove SDK debug-locator metadata
from that unsigned launcher; unknown SDK/foreign/signed images fail closed.
No Python/private-Core dependency is added to the installed app or NuGet.
Set `COAKKA_ARTIFACT_PYTHON` to the native Python executable if `python` is not
on the build PATH. Set the same environment
variables in PowerShell to absolute Windows paths, choose `win-x64`, and set
`TEMP`/`TMP` to a task-owned directory under the work root. Then:

```text
cd runtime/csharp
dotnet publish basic/CoAkka.Runtime.Sample.csproj -c Release -r win-x64 -o "$env:COAKKA_SAMPLE_WORK_ROOT/csharp/published/win-x64" -warnaserror
& "$env:COAKKA_SAMPLE_WORK_ROOT/csharp/published/win-x64/CoAkka.Runtime.Sample.exe"
```

If Windows asks to allow a trusted test executable, allow it manually; do not
disable app protection or signing/security policy. The Java8 ASCII native-cache
limitation is not a C# filesystem limitation.

Publish only the selected installed/published output, never the entire work
root. Windows finalization keeps its hash-named original under
`<work>/csharp/apphost-private` for private diagnosis; do not redistribute it.
The temporary replacement is same-directory/atomic, with a bounded15s build
step; an unadmitted stage after forced termination requires explicit inspection,
not an automatic overwrite. A previously finalized exact hash is idempotent.
`scripts/pe_release_metadata.py` is an exact generated Core artifact utility;
do not edit it as a fork. It supplies build-file policy, not runtime/OS probes.

## Ownership and failure law

The app owns its text handler, two-second request budgets, assertions and
scoped shutdown. A typed matched route-miss is checked by its Core-issued reason
code and original target, not error text or a historical reason-name string.
Cleanup runs after success or failure; an operation and shutdown failure both
survive in an AggregateException. This is a cold application scope, not a new
runtime retry policy.

Queue, execution, connection, monitor and delivery choices are omitted from the
business sample. The connector forwards omitted queue intent as0 and Core owns
normalization; snapshots report its accepted capacity. A previously compiled
C# optional argument remains an explicit value until the app is recompiled.
This also applies to `ConnectorOrchestrator.StartLocal`: its omitted queue
forwards0, `StartSpec` is submitted intent, and `RuntimeConfig()` is Core's
accepted state. The facade does not probe the platform or supply a default table.
The sample reads effective Core AUTO/MULTIPLEX/monitorOFF and request/response
delivery2/2 facts without capability probes or a mirrored defaults table.

Build tools and the qualification harness own subprocess limits and temporary
staging. Process termination, sleep or power loss is not a graceful-shutdown
guarantee. Qualification holds a scoped awake lease for long workloads without
changing persistent power/security settings. These examples are correctness and
package evidence, not a throughput or whole-VM leak claim. Remaining samples,
tools, Android/camera, signing/legal and coordinated release gates stay separate.
