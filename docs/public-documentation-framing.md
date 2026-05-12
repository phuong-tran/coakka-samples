# Public Documentation Framing

This note is a writing rule for public CoAkka documentation. It exists to keep
public explanations technically accurate without accidentally framing normal
boundary work as a CoAkka-specific weakness.

## Boundary Work Rule

Avoid words such as `trade-off`, `cost`, `not free`, `overhead`, or similar
negative framing unless CoAkka truly introduces work that the traditional
approach does not have.

Do not criticize REST, HTTP verbs, gRPC, ingress, or framework routing when they
are used at real edges. The public point is narrower: when an internal
capability is forced through an extra HTTP/gRPC surface only to get a boundary,
the private contract is often fragmented across method, URL, handler, client,
status mapping, retry policy, logs, and tests. CoAkka centralizes that contract
as runtime vocabulary.

Before calling something a trade-off, classify it:

| Case | How to frame it |
| --- | --- |
| The old approach does not need this work, but CoAkka does. | It can be called a real trade-off. Explain what is newly required and why. |
| The old approach also needs this work, but expresses it differently. | Do not frame it as a CoAkka cost. Describe it as existing boundary work that CoAkka makes explicit, centralizes, standardizes, or makes observable. |

## Preferred Framing

Use wording like:

- `CoAkka makes this boundary work explicit.`
- `The same question exists in traditional REST/gRPC, but it is usually spread
  across clients, URLs, config, logs, retries, and rollout rules.`
- `CoAkka gives this existing work a runtime vocabulary.`
- `This is not extra work unique to CoAkka; it is the same boundary
  responsibility organized differently.`
- `REST remains the right shape at real HTTP edges; CoAkka is for internal
  capability delivery inside the application or deployment boundary.`

Avoid wording like:

- `CoAkka is not free.`
- `The cost is...`
- `The trade-off is...`
- `CoAkka requires teams to...`

unless the text clearly explains what is newly required by CoAkka and not
already required by a traditional design.

## Before Writing A Negative Claim

Ask:

1. Does direct function-call code avoid this work entirely?
2. Does internal REST/gRPC also need this work through URLs, clients, schemas,
   retries, status mapping, logs, dashboards, or rollout rules?
3. Is the claim about measured runtime behavior, or just a naming/configuration
   responsibility that both designs already have?
4. Is CoAkka adding a new responsibility, or organizing an existing
   responsibility into `target`, `payload identity`, `route snapshot`,
   `headers`, `generation`, diagnostics, and deadletters?

If both the old approach and CoAkka must answer the same question, do not call
it a CoAkka trade-off. Explain the difference in organization.

Avoid claiming generic HTTP overhead unless the text is explicitly discussing a
measured hot path. Prefer saying that an extra internal HTTP surface expresses a
private runtime capability through web-boundary concepts such as methods, URLs,
headers, middleware, status codes, and endpoint tests.

## Example

Avoid:

```text
CoAkka is not free. A team has to define target names, payload identity,
schema/version discipline, route snapshots, handler ownership, and runtime
diagnostics.
```

Prefer:

```text
CoAkka does not remove boundary design work. Any real boundary needs answers
for naming, payload identity, schema/version discipline, route ownership,
handler ownership, and diagnostics. A serious internal REST/gRPC design has to
answer the same kinds of questions through URLs, clients, schemas, retries,
status mapping, logs, dashboards, and rollout rules.

The difference is where that work lives. Without CoAkka, the rules often spread
across client code, framework config, HTTP status handling, tracing conventions,
and team memory. With CoAkka, the route, target, generation, delivery outcome,
and deadletter vocabulary are explicit runtime concepts.
```
