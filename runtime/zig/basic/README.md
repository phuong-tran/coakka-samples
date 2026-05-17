# Zig Runtime Basic

This sample unpacks the public Zig source connector package and runs its
packaged runtime smoke.

It demonstrates:

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
