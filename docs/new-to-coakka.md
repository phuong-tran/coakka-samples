# New To CoAkka

CoAkka is a native-backed runtime and logger toolkit for application-owned
work. It helps an app route work by target name, handle request/reply,
deadletters, bounded queues, diagnostics, and native-backed logging without
turning every internal boundary into another hand-written HTTP endpoint.

## Two Public Repositories

| Repository | Use it for | Link |
| --- | --- | --- |
| `coakka-samples` | Runnable examples and code you can inspect first. | https://github.com/phuong-tran/coakka-samples |
| `coakka-publish` | Released packages, native archives, manifests, checksums, compatibility matrix, and release notes. | https://github.com/phuong-tran/coakka-publish |

If you are new, this repository is the starting point. `coakka-publish` is the
artifact source of truth.

The docs directory is the stable place to keep reading after an npm page:
https://github.com/phuong-tran/coakka-samples/tree/main/docs

## What You Should Notice First

CoAkka is easiest to understand from a fake backend HTTP example.

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

The target is a capability name such as `samples.runtime.node.echo`. The app
registers a handler for the target it owns, and callers ask that target instead
of building another internal HTTP endpoint.

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

## Start With JavaScript

The npm lane is published and registry-verified, so these samples install from
npm:

```sh
bash run.sh runtime node basic
bash run.sh runtime bun basic
bash run.sh runtime electron basic
bash run.sh logger node basic
bash run.sh logger bun basic
bash run.sh logger electron basic
```

The npm packages are:

- `coakka-v2-connector-node`
- `coakka-v2-connector-bun`
- `coakka-v2-connector-electron`
- `coakka-logger-node`
- `coakka-logger-bun`
- `coakka-logger-electron`

Use the package manager's `latest` version for first-run onboarding. Exact
released versions and native generations live in the compatibility matrix in
`coakka-publish`.

If you want to test npm first without cloning any CoAkka source repository,
use [First npm Smoke](first-npm-smoke.md). It walks through the fake backend
HTTP before shape and the CoAkka target after shape with the same customer
command.

## Then Read The Artifact Repo

Use `coakka-publish` when you need exact released files, checksums, release
notes, or compatibility status:

- https://github.com/phuong-tran/coakka-publish
- https://github.com/phuong-tran/coakka-publish/blob/main/docs/compatibility-matrix.md
