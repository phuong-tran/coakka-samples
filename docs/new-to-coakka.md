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

## What You Should Notice First

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

The packages are:

- `coakka-v2-connector-node@1.3.1`
- `coakka-v2-connector-bun@1.3.1`
- `coakka-v2-connector-electron@1.3.1`
- `coakka-logger-node@1.2.1`
- `coakka-logger-bun@1.2.1`
- `coakka-logger-electron@1.2.1`

## Then Read The Artifact Repo

Use `coakka-publish` when you need exact released files, checksums, release
notes, or compatibility status:

- https://github.com/phuong-tran/coakka-publish
- https://github.com/phuong-tran/coakka-publish/blob/main/docs/compatibility-matrix.md
