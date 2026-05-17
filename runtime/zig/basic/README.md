# Zig Runtime Basic

This sample unpacks the public native runtime archive, builds a small Zig
program against the C ABI headers, and runs a minimal runtime smoke.

It demonstrates:

- direct Zig import of the public runtime C headers
- native runtime loading from the public archive
- route snapshot apply
- raw request/reply through the delivered-request lane
- route-miss deadletter through the ask-client path
- start, stats read, stop, and destroy

It intentionally does not cover queue pressure, packaging, or cluster behavior.

Run from the repository root:

```sh
bash run.sh runtime zig basic
```
