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
| Native file-transfer contract | Public C11 source exercises a `9 * 1024 * 1024 + 731` byte multi-quantum loopback transfer through only the File Lane C ABI and checks SHA-256, durable receiver completion, progress, and counters. The exact promoted archive `2.4.0+c2f53117` passes the five-platform release gates and the public macOS consumer. | `bash run.sh runtime-test file-lane` |
| SFTP artifact publisher addon | Native addon `1.1.0+42841ae2` loads beside Runtime `2.3.0` on Linux ARM64/x86-64, macOS ARM64, and Windows 11 ARM64/x86-64. Every module exposes exactly 11 reviewed C symbols, has no ambient libssh2/libcrypto/libssl dependency, and passes matching-host pinned SFTP acquisition, integrity/no-clobber, failure, cancellation, and File Lane delivery evidence. | `bash runtime-addons/artifact-publisher-sftp/native/run.sh published` |
| Native streaming contract | Public C11 source transfers 97 ordered variable-size frames and verifies metadata, source-reported drops, pressure, waits, counters, terminal state, forget, and stop through only the Stream Lane C ABI. | `bash run.sh runtime-test stream-lane` |
| Sanitizers | Consumer-only ASan/UBSan and TSan controls are available from the public harness. A Core sanitizer claim requires the exact recorded runtime source generation and harness to be instrumented in one build; ordinary production binaries are never described as implicitly instrumented. Hosted CI does not run these expensive profiles. | See `runtime-test/README.md`; run sanitizer profiles explicitly on a controlled local or evaluator host. |
| Logger pressure | JVM, Java, Python, Node.js, Go, C#, Rust, and native logger pressure samples verify bounded logging rejection and dropped counters. | `bash run.sh logger <language> pressure` |
| Artifact pins | Public samples resolve pinned public artifacts and validate SHA256 rows. | `bash scripts/check-artifact-pins.sh` |
| Package-manager execution | npm Node.js and Bun `2.4.0` clean installs pass native request/reply, and Electron `2.4.0` passes its main-process intent smoke. Their registry tarballs match the staged receipt and carry native generation `2.4.0+c2f53117`. NuGet `2.4.0` passes repository-signature verification, exact comparison of all 16 candidate entries, clean request/reply/deadletter, and a live Spring Boot-to-C# CRUD exchange using explicit network nodes against the same native generation. PyPI `2.3.0` is byte-identical to its staged wheel and passes clean request/reply, deadletter, hot reload, and local desktop CRUD. Go `v1.6.0` and Swift `v2.3.0` remain on native generation `2.3.0+a83ab412`. | [Runtime Package And Platform Evidence](runtime-package-platform-evidence.md) |
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
