# Private Node/Bun runtime candidate samples

This branch validates a private candidate, not a registry release. Node and Bun
use the exact archives in `artifacts/runtime-js-candidate.json`, version
`3.0.0`, with bundled Core
`3.0.0+1522b4a9f30da1039ef8c7d198bbecfc749a2456` (ABI 1).
Node 22.0.0 and Bun 1.3.14 are the required minima, except Bun on Windows
ARM64 requires 1.4.0. The catalog records each Bun target requirement. Both candidates include
macOS ARM64, Linux ARM64/x64 and Windows ARM64/x64. Each host requires its own
installed default-path execution on all five targets before promotion;
minimum-OS and publication claims remain separate gates. Earlier long resource
and lifecycle campaigns remain historical evidence for the unchanged processing
source, not execution evidence for these new archive identities.

Node, Bun and Electron share one candidate release train. Electron consumes
the exact Node version and archive recorded in its own candidate catalog.
Tauri follows its separate Rust release train. Match the full catalog identity,
not only the version string, when obtaining a candidate archive.

Obtain the matching archive from the private candidate owner. Do not substitute
an npm version, unpacked source tree or separate native library.

## Run

Create an existing work directory on suitable storage. On the development Mac,
use mounted external APFS storage, not the internal SSD or a default temp path.
Set absolute paths; the runner never downloads a missing archive:

```sh
export COAKKA_SAMPLE_WORK_ROOT="/absolute/work"
export COAKKA_RUNTIME_CANDIDATE_PACKAGE="/absolute/coakka-v2-connector-node-3.0.0.tgz"
node coakka-runtime/scripts/runtime-js-candidate.mjs node basic
node coakka-runtime/scripts/runtime-js-candidate.mjs node deadletter
# Select the Bun archive instead, then:
bun coakka-runtime/scripts/runtime-js-candidate.mjs bun basic
```

The corresponding `runtime/{node,bun}/.../run.sh` wrappers invoke this runner.
Windows may invoke the same JavaScript entrypoint with native Node/Bun and
Windows absolute paths; WSL execution is not evidence for the Windows DLL.
Node requires its standard npm CLI next to the runtime installation. Bun uses
its own package manager. No separate Core install or native toolchain is needed.

The runner verifies the archive hash/length before creating a unique task
directory, disables install hooks, uses a task-owned cache and verifies every
installed package member against the catalog before running the app. Node npm
uses offline mode. Bun installs only the verified local archive with its
dependency-free package metadata; this is not a network sandbox claim.
It refuses native/source/loader environment overrides rather than hiding them.
The catalog is a trusted repository input, not an authenticity signature.

Only the unique child directory is removed, after its child process exits, on
success, failure or handled SIGINT/SIGTERM. The caller's work directory remains.
Install and app child deadlines are 60s and 30s; termination escalates after 1s.
The app's request deadline remains 2s. SIGKILL, power loss and an OS process that
cannot be reaped are outside graceful cleanup guarantees. Do not run hostile
package managers or mutate inputs concurrently.

Admission bounds: catalog 1MiB; archive 32MiB; 64 package files, each 8MiB,
64MiB total; 256 tree entries and depth 8. Package links, missing/extra/changed
files, dependency or install-hook drift are refused. This cold installation
policy is sample-owned; it does not replace Core configuration or capability
policy.

## App ownership

The application supplies its identity and owned route only. It reads accepted
startup facts from Core; it does not probe the OS, repeat a default table or
select a legacy response pipe. AUTO/MULTIPLEX and delivery modes are reported
from the runtime, not reconstructed from the start input. Both basic apps check
the JSON echo and await shutdown. The deadletter app checks the typed route-miss
outcome and retires its bounded observation iterator before awaiting shutdown.

Historical walkthrough recordings and older package guides are not evidence
for this candidate. Exact installed sample evidence is tracked separately.
Source/package/resource/shrink evidence remains attached to each exact archive;
this sample is not a new throughput or whole-host leak qualification.
Other connectors, tools, Android/camera, coordinated release,
legal/signing and publication gates are not closed by these three samples.
