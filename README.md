# CoAkka Samples

<p align="center"><img src="docs/common/assets/brand/coakka-logo.png" alt="CoAkka" width="480"></p>

[Website](https://coakka-ecosystem-preview.vivid-plume-2955.chatgpt.site/) · [Get started](#choose-what-you-want-to-build) · [Documentation](docs/README.md)

**Contents**

[Choose a product](#choose-what-you-want-to-build) · [CoAkka Runtime](#coakka-runtime-languages-frameworks-and-app-hosts) · **[CoAkka HTTP Runtime](#coakka-http-runtime-shared-http-familiar-languages)** · [HTTP Inspect](#coakka-http-runtime-inspect) · [Runtime addons](#coakka-runtime-addons) · [Runtime Client / Inspect](#coakka-runtime-client-and-inspect) · [Node–Python demo](#try-the-nodepython-web-demo)

**Shared native cores. Familiar APIs across languages.**

CoAkka is a **polyglot, cross-platform ecosystem** built on shared native cores.
**CoAkka Runtime** is a **native-backed distributed runtime for
application-owned work** across languages, processes and hosts. Your application
keeps its handlers, business rules and data.

Use **HTTP Runtime** for HTTP services and **Logger** for logging with bounded
storage. Each product can be adopted independently and has its own packages
and versions.

**[Choose your language and run your first example](#choose-what-you-want-to-build)**

## Try the Node–Python web demo

Build a basket in your browser. Node.js sends it to a separate Python process,
which returns item prices, shipping and the total through CoAkka Runtime.

**[Run the demo and read the walkthrough](coakka-runtime/scenarios/order-quote-node-python/README.md)** · Published npm/PyPI **3.0.0** packages · Two terminals, no Docker required.

[![Node–Python demo: change the basket and receive an updated quote from Python.](docs/coakka-runtime/assets/node-python-order-quote-demo.gif)](coakka-runtime/scenarios/order-quote-node-python/README.md)

Change the quantities, request a quote and see Python return the updated total.
[View a still screenshot](docs/coakka-runtime/assets/node-python-order-quote-web.png).

## Choose what you want to build

| I want to… | Product | Start here |
| --- | --- | --- |
| Call named application work across languages or processes | CoAkka Runtime | [Choose my language and run one example](docs/coakka-runtime/getting-started.md) |
| Build an HTTP API, web frontend or streaming endpoint | CoAkka HTTP Runtime | [Choose my HTTP language guide](docs/coakka-http-runtime/getting-started.md) |
| Record logs and see pressure when output falls behind | CoAkka Logger | [Choose my Logger example](docs/coakka-logger/README.md#start-with-one-consumer) |

## CoAkka Runtime: languages, frameworks and app hosts

CoAkka Runtime brings application-owned work to backend, desktop and device
applications. Choose the integration that matches your code:

| Application language or host | Start here |
| --- | --- |
| JavaScript / TypeScript on Node.js and Bun; Python; Java and Kotlin; C#; Go; Swift | [Language-first Runtime examples](coakka-runtime/README.md#first-run-choose-your-language) |
| Spring Boot and Quarkus | [Framework customer applications](coakka-runtime/scenarios/customer-crud/framework-registry/README.md) |
| Electron and Tauri desktop apps | [Electron](coakka-runtime/electron/registry-basic/README.md) · [Tauri](coakka-runtime/tauri/README.md) |
| C / C++, Rust, Zig, Android and Mojo | [Source/archive guides and previews](coakka-runtime/README.md#other-languages-and-evaluations) |

Released 3.0.0 first runs cover Node.js, Bun, Python, Java/Kotlin, C#, Go,
Swift, Electron, Spring Boot and Quarkus. Tauri, C/C++, Rust and Zig follow their own versioned
source/archive guides; Android and Mojo are previews. Each guide gives its
actual package and platform scope.

## CoAkka Runtime addons

**Runtime addons** add optional capabilities around CoAkka Runtime. The released
**Artifact Source Addons** obtain and verify files from external sources before
Runtime transfers those files between application peers.

For example, acquire an exact model file from **S3/MinIO** or **Hugging Face Hub**,
verify it, then deliver it to the worker that needs it. Other provider guides
cover HTTPS, SFTP, cloud storage and local files.

Current addon packages expose a **native C interface**. Each addon has its own
version, supported platforms and Runtime compatibility; high-level language
addon bindings are not currently released. Add only the capability you need.

[Understand the workflow](docs/coakka-runtime/guides/runtime-addons.md) ·
[Choose a provider example](coakka-runtime/addons/README.md) ·
[Package and compatibility catalog](https://github.com/phuong-tran/coakka-publish/blob/main/runtime-addons/README.md).

## CoAkka Runtime Client and Inspect

Use the companion tools to try Runtime before writing another application,
or to check a request while integrating it.

| Tool | Use it for |
| --- | --- |
| [CoAkka Runtime Client](docs/coakka-runtime/operations/coakka-runtime-client.md) — `coakka-runtime-client` | Send test requests from a terminal or script and inspect the returned result. |
| [CoAkka Runtime Inspect](docs/coakka-runtime/operations/coakka-runtime-inspect.md) — `coakka-runtime-inspect` | Explore Runtime facts and try request/reply interactions in a browser. |

These are the product names. The released Client command is `coakka-client`;
the Inspect command is `coakka-runtime-inspect`. Follow each tool's guide for
its published version, connection setup and inspection scope.

## CoAkka HTTP Runtime: shared HTTP, familiar languages

**CoAkka HTTP Runtime** (`coakka-http-runtime`) provides an HTTP server and
client for polyglot applications. Use it for APIs, web frontends, uploads,
files, streaming and WebSocket services.

Its language packages share one native HTTP core. Common HTTP behavior and
fixes can reach several languages through updated packages, while each service
keeps its own handlers and business rules. Teams can reuse their understanding
of HTTP across services instead of maintaining unrelated HTTP behavior for
every application language.

HTTP Runtime serves the HTTP edge; CoAkka Runtime connects named application
work. An HTTP handler can call Runtime when it needs that work, and either
product can also be adopted independently.

**Package-manager releases are in preparation for npm, Maven Central, Go
modules and PyPI.** The current installation path uses 1.0.0 repository archives.
Start with the [HTTP language guides](docs/coakka-http-runtime/getting-started.md)
and [current archive catalog](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/README.md).

### CoAkka HTTP Runtime Inspect

**Swagger-like API exploration for an inspection-enabled HTTP service.**
Browse the route descriptions a service publishes, try HTTP requests in a
browser and export an **OpenAPI 3.0.3** document. Inspect provides its own UI;
the exported document can also be used by OpenAPI tools such as Swagger UI.

Current Inspect archives target **macOS ARM64** and **Linux ARM64**. Service
inspection must be explicitly enabled, and language-connector APIs for declaring
route/schema metadata are still being completed. Follow the current integration
status before adding inspection to a language example.

[Explore Inspect and OpenAPI](https://github.com/phuong-tran/coakka-publish/blob/main/docs/coakka-http-runtime/inspect/metadata-and-openapi.md) ·
[Current integration status](https://github.com/phuong-tran/coakka-publish/blob/main/docs/coakka-http-runtime/inspect/metadata-and-openapi.md#current-package-integration) ·
[Inspect packages and setup](https://github.com/phuong-tran/coakka-publish/blob/main/coakka-http-runtime/inspect/README.md).

## First time here?

1. Choose one product and your language above.
2. Follow that guide's prerequisites, run command and expected result.
3. Read its application source, then [choose a Runtime use case](docs/coakka-runtime/guides/examples-and-use-cases.md)
   or the selected product's next example.

For Runtime, the language-first path uses released 3.0.0 packages. It starts
with one local request and reply.
Older container images and local-package checks are labeled separately.

## Explore further

- [New to CoAkka](docs/common/new-to-coakka.md) explains the products and the application model.
- [Documentation](docs/README.md) leads to integration, operation and reference.
- [All Runtime examples](coakka-runtime/README.md), [HTTP examples](coakka-http-runtime/README.md)
  and [Logger examples](coakka-logger/README.md) show their own language coverage.
- [Current packages](docs/common/current-packages.md) records released versions.
- [Troubleshooting](docs/coakka-runtime/operations/troubleshooting.md) helps with Runtime setup.

Repository helpers: run bash run.sh list to list examples, or bash run.sh doctor
to inspect prerequisites. You only need the tools for the example you choose.

Samples is a rolling repository. Follow each example's declared dependencies;
versioned source mirrors retain their original content.
[Publish](https://github.com/phuong-tran/coakka-publish) hosts package catalogs
and versioned downloads. [Product layout](docs/common/product-layout.md) explains the repository map.

[Contributing](CONTRIBUTING.md) · [Support](SUPPORT.md) · [Changelog](CHANGELOG.md)

Contact: gabrielgun1983@gmail.com
