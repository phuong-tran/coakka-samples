# Envelope And Deadletter Map

This page is the exact public lookup for `Envelope`, one-way delivery, and
`Deadletter`. Use the broader
[Runtime Message And Routing Model](../explanation/runtime-message-and-routing-model.md) for
the end-to-end mental model.

## Contents

- [Read The Outcome Boundary First](#read-the-outcome-boundary-first)
- [Build One Request Envelope](#build-one-request-envelope)
- [Envelope Fields](#envelope-fields)
- [Use Headers As Context, Not A Second Payload](#use-headers-as-context-not-a-second-payload)
- [one_way And Message Kind](#one_way-and-message-kind)
- [Delivery Hints](#delivery-hints)
- [Deadletter Fields](#deadletter-fields)
- [Where A Deadletter Is Produced](#where-a-deadletter-is-produced)
- [Deadletter Reasons](#deadletter-reasons)
- [Failover Is Not User Retry](#failover-is-not-user-retry)
- [TLS And mTLS Failures](#tls-and-mtls-failures)
- [Timeout And Retry Rules](#timeout-and-retry-rules)

## Read The Outcome Boundary First

A send has three distinct outcome layers. Do not collapse them into one generic
error.

| Boundary | What happened | Caller-visible result |
| --- | --- | --- |
| Before admission | Runtime is not started or is closed, arguments cannot be copied, or the bounded ingress queue cannot accept work. | Synchronous connector/native submit failure. No automatic deadletter exists because runtime did not admit the envelope. |
| After admission | Routing, queue handoff, locality policy, remote transport, or runtime lifecycle prevents completion. | A `Deadletter` on the runtime deadletter lane; Core matches an ask terminal to its ticket and the connector presents it to the caller. |
| After business ownership | The target handler ran and returned a domain result or error. | `Envelope(kind=RESPONSE)` with business `status`, `error_code`, and `error_message`; this is not a deadletter. |

The practical rule is:

```text
submit returned non-OK -> not admitted; handle the synchronous error
submit returned OK     -> later delivery failure is a deadletter
handler returned error -> business RESPONSE, not a deadletter
```

## Build One Request Envelope

Start from the business intent and let the connector normalize fields that its
host-language API owns. Assemble a reply-capable request in this order:

1. choose one stable capability `target`, then select `REQUEST` with
   `one_way=false`;
2. identify the payload with `message_type`, a positive
   `payload_schema_version`, and `payload_format`;
3. encode the business data into `payload`;
4. create a unique `message_id` and request/response `correlation_id`; the
   connector may generate these together with `reply_to`;
5. set a bounded `timeout_ms` wait/delivery budget;
6. add only the small cross-cutting context the handler or diagnostics need;
7. validate the semantic combination before admission.

A language-neutral billing request can look like this:

```yaml
message_id: msg-01J...
correlation_id: ask-01J...
source: checkout-api
target: billing.charge
reply_to: checkout-api
kind: REQUEST
one_way: false
timeout_ms: 1500
message_type: billing.charge.v1
payload_schema_version: 1
payload_format: JSON
headers:
  trace_id: trace-92f1
  tenant_id: tenant-acme
  idempotency_key: checkout-7842:charge
payload: '{"checkout_id":"7842","amount_minor":125000,"currency":"VND"}'
```

The exact header names are application contracts, not reserved Core names.
`route_key_hint` in a rendezvous-hash route names the header whose value Core
should read for that route; it does not make every header part of routing.

A user retry is a new submission. Give the new envelope a new `message_id` and
let the connector create a new request correlation identity. Preserve the same
business idempotency key when both submissions refer to one logical operation.

## Envelope Fields

The wire schema has 17 fields. Connector APIs may generate or normalize some
of them, but their meanings stay the same across languages.

| Field | Meaning | Main rule |
| --- | --- | --- |
| `message_id` | Unique identity of this message instance. | Preserve it for deadletter matching and diagnostics. |
| `correlation_id` | Identity used to match request and response. | Required for `RESPONSE` and non-one-way `REQUEST`. |
| `source` | Logical sender or responder identity. | Diagnostic metadata, not payload identity. |
| `target` | Stable capability address used for routing. | Required for routed request/event traffic. |
| `reply_to` | Reply-path hint. | Only for reply-capable requests; forbidden on responses and one-way requests. |
| `kind` | `REQUEST`, `RESPONSE`, or `EVENT`. | Must not be `UNSPECIFIED`. |
| `one_way` | Whether the sender expects no response. | A response cannot be one-way. |
| `timeout_ms` | Runtime delivery/wait budget hint. | Not a business SLA and not an automatic retry policy. |
| `payload` | Opaque business bytes. | Runtime transports but does not interpret them. |
| `headers` | Small bridge, trace, tenant, idempotency, or operational metadata. | Do not turn headers into a second payload schema or expose secrets in logs. |
| `status` | Business outcome on a response. | Do not use it for runtime delivery failure. |
| `error_code` | Stable business error code. | Meaningful on an error response. |
| `error_message` | Human-readable business error detail. | Diagnostic business text, not a transport category. |
| `delivery_hint` | Local/remote routing preference. | Requests/events may set it; responses must use router default. |
| `message_type` | Stable business payload contract name. | Prefer it over an ad-hoc payload-type header. |
| `payload_schema_version` | Version of the business payload contract. | Typed connector paths use a positive version. |
| `payload_format` | `JSON`, `PROTOBUF`, `THRIFT`, `MSGPACK`, `PLAIN_TEXT`, or `BINARY`. | Declare how payload bytes should be decoded. |

The runtime semantic checks that are easiest to miss are:

- `RESPONSE` requires `correlation_id`
- `RESPONSE` must not be `one_way`
- `RESPONSE` must not carry `reply_to`
- `RESPONSE` must not override `delivery_hint`
- non-one-way `REQUEST` requires `correlation_id`
- one-way `REQUEST` must not carry `reply_to`

A connector builder may reject an invalid combination before submission. If an
invalid encoded envelope is admitted, runtime emits
`Deadletter(reason=INVALID_ENVELOPE)`.

## Use Headers As Context, Not A Second Payload

Good header values are small, bounded context that travels beside the business
message:

- trace/request identity used to correlate logs and diagnostics;
- tenant or app-host context already authorized by the application;
- a business idempotency key used by the handler or reconciliation path;
- one stable shard/affinity key when a route explicitly names it;
- small operational tags that are safe to copy and observe.

Keep these values out of headers:

- fields that change the business meaning of the command or query: put them in
  the payload and version that payload contract;
- payload type, schema version, or encoding: use the first-class envelope
  fields;
- large documents, binary content, or unbounded metadata;
- raw authentication tokens, private keys, or other secrets that could appear
  in diagnostics;
- retry counters or policies that ask Core to infer business idempotency.

The app-host authenticates and authorizes before submission. A tenant header is
propagated context, not proof of authorization by itself.

## `one_way` And Message Kind

`one_way` is a delivery expectation on the envelope. It is not a delivery hint,
an overload policy, or permission to hide failures.

| Shape | `kind` | `one_way` | Correlation/reply expectation |
| --- | --- | --- | --- |
| Ask/query/command with a reply | `REQUEST` | `false` | Requires `correlation_id`; Core tracks the response, matched deadletter or timeout, and the connector projects the terminal outcome. |
| Fire-and-observe command | `REQUEST` | `true` | No `reply_to` and no business response is expected. Admission failure remains synchronous; later failure remains observable. |
| Domain event | `EVENT` | normally `true` | No pending ask. Use deadletter observation, stats, and monitor events for delivery evidence. |
| Business reply | `RESPONSE` | `false` | Requires `correlation_id`; carries business status and must not override routing intent. |

One-way means only that the sender does not wait for a business response:

- the connector/native submit result still reports whether runtime admitted it
- an admitted one-way envelope can still produce a deadletter
- explicit overload policy can shed it as
  `ONE_WAY_DROPPED_BY_POLICY`
- `remote_outbound_one_way_drop_count` records that policy path
- a one-way API must not allocate a pending ask just to make the send look like
  request/reply

Use connector methods such as `sendOneWay...` when available. Subscribe to the
connector deadletter/diagnostic surface when the application needs delivery
evidence; the one-way call itself normally reports admission, not later
delivery completion.

## Delivery Hints

`delivery_hint` constrains route selection. It does not describe reply behavior;
that is the job of `one_way`.

| Hint | Meaning |
| --- | --- |
| `UNSPECIFIED` / `ROUTER_DEFAULT` | Use normal runtime route policy. |
| `PREFER_LOCAL` | Prefer a local eligible endpoint, but permit remote routing. |
| `REQUIRE_LOCAL` | Fail with `LOCALITY_MISMATCH` when active placement cannot satisfy local delivery. |
| `REQUIRE_REMOTE` | Fail with `LOCALITY_MISMATCH` when active placement cannot satisfy remote delivery. |

## Deadletter Fields

| Field | Meaning |
| --- | --- |
| `original_envelope` | The submitted envelope, including its `message_id`. |
| `reason` | Stable machine-readable runtime failure category. |
| `detail` | Human-readable diagnostic text. Do not parse it as an error category. |
| `active_generation` | Route/control generation active when the failure was produced. |
| `resolved_host` / `resolved_port` | Chosen destination when resolution had progressed that far. |
| `endpoint_attempt_count` | Core endpoint attempts consumed by this outcome, not application retries. |
| `transport_failure_metadata` | Optional bounded `transport-failure/1` facts for transport failures. |

Responses are matched by `correlation_id`. Deadletters identify the failed
attempt through `original_envelope.message_id`; connectors may also expose the
original correlation ID as observation metadata.

## Where A Deadletter Is Produced

The caller observes one deadletter surface, but the record can be produced at
different runtime boundaries:

| Producer | Example facts | What reaches the caller |
| --- | --- | --- |
| Caller-side runtime before remote ownership | No active snapshot, route miss, locality mismatch, outbound queue expiry, connection/TLS refusal, or remote reply timeout. | The caller runtime builds the deadletter from the original admitted envelope. |
| Selected peer runtime after the request frame arrives | Inbound envelope invalid, peer ingress queue rejected, peer runtime stopping, peer route/handler absent, or local handoff failed. | The peer builds a deadletter; the caller runtime may perform eligible endpoint failover, otherwise forwards that record to the caller surface. |
| No envelope exists yet | An inbound TLS handshake fails before a request frame can be decoded. | No deadletter can contain an original envelope. The receiving runtime records a typed ingress/transport event instead. |
| Business handler owns the request | The handler accepts or rejects the domain operation. | A business `RESPONSE`, not a deadletter. If no terminal result returns within budget, the caller observes timeout ambiguity. |

The current `Deadletter` schema has no `producer_runtime_id` field. Do not infer
physical origin from `reason` alone. Retain the caller-selected endpoint and
correlate `active_generation`, `resolved_host/port`, transport metadata, and
runtime monitor facts such as forwarded-deadletter counters. A forwarded peer
deadletter preserves the original envelope and peer-produced causal facts; the
caller runtime accounts the endpoint-attempt chain.

This is why “the peer had no target” and “the caller had no route” are not the
same incident. Both may surface a routing-shaped reason, but the selected
endpoint, active generation, and forwarded-deadletter evidence identify the
boundary that needs repair.

## Deadletter Reasons

The numeric values are stable across connectors and the core runtime.

| Value | Reason | Meaning and usual response |
| ---: | --- | --- |
| `0` | `UNSPECIFIED` | No useful category. Treat as an integration defect and retain the whole deadletter for diagnosis. |
| `1` | `NO_ACTIVE_SNAPSHOT` | No active route snapshot. Apply startup/control configuration. |
| `2` | `ROUTE_MISS` | Target is absent from the active snapshot. Fix target spelling or routing configuration. |
| `3` | `NO_RESPONSIBLE_HOST` | Route exists without a responsible endpoint. Repair the snapshot. |
| `4` | `QUEUE_REJECTED` | A bounded queue rejected work. Back off, shed load, or tune only from pressure evidence. |
| `5` | `LOCAL_HANDOFF_FAILED` | Local adapter could not accept the handoff. Inspect handler/adapter lifecycle and local pressure. |
| `6` | `DELIVERY_FAILED` | Generic internal delivery/setup failure where no narrower stable reason applies. Use the reason as category and `detail` only for diagnosis. |
| `7` | `REMOTE_TRANSPORT_FAILED` | Remote connectivity, exchange, protocol, pressure, or resource failure. Inspect structured transport metadata. |
| `8` | `RUNTIME_STOPPED` | Runtime stopped before delivery completed. Coordinate shutdown/drain and retry only under application policy. |
| `9` | `INVALID_ENVELOPE` | Encoded envelope or its semantic field combination is invalid. Fix the connector/producer. |
| `10` | `ENDPOINT_UNAVAILABLE` | Matching endpoints are drained or unavailable. Wait for a route update or fail fast. |
| `11` | `REMOTE_REPLY_TIMEOUT` | Remote request crossed the transport boundary but no terminal result arrived in budget. Delivery may be ambiguous; reconcile before retrying non-idempotent work. |
| `12` | `LOCALITY_MISMATCH` | `REQUIRE_LOCAL` or `REQUIRE_REMOTE` cannot be satisfied by current placement. Fix the hint or route. |
| `13` | `EXPIRED_BEFORE_DELIVERY` | An admitted envelope exhausted its budget before onward delivery. Reduce pressure or revise the budget. |
| `14` | `ONE_WAY_DROPPED_BY_POLICY` | Explicit overload policy shed an admitted one-way envelope to protect reply-capable work. Observe the drop counter and validate that loss is acceptable. |
| `15` | `REMOTE_TRANSPORT_SECURITY_FAILED` | TLS/mTLS configuration or secure-session establishment failed. Inspect the redacted structured security code. |

Deadletters are terminal for that submitted envelope. Runtime endpoint failover
inside one delivery attempt and an application retry are different operations.
Submitting again creates a new application attempt and should use a new
`message_id` while preserving business idempotency context.

## Failover Is Not User Retry

Think of one admitted request as one registered parcel with one tracking number
and one delivery deadline.

If the first post office is closed before it accepts the parcel, the delivery
network may take the same parcel to another eligible post office. That is Core
endpoint failover:

- it stays inside the same request lifecycle and preserves the same envelope
  identity;
- it consumes the original remaining monotonic deadline rather than starting a
  fresh timeout;
- it requires safe evidence that the previous endpoint did not take delivery
  ownership, such as endpoint-scoped `NOT_DELIVERED` transport evidence;
- a peer-side `QUEUE_REJECTED`, `LOCAL_HANDOFF_FAILED`, or `RUNTIME_STOPPED`
  deadletter is also eligible for another endpoint attempt while budget and an
  eligible endpoint remain;
- `endpoint_attempt_count` reports the post offices tried for this one parcel.

If the delivery network returns the parcel or the deadline ends, that request
has a terminal outcome. Only then may the sender create a new parcel submission
under application policy. That is user retry:

- it uses a new `message_id` and consumes a separate bounded application retry
  budget;
- it preserves the business idempotency identity when the logical operation is
  the same;
- it is owned by the application because only business code knows whether
  repeating the effect is safe.

Do not fail over after evidence that an endpoint may have accepted ownership.
`REMOTE_REPLY_TIMEOUT` is terminal and ambiguous, not permission to try the same
effect on another endpoint. A late or lost confirmation cannot prove that the
recipient did not act.

```text
one admitted Envelope
  endpoint A -> NOT_DELIVERED, budget remains
  endpoint B -> RESPONSE
  result: one request, two endpoint attempts, no user retry

terminal timeout or deadletter
  application reconciles business identity
  application may submit a new Envelope with a new message_id
  result: user retry, not endpoint failover
```

## TLS And mTLS Failures

Security failures have two different identity boundaries:

| Failure boundary | Result |
| --- | --- |
| Applying or reloading invalid local TLS/mTLS configuration | Synchronous security apply result; the previous active immutable security context remains unchanged. This is not an envelope deadletter. |
| Outbound secure-session failure for an admitted envelope | `REMOTE_TRANSPORT_SECURITY_FAILED` deadletter with bounded `transport-failure/1` metadata. No business bytes were delivered. |
| Inbound handshake failure before a request frame exists | No original envelope identity exists, so runtime cannot construct a deadletter. Runtime records an ingress event of kind `REMOTE_TRANSPORT_FAILED` with typed security facts instead. |

Current stable security codes are:

| Code | Meaning |
| --- | --- |
| `TLS_CONFIG_INVALID` | Local TLS profile cannot build a usable secure context. |
| `TLS_HANDSHAKE_FAILED` | Secure-session negotiation failed without a narrower certificate category. |
| `PEER_CERT_UNTRUSTED` | Peer certificate chain does not validate against configured trust roots. |
| `PEER_CERT_EXPIRED` | Peer certificate is outside its validity window; verify certificate rotation and host clock. |
| `PEER_IDENTITY_MISMATCH` | Certificate identity does not match the endpoint host being verified. |
| `CLIENT_CERT_REQUIRED` | mTLS peer did not provide an acceptable client identity. |

The metadata map uses these stable keys:

```text
schema=transport-failure/1
domain=SECURITY
phase=HANDSHAKE
code=<stable code above>
certainty=NOT_DELIVERED
scope=LOCAL_PROFILE|ENDPOINT|CONNECTION
```

Only `certainty=NOT_DELIVERED` plus `scope=ENDPOINT` permits automatic core
endpoint failover. A local profile problem is not repaired by trying another
endpoint. Raw TLS-library diagnostics, certificate contents, credential paths,
and private-key material must not appear in deadletters or logs.

Read [Runtime TLS And mTLS](tls-and-mtls.md) for configuration, rotation, and
connector examples.

## Timeout And Retry Rules

- A caller-side ask timeout means no reply or matched deadletter arrived before
  the caller wait budget. It does not prove that the handler never ran.
- `REMOTE_REPLY_TIMEOUT` is runtime evidence from the remote delivery path. It
  is a deadletter reason, not a business timeout response.
- Never blindly retry a non-idempotent command after an ambiguous timeout.
- Route miss and invalid envelope are configuration/producer defects; retries
  without a corresponding change only add load.
- Queue rejection and one-way shedding are pressure evidence; backoff and load
  shedding come before increasing bounded capacity.

Think of a timeout like a restaurant order whose pickup buzzer did not ring in
time. The kitchen may not have accepted the ticket, may still be cooking, may
have completed the meal while the buzzer failed, or may have sent a signal that
arrived too late. The caller's deadline proves only that no terminal answer was
observed; it does not reveal which kitchen state is true.

For a read/query, a short bounded user retry may be acceptable. For an
idempotent command, reuse the same business idempotency key so the handler can
return the recorded outcome instead of applying the effect twice. For a
non-idempotent command such as charging money, do not submit a second business
effect merely because the caller timed out. Query/reconcile the first operation
by business identity, wait for a late terminal outcome when the integration
exposes it, or enter an application-owned compensation/manual-review path.

Use this response order:

1. classify whether the observed fact is synchronous refusal, deadletter,
   business response, caller timeout, or `REMOTE_REPLY_TIMEOUT`;
2. confirm whether Core already performed safe endpoint failover by checking
   `endpoint_attempt_count` and monitor facts; do not count those endpoint
   attempts as user retries;
3. preserve original `message_id`, correlation, target, active generation,
   selected endpoint, attempt count, and the business idempotency key;
4. reconcile ambiguous work before retrying a non-idempotent operation;
5. if a user retry is safe, create a new envelope with a new `message_id`,
   preserve business identity, and consume a bounded application retry budget;
6. do not increase timeout or queue capacity until pressure/latency evidence
   identifies the actual slow boundary.
