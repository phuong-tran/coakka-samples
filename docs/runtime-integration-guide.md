# Runtime Integration Guide

This guide is for moving from the samples to an existing service. The samples
are intentionally small, but the integration shape should stay the same:

1. add the language connector artifact
2. start one runtime participant per process
3. publish a route table
4. register local handlers for targets owned by the process
5. send typed requests/events to peer targets
6. handle deadletters and shut down cleanly

## Current Public Transport

The public publish surface currently exposes logger packages and the sanitized
native runtime C ABI package. Runtime JVM, language connector, Spring Boot, and
Quarkus package-backed samples remain paused while those package contents are
rebuilt and republished against the sanitized runtime surface. With a matching
local unpublished artifact set, local in-process request/reply samples and
cross-process customer scenarios keep business traffic on the runtime path.

Delivery failures remain explicit runtime failures. The samples should not hide
route, queue, or transporter failures behind an internal store HTTP fallback.
HTTP stays at real external edges such as the browser-facing web API.

The customer scenario exposes that distinction through a `deliveryMode` field:

| Value | Meaning |
| --- | --- |
| `runtime` | The business response came back through the runtime route. |
| `store-http-direct` | The store HTTP API was called directly. |

## Start Spec

Every language exposes the same start-spec concepts.

| Field | Integration rule |
| --- | --- |
| `systemName` | Stable logical service name, such as `billing-api` or `customer-store`. |
| `nodeId` | Concrete process identity. Include instance/host/pod identity when multiple instances run. |
| `queueCapacity` | Bounded queue size. Start conservative, measure pressure, then tune. |
| `strictNoDrop` | Prefer `true` while integrating so overload becomes visible. |
| `separateDeliveredRequestLane` | Prefer `true` for request/reply services so inbound work does not share the response/deadletter lane. |
| `generation` | Monotonic route-table version. Increment when applying a new route snapshot. |
| `routes` | Target-to-endpoint map. Local targets are owned here; peer targets point elsewhere. |

## Target Design

Use target names as stable service-contract addresses, not process names:

```text
samples.customer.frontend
samples.customer.store
samples.customer.audit
```

A target should answer “who owns this contract?” rather than “where is the
current host?”. The route table maps that target to a host/port endpoint.

## Endpoint Flags

Mark only targets served by the current process as `LOCAL`.

```text
current process owns samples.customer.store -> LOCAL
peer process owns samples.customer.frontend -> non-local
```

Register handlers only for local targets. Sending to a missing or unreachable
target should produce a deadletter that the caller can log, surface, or retry
according to business rules.

## Payload Contract

Every payload identity has:

- message type
- schema version
- payload format

The samples use JSON because it is easy to inspect:

```text
samples.customer.create.request.v1
schema version: 1
format: JSON
```

For a real system, keep payload identities stable and version them deliberately.
Do not reuse a message type for an incompatible payload shape.

When two services share a payload contract, keep the DTOs and message-type
strings in one shared source of truth. The Spring customer scenario uses a
`customer-contract` module for that purpose. The web and store services each
adapt those shared strings into runtime `ConnectorPayloadIdentity` values at the
connector boundary.

## Handler Pattern

A handler should:

1. decode the payload
2. validate the request
3. perform the local operation
4. return a typed reply using the original request correlation

Keep handler ownership local. The runtime owns routing and correlation; the
service still owns its business state and validation.

## Caller Pattern

A caller should:

1. choose source and target
2. attach a payload identity
3. set a timeout
4. name the operation for diagnostics
5. handle success, timeout, and deadletter paths

Start with explicit timeout values. The samples use `2s` for tiny local demos
and `5s` in web scenarios. Production values should come from service SLOs and
failure budgets, not from the sample defaults.

## Queue And Failure Policy

The samples use:

```text
queueCapacity = 128
strictNoDrop = true
```

That is a conservative integration default. It makes queue pressure visible
instead of hiding it behind unbounded memory growth or silent drops.

For production:

- size queues from expected burst size and memory budget
- expose queue/deadletter counters in service diagnostics
- treat deadletters as first-class errors
- avoid retry loops that can amplify pressure

## Shutdown

Close the orchestrator during process shutdown. In long-running services, wire
this into the host framework lifecycle:

- JVM/Spring: bean lifecycle or `AutoCloseable`
- Python: context manager or application shutdown hook
- Node.js: `SIGINT`/`SIGTERM` handlers
- Go: `defer orchestrator.Close()` plus signal handling for servers

## Language Recipes

Start from the language closest to your service:

- [JVM runtime samples](../runtime/jvm/README.md)
- [Python runtime samples](../runtime/python/README.md)
- [Node.js runtime samples](../runtime/node/README.md)
- [Go runtime samples](../runtime/go/README.md)

Then inspect the customer scenarios for cross-process wiring:

```sh
bash run.sh scenarios check
```

Artifact-backed native runtime runs can use the public publish checkout. Paused
language/framework runs require `COAKKA_ALLOW_PAUSED_RUNTIME=1`,
`COAKKA_PUBLISH_ROOT`, and, for JVM lanes, `COAKKA_PUBLISH_MAVEN_LOCAL` to
point at a local unpublished artifact set until the public package channel
reopens.
For public non-Maven packages, the sample resolver verifies artifact SHA256
against `artifacts/public-artifacts.tsv` from the publish surface before the
package is unpacked or installed.
