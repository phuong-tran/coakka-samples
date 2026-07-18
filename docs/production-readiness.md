# Production Readiness

CoAkka is an early public runtime surface. The samples are useful for learning
the model and verifying the published artifacts, but production confidence
should come from measurements in the environment where the system will run.

This page is intentionally direct about fit, measurement, and ownership.

## Where CoAkka Fits

CoAkka is a good fit when an application needs one or more of these boundaries:

- runtime request/reply or event delivery by stable target names
- explicit payload identity across services or languages
- route snapshots and route generations instead of hand-wired hand-wired clients
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

## Current Maturity

The public artifacts are published and pinned, but the ecosystem should still
be treated as an early integration surface until each target deployment has its
own operating evidence. The sample repository currently spans logger and
runtime connector lines on the `1.2.1` generation plus the runtime-client CLI
line on `1.3.1+2215b0f`; those version numbers do not replace deployment
validation.

Before production use, collect evidence for:

- Linux and container operation under realistic deployment limits
- restart behavior for app hosts and runtime participants
- reconnect behavior when peers, routes, or transport links disappear
- queue pressure and strict no-drop behavior under bursts
- memory use over long-running workloads
- deadletter volume, timeout rates, and retry amplification risk
- artifact and native-library loading on the target OS and CPU architecture
- rollout behavior when route snapshots or payload schemas change

Use the samples as a starting point, not as a substitute for those measurements.
For the public evidence ledger and known gaps, see
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

Retries should stay a business policy above the runtime. The runtime can return
reply, deadletter, and timeout outcomes; the application decides whether the
operation is safe to retry and how to prevent retry loops from amplifying load.

## Suggested Rollout Path

1. Run one container sample and one language basic sample.
2. Read the [Runtime Glossary](runtime-glossary.md) until `target`,
   `envelope`, `payload identity`, `deadletter`, and `generation` are clear.
3. Wire one non-critical application workflow through a local runtime target.
4. Add deadletter, timeout, queue, and route-generation metrics.
5. Move one cross-process or cross-language handler only after the local shape
   is understood.
6. Run restart, reconnect, memory, and queue-pressure tests in Linux/container
   conditions before standardizing on the path in that environment.
