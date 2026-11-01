# CoAkka Logger Documentation

**Native-backed logging across languages.**

CoAkka Logger records application logs with bounded storage and visible
acceptance or refusal. Its language connectors share a native logging core.
Logger can be used independently of CoAkka Runtime and HTTP Runtime, and has
its own packages and version.

## Start with one consumer

Choose the language you use. Each guide gives its package, prerequisites and
basic example; you do not need to begin with Node.js.

| Application | First guide |
| --- | --- |
| Node.js | [Node.js Logger](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-logger/node/README.md) |
| Bun | [Bun Logger](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-logger/bun/README.md) |
| Electron | [Electron Logger](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-logger/electron/README.md) |
| Python | [Python Logger](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-logger/python/README.md) |
| Kotlin / Java | [JVM Logger](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-logger/jvm/README.md) |
| C# | [C# Logger](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-logger/csharp/README.md) |
| Go | [Go Logger](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-logger/go/README.md) |
| Swift | [Swift Logger](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-logger/swift/basic/README.md) |
| Other hosts | [All Logger language guides](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-logger/README.md#language-guides) |

The basic example starts one logger, submits one record, reads it, checks
counters and closes. The sample owns reading and displaying records; it does
not implicitly configure a background file writer or external collector.
Use the chosen guide's actual platform scope and the
[package ledger](../common/current-packages.md) for released versions.

For a small Node.js app without cloning Samples, follow
[one record from install to close](first-node-record.md).

## Why logging affects an application

Formatting messages, creating temporary values and writing output use
application resources. Slow console, file or network output can hold up a
request. An asynchronous queue absorbs bursts but uses memory and still fills
when output stays slower than producers.

Bounded storage makes that pressure visible. Keep messages and output buffers
bounded; check whether writes were accepted and measure application latency
alongside accepted/refused record counts. Avoid expensive message construction
when the selected integration's level/category check says the record is disabled.

## Check behavior under pressure

After the basic example, run your language's pressure example where available.
Observe accepted/refused writes, counters and shutdown. A larger capacity uses
more memory and can delay visibility; it cannot fix persistently slow output.

The [Logger sample guide](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-logger/README.md)
explains consumer choices and the meaning of counters. The
[Logger catalog](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-logger/README.md)
records packages. Admission, reading a record and durable storage are different
outcomes; use the selected package's documented output and close contract.

Logger configuration is separate from [Runtime monitoring](../coakka-runtime/operations/runtime-monitoring.md)
and [HTTP Runtime observation](../coakka-http-runtime/README.md).

[All product documentation](../README.md).
