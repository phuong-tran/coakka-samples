# CoAkka Ecosystem Naming

Public CoAkka docs treat `CoAkka` as the ecosystem and brand prefix. They
should avoid using `coakka` alone as a concrete package identity when a more
specific product name is available.

## Product Names

| Name | Meaning | Sample responsibility |
| --- | --- | --- |
| `CoAkka` | The ecosystem and brand family. | Use for the project identity and cross-product docs. |
| `CoAkka Runtime` | The runtime product family. | Use when explaining runtime delivery, targets, route snapshots, replies, deadletters, and diagnostics. |
| `coakka-runtime-core` | The native runtime engine and C ABI surface. | Consume only through published artifacts; do not place sink, dashboard, or business schema semantics here. |
| `coakka-runtime-connector` | Host-language connector packages that bind app-host code to the runtime core. | Show language/framework integration and app-owned handlers. |
| `coakka-runtime-client` | The CLI runtime client used to drive and diagnose runtime paths. | Show local/native CLI workflows, Docker verification smokes, and scripted request/reply verification. |
| `coakka-runtime-inspect` | The browser-visible runtime inspection surface. | Show runtime diagnostics, route state, and operator-oriented visibility without making it the runtime owner. |
| `CoAkka HTTP Runtime` | Shared HTTP contract and language package/addon family. | Show HTTP handlers, bounded queues, backpressure, streaming, health, monitoring, and HTTP lifecycle separately from message routing. |
| `coakka-logger` | The bounded logger product surface. | Show logger behavior separately from runtime routing unless a sample intentionally combines both. |

## Public Repository Names

| Repository | Public role |
| --- | --- |
| `coakka-samples` | Runnable public samples, scenarios, screenshots, videos, and sample commands. |
| `coakka-publish` | Public artifact mirror, release notes, compatibility matrix, package-manager status, checksums, and tool downloads. |
| `coakka-runtime-go` | Go module for CoAkka Runtime. |
| `coakka-logger-go` | Go module for CoAkka Logger. |
| `coakka-runtime-swift` | SwiftPM package for CoAkka Runtime with Linux ARM64/x86-64, macOS ARM64, and Windows ARM64/x86-64 native payloads. |
| `coakka-logger-swift` | SwiftPM package for CoAkka Logger on macOS ARM64. |

Package repos should keep root READMEs short and link deeper concept docs from
`docs/`. Shared wording for those package docs is controlled by
`coakkaCoreNativeDev/public-docs/package-repos/` before it is copied to public
package repositories.

## Repository Path Names

The sample directory identifies the product and workflow. Find installable
coordinates in [Current Packages](current-packages.md).

| Path | Purpose |
| --- | --- |
| `coakka-runtime/` | Runtime connectors and application scenarios. |
| `coakka-runtime/tools/coakka-client/` | Runtime Client examples. Run the published command `coakka-client`. |
| `coakka-runtime/tools/coakka-runtime-inspect/` | Runtime Inspect examples. |
| `coakka-http-runtime/` | HTTP Runtime examples. |
| `coakka-logger/` | Logger examples. |
| `coakka-runtime/containers/` | Runtime demonstrations using containers. |

See [Product Layout](product-layout.md) for the repository map. Historical
archives and source mirrors use the paths recorded for their release.

## Runtime Client Naming

Use **CoAkka Runtime Client** with the released command **`coakka-client`**.
The ecosystem identifier is `coakka-runtime-client`. Use that identifier
when referring to the product in the catalog.

## Public Wording Rules

- Introduce the ecosystem through CoAkka Runtime first. Keep Logger and Runtime
  addons visible; introduce the independent HTTP server/client as CoAkka HTTP
  Runtime without making it the replacement for the distributed runtime.
- Distinguish registry packages from repository archives. HTTP Runtime has
  archive-backed samples, not npm/PyPI/Maven Central or tagged Go-module
  distribution; Runtime/Logger coordinates do not install HTTP Runtime.
- Use `CoAkka` for the ecosystem.
- Use `CoAkka Runtime` for runtime behavior and the runtime product family.
- Use `coakka-runtime-core` for native engine or C ABI packaging.
- Use `coakka-runtime-connector` for language and framework connectors.
- Use `coakka-runtime-client` for CLI-driven runtime workflows.
- Use `coakka-runtime-inspect` for browser-visible runtime inspection workflows.
- Use `CoAkka HTTP Runtime` for the shared HTTP contract and its language
  packages/addons; do not shorten it to CoAkka Runtime when the HTTP boundary
  matters.
- Use `coakka-logger` for logger samples and logger packages.
- Avoid calling every package simply `CoAkka`; that hides which boundary owns
  behavior.
