# CoAkka Samples

<p align="center"><img src="docs/common/assets/brand/coakka-logo.png" alt="CoAkka" width="480"></p>

**Shared native cores. Familiar APIs across languages.**

CoAkka is a **polyglot, cross-platform ecosystem**. Its central product,
**CoAkka Runtime**, is a **native-backed distributed runtime for
application-owned work** across languages, processes and hosts.

Keep business handlers in the language that fits the work. A Node.js API can
call a Python operation or a JVM service through Runtime; the application
still owns its rules and data. **HTTP Runtime** handles HTTP services, while
**Logger** records application events with bounded storage. Each product can
be adopted independently and has its own packages and version.

## Choose what you want to build

| I want to… | Product | Start here |
| --- | --- | --- |
| Call named application work across languages or processes | CoAkka Runtime | [Choose my language and run one example](docs/coakka-runtime/getting-started.md) |
| Build an HTTP API, web frontend or streaming endpoint | CoAkka HTTP Runtime | [Choose my HTTP language guide](docs/coakka-http-runtime/getting-started.md) |
| Record logs and see pressure when output falls behind | CoAkka Logger | [Choose my Logger example](docs/coakka-logger/README.md#start-with-one-consumer) |

## First time here?

1. Choose one product and your language above.
2. Follow that guide's prerequisites, run command and expected result.
3. Read its application source, then [choose a Runtime use case](docs/coakka-runtime/guides/examples-and-use-cases.md)
   or the selected product's next example.

For Runtime, the language-first path uses released 3.0.0 packages. It starts
with one local request and reply; Docker is an optional later demonstration.
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
