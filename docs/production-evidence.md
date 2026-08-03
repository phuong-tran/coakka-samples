# Production Evidence

This page is the product evidence ledger for the public sample repository. The
samples show that the published artifact surface is runnable and that the
runtime vocabulary stays consistent across languages. Capacity, SLO, and
operator-acceptance claims are attached to the target environment where the
runtime path will operate.

## Current Evidence

| Area | Public evidence today | How to run |
| --- | --- | --- |
| Container runtime path | Node.js web to Python store runs as separate processes with browser-visible state and no store REST fallback; screenshots are committed under `docs/assets/`. | `bash run.sh containers node-python` |
| Local request/reply | JVM, Python, Node.js, Go, C#, Rust, native C/C++, Mojo, and Zig basic runtime samples. | `bash run.sh runtime <language> basic` |
| Route miss and deadletter | JVM, Java, Python, Node.js, Go, native C/C++, Mojo, and Zig samples expose route-miss/deadletter behavior. | `bash run.sh runtime jvm deadletter`, `bash run.sh runtime python deadletter`, `bash run.sh runtime native basic` |
| Route reload | Python demonstrates the connector path. The public native C11 harness alternates full route snapshots across quota-gated concurrent traffic, observes every accepted generation as active before releasing the next traffic slice, proves active/replaced target outcomes, and rejects stale or invalid generations without mutating final state. This means route-snapshot reload, not TLS credential reload. | `bash run.sh runtime python hot-reload`; `bash run.sh runtime-test hot-reload` |
| Queue pressure | Native C pressure sample verifies bounded queue rejection and deadletter counters. | `bash run.sh runtime native pressure` |
| Native public-ABI evidence | Repeatable smoke, pressure, stress, and soak scenarios enforce route, terminal-outcome, reply, deadletter, and bounded-admission invariants and emit one machine-readable JSON result. Prefer controlled Linux for deployment-oriented measurements; macOS, Windows, CI, Docker, and VM runs are portability evidence. | `bash run.sh runtime-test smoke` |
| Native connection-strategy contract | Public C source checks all four modes against effective capabilities, structured atomic apply/rejection, invalid-apply rollback, complete startup lifecycle, and post-start immutability. Its JSON explicitly excludes active TLS/mTLS certificate exercise. | Build `coakka_runtime_v2_connection_strategy_evidence` from `coakka-samples/runtime-test/` or the included publish projection at `runtime-test/`. |
| Native concurrency contract | Public C11 source releases concurrent producers against one runtime, requires exactly one reply or explicit queue-pressure deadletter per admitted request, rejects unexplained terminal outcomes, verifies submit-versus-stop convergence on `CLOSED`, and contends independent complete runtime lifecycles. Small CI profiles are portability gates; thread count, request count, lifecycle iterations, queue capacity, and timeout remain configurable for deeper evaluation. | `bash run.sh runtime-test race --threads 4 --requests 256` |
| ThreadSanitizer | Consumer-only TSan is available from the public harness. A Core race claim is made only when the exact recorded runtime source generation and the harness are instrumented in one build; ordinary production binaries are never described as implicitly instrumented. | See `runtime-test/README.md`; run the separate Core source-level TSAN workflow. |
| Logger pressure | JVM, Java, Python, Node.js, Go, C#, Rust, and native logger pressure samples verify bounded logging rejection and dropped counters. | `bash run.sh logger <language> pressure` |
| Artifact pins | Public samples resolve pinned public artifacts and validate SHA256 rows. | `bash scripts/check-artifact-pins.sh` |
| CI smoke | GitHub Actions checks entrypoints, artifact pins, quickstart, selected logger samples, and selected runtime samples. | `.github/workflows/sample-smoke.yml` |
| Manual smoke-load | Workflow-dispatched benchmark smoke emits validated JSON for selected profiles. | `.github/workflows/bench-smoke.yml` |

## Visual Evidence

The Node.js web to Python store container sample is verified from the published
multi-arch image path:

```sh
bash run.sh containers node-python up -d
bash run.sh containers node-python smoke
```

Screenshots:

![Node.js web UI showing accepted runtime create command and counters](assets/container-node-python-create-web.png)

![Python store UI showing customer state changed by runtime messages](assets/container-node-python-create-store.png)

The web screenshot shows an accepted create command and runtime counters. The
store screenshot shows the same customer state changed by runtime messages from
the web process.

## Deployment Evidence To Collect Per Environment

Collect these areas per deployment profile:

- sustained cross-process load under realistic Linux memory and CPU limits
- peer restart and reconnect behavior across long-running workloads
- route reload under concurrent cross-process traffic
- latency envelopes across operating systems, CPU classes, and container
  runtimes
- durable memory profile under multi-hour or multi-day service operation
- operator dashboards, alerts, and incident runbooks
- package-manager lanes for Mojo and Zig

## Evidence Checklist For A Deployment

When a service standardizes on this runtime path, collect evidence in the
target environment:

| Check | Required observation |
| --- | --- |
| Linux/container baseline | Same sample or service path runs under the target container runtime and resource limits. |
| Load and queue pressure | Queue depth, rejection, deadletter, timeout, and matched-reply counters stay explainable under burst traffic. |
| Restart | A participant restart fails closed, recovers cleanly, and does not duplicate business ownership. |
| Reconnect | Peer or transport interruption produces visible runtime outcomes and then resumes on a healthy route. |
| Route reload | Newer route generations apply; stale generations are rejected; invalid snapshots do not replace the active table. |
| Memory | Resident memory and queue sizes stay within the service budget under steady load. |
| Retry policy | User retries remain application policy and do not amplify runtime pressure blindly. |
| Artifact loading | Native libraries load from the same package layout used in deployment. |
| Observability | Logs/metrics include target, source, generation, node identity, route miss, timeout, deadletter, and queue pressure counters. |

## Benchmark Policy

Benchmarks are useful only when the metadata travels with the number. Public
sample benchmark artifacts must record:

- repository commit
- public artifact generation
- OS and CPU
- container or host runtime
- queue capacity and `strictNoDrop`
- warmup and measured repetitions
- aggregation rule
- whether the result is only a smoke-load reference

Benchmark artifacts are supporting evidence, not the main product positioning.
CoAkka's primary claim is the runtime capability boundary: target ownership,
route snapshots, bounded delivery, replies, deadletters, and diagnostics for
application-owned work.

Benchmark comparisons should stay at the transport-backed runtime delivery boundary. CoAkka
is not an L7 HTTP/gRPC framework benchmark; do not frame numbers as HTTP/gRPC
replacement claims. Compare route lookup, bounded admission, framing, delivery
outcome, reply matching, deadletter behavior, and queue pressure under the same
payload and transport profile.
If no same-class runtime comparator exists, use benchmark artifacts for CoAkka
release regression and deployment-profile capacity evidence.

The current `bench-smoke` workflow is a shape check. Treat its JSON output as
public CI evidence, not as a substitute for target-environment capacity work.

## Current Reading Order

1. Run a container sample.
2. Read [Runtime Glossary](runtime-glossary.md).
3. Run one basic runtime sample in a familiar language.
4. Run one deadletter or pressure sample.
5. Read [Runtime Integration Guide](runtime-integration-guide.md).
6. Use this page as the production evidence checklist.
