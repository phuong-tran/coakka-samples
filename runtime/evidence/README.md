# Runtime Evidence Compatibility Index

The active public native harness now lives at the root-level
[`runtime-test/`](../../runtime-test/README.md), where it is visible before any
language- or framework-specific sample. This directory remains an index and a
compatibility location for older commands.

The first lane is the native public-ABI baseline:

```sh
bash run.sh runtime-test smoke
```

It consumes the published native runtime package through the public C ABI. It
does not include or reach into private core sources.

Prefer a controlled Linux host when measurements will inform Kubernetes or
container deployment decisions. macOS, Windows, Docker, CI, and VM runs remain
useful portability gates, but virtualized throughput is not a comparison point.

The measured local target path is:

```text
caller builds request
  -> native submit path
  -> bounded runtime ingress
  -> route snapshot resolves the stable target
  -> local handler handoff
  -> deterministic echo handler
  -> reply submit
  -> terminal response routing
  -> caller drains the response
```

The native lane covers four workload shapes:

| Mode | Purpose |
| --- | --- |
| `smoke` | Prove one finite local request/reply path and its route invariants. |
| `pressure` | Force bounded admission and prove rejection becomes deadletter evidence. |
| `stress` | Run a larger finite local request/reply workload with an explicit payload. |
| `soak` | Submit for a bounded duration with an explicit in-flight guard, then drain. |

The same public source directory also contains a capability-aware connection
strategy executable. It verifies all four connection modes, structured
unsupported/not-entitled results, atomic invalid apply, startup lifecycle, and
post-start immutability. See [Runtime Test](../../runtime-test/README.md) for
its build command and exact TLS/non-TLS scope.

Payload-oriented runs are opt-in:

```sh
bash run.sh runtime-test smoke --payload 64K --requests 128
bash run.sh runtime-test stress --payload 128K --requests 2000
bash run.sh runtime-test soak --payload 64K --duration 30s --max-in-flight 64
```

Finite runs are capped at 500,000 requests. Pressure remains small-payload
because its purpose is admission evidence, not large-frame throughput.

Every lane aggregates counters and prints one final JSON document. Setup
messages go to stderr; stdout remains machine-readable JSON. Per-request
logging is intentionally absent because it changes the workload.

Treat every number as environment-local. OS, architecture, CPU, memory
pressure, thermal state, background workload, virtualization, containers,
compiler, build profile, and power mode can all change the result. Run the same
command several times under controlled conditions and compare complete JSON
documents, not one isolated throughput value.

See [Runtime Test](../../runtime-test/README.md) for the exact measurement
boundary, pass invariants, mode semantics, JSON fields, and source/prebuilt
execution paths.
