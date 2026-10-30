# CoAkka Samples

<p align="center"><img src="docs/common/assets/brand/coakka-logo.png" alt="CoAkka" width="480"></p>

**Shared native cores. Familiar APIs across languages.**

CoAkka is a **polyglot, cross-platform ecosystem** built on **shared native
cores**. Its central product, **CoAkka Runtime**, is a **distributed runtime
for application-owned work** across languages, processes and hosts.

**CoAkka HTTP Runtime** brings a shared native HTTP core to HTTP services, and
**CoAkka Logger** provides native-backed logging with bounded storage. Each
product can be adopted independently, with language connectors sharing that
product's core through familiar APIs.

Application code can use an HTTP handler to call work in a Python process
through Runtime and record its own events through Logger. Business rules
remain in the application.

This repository shows those products in small, runnable examples. Choose the
problem you want to solve, then try one example in your language. Each product
can be used independently and has its own packages and version.

| Product | What it helps you do | Start here | First command |
| --- | --- | --- | --- |
| CoAkka Runtime | Distributed runtime for application-owned work across processes and languages. | [Language samples, tools, addons and demos](coakka-runtime/README.md) | `bash run.sh coakka-runtime list` |
| CoAkka HTTP Runtime | HTTP server/client with a native core shared across languages. | [HTTP server/client examples](coakka-http-runtime/README.md) | `bash coakka-http-runtime/go/run.sh run` |
| CoAkka Logger | Native-backed logging with bounded storage and visible pressure. | [Logging and pressure examples](coakka-logger/README.md) | `bash run.sh coakka-logger node basic` |

For a two-process Runtime example with Docker, run
`bash run.sh coakka-runtime containers node-python`.
Use `bash run.sh doctor` for prerequisites and `bash run.sh list` for available
examples. Read the selected product guide before installing its package.
Runtime package coordinates do not install HTTP Runtime.

Shared documentation lives in [docs/](docs/README.md):

- [New to CoAkka](docs/common/new-to-coakka.md) and [ecosystem overview](docs/common/ecosystem-overview.md).
- [Current packages](docs/common/current-packages.md) and [package/platform evidence](docs/coakka-runtime/reference/runtime-package-platform-evidence.md).
- [Product layout](docs/common/product-layout.md), [integration guidance](docs/coakka-runtime/guides/ai-assisted-integration.md) and [troubleshooting](docs/coakka-runtime/operations/troubleshooting.md).

Samples is a rolling repository. Exact dependencies and executable checks are
its integration contract. Versioned source mirrors preserve their recorded
contents. Private candidate guides are clearly scoped and do not publish a
package or expand platform support.

[Contributing](CONTRIBUTING.md) · [Support](SUPPORT.md) · [Changelog](CHANGELOG.md)

Contact: `gabrielgun1983@gmail.com`
