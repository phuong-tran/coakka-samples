# CoAkka Samples

<p align="center"><img src="docs/assets/brand/coakka-logo.png" alt="CoAkka" width="480"></p>

CoAkka is a polyglot runtime ecosystem. Select the product whose boundary fits
your application; products have independent lifecycles, versions and evidence.

| Product | Start here | First command |
| --- | --- | --- |
| CoAkka Runtime | [Language samples, tools, addons and demos](coakka-runtime/README.md) | `bash run.sh coakka-runtime list` |
| CoAkka HTTP Runtime | [HTTP server/client examples](coakka-http-runtime/README.md) | `bash coakka-http-runtime/run.sh verify` |
| CoAkka Logger | [Logging and pressure examples](coakka-logger/README.md) | `bash run.sh coakka-logger node basic` |

For a two-process Runtime example with Docker, run
`bash run.sh coakka-runtime containers node-python`.
Use `bash run.sh doctor` for prerequisites and `bash run.sh list` for available
examples. Read the selected product guide before installing its package.
Runtime package coordinates do not install HTTP Runtime.

Shared documentation lives in [docs/](docs/README.md):

- [New to CoAkka](docs/new-to-coakka.md) and [ecosystem overview](docs/ecosystem-overview.md).
- [Current packages](docs/current-packages.md) and [package/platform evidence](docs/runtime-package-platform-evidence.md).
- [Product layout](docs/product-layout.md), [integration guidance](docs/ai-assisted-integration.md) and [troubleshooting](docs/troubleshooting.md).

Samples is a rolling repository. Exact dependencies and executable checks are
its integration contract. Versioned source mirrors preserve their recorded
contents. Private candidate guides are clearly scoped and do not publish a
package or expand platform support.

[Contributing](CONTRIBUTING.md) · [Support](SUPPORT.md) · [Changelog](CHANGELOG.md)

Contact: `gabrielgun1983@gmail.com`
