# Production Readiness

CoAkka is a published runtime product surface. The samples are runnable product
evidence and integration guidance; production confidence is then attached to
measurements in the environment where the system will run.

This page is intentionally direct about fit, measurement, and ownership.

## Where CoAkka Fits

CoAkka is a good fit when an application needs one or more of these boundaries:

- runtime request/reply or event delivery by stable target names
- explicit payload identity across services or languages
- route snapshots and route generations instead of hand-wired clients
- deadletters and timeout outcomes that are visible as runtime vocabulary
- local and cross-process handlers that can move between JVM, Python, Node.js,
  Go, C#, Rust, Mojo and Zig source samples, and native C/C++ without
  redesigning the business contract

HTTP still belongs at real external edges such as browser/API entry points.
CoAkka is for the runtime boundary after the app has accepted work and
needs to route that work to another target, process, or language host.

## When Direct Calls Are Enough

CoAkka may be unnecessary when the system is only one or two services with a
stable REST or gRPC call and no need for runtime route snapshots, target-based
addressing, payload identity, deadletters, or polyglot handler ownership.

The model is different:

```text
traditional client call:
  URL or client object -> request DTO -> HTTP/gRPC response

CoAkka runtime call:
  target -> envelope -> payload identity -> reply or deadletter
```

That model is useful when those runtime boundaries matter. When the application
only needs one ordinary service call, the direct service call should stay direct.

## Product Readiness And Deployment Evidence

The public artifacts are published and pinned. They are product artifacts, not
temporary walkthrough assets. The current runtime and logger generations live
in the `coakka-publish` compatibility matrix and release notes. Those version
numbers identify the published lanes, while capacity and SLO claims stay tied
to each target deployment profile.

For each deployment profile, collect evidence for:

- Linux and container operation under realistic deployment limits
- restart behavior for app hosts and runtime participants
- reconnect behavior when peers, routes, or transport links disappear
- queue pressure and strict no-drop behavior under bursts
- memory use over long-running workloads
- deadletter volume, timeout rates, and retry amplification risk
- artifact and native-library loading on the target OS and CPU architecture
- rollout behavior when route snapshots or payload schemas change

When the deployment uses [Runtime File Transfer](runtime-file-transfer.md),
also collect:

- sender and receiver `COMPLETED + OK` evidence for the same transfer identity;
- queue-full, cancellation, timeout, restart/resume, disk-full, and integrity
  failure behavior;
- TLS or mutual-TLS certificate and peer-identity failure evidence for the
  actual network boundary;
- destination filesystem capacity, temporary-file cleanup, checkpoint
  durability, and terminal-record retention behavior;
- throughput, CPU, memory, and worker-count measurements for representative
  file sizes without assuming development-host capacity.

Use the samples as the supported public integration surface, then attach
deployment-specific measurements to the environment being standardized. For the
public evidence ledger and target-environment checklist, see
[Production Evidence](production-evidence.md).

## Config Ownership

The runtime does not silently read Kubernetes metadata, environment variables,
or service-discovery data by itself. The app host or connector layer owns that
mapping and passes the result into the start spec.

That is deliberate:

- tests can build start specs without depending on a specific platform
- the same runtime vocabulary works in CLI, desktop, service, container, and
  embedded-style hosts
- platform-specific policy stays in the app host, framework adapter, or control
  plane instead of being hidden inside the runtime

Production integrations still map values such as `systemName`, `nodeId`,
endpoint host/port, route generation, and route entries from their own
deployment source. For container examples, see
[Containerized Runtime Notes](containerized-runtime.md).

## Observability Expectations

The samples print runtime info, stats, matched replies, and deadletters. A real
service should integrate those signals into its normal observability path:

- queue depth and queue rejection counters
- route miss and deadletter counters
- matched response and matched deadletter counters
- timeout counts by operation or target
- active route generation
- runtime identity: `systemName`, `nodeId`, endpoint host/port
- file-lane queue depth, active transfers, retained records, completed bytes,
  failures by result, cancellation, and progress age when file transfer is used

Retries should stay a business policy above the runtime. The runtime can return
reply, deadletter, and timeout outcomes; the application decides whether the
operation is safe to retry and how to prevent retry loops from amplifying load.

## Suggested Rollout Path

1. Run one container sample and one language basic sample.
2. Read the [Runtime Glossary](runtime-glossary.md) until `target`,
   `envelope`, `payload identity`, `deadletter`, and `generation` are clear.
3. Wire one bounded application workflow through a local runtime target.
4. Add deadletter, timeout, queue, and route-generation metrics.
5. Move one cross-process or cross-language handler only after the local shape
   is understood.
6. Run restart, reconnect, memory, and queue-pressure tests in Linux/container
   conditions as part of standardizing on the path in that environment.
