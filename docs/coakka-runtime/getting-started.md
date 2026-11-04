# Start With CoAkka Runtime

**Choose your language, run one example and see a reply.**

These first-run apps use released Runtime **3.2.1** packages. Start with one
language's tools; Docker and Kubernetes are optional later steps. The package
includes the matching Runtime components.

## Get the examples

~~~sh
git clone https://github.com/phuong-tran/coakka-samples.git
cd coakka-samples
~~~

All paths below start from this repository root. If you enter an example
directory, return to the repository root before choosing another example.

## Choose your language

| Your application | Tools needed | First-run instructions and code |
| --- | --- | --- |
| Node.js | Node.js 22 and npm | [Node.js](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/node/registry-basic/README.md) |
| Bun | Bun 1.3.14; Windows ARM64: 1.4.0 | [Bun](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/bun/registry-basic/README.md) |
| Electron | Node.js/npm; the sample installs Electron 42 | [Electron](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/electron/registry-basic/README.md) |
| Python | Standard CPython 3.11 or later | [Python](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/python/registry-basic/README.md) |
| C# | .NET SDK 8 or later | [C#](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/csharp/registry-basic/README.md) |
| Kotlin | JDK 17 to run the sample build | [Kotlin](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/jvm/registry-basic/README.md#kotlin) |
| Java | JDK 17 to run the sample build | [Java](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/jvm/registry-basic/README.md#java) |
| Go | Go 1.22.12 and a C compiler; Windows ARM64: Go 1.25.5 | [Go](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/go/registry-basic/README.md) |
| Swift | A supported Swift installation for your platform | [Swift](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/swift/registry-basic/README.md) |

The language README contains the exact command, expected result, source file
and close behavior. Follow that one path before adding another language.

Spring Boot or Quarkus user? After the plain JVM example, try the
[published framework application](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/scenarios/customer-crud/framework-registry/README.md).

## Understand what succeeded

Most examples print:

~~~text
accepted-hello
~~~

Kotlin prints hello-runtime-jvm inside its response line; Java prints
hello-runtime-java. Electron opens a window with a reply and then exits.
Each language guide shows its actual expected output.

The application starts one local Runtime, registers a handler, sends a request
to that handler and closes cleanly. Read those steps in your language's source.
This first run is intentionally in one process; choose a larger example next.

Try [Node.js checkout with Python pricing](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/scenarios/order-quote-node-python/README.md)
for a two-terminal cart-and-quote example using the published 3.2.1 packages.

## Choose your next step

| I want to… | Continue here |
| --- | --- |
| Handle a customer operation or use a framework | [Examples and use cases](guides/examples-and-use-cases.md) |
| Add Runtime to my existing application | [Integration guide](guides/runtime-integration-guide.md) |
| Understand why and when to use Runtime | [The CoAkka story](explanation/coakka-story.md) |
| Connect work across processes or languages | [Process and language examples, with their versions](guides/examples-and-use-cases.md#work-across-processes-and-languages) |
| Inspect settings or enable monitoring | [Defaults and monitoring](operations/runtime-monitoring.md) |

## If the first run fails

- Confirm the working directory and required language version in the selected README.
- Use the complete sample and its declared package dependency together.
- Check the [platform requirements](reference/runtime-package-platform-evidence.md).
  Packages cover macOS ARM64, Linux ARM64/x64 and Windows ARM64/x64; Swift's
  Windows ARM64 path alone remains preview.
- Follow [troubleshooting](operations/troubleshooting.md) before changing advanced settings.

The [package ledger](../common/current-packages.md) records exact released
coordinates. [The changelog](CHANGELOG.md) explains migration from 2.x.

## Other languages and evaluation paths

C/C++, Rust, Tauri and Zig have their own released snapshots or local
evaluation paths; choose their exact source and package together in the
[Runtime sample index](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/README.md#other-languages-and-evaluations).
The [Zig 3.2.1 source candidate](previews/3.2.1/runtime-zig-candidate.md) and
[Mojo 3.2.1 preview](previews/3.2.1/runtime-mojo-candidate.md) use Core 3.2.
Android remains a separate preview.

Local archive/feed tests are for evaluating a package, not a prerequisite for
using the published packages above. Their instructions stay in the
[evaluation guides](previews/README.md). HTTP Runtime and Logger use their own
[get-started paths](../README.md).
