# CoAkka Runtime Samples

**Application-owned work across languages and processes.**

CoAkka Runtime is a **polyglot, cross-platform distributed runtime**, backed by
a **shared native core**. Name the work your application owns, register its
handler and ask for a result. The handler can live in the same process or in a
different language/process; business rules remain application code.

An API may need an image processed, a customer stored or a device reading
fetched. Runtime gives that work a stable target name while your application
chooses where the handler lives. Read [the story](../docs/coakka-runtime/explanation/coakka-story.md)
for the model, or run one example below.

## First run: choose your language

These apps use **published 3.0.0 packages**. Each README gives prerequisites,
a command, expected output, the source file and how the app closes.

| Your application | First run |
| --- | --- |
| Node.js | [Node.js](node/registry-basic/README.md) |
| Bun | [Bun](bun/registry-basic/README.md) |
| Electron | [Electron desktop app](electron/registry-basic/README.md) |
| Python | [Python](python/registry-basic/README.md) |
| C# | [C#](csharp/registry-basic/README.md) |
| Kotlin | [Kotlin](jvm/registry-basic/README.md#kotlin) |
| Java | [Java](jvm/registry-basic/README.md#java) |
| Go | [Go](go/registry-basic/README.md) |
| Swift | [Swift](swift/registry-basic/README.md) |
| Spring Boot / Quarkus | [Framework customer application](scenarios/customer-crud/framework-registry/README.md) |

[Get the Samples checkout first](../docs/coakka-runtime/getting-started.md#get-the-examples).
You only need your chosen language's tools. The first request/reply app runs
locally and does not require Docker, Kubernetes or a separate Core build.

## Choose a use case

[Examples and use cases](../docs/coakka-runtime/guides/examples-and-use-cases.md)
separates small local apps, business operations, process/language demonstrations,
files, streams and device examples.

- [Node.js customer operation](../docs/coakka-runtime/guides/first-npm-smoke.md):
  a published-package walkthrough without cloning this repository.
- [Node.js checkout, Python pricing](scenarios/order-quote-node-python/README.md):
  edit a browser basket and receive a quote across two processes using published 3.0.0 packages.
- [Customer applications](scenarios/customer-crud/framework-registry/README.md):
  Spring Boot or Quarkus with Runtime 3.0.0 from Maven Central.
- [Electron application](electron/registry-basic/README.md): a window sends work
  through its main process.
- [Files](../docs/coakka-runtime/guides/runtime-file-transfer.md) and
  [streams](../docs/coakka-runtime/guides/runtime-streaming.md): follow the named
  example's package requirements.

## Other languages and evaluations

| Language | Exact source/package path |
| --- | --- |
| C/C++ | [Released older snapshot](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime/native) · [Local 3.0.0 evaluation](native/README.md) |
| Rust | [Released older snapshot](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime/rust) · [Local 3.0.0 evaluation](rust/README.md) |
| Tauri | [Released older snapshot](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime/tauri) · [Local 3.0.0 evaluation](tauri/README.md) |
| Zig | [Released older snapshot](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime/zig) · [Local 3.0.0 evaluation](zig/README.md) |
| Android / Mojo | [Android](android/README.md) · [Mojo](mojo/README.md): evaluation previews |

These paths keep their own version and prerequisites. [Local evaluation guides](../docs/coakka-runtime/previews/README.md)
are for checking selected packages, not a prerequisite for the published first runs.

The [Node-to-Python container demo](containers/node-python/README.md) uses
historical 1.x images. It demonstrates two processes but is not the current
3.0.0 installation path. [RPi camera](demos/rpi-camera/README.md) and other device
examples have their own hardware and package requirements.

## Put the pattern in your application

- [Integration guide](../docs/coakka-runtime/guides/runtime-integration-guide.md).
- [Defaults and monitoring](../docs/coakka-runtime/operations/runtime-monitoring.md).
- [Migration and 3.0.0 changes](../docs/coakka-runtime/CHANGELOG.md).
- [Current packages](../docs/common/current-packages.md) and [platform requirements](../docs/coakka-runtime/reference/runtime-package-platform-evidence.md).

[Runtime documentation](../docs/coakka-runtime/README.md) · [All products](../README.md).
