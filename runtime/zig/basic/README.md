# Zig Runtime Basic

This sample keeps the runnable Zig entrypoint in this repository while using
the public Zig source connector package for the reusable runtime helper and
bundled native runtime library.

It demonstrates:

- sample-local Zig code over the published Zig runtime helper
- source package extraction from the public artifact surface
- native runtime loading from the package bundle
- route snapshot apply
- raw request/reply through the delivered-request lane
- route-miss deadletter through the ask-client path
- start, stats read, stop, and destroy

It intentionally does not cover queue pressure, packaging, or cluster behavior.

Run from the repository root:

```sh
bash run.sh runtime zig basic
```
