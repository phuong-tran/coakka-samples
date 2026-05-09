# Benchmark And Load Policy

This directory is reserved for repeatable load and benchmark support. It does
not contain production benchmark claims yet.

## Result Classes

| Class | Directory | Meaning |
| --- | --- | --- |
| macOS smoke-load | `macos-smoke/` | Developer-machine reference only. Useful for shape checks and coarse regression checks on the same machine. |
| Linux CI load | `linux-ci/` | Linux runner output. More useful than macOS for public comparison, but still not a bare-metal claim. |
| Linux hardware benchmark | `linux-hardware/` | Reserved for self-hosted or dedicated Linux hardware runs. This is the right source for durable performance claims. |

Do not compare macOS numbers against Linux numbers as product claims. macOS
results are allowed only when clearly labeled as reference smoke-load output.

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
the same developer machine? They should not answer: what is the production
throughput of CoAkka?

## Linux Pending Path

The first Linux step should be a GitHub Actions job that emits JSON artifacts
with the same metadata shape. Later, a self-hosted Linux runner can reuse the
same output format.
