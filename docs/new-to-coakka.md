# New To CoAkka

CoAkka is a native-backed runtime and logger toolkit for application-owned
work. It helps an app route work by target name, handle request/reply,
deadletters, bounded queues, diagnostics, and native-backed logging without
turning every internal boundary into another hand-written HTTP endpoint.

If you are evaluating CoAkka for the first time, read it in this order:

| Step | Read or run | Why |
| --- | --- | --- |
| 1 | This page | Understand the problem and repository map. |
| 2 | `coakka-samples` quick run | See a target, handler, reply, and logger output. |
| 3 | [Runtime Field Guide](runtime-field-guide.md) | Connect the sample to Kubernetes, topology, queues, overload, Nginx, and mTLS boundaries. |
| 4 | [How It Works](how-it-works.md) | Understand app-host, connector, runtime, route snapshot, and handler ownership. |
| 5 | [Runtime Integration Guide](runtime-integration-guide.md) | Map an existing service into `RuntimeStartSpec`, routes, handlers, and shutdown. |
| 6 | [Questions And Answers](qna.md) | Check the common objections: gRPC, Feign, Istio, sockets, load balancing, generations, and Saga. |

## Two Public Repositories

| Repository | Use it for | Link |
| --- | --- | --- |
| `coakka-samples` | Runnable examples and code you can inspect first. | https://github.com/phuong-tran/coakka-samples |
| `coakka-publish` | Released packages, native archives, manifests, checksums, compatibility matrix, and release notes. | https://github.com/phuong-tran/coakka-publish |

If you are new, start with `coakka-samples`. Use `coakka-publish` when you need
exact released files, checksums, compatibility status, or release history.

## What You Should Notice First

CoAkka is easiest to understand from a fake backend HTTP handoff.

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
auth, and deployment policy still belong to the app. CoAkka removes backend
HTTP that only exists to give capability code owned by the same app or team an
address.

Runtime samples use a target name:

```text
caller code -> CoAkka target -> handler -> reply or deadletter
```

The target is a capability name such as `samples.runtime.node.echo` or
`billing.invoice.create`. The app registers a handler for the target it owns,
and callers ask that target instead of building another internal HTTP endpoint.

Logger samples use a bounded logger:

```text
app code -> bounded native logger -> drain/counters
```

The sample output shows accepted records, drained records, dropped records, and
native version diagnostics.

## What CoAkka Is Not

CoAkka is not a Kafka clone, a hosted broker, a service mesh, a web framework,
or a replacement for public HTTP/gRPC APIs. It is an embedded runtime surface
used by an application host through language packages.

Public edges, authentication, deployment policy, service discovery, and product
APIs still belong to the application architecture around CoAkka.

## First Learning Path

1. Clone and run the smallest samples:

   ```sh
   git clone https://github.com/phuong-tran/coakka-samples.git
   cd coakka-samples
   bash run.sh runtime node basic
   bash run.sh logger node basic
   ```

2. Try the no-checkout npm smoke if you want to start from package-manager
   install commands:

   https://github.com/phuong-tran/coakka-samples/blob/main/docs/first-npm-smoke.md

3. Read [Runtime Field Guide](runtime-field-guide.md) before jumping into
   advanced routing details. It explains the practical path from a local
   runtime to Kubernetes Service DNS, stable targets, route snapshots, bounded
   queues, overload signals, Nginx, mTLS placement, and logger evidence.

4. Read sample docs for the lane you care about:

   https://github.com/phuong-tran/coakka-samples/tree/main/docs

5. Check released artifacts, checksums, and compatibility status:

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
