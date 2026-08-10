# Envelope And Deadletter Map

This page is the exact public lookup for `Envelope`, one-way delivery, and
`Deadletter`. Use the broader
[Runtime Message And Routing Model](runtime-message-and-routing-model.md) for
the end-to-end mental model.

## Read The Outcome Boundary First

A send has three distinct outcome layers. Do not collapse them into one generic
error.

| Boundary | What happened | Caller-visible result |
| --- | --- | --- |
| Before admission | Runtime is not started or is closed, arguments cannot be copied, or the bounded ingress queue cannot accept work. | Synchronous connector/native submit failure. No automatic deadletter exists because runtime did not admit the envelope. |
| After admission | Routing, queue handoff, locality policy, remote transport, or runtime lifecycle prevents completion. | A `Deadletter` on the runtime deadletter lane; an ask connector normally matches it to the pending caller. |
| After business ownership | The target handler ran and returned a domain result or error. | `Envelope(kind=RESPONSE)` with business `status`, `error_code`, and `error_message`; this is not a deadletter. |

The practical rule is:

```text
submit returned non-OK -> not admitted; handle the synchronous error
submit returned OK     -> later delivery failure is a deadletter
handler returned error -> business RESPONSE, not a deadletter
```

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

## `one_way` And Message Kind

`one_way` is a delivery expectation on the envelope. It is not a delivery hint,
an overload policy, or permission to hide failures.

| Shape | `kind` | `one_way` | Correlation/reply expectation |
| --- | --- | --- | --- |
| Ask/query/command with a reply | `REQUEST` | `false` | Requires `correlation_id`; connector tracks a response, matched deadletter, or caller timeout. |
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
