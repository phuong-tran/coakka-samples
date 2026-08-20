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
| Native file-transfer contract | Public C11 source exercises a `9 * 1024 * 1024 + 731` byte multi-quantum loopback transfer through only the File Lane C ABI and checks SHA-256, durable receiver completion, progress, and counters. The Simple profile remains supported; the Owner-aware profile additionally verifies exact owner projection and grant handoff against the sealed `2.5.0+4b65d0b2` candidate without claiming publication. | `bash run.sh runtime-test file-lane-simple`; `bash run.sh runtime-test file-lane-owner-aware` |
| Artifact-source addons | Eleven native addons at `1.1.0+d1032f6d` and SFTP replacement `1.2.0+88b9a047` expose exact reviewed 11-symbol C ABIs. The released implementations carry bounded lifecycle, cancellation, timeout, integrity/no-clobber, fault, resource-recovery, and platform evidence. Public samples independently consume the archives and require publisher/sender plus receiver `COMPLETED + OK`. Deterministic fixtures are not live provider certification. | `bash run.sh runtime-addons all published` |
| Native streaming contract | Public C11 source transfers 97 ordered variable-size frames and verifies metadata, source-reported drops, pressure, waits, counters, terminal state, forget, and stop through only the Stream Lane C ABI. The Owner-aware profile adds exact publisher projection and single-admission grant handoff. | `bash run.sh runtime-test stream-lane-simple`; `bash run.sh runtime-test stream-lane-owner-aware` |
| Sanitizers | Consumer-only ASan/UBSan and TSan controls are available from the public harness. A Core sanitizer claim requires the exact recorded runtime source generation and harness to be instrumented in one build; ordinary production binaries are never described as implicitly instrumented. Hosted CI does not run these expensive profiles. | See `runtime-test/README.md`; run sanitizer profiles explicitly on a controlled local or evaluator host. |
| Logger pressure | JVM, Java, Python, Node.js, Go, C#, Rust, and native logger pressure samples verify bounded logging rejection and dropped counters. | `bash run.sh logger <language> pressure` |
| Artifact pins | Public samples resolve pinned public artifacts and validate SHA256 rows. | `bash scripts/check-artifact-pins.sh` |
| Runtime tools | `coakka-client` and `coakka-runtime-inspect` `2.4.0+c2f53117` archives are published for all five native platforms. `coakka-client` has macOS ARM64 command evidence; Linux ARM64/x86-64 have matching-architecture Docker build and dependency evidence. Inspect has macOS ARM64 and Linux ARM64/x86-64 matching-host command plus `serve` evidence. Both Windows targets pass cross-build, PE architecture, dependency, archive, and checksum gates without a matching-host execution claim. | `bash run.sh runtime-client`; `bash run.sh runtime-inspect published-smoke` |
| Package-manager execution | Maven Central `io.github.phuong-tran.coakka:runtime:2.4.1` passes signed bundle validation plus clean Java 8 and Java 26 consumers and carries native generation `2.4.0+c2f53117`. Maven Central framework adapters `spring-boot-starter:2.4.1` and `quarkus-extension:2.4.1` match their production-signed candidates; empty-cache Java 17 consumers complete Spring Boot `3.5.16` request/reply and Quarkus `3.35.2` fast-jar HTTP request/reply. Maven Central `io.github.phuong-tran.coakka:logger:1.2.2` carries logger generation `1.2.1+f50756ebff0d`; its public files match the signed candidate and an empty-cache Java 8 consumer completes emit/drain. npm Node.js and Bun Runtime `2.5.0` clean installs pass native request/reply, and Electron Runtime `2.5.0` passes the main-process intent smoke while resolving exact Node.js `2.5.0`; all three registry tarballs are byte-identical to sealed candidate `6b56a27` and carry native generation `2.5.0+4b65d0b2`. Logger npm `1.2.7` remains on sealed candidate `7718ce6`; Node.js and Bun pass emit/drain and Electron passes its main-process intent smoke over logger generation `1.2.1+f50756ebff0d`. NuGet Runtime `2.5.0` and Logger `1.2.3` target `net8.0`; the exact public packages pass repository-signature and candidate-entry verification plus packaged behavior on .NET 8, 9, and 10. PyPI Runtime `2.5.0` is byte-identical to sealed candidate `6b56a27`, carries the five-platform native generation `2.5.0+4b65d0b2`, exposes normalized file-scope license metadata, and passes a clean registry-installed request/reply consumer; candidate package tests also cover File Lane and Stream Lane. SwiftPM `v2.5.0` and Runtime Go `v1.8.0` pass package readiness and clean remote-tag request/reply consumers on macOS ARM64. Runtime Go `v1.8.0` additionally verifies the Go `1.22` compatibility floor and current stable toolchain. Logger Go `v1.2.6` passes clean remote-tag emit/drain consumers with Go `1.18.10` and stable Go while retaining native generation `1.2.1+f50756ebff0d`. | [Runtime Package And Platform Evidence](runtime-package-platform-evidence.md), [npm Runtime 2.5.0](releases/2026-08-20-npm-runtime-2.5.0.md), [PyPI Runtime 2.5.0](releases/2026-08-20-python-runtime-2.5.0-pypi.md), [Go Logger v1.2.6](https://github.com/phuong-tran/coakka-publish/blob/main/docs/releases/2026-08-17-go-logger-v1.2.6.md), [Framework Adapter Maven Central 2.4.1](releases/2026-08-17-framework-adapters-maven-central-2.4.1.md), [NuGet Runtime 2.5.0](releases/2026-08-20-nuget-csharp-runtime-2.5.0.md), [NuGet .NET App-Host Baseline](releases/2026-08-17-dotnet-nuget-app-host-baseline.md), and [JVM Logger Maven Central 1.2.2](releases/2026-08-17-logger-jvm-maven-central-1.2.2.md) |
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
