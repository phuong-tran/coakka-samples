# New To CoAkka

CoAkka Runtime is a native-backed capability runtime for application-owned
work across processes and languages. It helps an app route work by target
name, handle request/reply, deadletters, bounded queues, and diagnostics
without turning every internal boundary into another hand-written HTTP
endpoint.

Application-owned work means capability code governed by the same product or
application boundary, even when it runs in another process, language,
container, or host.

CoAkka Logger is a separate bounded logging surface in the same ecosystem.

If you are evaluating CoAkka for the first time, read it in this order:

| Step | Read or run | Why |
| --- | --- | --- |
| 1 | This page | Understand the problem and repository map. |
| 2 | `bash run.sh containers node-python` | See two real processes, two languages, browser-visible state, and no backend HTTP fallback. |
| 3 | [Runtime Field Guide](runtime-field-guide.md) | Connect the sample to the normal Kubernetes Service DNS shape, queues, overload, Nginx, and mTLS boundaries. |
| 4 | [How It Works](how-it-works.md) | Understand app-host, connector, runtime, route snapshot, and handler ownership. |
| 5 | [Runtime Integration Guide](runtime-integration-guide.md) | Map an existing service into `RuntimeStartSpec`, routes, handlers, and shutdown. |
| 6 | [Questions And Answers](qna.md) | Check the common objections: gRPC, Feign, Istio, sockets, load balancing, generations, and Saga. |

## Two Main Public Repositories

| Repository | Use it for | Link |
| --- | --- | --- |
| `coakka-samples` | Runnable examples and code you can inspect first. | https://github.com/phuong-tran/coakka-samples |
| `coakka-publish` | Released packages, native archives, manifests, checksums, compatibility matrix, and release notes. | https://github.com/phuong-tran/coakka-publish |

If you are new, start with `coakka-samples`. Use `coakka-publish` when you need
exact released files, checksums, compatibility status, or release history.
Language-specific public package repositories can exist separately. See
[Current Packages](current-packages.md) for current package-manager entrypoints
and repository links.

## What You Should Notice First

CoAkka is easiest to understand from an internal HTTP handoff that exists
primarily to give capability code an address.

Before CoAkka, a team may keep the public API route and then add another
private HTTP endpoint only to call work owned by the same app or team:

```text
POST /api/customers
  -> fetch("http://customer-store/backend/customers")
  -> store.create(...)
```

After CoAkka, the public API route can stay exactly where it belongs, but the
internal handoff becomes a runtime target:

```text
POST /api/customers
  -> ask target "samples.customer.store.create"
  -> store.create(...)
  -> runtime reply or deadletter
```

The important change is not "HTTP is bad." Public HTTP, gRPC, browser APIs,
auth, and deployment policy still belong to the app. CoAkka removes the
internal HTTP-shaped handoff when it only exists to give capability code owned
by the same app or team an address.

Runtime samples use a target name:

```text
caller code -> CoAkka target -> handler -> reply or deadletter
```

The target is a capability name such as `samples.runtime.node.echo` or
`billing.invoice.create`. The app registers a handler for the target it owns,
and callers ask that target instead of building another internal HTTP endpoint.

CoAkka Logger is covered separately. First-time readers should continue with
the Runtime learning path, then return to Logger when they want bounded,
cross-language operational evidence and diagnostics that fit beside the
Runtime vocabulary.

## What CoAkka Is Not

CoAkka is not a Kafka clone, a hosted broker, a service mesh, a web framework,
or a replacement for public HTTP/gRPC APIs. It is an embedded runtime surface
used by an application host through language packages.

Public edges, authentication, deployment policy, and product APIs still belong
to the application architecture around CoAkka.

## First Learning Path

1. Clone the samples repo and run the golden path:

   ```sh
   git clone https://github.com/phuong-tran/coakka-samples.git
   cd coakka-samples
   bash run.sh containers node-python
   ```

   This path uses the published container images to show two runtime
   participants in different languages. It is the best first proof that CoAkka
   is not just a local function-call wrapper.

2. If Docker is not available, run the smallest local Runtime package check:

   ```sh
   bash run.sh runtime node basic
   ```

3. Optionally run the smallest local Logger package check:

   ```sh
   bash run.sh logger node basic
   ```

4. Try the no-checkout npm smoke if you want to start from package-manager
   install commands:

   https://github.com/phuong-tran/coakka-samples/blob/main/docs/first-npm-smoke.md

5. Read [Runtime Field Guide](runtime-field-guide.md) before jumping into
   advanced routing details. Its first pass explains the practical path from a
   local runtime to Kubernetes Service DNS, stable targets, route snapshots,
   bounded queues, overload signals, Nginx, mTLS placement, and logger
   evidence. You can leave expanded endpoints, custom policies, and generation
   changes for later.

6. Read sample docs for the lane you care about:

   https://github.com/phuong-tran/coakka-samples/tree/main/docs

7. Check released artifacts, checksums, and compatibility status:

   https://github.com/phuong-tran/coakka-publish
   https://github.com/phuong-tran/coakka-publish/blob/main/docs/compatibility-matrix.md

## How The Main Docs Fit Together

| Doc | Use it when |
| --- | --- |
| [Runtime Field Guide](runtime-field-guide.md) | You understand the basic idea and want to know how a real topology should be shaped. |
| [How It Works](how-it-works.md) | You want the runtime lifecycle and ownership model. |
| [Runtime Message And Routing Model](runtime-message-and-routing-model.md) | You need the vocabulary: start spec, route snapshot, envelope, ask, reply, timeout, and deadletter. |
| [Runtime Integration Guide](runtime-integration-guide.md) | You are wiring CoAkka into an existing service or framework. |
| [Runtime Cluster Routing](runtime-cluster-routing.md) | You are past the simple Service DNS shape and need expanded endpoints, route policies, or generation discipline. |
| [Questions And Answers](qna.md) | You want direct answers to architecture objections and boundary questions. |
| [Current Packages](current-packages.md) | You need current package-manager entrypoints and version notes. |
