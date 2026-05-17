# Production Evidence

This page is the current evidence ledger for the public sample repository. It
is intentionally narrower than a production claim. The samples prove the public
artifact surface is runnable and that the runtime vocabulary is consistent
across languages; they do not prove capacity for an arbitrary production
deployment.

## Current Evidence

| Area | Public evidence today | How to run |
| --- | --- | --- |
| Container runtime path | Node.js web to Python store runs as separate processes with browser-visible state and no store REST fallback; screenshots are committed under `docs/assets/`. | `bash run.sh containers node-python` |
| Local request/reply | JVM, Python, Node.js, Go, C#, Rust, native C/C++, Mojo, and Zig basic runtime samples. | `bash run.sh runtime <language> basic` |
| Route miss and deadletter | JVM, Java, Python, Node.js, Go, native C/C++, Mojo, and Zig samples expose route-miss/deadletter behavior. | `bash run.sh runtime jvm deadletter`, `bash run.sh runtime python deadletter`, `bash run.sh runtime native basic` |
| Route reload | Python hot-reload applies a newer generation and rejects stale or invalid snapshots. | `bash run.sh runtime python hot-reload` |
| Queue pressure | Native C pressure sample verifies bounded queue rejection and deadletter counters. | `bash run.sh runtime native pressure` |
| Logger pressure | JVM, Java, Python, Node.js, Go, C#, Rust, and native logger pressure samples verify bounded logging rejection and dropped counters. | `bash run.sh logger <language> pressure` |
| Artifact pins | Public samples resolve pinned public artifacts and validate SHA256 rows. | `bash scripts/check-artifact-pins.sh` |
| CI smoke | GitHub Actions checks entrypoints, artifact pins, quickstart, selected logger samples, and selected runtime samples. | `.github/workflows/sample-smoke.yml` |
| Manual smoke-load | Workflow-dispatched benchmark smoke emits validated JSON for selected profiles. | `.github/workflows/bench-smoke.yml` |

## Visual Evidence

The Node.js web to Python store container sample was captured from the
prebuilt public image path:

```sh
bash run.sh containers node-python up -d
bash run.sh containers node-python smoke
```

Screenshots:

- [Node.js web UI](assets/container-node-python-create-web.png)
- [Python store UI](assets/container-node-python-create-store.png)

The web screenshot shows an accepted create command and runtime counters. The
store screenshot shows the same customer state changed by runtime messages from
the web process.

## Not Proven Yet

These are not public production guarantees yet:

- sustained cross-process load under realistic Linux memory and CPU limits
- peer restart and reconnect behavior across long-running workloads
- route reload under concurrent cross-process traffic
- latency envelopes across operating systems, CPU classes, and container
  runtimes
- durable memory profile under multi-hour or multi-day service operation
- operator dashboards, alerts, and incident runbooks
- package-manager lanes for Mojo and Zig

## Evidence Checklist For A Real Deployment

Before a service treats this runtime path as production-ready, collect evidence
in the target environment:

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

The current `bench-smoke` workflow is a shape check. Treat its JSON output as
public CI evidence, not a production capacity claim.

## Current Reading Order

1. Run a container sample.
2. Read [Runtime Glossary](runtime-glossary.md).
3. Run one basic runtime sample in a familiar language.
4. Run one deadletter or pressure sample.
5. Read [Runtime Integration Guide](runtime-integration-guide.md).
6. Use this page as the production evidence checklist.
