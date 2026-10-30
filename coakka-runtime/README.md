# CoAkka Runtime Samples

**Application-owned work across languages and processes.**

CoAkka Runtime is a **polyglot, cross-platform distributed runtime for
application-owned work**, backed by a **shared native core**. Its native-backed
language connectors expose familiar APIs while keeping business behavior in
application code.

An API needs an image processed, a customer stored or a device reading fetched.
These are pieces of application work, even when the code that handles them
lives in another language or process. As the application grows, connecting
those pieces can mean maintaining separate endpoints, client wrappers and
delivery conventions for every handoff.

CoAkka Runtime makes that work callable by a stable target name. For example,
a Node.js API can ask a Python handler to process an image. The application
chooses which process owns the handler; Runtime delivers the request, matches
its reply and reports delivery failures. Business rules remain application code.

Use it to connect existing services, introduce another language for a specific
job or move work into a separate process gradually. The examples below start
with one local exchange and lead to larger application shapes. Read
[The CoAkka Story](../docs/coakka-runtime/explanation/coakka-story.md) for the
reasoning behind this model.

Run one request/reply example in your language, then add the capabilities you need.

**Version choice comes first.** Main-branch examples use unpublished 3.0.0
packages. The published-example links below point to a frozen 2.x snapshot:
use its complete code, runner and pinned dependencies together. Follow the
[package ledger](../docs/common/current-packages.md) for released coordinates.

| Language | Use a published package | Evaluate the current candidate |
| --- | --- | --- |
| C/C++ | [Published example](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime/native) | [3.0.0 example](native/README.md) |
| Kotlin / Java | [Published example](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime/jvm) | [3.0.0 example](jvm/README.md) |
| Node.js | [Published example](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime/node) | [3.0.0 example](node/README.md) |
| Bun | [Published example](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime/bun) | [3.0.0 example](bun/README.md) |
| Electron | [Published example](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime/electron) | [3.0.0 example](electron/README.md) |
| Python | [Published example](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime/python) | [3.0.0 example](python/README.md) |
| C# | [Published example](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime/csharp) | [3.0.0 example](csharp/README.md) |
| Go | [Published example](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime/go) | [3.0.0 example](go/README.md) |
| Swift | [Published example](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime/swift) | [3.0.0 example](swift/README.md) |
| Rust | [Published example](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime/rust) | [3.0.0 example](rust/README.md) |
| Tauri | [Published example](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime/tauri) | [3.0.0 example](tauri/README.md) |
| Zig | [Published example](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime/zig) | [3.0.0 example](zig/README.md) |

For a candidate, first read its [3.0.0 package requirements](../docs/coakka-runtime/previews/README.md).
Each language README leads to a basic example with prerequisites, a command,
expected output and clean shutdown. A package's included Core is loaded by its
connector; ordinary users do not build Core separately.

## What to try next

- [Deadletter and integration behavior](../docs/coakka-runtime/guides/runtime-integration-guide.md).
- [File transfer](../docs/coakka-runtime/guides/runtime-file-transfer.md), [streaming](../docs/coakka-runtime/guides/runtime-streaming.md) or [replica file fanout](go/replica-file-fanout/README.md).
- [Spring Boot and Quarkus scenarios](scenarios/customer-crud/README.md).
- [Client and Inspect](../docs/coakka-runtime/operations/README.md), or the [RPi camera demo](demos/rpi-camera/README.md).
- [Defaults and monitoring](../docs/coakka-runtime/operations/runtime-monitoring.md).

[Android](android/README.md) and [Mojo](mojo/README.md) are evaluation previews,
with their own prerequisites. They are not registry installation paths.

For deployment planning and deeper reading, use the [Runtime documentation](../docs/coakka-runtime/README.md).
[Choose another product](../README.md).
