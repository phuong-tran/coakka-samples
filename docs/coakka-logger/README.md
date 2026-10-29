# CoAkka Logger Documentation

**Native-backed logging across languages.**

CoAkka Logger is a cross-platform logging library for polyglot applications.
Its language connectors share a native logging core with bounded storage,
explicit acceptance or refusal, and counters for observing pressure. It has
its own lifecycle and packages and can be used independently of the other
CoAkka products.

## Why logging affects an application

Formatting messages, creating temporary values and writing output all consume
application resources. Synchronous console, file or network output can hold up
a request. An asynchronous queue can absorb bursts, but uses memory and still
fills when output stays slower than producers. Logging can therefore affect
both throughput and how long the slowest requests take.

A bounded queue makes overload visible. It does not make a slow destination
faster. Keep messages and downstream buffers bounded, check admission results
and measure application latency alongside accepted/refused record counts.
Avoid expensive message construction when the host integration's supported
level/category check says the record would be disabled.

## Start with one consumer

Use the [Logger sample guide](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-logger/README.md)
to choose a language and run a basic consumer. It explains manual drain,
pressure examples, counters and each integration's package requirements.
Use the [Logger catalog](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-logger/README.md)
and [published package ledger](../common/current-packages.md) for actual versions.

Start one logger, submit one record, consume it and inspect the counters, then
close it. The basic samples own reading and displaying records; they do not
implicitly configure a background file writer or external collector.

## Check behavior under pressure

Run the selected host's pressure example before choosing capacity and output
policy. Observe accepted and refused writes, retained records, output progress,
application latency and shutdown. A larger capacity uses more memory and can
delay visibility; it is not a substitute for sufficient output capacity.
The language guide defines that integration's exact API and close behavior.

Logger configuration is separate from [Runtime monitoring](../coakka-runtime/operations/runtime-monitoring.md)
and [HTTP Runtime observation](../coakka-http-runtime/README.md).
Return to the [documentation index](../README.md).
