# CoAkka HTTP Runtime Documentation

CoAkka HTTP Runtime gives C/C++, Java/Kotlin, Python,
JavaScript/TypeScript, and Go one shared bounded HTTP contract through idiomatic
APIs. Health and snapshots are public per connector; a dedicated
monitor channel is claimed only where that package actually exposes it.

## Contents

- [Start Here](#start-here)
- [How It Works](#how-it-works)
- [Language Guides](#language-guides)
- [Operations And Evidence](#operations-and-evidence)
- [Release Status](#release-status)
- [Samples](#samples)

## Start Here

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

- [Dated comparison with Netty, Tomcat, Spring, Jetty, Chi, Gin, and Bun](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/comparison-2026-09-13.md)
- [Focused comparisons with familiar platforms](https://github.com/phuong-tran/coakka-publish/tree/main/coakka-http-runtime/docs/comparisons)
- [Raspberry Pi 5 benchmark protocol](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/docs/benchmark-rpi5.md)

The comparison is a positioning snapshot, not a performance leaderboard. The
benchmark document keeps result tables explicitly pending until the frozen
Linux ARM64 artifacts are measured on the physical Pi.

## Release Status

CoAkka HTTP Runtime `1.0.0` is staged as an immutable private candidate for
macOS ARM64, Linux ARM64/x86-64, and Windows ARM64/x86-64. npm, Maven Central,
PyPI, a tagged public Go module, production signing, runnable samples, and
external benchmark claims remain closed gates.

No staged artifact is treated as publishable until package contents, language
README, sample source, and matching-host application evidence agree.

## Samples

Runnable examples will live under the stable
[sample area](../../coakka-http-runtime/README.md). That directory currently
records the lane plan and promotion contract; it does not advertise an install
command before the package-resolution and matching-host smoke paths are ready.
