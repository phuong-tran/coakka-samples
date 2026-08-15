# Keep HTTP At The Edge

HTTP is the right protocol for browsers, public APIs, gateways, webhooks, and
independently owned service APIs. CoAkka does not replace those boundaries.

The architectural problem begins when application-owned work becomes another
HTTP service only because it moved into a different module, process, host
language, or deployment unit. The new endpoint may look like a service API,
but its real purpose is only to give an internal capability an address. That
choice spreads HTTP servers, clients, middleware, connection pools, retries,
status mapping, and duplicated failure policy across the internal execution
path.

CoAkka keeps a different responsibility split: HTTP remains at the external
edge, while selected application-owned work crosses the runtime boundary as a
targeted envelope with bounded admission and an explicit reply, timeout, or
deadletter outcome.

## Boundary In One View

```text
external client
  -> HTTP edge hosted by Node.js, Bun, JVM, Go, or another app framework
  -> authentication, authorization, validation, and edge policy
  -> thin HTTP-to-CoAkka request adapter
  -> CoAkka target, route, bounded admission, and request/reply lifecycle
  -> application-owned handler
  -> structured reply, timeout, rejection, or deadletter
  -> thin CoAkka-to-HTTP response adapter
  -> HTTP response
```

The two thin adapters belong to the edge application. They translate between
two already-defined contracts; they do not create another distributed runtime
inside the HTTP framework.

## HTTP Still Owns The External Contract

The edge application continues to own concerns that are genuinely HTTP or
public API policy:

- method, path, query, headers, content negotiation, and response shape;
- TLS termination or gateway integration at the chosen deployment boundary;
- authentication, authorization, tenant policy, validation, and rate limits;
- public idempotency rules and API compatibility;
- browser, mobile, webhook, third-party, and OpenAPI-facing behavior.

These concerns should not be pushed into CoAkka Runtime. Runtime should not
learn HTTP methods, paths, cookies, framework middleware, or public status-code
policy merely to make the integration look complete.

## Runtime Owns Internal Delivery

After the edge application has decided that valid application work should run,
CoAkka owns the runtime responsibilities:

- stable target and source identity;
- active route generation and endpoint selection;
- bounded admission and visible pressure;
- envelope framing and request/reply correlation;
- timeout, cancellation observation, deadletter, and delivery diagnostics;
- local or peer handler delivery without changing the application target.

This is a responsibility boundary, not a claim that all application-layer
protocols are interchangeable. HTTP remains an L7 external API protocol.
CoAkka provides a runtime delivery contract for work that should not have to
become another HTTP API first.

## Thin Request Adapter

The ingress adapter should perform a small, auditable mapping after edge policy
has accepted the request:

```text
validated request data  -> bounded CoAkka payload
application operation   -> stable runtime target
request identity        -> correlation and trace context
remaining request budget -> bounded runtime wait budget
```

It should not:

- forward an unbounded body into Runtime;
- construct target names directly from untrusted paths or headers;
- hide an unbounded queue behind an asynchronous framework callback;
- introduce automatic retries whose business safety is unknown;
- treat submission acceptance as business completion.

Authentication and validation failures finish at the edge without entering
Runtime. A successfully admitted ask still has to finish with a reply or an
explicit terminal runtime outcome.

## Thin Reply-To-HTTP Adapter

When Runtime completes the ask, the edge needs only a narrow conversion back
to its public contract:

```text
CoAkka business reply       -> application-selected HTTP status and body
reply content metadata      -> allowlisted response headers and Content-Type
runtime admission rejection -> bounded overload response chosen by the app
runtime timeout             -> timeout response chosen by the app
deadletter                  -> explicit unavailable/failure mapping
client disconnect           -> stop waiting and request cancellation when safe
```

The mapping is application policy. CoAkka should not globally decree that every
deadletter is one HTTP status, or that every runtime timeout means the remote
handler did not execute. The adapter must distinguish its local wait outcome
from business rollback or distributed cancellation guarantees.

A production-shaped response adapter should:

- emit at most one response for one accepted HTTP request;
- bound payload materialization and serialization;
- propagate only reviewed metadata rather than arbitrary runtime fields;
- preserve request, trace, and diagnostic correlation;
- make timeout, pressure, cancellation, and unavailable outcomes observable;
- avoid hidden retries and fallback HTTP calls to the same internal work;
- release connector and request-owned state on every terminal path.

The adapter is thin because it translates outcomes. It does not own runtime
routing, topology, queueing, retries, handler discovery, or business workflow.

## Node.js Versus Bun Is Then An Edge Choice

Node.js and Bun can still differ in HTTP throughput, startup time, memory use,
tooling, package compatibility, and operational maturity. Those differences
remain legitimate edge-host selection criteria.

They should not determine the internal execution architecture. Once HTTP is
kept at the edge and the adapter is deliberately thin, both hosts perform the
same bounded responsibility: accept an external request, submit selected work,
and translate the structured terminal outcome into an HTTP response.

A faster HTTP host can make that edge segment faster. It cannot repair an
architecture that has turned every internal handoff into another HTTP server,
client, retry stack, connection pool, and status-mapping surface.

```text
Making the HTTP wrapper faster is useful.
Needing fewer internal HTTP wrappers is the architectural improvement.
```

For this reason, Bun versus Node.js becomes an edge-host decision rather than a
distributed-system architecture decision. Benchmark either host when edge cost
matters, but do not use that benchmark to justify reproducing HTTP-shaped
internal boundaries that the application does not otherwise need.

## When Internal HTTP Is Still Correct

Keep HTTP or gRPC for an internal boundary when it is a real service API:

- the service has independent ownership and release compatibility;
- method/path or RPC schema is the intended product contract;
- heterogeneous clients must integrate without the CoAkka runtime contract;
- gateway, proxy, cache, streaming, or platform policy requires that protocol;
- the endpoint is expected to remain useful outside one application's runtime
  topology.

CoAkka is not a reason to erase a legitimate service boundary. It is a way to
avoid manufacturing one for application-owned capability delivery.

## Review Questions

Before adding another internal HTTP endpoint, ask:

1. Is this a real independently owned service API, or does the capability only
   need a stable application address?
2. Would the endpoint still be useful if the handler moved back into the same
   process?
3. Are method, path, status, client, retry, and middleware surfaces expressing
   product semantics or only transport plumbing?
4. Can the work use a stable CoAkka target with bounded admission and explicit
   reply/deadletter evidence instead?
5. If HTTP remains at the edge, is the request and response adapter small
   enough to audit for bounds, ownership, timeout, and cancellation?

Read [Questions And Answers](qna.md) for the Bun/Node positioning question and
[Runtime Message And Routing Model](runtime-message-and-routing-model.md) for
the complete target, envelope, reply, timeout, and deadletter vocabulary. For a
full-duplex browser edge, read
[WebSocket Integration With CoAkka](runtime-websocket-integration.md); the
app-host still owns the public protocol while Runtime messages and Stream Lane
remain behind it.
