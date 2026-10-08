# CoAkka Samples

<p align="center">
  <img src="docs/assets/brand/coakka-logo.png" alt="CoAkka" width="480">
</p>

[![sample-smoke](https://github.com/phuong-tran/coakka-samples/actions/workflows/sample-smoke.yml/badge.svg)](https://github.com/phuong-tran/coakka-samples/actions/workflows/sample-smoke.yml)

**CoAkka is a polyglot, multi-language, multi-platform distributed runtime ecosystem.**

Start with **CoAkka Runtime**, the native-backed distributed runtime for
application-owned work. Route a request to a stable target across processes
and languages; receive a reply or explicit delivery failure, with bounded
admission and diagnostics. Keep business policy in the application.

CoAkka Logger adds bounded logging, Runtime addons add optional integration
capabilities, and the separate **CoAkka HTTP Runtime** provides an HTTP server
and client for real HTTP edges. Products have independent lifecycles, versions,
and distribution channels; adopting one does not require adopting them all.

## Contents

- [First run](#first-run)
- [CoAkka ecosystem](#coakka-ecosystem)
- [Choose a sample](#choose-a-sample)
- [Install Runtime and Logger packages](#install-runtime-and-logger-packages)
- [Featured Runtime samples](#featured-runtime-samples)
- [CoAkka HTTP Runtime — repository archives](#coakka-http-runtime)
- [Documentation](#documentation)
- [Repository map](#repository-map)
- [Verification and contribution](#verification-and-contribution)
- [License and trademark](#license-and-trademark)

## First Run

With Docker available, start with two processes in two languages:

```sh
git clone https://github.com/phuong-tran/coakka-samples.git
cd coakka-samples
bash run.sh containers node-python
```

The Node.js web app calls the Python store through CoAkka Runtime, not a hidden
store REST fallback. Inspect the browser-visible state and
[committed screenshots](docs/production-evidence.md#visual-evidence).

Without Docker, use Node.js and the published Runtime package:

```sh
bash run.sh runtime node basic
```

Check the [Node.js prerequisites](runtime/node/README.md), or choose another
language in the [Runtime sample guide](runtime/README.md). The
[no-checkout npm smoke](docs/first-npm-smoke.md) is available if you prefer
installing a package before cloning samples.

## CoAkka Ecosystem

| Product | Responsibility | Distribution and starting point |
| --- | --- | --- |
| **CoAkka Runtime** | Distributed application-owned work: targets, request/reply, deadletters, File Lane and Stream Lane. | [Runtime samples](runtime/README.md); published language packages and exact native/source archives. |
| CoAkka Logger | Bounded logging with visible admission, delivery and pressure outcomes. | [Logger samples](logger/README.md); independently versioned packages. |
| Runtime Addons | Optional integrations composed with Runtime, including artifact acquisition before File Lane delivery. | [Addon samples](runtime-addons/README.md); native archives, not bundled language connectors. |
| CoAkka HTTP Runtime | Independent HTTP server/client: routes, static files, streaming, SSE, WebSocket and TLS/mTLS. | [HTTP samples](coakka-http-runtime/README.md); checksum-pinned repository archives, **not npm, PyPI, Maven Central or tagged Go modules**. |

Runtime changes the internal handoff, not the public API:

```text
HTTP / UI / job -> application policy -> CoAkka Runtime target -> handler
                                                    <- reply or deadletter
```

The HTTP edge may use an existing framework or CoAkka HTTP Runtime. Runtime
and HTTP Runtime do not share a lifecycle or package identity. See the
[ecosystem overview](docs/ecosystem-overview.md) and
[HTTP boundary guide](docs/http-edge-runtime-boundary.md).

Kubernetes is a first-class deployment lane, not a prerequisite. Runtime also
fits standalone services, containers, VMs, bare metal and supported edge hosts.
Check [package/platform evidence](docs/runtime-package-platform-evidence.md)
for the exact product, version, operating system and CPU.

## Choose A Sample

| Goal | Command or doc |
| --- | --- |
| First proof across two processes and two languages | `bash run.sh containers node-python` |
| Smallest local Runtime API | `bash run.sh runtime node basic` |
| Smallest local Logger API | `bash run.sh logger node basic` |
| Route miss and deadletter evidence | `bash run.sh runtime node deadletter` |
| Route generation and hot reload | `bash run.sh runtime python hot-reload` |
| Native public-ABI correctness and connection-strategy evidence | `bash run.sh runtime-test smoke` |
| Framework handoff shape | `bash run.sh list` then choose a `runtime/scenarios/customer-crud/*` lane |
| Published Node.js npm package | `bash run.sh runtime node basic`; [no-checkout npm smoke](docs/first-npm-smoke.md) |
| Published Bun npm package | `bash run.sh runtime bun basic` |
| Published Electron npm package | `bash run.sh runtime electron basic` |
| Tauri/Rust source-archive integration | [Tauri integration guide](runtime/tauri/README.md) |
| Android signed preview | [Android integration guide](runtime/android/README.md) |
| Published PyPI packages | `bash run.sh runtime python basic`; `bash run.sh logger python basic` |
| Published NuGet packages | `bash run.sh runtime csharp basic`; `bash run.sh logger csharp basic` |

Use `bash run.sh doctor` to check local prerequisites and `bash run.sh list`
to see available lanes.

## Install Runtime And Logger Packages

Stable application lanes install CoAkka Runtime and Logger from their native
package registry or checksum-pinned source archive. You do not need to build
the native core or clone a connector repository for those paths. Android preview
is listed separately because it has no public Maven coordinate.

| Host lane | Distribution | Runtime | Logger | Sample or guide |
| --- | --- | --- | --- | --- |
| JVM | Maven Central | [`coakka.runtime` 2.5.3](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/runtime/2.5.3) | [`coakka.logger` 1.2.2](https://central.sonatype.com/artifact/io.github.phuong-tran.coakka/logger/1.2.2) | `bash run.sh runtime jvm basic` |
| Node.js | npm | [`coakka-v2-connector-node@2.5.3`](https://www.npmjs.com/package/coakka-v2-connector-node/v/2.5.3) | [`coakka-logger-node@1.2.7`](https://www.npmjs.com/package/coakka-logger-node/v/1.2.7) | `bash run.sh runtime node basic` |
| Bun | npm | [`coakka-v2-connector-bun@2.5.3`](https://www.npmjs.com/package/coakka-v2-connector-bun/v/2.5.3) | [`coakka-logger-bun@1.2.7`](https://www.npmjs.com/package/coakka-logger-bun/v/1.2.7) | `bash run.sh runtime bun basic` |
| Electron main process | npm | [`coakka-v2-connector-electron@2.5.3`](https://www.npmjs.com/package/coakka-v2-connector-electron/v/2.5.3) | [`coakka-logger-electron@1.2.7`](https://www.npmjs.com/package/coakka-logger-electron/v/1.2.7) | `bash run.sh runtime electron basic` |
| Python | PyPI | [`coakka-v2-connector==2.5.3`](https://pypi.org/project/coakka-v2-connector/2.5.3/) | [`coakka-logger==1.2.2`](https://pypi.org/project/coakka-logger/1.2.2/) | `bash run.sh runtime python basic` |
| .NET | NuGet | [`CoAkka.Runtime@2.5.3`](https://www.nuget.org/packages/CoAkka.Runtime/2.5.3) | [`CoAkka.Logger@1.2.3`](https://www.nuget.org/packages/CoAkka.Logger/1.2.3) | `bash run.sh runtime csharp basic` |
| Go | Go modules | [`coakka-runtime-go@v1.8.3`](https://github.com/phuong-tran/coakka-runtime-go/tree/v1.8.3) | [`coakka-logger-go@v1.2.6`](https://pkg.go.dev/github.com/phuong-tran/coakka-logger-go@v1.2.6) | `bash run.sh runtime go basic` |
| Swift | SwiftPM | [`coakka-runtime-swift@v2.5.3`](https://github.com/phuong-tran/coakka-runtime-swift/tree/v2.5.3) | [`coakka-logger-swift@v1.2.2`](https://github.com/phuong-tran/coakka-logger-swift/releases/tag/v1.2.2) | `bash run.sh runtime swift basic` |
| Tauri/Rust host | Checksum-pinned source archive, not crates.io | [`coakka-runtime-tauri-intents` 2.5.3 source](https://github.com/phuong-tran/coakka-publish/tree/main/runtime/tauri/releases/2.5.1%2B26f7944de4a4e0598845a54e4775f9463a9e33be-0ba485e) | [`coakka-logger-tauri-intents` 1.2.2 source](https://github.com/phuong-tran/coakka-publish/tree/main/logger/tauri/releases/1.2.1%2Bf50756ebff0d-7718ce6) | [Tauri integration guide](runtime/tauri/README.md) |
| Android preview | Signed preview AAR, no public Maven coordinate | [`coakka-runtime-android` 1.2.0 preview](runtime/android/README.md) | Use the app's Android logging policy | [Android integration guide](runtime/android/README.md) |

Node.js, Bun, and Electron are separate npm packages with different host
boundaries. Install the package for the runtime that actually owns native
resources:

```sh
npm install coakka-v2-connector-node@2.5.3 coakka-logger-node@1.2.7
bun add coakka-v2-connector-bun@2.5.3 coakka-logger-bun@1.2.7
npm install coakka-v2-connector-electron@2.5.3 coakka-logger-electron@1.2.7
```

Electron `42+` and Node.js `22+` are required by the Electron lane. Only the
Electron main process owns CoAkka Runtime; preload and renderer code receive a
narrow intent API rather than native handles.

Python and .NET use their own registries:

```sh
python3 -m pip install coakka-v2-connector==2.5.3 coakka-logger==1.2.2
dotnet add package CoAkka.Runtime --version 2.5.3
dotnet add package CoAkka.Logger --version 1.2.3
```

Tauri is not an npm or crates.io connector. Download and verify the `2.5.3`
source archive from the table, unpack it, and use a Rust path dependency from
the trusted Tauri host:

```toml
[dependencies]
coakka-tauri-intents = { path = "/path/to/coakka-runtime-tauri-intents-2.5.3-source/coakka-tauri-intents" }
```

Android `1.2.0` is a preview, not a public install coordinate. A permitted
Android consumer places the exact verified AAR under `app/libs` and uses:

```kotlin
dependencies {
    implementation(files("libs/coakka-runtime-android-1.2.0.aar"))
}
```

Do not invent a Maven coordinate for Android. See
[Current Packages](docs/current-packages.md) for every release lane, native
generation, and exact compatibility evidence.

## Featured Runtime Samples

`coakka-samples` contains reviewable source and consumer projects only. Its
sample binaries are versioned files in `coakka-publish/main`, never GitHub
Release attachments.

| Sample | Source and run guide | Published binaries |
| --- | --- | --- |
| Raspberry Pi camera livestream | [`runtime-streaming-demo/rpi-camera/`](runtime-streaming-demo/rpi-camera/README.md) | [`coakka-publish/samples/runtime/native/rpi-camera/releases/1.1.0/`](https://github.com/phuong-tran/coakka-publish/tree/main/samples/runtime/native/rpi-camera/releases/1.1.0) |
| Native artifact publishers | [`runtime-addons/`](runtime-addons/README.md) | Eleven addons at `1.1.0+d1032f6d`; SFTP at `1.2.0+88b9a047`. |

[`runtime-streaming-demo/`](runtime-streaming-demo/README.md) is the top-level
lane for complete Stream Lane workflows. It is a sibling of
[`runtime-addons/`](runtime-addons/README.md), not a subdirectory of one
language binding.

## CoAkka HTTP Runtime

**One shared native HTTP runtime, with idiomatic APIs across languages.**

Polyglot services often reimplement the same HTTP mechanics separately in Go,
Python, JavaScript and the JVM. CoAkka HTTP Runtime reduces that fragmentation:
transport, parsing, routing, limits, timeouts and shutdown share one native
implementation, while handlers keep their language's normal programming style.
When that shared implementation is fixed or optimized, every connector shipping
the updated runtime can benefit; the work need not be repeated per language.
This is not a promise of identical throughput across hosts.

It supports HTTP server/client requests, static frontend and file delivery,
streaming, SSE, WebSocket, TLS/mTLS, live route/handler changes, bounded
monitoring and graceful shutdown. Read
[why it exists, its benefits and capabilities](docs/coakka-http-runtime-introduction.md)
before choosing a sample.

HTTP Runtime is a newer, independent product. Its samples use the exact
`1.0.0` candidate archives available in `coakka-publish/main`; retained
candidate directory names identify those files, not a required working branch.
It is not installed by any Runtime/Logger coordinate in the table above.
There is no HTTP Runtime npm, PyPI, Maven Central or tagged Go-module release
in this distribution.

The [HTTP sample guide](coakka-http-runtime/README.md) covers C11, C++20,
Go, Kotlin/JVM, Python, and TypeScript on Node.js/Bun, with prerequisites,
feature coverage and host-inlined request handlers. No private Core or
connector checkout is required.

```sh
export COAKKA_PUBLISH_ROOT=/path/to/coakka-publish
export COAKKA_HTTP_SAMPLE_WORK_ROOT=/path/to/build-volume/http-samples
bash coakka-http-runtime/run.sh go smoke
```

Use the [feature index](coakka-http-runtime/FEATURES.md) for routing, static
files, streaming, SSE, WebSocket, TLS/mTLS, outbound HTTP, monitoring and
shutdown examples. The [sample scope](coakka-http-runtime/README.md#verified-candidate-scope)
distinguishes executed samples from package-platform qualification.
[Pi 5 benchmarks](coakka-http-runtime/benchmark-rpi5/README.md) describe a
specific workload, not a cross-product performance guarantee.

## Documentation

Use the [documentation index](docs/README.md) for the complete reading map.

| You want to… | Start here |
| --- | --- |
| Understand why CoAkka exists | [New to CoAkka](docs/new-to-coakka.md), [the story](docs/coakka-story.md) |
| Integrate Runtime in an existing app | [Runtime integration](docs/runtime-integration-guide.md), [sample lanes](docs/sample-lanes.md) |
| Design routes, failures and lifecycle | [Message/routing model](docs/runtime-message-and-routing-model.md), [Runtime field guide](docs/runtime-field-guide.md) |
| Move files or live streams | [File Lane](docs/runtime-file-transfer.md), [Stream Lane](docs/runtime-streaming.md), [replica ownership](docs/runtime-lane-owner-grants.md) |
| Add logging or optional capabilities | [Logger](logger/README.md), [Runtime addons](docs/runtime-addons.md) |
| Build an HTTP server/client | [HTTP Runtime guide](coakka-http-runtime/README.md), [language samples](coakka-http-runtime/README.md#languages) |
| Check deployment fit and evidence | [Production readiness](docs/production-readiness.md), [production evidence](docs/production-evidence.md) |
| Resolve a problem | [Troubleshooting](docs/troubleshooting.md), [support](SUPPORT.md) |

## Repository Map

| Repository | Use it for |
| --- | --- |
| [`coakka-samples`](https://github.com/phuong-tran/coakka-samples) | Runnable examples and code you can inspect first. |
| [`coakka-publish`](https://github.com/phuong-tran/coakka-publish) | Released packages, native archives, manifests, checksums, compatibility matrix, and release notes. |
| [`coakka-runtime-go`](https://github.com/phuong-tran/coakka-runtime-go) | Public Go module for CoAkka Runtime. |
| [`coakka-logger-go`](https://github.com/phuong-tran/coakka-logger-go) | Public Go module for CoAkka Logger. |
| [`coakka-runtime-swift`](https://github.com/phuong-tran/coakka-runtime-swift) | Public SwiftPM runtime package with all five native payloads; Swift execution is verified on macOS ARM64. |
| [`coakka-logger-swift`](https://github.com/phuong-tran/coakka-logger-swift) | Public SwiftPM logger package for macOS ARM64. |

Use `coakka-samples` when you want to run examples. Use `coakka-publish` when
you need exact released files, checksums, compatibility status, or release
history.

## Verification And Contribution

This is a rolling sample repository, not an aggregate product release. It does
not create GitHub Releases. Exact dependency pins and the applicable CI results
identify the sample surface; each product keeps its own version and evidence.

- Use `bash run.sh doctor` and `bash run.sh list` for Runtime/Logger sample discovery.
- Use [Runtime Test](runtime-test/README.md) for native Runtime correctness,
  concurrency, pressure and opt-in sanitizers.
- Use [HTTP Runtime verification](coakka-http-runtime/README.md#run) for its
  separate archive-backed build, application and security checks.
- Use [benchmark tooling](bench/README.md) for Runtime regression evidence;
  HTTP measurements live in their own [benchmark guide](coakka-http-runtime/benchmark-rpi5/README.md).
- Follow [Contributing](CONTRIBUTING.md) and [AI-assisted integration](docs/ai-assisted-integration.md)
  before adapting samples. Report the exact product, package and host.

Contact: `gabrielgun1983@gmail.com`.

## License And Trademark

See [TRADEMARKS.md](TRADEMARKS.md) for CoAkka naming and trademark guidance.

CoAkka is free for application use, including commercial and production use.
Sample source and documentation use the [Apache License, Version 2.0](LICENSE).
CoAkka Native Core files use the
[CoAkka Native Artifact License 1.2](NATIVE-LICENSE.md), which permits ordinary
application and SaaS use but requires a separate agreement to sell or offer
CoAkka itself as managed runtime or infrastructure. See
[Package Licensing](docs/package-licensing.md) for the file-scope map used by
binary-bearing platform packages.
