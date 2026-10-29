# CoAkka Logger Samples

Read the [product documentation](../docs/coakka-logger/README.md) for the full guide.

CoAkka Logger gives applications a bounded logging queue, explicit admission
outcomes and observable counters across host languages. These examples show
what is accepted, refused, read and counted. Logger has its own packages and
lifecycle; it can be used without CoAkka Runtime or CoAkka HTTP Runtime.

## Start With One Consumer

From the repository root:

```sh
bash run.sh coakka-logger list
bash run.sh coakka-logger node basic
bash run.sh coakka-logger node pressure
```

Read the [Node.js guide](node/README.md) for prerequisites. Its basic and pressure
examples install the exact published `coakka-logger-node@1.2.7` package. Choose
another host from the language table below and follow that guide's package and
platform requirements. [Current Packages](../docs/common/current-packages.md) lists
independently versioned coordinates.

The basic example starts one logger, emits one record, reads it and inspects
counters, then closes the logger. This is **manual drain**: the sample owns
reading and displaying records. It is not a background file or collector setup.

## How Logging Can Slow An Application

| Work | Possible application cost | What to check |
| --- | --- | --- |
| Message construction, serialization and diagnostic capture | CPU spent before admission, even if a later call is refused. | Avoid constructing expensive disabled messages. |
| Temporary strings, objects and buffers | Memory traffic and, in managed hosts, allocation and garbage-collection work. | Message size and allocation/GC measurements under realistic load. |
| Shared state and synchronization | Contention between producers and the consumer. | Logging-call latency and application tail latency. |
| Console, file or collector output | A slow destination can stall a thread that writes synchronously. | Sink latency, failures and where output runs. |
| Retained asynchronous work | Backlog uses memory; a bounded queue eventually fills if output cannot keep up. | Queue depth, high-water mark and rejected writes. |

A bounded queue makes overload visible; it does not remove these costs or make
an overloaded destination faster. Increasing capacity can absorb a burst, but
retains more records and can delay when operators see them. Keep app-created
messages and downstream sink buffers bounded too.

For an already started Node.js `logger`, check before constructing an expensive
message using the supported level/category probe:

```js
if (logger.isEnabledForCategory("orders", CoakkaLoggerLevel.DEBUG)) {
  logger.debug("orders", JSON.stringify(diagnosticState));
}
```

Here `diagnosticState` is application data. The probe avoids that serialization
when disabled; it does not reserve queue space. Handle the final logging call's
result or error. The application still owns redaction and should avoid putting
credentials or large business payloads in diagnostic messages.

## Observe Pressure Instead Of Hiding It

The [Node pressure example](node/pressure/README.md) deliberately attempts eight
enabled writes with `queueCapacity: 2` before reading any records. It uses
`logger.info(...)`, catches `LoggerStatusError`, reads records with
`logger.poll()` and inspects `logger.stats()`. Its assertions require:

```text
coakka_logger_pressure attempts=8 accepted=2 rejected=6 capacity=2 highWatermark=2
coakka_logger_stats emitted=2 delivered=2 dropped=6
```

This is a deterministic admission check, not a throughput benchmark. The six
queue-full writes are refused and counted; the two admitted records are read
before the logger is closed. [Native C pressure](native/pressure/README.md)
exercises the same boundary through the public native API.

Use these Node.js snapshot fields when monitoring a similar integration:

| Field | Read it as |
| --- | --- |
| `queueCapacity` | The configured record-count limit. Category/message size bounds are separate. |
| `queueDepth` | Records currently waiting in the queue. Sustained growth needs investigation. |
| `queueHighWatermark` | Peak queue depth for this logger instance. |
| `emittedCount` | Records admitted into the native queue. |
| `deliveredCount` | Records dequeued by a reader or writer, before managed sink I/O. |
| `droppedCount` | Drops accounted by Logger, including queue-full refusal. |

Native filtering and configured pressure policies can also increase drop
counters. A host connector can skip disabled calls before native admission,
so these are not counters for every attempted application call. Read the
connector's actual result/error and, where its API exposes them, sink-failure
and pressure counters. Do not recursively log every rejection into the same
full queue; choose the application's response to overload deliberately.

## Choose A Consumer And Close It Deliberately

The basic and pressure examples use manual drain. The application owns the
consumer and any onward sink or collector. If that consumer performs blocking
I/O on a request thread, the request can still wait for logging output.

The native Logger also supports file, console and combined sink modes with a
writer worker. These move output work off the emitter, but calls still have
copying and synchronization costs. Sink options differ across packages; use
only what the selected connector exposes rather than assuming every native
mode exists in every language API.

Stop producing records before closing their logger. Drain manual-mode records
that must be consumed before destruction; `close()` does not persist unread
records. Managed-sink shutdown joins its writer, so queued work and a slow sink
can affect shutdown latency. Observe sink failures rather than assuming every
accepted record reached its destination.

Admission, dequeue, successful sink write and durable storage are separate
outcomes. A delivered counter is not a remote collector acknowledgement or a
crash/power-loss guarantee. Use a storage protocol with an explicit durability
contract for mandatory audit records.

Keep an existing framework logger when its filtering, bounds, output,
shutdown and loss reporting already meet the application's needs. CoAkka is
useful when those contracts need to stay explicit across language hosts; it
also adds integration and lifecycle work that should earn its place.

## Language Guides

| Host | Examples |
| --- | --- |
| [JVM and Java](jvm/README.md) | `jvm/basic`, `jvm/java-basic`, `jvm/pressure`, `jvm/java-pressure` |
| [Python](python/README.md) | `python/basic`, `python/pressure` |
| [Node.js](node/README.md) | `node/basic`, `node/pressure` |
| [Bun](bun/README.md) | `bun/basic`, `bun/pressure` |
| [Electron](electron/README.md) | `electron/basic`; the main process owns Logger. |
| [Tauri](tauri/README.md) | `tauri/basic`; the Rust app host owns Logger. |
| [Go](go/README.md) | `go/basic`, `go/pressure` |
| [C#](csharp/README.md) | `csharp/basic`, `csharp/pressure` |
| [Rust](rust/README.md) | `rust/basic`, `rust/pressure` |
| [Swift](swift/basic/README.md) | `swift/basic` |
| [Native C/C++](native/README.md) | `native/basic`, `native/pressure` |
| [Zig](zig/README.md) | `zig/basic` |
| [Mojo](mojo/README.md) | `mojo/basic` |

These examples demonstrate their named paths; the table does not expand their
OS/CPU or production-support evidence. See [Product Layout](../docs/common/product-layout.md)
for repository navigation and [Runtime Logging And Observability](../docs/coakka-runtime/operations/runtime-logging-observability.md)
for composing logging with Runtime's separate delivery facts.
