# CoAkka Runtime Samples

[Choose your language](#first-run-choose-your-language) · [Use cases](#choose-a-use-case) · [Addons](#runtime-addons) · [Documentation](../docs/coakka-runtime/README.md) · [Packages](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-runtime/README.md) · [All products](../README.md)

**Application-owned work across languages and processes.**

CoAkka Runtime is a **polyglot, cross-platform distributed runtime** backed by a
**shared native core**. Its native-backed language packages connect named pieces
of application work while your application keeps its business rules and data.

For example, a Node.js checkout can ask a Python pricing function for a quote.
Runtime carries the request and reply; Python calculates the price. Start with
one local example in your language, then try a two-process application.

Read [the CoAkka story](../docs/coakka-runtime/explanation/coakka-story.md) for
why the work belongs to the application. The normal connector package includes
its matching native Runtime; no separate Core installation is needed.

## Languages, frameworks and desktop apps

Runtime integrations include **JavaScript and TypeScript (Node.js / Bun),
Python, Java, Kotlin, C#, Go and Swift**, with **Spring Boot** and **Quarkus**
framework adapters and **Electron** desktop support. The language table below
leads to the released 3.0.0 first runs.

**Tauri**, C/C++, Rust and Zig use version-specific source/archive guides;
Android and Mojo remain previews. Choose the exact version and platform in
those guides rather than applying one package's support scope to another.

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

## Runtime addons

**Runtime addons** add optional capabilities around CoAkka Runtime. The released
**Artifact Source Addons** obtain and verify files from external sources before
Runtime transfers those files between application peers.

For example, acquire an exact model file from **S3/MinIO** or **Hugging Face Hub**,
verify it, then deliver it to the worker that needs it. Other provider guides
cover HTTPS, SFTP, cloud storage and local files.

Current addon packages expose a **native C interface**. Each addon has its own
version, supported platforms and Runtime compatibility; high-level language
addon bindings are not currently released. Add only the capability you need.

[Understand the workflow](../docs/coakka-runtime/guides/runtime-addons.md) ·
[Choose a provider example](addons/README.md) ·
[Package and compatibility catalog](https://github.com/phuong-tran/coakka-publish/blob/main/runtime-addons/README.md).

## Runtime companion tools

- [CoAkka Runtime Client](../docs/coakka-runtime/operations/coakka-runtime-client.md)
  — published command `coakka-client`: try requests from a terminal or script.
- [CoAkka Runtime Inspect](../docs/coakka-runtime/operations/coakka-runtime-inspect.md)
  — published command `coakka-runtime-inspect`: explore Runtime facts and try
  a request in a browser.

The tool guides keep their own release and inspection scope. Their version is
independent of the connector package selected for your application.

## Put the pattern in your application

- [Integration guide](../docs/coakka-runtime/guides/runtime-integration-guide.md).
- [Defaults and monitoring](../docs/coakka-runtime/operations/runtime-monitoring.md).
- [Migration and 3.0.0 changes](../docs/coakka-runtime/CHANGELOG.md).
- [Current packages](../docs/common/current-packages.md) and [platform requirements](../docs/coakka-runtime/reference/runtime-package-platform-evidence.md).

[Runtime documentation](../docs/coakka-runtime/README.md) · [All products](../README.md).
