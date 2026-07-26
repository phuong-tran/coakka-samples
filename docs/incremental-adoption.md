# Incremental Adoption

CoAkka is flexible because it does not require a rewrite-first migration. A
team can keep working systems in place, pick one painful runtime boundary, and
test whether target-based delivery makes that boundary clearer.

The goal is not to replace every public API, standardize every service on one
framework, or migrate the whole architecture at once. Public HTTP and gRPC
edges can stay public. Direct in-process calls can stay direct. Legacy services
can keep running while one noisy handoff is made explicit.

## Start With One Painful Boundary

Choose a boundary where the current shape already hurts:

- a private backend HTTP endpoint exists only to call app-owned work
- one workflow crosses process or language boundaries
- timeouts, retries, or route misses are hard to explain
- a handler may start same-process but later move to another process
- diagnostics are spread across clients, URLs, status mapping, and logs

Then keep the first CoAkka step small:

```text
pick one runtime boundary
  -> add one host-language connector
  -> route one typed target through CoAkka
  -> inspect reply, timeout, stats, or deadletter evidence
  -> expand only if the boundary becomes clearer
```

That gives the team evidence before it commits to a broader migration.

## What Stays The Same

CoAkka does not force the application around it to change shape.

- Browser/API HTTP can remain the public edge.
- Existing auth, validation, and business policy stay in the app host.
- Stable direct calls can stay direct.
- Existing services, jobs, desktop flows, and integration systems can keep
  running.
- A real HTTP or gRPC service API should stay HTTP or gRPC.

CoAkka is for the internal runtime boundary where work is better named as a
target than as another backend URL.

## What Changes

The call-site stops depending on a fake backend network API just to gain an
address:

```text
before:
  controller -> private backend URL -> HTTP client -> handler

after:
  controller -> CoAkka target -> handler -> reply or deadletter
```

The useful change is ownership. The app still owns request parsing,
authorization, transactions, idempotency, and user-facing responses. CoAkka
owns target routing, active route generation, bounded admission, timeout,
reply matching, deadletters, and runtime diagnostics.

## Why It Matters

Many architecture ideas fail because the first step is too large. If adopting a
runtime boundary requires a platform reset, a full rewrite, or a coordinated
migration event, most teams will reject it even when the model is technically
sound.

CoAkka is designed to let the first step be small and reversible. Wrap one
polyglot handoff, one failure-prone route, or one fake backend HTTP boundary.
If delivery evidence improves, expand from there. If a path is already simple
and direct, leave it alone.

This is the practical adoption model:

```text
do not migrate the system first
make one painful boundary explicit first
```
