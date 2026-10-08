# CoAkka HTTP Runtime Documentation

CoAkka HTTP Runtime gives C/C++, Java/Kotlin, Python,
JavaScript/TypeScript, and Go one shared bounded HTTP contract through idiomatic
APIs. Health and snapshots are public per connector; a dedicated
monitor channel is claimed only where that package actually exposes it.

This is the separate HTTP server/client product, not the distributed
target/message runtime. Return to the [ecosystem index](../README.md) for
CoAkka Runtime, Logger and addons.

## Contents

- [Start Here](#start-here)
- [How It Works](#how-it-works)
- [Language Guides](#language-guides)
- [Operations And Evidence](#operations-and-evidence)
- [Release Status](#release-status)
- [Samples](#samples)

## Start Here

Start with [Introducing CoAkka HTTP Runtime](../coakka-http-runtime-introduction.md):
why HTTP mechanics become fragmented across languages, how a shared native
implementation reduces repeated work, which improvements can benefit all
connectors, and what remains language-specific. It also maps the supported
features and explains the archive-only distribution.

The product README explains why the shared runtime exists, shows the ordinary
route-and-handler shape, and introduces its capability and ownership model:

- [CoAkka HTTP Runtime](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-http-runtime)
- [Complete capability matrix](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/capabilities.md)
- [Queue, backpressure, health, and monitoring operations](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/operations.md)

## How It Works

- [How requests, pressure, monitoring, and shutdown flow](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/how-it-works.md)
- [App Host, language connector, shared foundation, and addon roles](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/app-host-and-connectors.md)
- [Serve a built frontend beside API, SSE, and WebSocket routes](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/frontend-and-backend.md)

## Language Guides

- [Native C/C++](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-http-runtime/native)
- [Java/Kotlin](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-http-runtime/jvm)
- [Python](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-http-runtime/python)
- [JavaScript/TypeScript on Node and Bun](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-http-runtime/javascript)
- [Go](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-http-runtime/go)

Each guide has its own table of contents, first service, capacity/backpressure
behavior, truthful monitoring surface, lifecycle, and package gate.

## Operations And Evidence

- [Historical architecture comparison (not a benchmark)](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/comparison-2026-09-13.md)
- [Focused comparisons with familiar platforms](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-http-runtime/docs/comparisons)
- [Raspberry Pi 5 benchmark protocol](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/benchmark-rpi5.md)
- [Measured results and machine/configuration details](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/benchmark-results-rpi5.md)

The comparison is a positioning snapshot, not a performance leaderboard.
Measured rows carry their workload and machine context; unmeasured framework
profiles remain explicitly pending. Native results are standalone, not a
comparison with another HTTP implementation.

## Release Status

CoAkka HTTP Runtime `1.0.0` archives are available in `coakka-publish/main`,
under the exact candidate paths named by each language guide. Native and
connector packages cover macOS ARM64, Linux ARM64/x86-64 and Windows
ARM64/x86-64; sample-runner evidence is narrower and documented separately.
There is no npm, Maven Central, PyPI or tagged Go-module distribution for HTTP
Runtime yet. Do not substitute a CoAkka Runtime package coordinate.

See the [sample scope](../../coakka-http-runtime/README.md#verified-candidate-scope)
for executed profiles and the [feature index](../../coakka-http-runtime/FEATURES.md)
for example coverage and remaining gaps. This does not imply production
signing or unmeasured-platform support.

## Samples

Start in the [runnable sample area](../../coakka-http-runtime/README.md), then
use the [feature coverage index](../../coakka-http-runtime/FEATURES.md). Samples
consume checksum-pinned offline candidates, not a private source build. The
index distinguishes working examples from remaining coverage gaps.
