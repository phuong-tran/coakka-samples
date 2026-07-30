# coakka-samples

[![sample-smoke](https://github.com/phuong-tran/coakka-samples/actions/workflows/sample-smoke.yml/badge.svg)](https://github.com/phuong-tran/coakka-samples/actions/workflows/sample-smoke.yml)

Route application-owned work without inventing another internal REST API.

CoAkka Runtime is a native-backed capability runtime for application-owned
work across processes and languages. It helps an app route work by stable
target name, handle request/reply, report deadletters, enforce bounded
admission, and expose delivery diagnostics without turning every internal
handoff into another hand-written HTTP endpoint.

Application-owned work means capability code governed by the same product or
application boundary, even when it runs in another process, language,
container, or host.

CoAkka Logger is a separate bounded logging surface in the same ecosystem.

## First Run

Use this as the main front door when Docker is available:

```sh
git clone https://github.com/phuong-tran/coakka-samples.git
cd coakka-samples
bash run.sh containers node-python
```

This runs two real processes in two languages with browser-visible state and
no backend HTTP fallback. It is the best first proof that CoAkka is not just a
local function-call wrapper.

If Docker is not available, use the smallest local runtime fallback:

```sh
bash run.sh runtime node basic
```

## What To Notice

Before CoAkka, internal application work often becomes another backend HTTP
surface:

```text
controller -> backend URL -> HTTP client -> backend endpoint
```

That endpoint may not be a real public product API. It often exists only
because a capability needs an address across process, language, or deployment
boundaries.

With CoAkka, the public edge can stay public and the internal handoff can use
a runtime target:

```text
controller -> CoAkka target -> owning handler -> reply or deadletter
```

A tiny before/after shape:

```text
before:
  public createCustomer(request)
    -> customerClient.post("/internal/customers", request)
    -> customerStore.create(request)

after:
  public createCustomer(request)
    -> runtime.ask("customer.create", request)
    -> handler "customer.create"
    -> customerStore.create(request)
```

The useful shift is not "HTTP is bad." Public HTTP, gRPC, browser APIs,
auth, API gateways, Nginx, TLS/mTLS, and deployment policy still belong at
real external or platform boundaries. CoAkka focuses on application-owned work
that needs a runtime boundary without becoming another L7 service API by
default.

The runtime vocabulary is intentionally small:

```text
target
route snapshot
generation
bounded admission
reply
timeout
rejection
deadletter
delivery diagnostics
```

Callers submit an identified payload to a stable capability target. Connector
APIs may add stronger typing in their host language, but the common contract is
the target, payload identity, route snapshot, reply/deadletter, and evidence.

## Why Teams Adopt CoAkka

- Remove private REST handoffs that only exist to give app-owned work an
  address.
- Keep public HTTP and gRPC edges unchanged.
- Move handlers across process, language, container, or host boundaries without
  changing the caller vocabulary.
- Get bounded admission, timeout, rejection, and deadletter evidence instead of
  hidden retries and vague failures.

A minimal Runtime call shape:

```kotlin
runtime.handler("customer.create") { request ->
    customerStore.create(request)
}

val reply = runtime.ask(
    target = "customer.create",
    payload = request
)
```

Exact APIs vary by connector, but the runtime idea is the same: register the
owning handler, then ask the stable target.

## When To Use It

CoAkka is useful when:

- work is still owned by the same product or application boundary;
- the handler may move across process, language, container, or host;
- a stable target is clearer than another private URL and client wrapper;
- bounded admission, timeout, rejection, and deadletter evidence matter;
- multiple language connectors should share the same runtime vocabulary.

Do not add CoAkka when:

- an ordinary in-process function call is enough;
- the boundary is a real public API or independently owned service API;
- HTTP/gRPC/OpenAPI semantics are the product contract;
- the system needs durable broker topics, replay, consumer groups, or workflow
  history;
- the problem is auth, authorization, deployment policy, service-mesh policy,
  or business transaction design.

## Repository Map

| Repository | Use it for |
| --- | --- |
| [`coakka-samples`](https://github.com/phuong-tran/coakka-samples) | Runnable examples and code you can inspect first. |
| [`coakka-publish`](https://github.com/phuong-tran/coakka-publish) | Released packages, native archives, manifests, checksums, compatibility matrix, and release notes. |
| [`coakka-runtime-go`](https://github.com/phuong-tran/coakka-runtime-go) | Public Go module for CoAkka Runtime. |
| [`coakka-logger-go`](https://github.com/phuong-tran/coakka-logger-go) | Public Go module for CoAkka Logger. |
| [`coakka-runtime-swift`](https://github.com/phuong-tran/coakka-runtime-swift) | Public SwiftPM runtime package for macOS ARM64. |
| [`coakka-logger-swift`](https://github.com/phuong-tran/coakka-logger-swift) | Public SwiftPM logger package for macOS ARM64. |

Use `coakka-samples` when you want to run examples. Use `coakka-publish` when
you need exact released files, checksums, compatibility status, or release
history.

## Packages

Published package lanes are available for JVM, Node.js, Python, Go, C#, Swift,
and other runtime/logger surfaces. Package versions are independent across the
ecosystem; they do not need to share the same number.

Current package-manager entrypoints live in
[docs/current-packages.md](docs/current-packages.md). Compatibility and release
history live in
[`coakka-publish/docs/compatibility-matrix.md`](https://github.com/phuong-tran/coakka-publish/blob/main/docs/compatibility-matrix.md)
and
[`coakka-publish/docs/releases`](https://github.com/phuong-tran/coakka-publish/tree/main/docs/releases).

## Choose A Sample

| Goal | Command or doc |
| --- | --- |
| First proof across two processes and two languages | `bash run.sh containers node-python` |
| Smallest local Runtime API | `bash run.sh runtime node basic` |
| Smallest local Logger API | `bash run.sh logger node basic` |
| Route miss and deadletter evidence | `bash run.sh runtime node deadletter` |
| Route generation and hot reload | `bash run.sh runtime python hot-reload` |
| Framework handoff shape | `bash run.sh list` then choose a `runtime/scenarios/customer-crud/*` lane |
| Published npm without cloning samples | [docs/first-npm-smoke.md](docs/first-npm-smoke.md) |

Use `bash run.sh doctor` to check local prerequisites and `bash run.sh list`
to see available lanes.

## Docs Map

Start here:

- [New To CoAkka](docs/new-to-coakka.md)
- [Runtime Field Guide](docs/runtime-field-guide.md)
- [How It Works](docs/how-it-works.md)
- [Questions And Answers](docs/qna.md)

Core runtime model:

- [Runtime Message And Routing Model](docs/runtime-message-and-routing-model.md)
- [Runtime Integration Guide](docs/runtime-integration-guide.md)
- [Runtime Glossary](docs/runtime-glossary.md)
- [Runtime Cluster Routing](docs/runtime-cluster-routing.md)
- [Containerized Runtime](docs/containerized-runtime.md)

Adoption and evidence:

- [Incremental Adoption](docs/incremental-adoption.md)
- [Integration Path](docs/integration-path.md)
- [Production Evidence](docs/production-evidence.md)
- [Production Readiness](docs/production-readiness.md)
- [Architecture Review Guide](docs/architecture-review-guide.md)
- [AI Reviewer Onboarding](docs/ai-reviewer-onboarding.md)
- [The CoAkka Story](docs/coakka-story.md)

Frameworks and tools:

- [CoAkka Spring Boot](docs/coakka-spring-boot.md)
- [CoAkka Quarkus](docs/coakka-quarkus.md)
- [CoAkka Runtime Client](docs/coakka-runtime-client.md)
- [CoAkka Runtime Inspect](docs/coakka-runtime-inspect.md)
- [Sample Lanes](docs/sample-lanes.md)

Repository and package boundaries:

- [Current Packages](docs/current-packages.md)
- [Repository Boundaries](docs/repository-boundaries.md)
- [CoAkka Ecosystem Naming](docs/coakka-ecosystem-naming.md)

## Boring First Production Shape

For a Kubernetes deployment, the boring first shape is:

```text
public request
  -> nginx or API gateway
    -> app-host policy
      -> CoAkka target
        -> Kubernetes Service DNS endpoint
          -> runtime handler
```

Kubernetes can own pod membership, readiness, pod churn, and pod-level
distribution. CoAkka does not need to discover individual pods in that common
shape. The app or connector maps normal platform configuration into a route
snapshot, often with a stable generation such as `1`.

Advanced expanded endpoints, weighted routing, affinity, pressure-aware
routing, and generation changes are covered in
[Runtime Cluster Routing](docs/runtime-cluster-routing.md) and
[Runtime Field Guide](docs/runtime-field-guide.md).

## CI

The public sample smoke workflow runs the supported quick lanes from published
artifacts where possible:

```sh
bash run.sh doctor
bash run.sh containers node-python
bash run.sh runtime node basic
bash run.sh logger node basic
```

Individual sample READMEs document lane-specific prerequisites and expected
output.

## License And Trademark

See [TRADEMARKS.md](TRADEMARKS.md) for CoAkka naming and trademark guidance.
Sample source licensing and published artifact licensing are documented
separately. Use the license terms shipped with each release artifact for that
artifact, and the repository license files for sample source and docs.
