# Logger Samples

These samples show the CoAkka logger as a bounded native logging core consumed
through host-language connectors.

The useful claim is not "yet another logger API." The useful claim is that
logging becomes safer and easier to reason about when queue shape, drop
behavior, and delivery counters are explicit instead of hidden behind
best-effort adapters and optimistic defaults.

Many logging stacks become unreliable exactly when systems need them most:

- synchronous sinks stall request or worker paths
- asynchronous appenders hide queue growth until RAM and latency spike
- implicit drop behavior loses records without a clean emitted/delivered/dropped
  account
- language-by-language adapters drift in semantics and diagnostics

CoAkka logger is designed to be narrower and more predictable:

- bounded queue instead of pretending memory is infinite
- explicit reject/drop behavior under pressure
- stable native core shared across host-language connectors
- drain and counter semantics that are visible in samples and diagnostics
- a shape that fits runtime-aware applications instead of only console output

If the application already has a logging stack that is simple, bounded, and
easy to audit under pressure, keep it. The reason to use this logger is when
the team wants logging behavior that stays explicit under load instead of
becoming another hidden source of stalls, heap growth, or ambiguous loss.

## Why The Logger Matters

Logging is not free. The wrong logging path can quietly damage application
behavior:

- request latency rises because formatting and sink writes block hot paths
- worker throughput drops because log I/O competes with real work
- RAM use grows because queues are treated as a safety blanket instead of a
  hard boundary
- incident analysis gets weaker because the system cannot say clearly what was
  emitted, delivered, or dropped

The CoAkka logger exists to keep those tradeoffs visible. It does not promise
that every record will always be delivered. It promises that queue capacity,
accept/reject behavior, and drop counters are explicit so operators and
application owners can reason about the cost of logging instead of guessing.

## Why It Fits CoAkka

The logger and runtime are independent surfaces, but they fit together well.

The runtime already makes delivery outcomes explicit: target, route generation,
reply, deadletter, queue pressure, and lifecycle diagnostics. The logger
extends the same operational mindset to log delivery:

- bounded admission instead of hidden buffering
- explicit counters instead of vague "async logger" claims
- native-core semantics that stay aligned across JVM, Python, Node.js, Bun,
  Electron, Tauri, Go, C#, Rust, and native hosts
- cleaner correlation between runtime events and application/system logs

That makes the logger useful for:

- runtime route and deadletter tracing
- queue-pressure debugging
- operator-visible incident timelines
- polyglot services that need one logging contract across languages
- applications that want logging behavior to stay predictable under load

Start with JVM:

```sh
bash run.sh logger basic
```

Check local toolchains and artifact source:

```sh
bash run.sh doctor
```

Run all logger samples:

```sh
bash run.sh logger
```

From any leaf sample directory, run:

```sh
bash run.sh
```

If a sibling `../coakka-publish` checkout is not present, the samples download
the required artifacts from the public `coakka-publish` repository.

## What The Samples Prove

The basic samples print:

- native logger ABI/version/git commit
- one emitted and manually drained record
- basic emitted/delivered/dropped counters

The pressure samples fill a queue with capacity `2`, verify later writes are
rejected with queue pressure, then drain the accepted records and check dropped
counters.

This is the point of the pressure lane: when logging is under stress, the
system should not quietly pretend everything was fine. It should make the
boundary visible.

The point is not to replace every language logging framework. The point is to
show one small, predictable logging contract that can be carried across
language ports and still behave honestly under pressure.

The logger is system-facing, not just developer-console-facing. macOS is useful
for local development and first-run checks, but production-like validation
should happen on Linux where service supervision, native loading, filesystem
behavior, queue pressure, and deployment packaging match the target system more
closely.

## When To Reach For It

Use the CoAkka logger when the team cares about one or more of these:

- bounded logging behavior under pressure
- explicit drop/reject visibility instead of silent loss
- one logging contract across several host-language integrations
- runtime-oriented diagnostics that align with queue, route, and lifecycle
  evidence
- the ability to test logging pressure as a first-class system behavior

Keep a simpler framework logger when:

- the application is small and local defaults are already enough
- logging pressure is not an operational concern
- one language host is all that matters and native/shared semantics add no
  value

Current samples:

| Language | Sample | Artifact |
| --- | --- | --- |
| JVM | `jvm/basic`, `jvm/pressure` | published JVM logger jar |
| Python | `python/basic`, `python/pressure` | published Python wheel |
| Node.js | `node/basic`, `node/pressure` | published Node package |
| Bun | `bun/basic`, `bun/pressure` | published Bun package |
| Electron | `electron/basic` | published Electron package |
| Tauri | `tauri/basic` | published Tauri source package |
| Go | `go/basic`, `go/pressure` | published Go source package |
| C# | `csharp/basic`, `csharp/pressure` | published C# NuGet package |
| Rust | `rust/basic`, `rust/pressure` | published Rust archive package |
| Zig | `zig/basic` | published native C/C++ archive |
| Mojo | `mojo/basic` | published native C/C++ archive with a sample-local shim |
| Native C/C++ | `native/basic`, `native/pressure` | published native C/C++ archive |
