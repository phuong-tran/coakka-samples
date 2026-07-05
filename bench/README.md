# Benchmark And Load Policy

This directory is reserved for repeatable load and benchmark support. Treat the
files here as reference evidence tied to their metadata and execution context.

Run a local reference profile:

```sh
python3 bench/run_smoke_load.py \
  --profile runtime-native-pressure \
  --result-class macos-smoke \
  --output bench/macos-smoke/local-runtime-native-pressure.json
```

The GitHub Actions workflow `bench-smoke` runs the same harness on
`ubuntu-latest` only when triggered manually.

Every JSON artifact should pass the validator before it is uploaded:

```sh
python3 bench/validate_smoke_load.py bench/linux-ci/<commit>-<profile>.json
```

The validator checks common metadata and profile-specific invariants. For
example, `runtime-python-hot-reload` must finish on generation `2` after
rejecting stale and invalid route snapshots, and `runtime-native-pressure` must
show constrained-queue rejection and matching deadletter counts.

## Profiles

| Profile | Command | Evidence |
| --- | --- | --- |
| `runtime-native-pressure` | `bash run.sh runtime native pressure` | Bounded native queue pressure, queue rejection, and deadletter accounting. |
| `runtime-jvm-basic` | `bash run.sh runtime jvm basic` | JVM request/reply delivery and matched response accounting. |
| `runtime-python-hot-reload` | `bash run.sh runtime python hot-reload` | Route snapshot apply, stale generation rejection, invalid snapshot rejection, and final generation diagnostics. |

## Result Classes

| Class | Directory | Meaning |
| --- | --- | --- |
| macOS smoke-load | `macos-smoke/` | Developer-machine smoke evidence for shape checks and coarse same-machine regression checks. |
| Linux CI load | `linux-ci/` | Linux runner output. More useful than macOS for public comparison, but still not a bare-metal claim. |
| Linux hardware benchmark | `linux-hardware/` | Reserved for self-hosted or dedicated Linux hardware runs. Use this class when comparing durable Linux measurements. |

Do not compare macOS numbers against Linux numbers as if they were the same
execution context. macOS results stay in the repo as clearly labeled reference
smoke-load output.

## Required Metadata

Every result file should include:

- repository commit
- artifact manifest commit or raw base
- native runtime package version
- OS and kernel/version
- CPU model
- queue capacity
- `strictNoDrop`
- worker count
- warm-up duration
- measured duration
- repetition count
- aggregation rule, such as median of three runs

Without that metadata, a number is not useful after the local machine changes.

## macOS Use

macOS is acceptable for development guardrails:

- keep the machine on power
- disable Low Power Mode
- stop unrelated heavy background work
- warm up before recording
- compare changes on the same machine

These runs should answer: did this change obviously regress the sample shape on
the same developer machine? They are not a substitute for target-environment
capacity work.

## Linux Pending Path

The first Linux step is the manual GitHub Actions `bench-smoke` workflow. It
emits validated JSON artifacts with the same metadata shape. A self-hosted
Linux runner can reuse the same output format under `linux-hardware/` when a
dedicated measurement lane is ready.
