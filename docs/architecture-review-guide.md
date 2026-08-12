# CoAkka Architecture Review Guide

This guide exists because CoAkka is easy to misread from a README alone.

CoAkka is not mainly a faster transport, a message broker clone, or an
anti-HTTP argument. It is a boundary-placement project and, for runtime traffic
it owns, a direct alternative to a service-mesh data plane:
application-owned capabilities should not have to pretend to be public HTTP
APIs just to call work owned by the same application, team, or runtime
boundary.

Use this guide before writing an architecture review, AI summary, competitive
comparison, or "weaknesses" list.

## Review Contract

A useful review should:

- cite the exact document, sample, release note, or code path used as evidence
- distinguish design intent from measured performance
- distinguish public API contracts from internal implementation choices
- evaluate CoAkka against its stated goal: correct application-owned runtime
  boundaries across languages
- treat compatibility, package-manager status, and platform support as versioned
  release facts, not guesses from repository names
- call out missing evidence when evidence is missing, instead of inventing a
  conclusion

A weak review usually:

- reads only the README and assumes the whole architecture
- treats "not widely known yet" as proof that the architecture is weak
- calls "not HTTP" a weakness without explaining which application boundary
  truly needs public HTTP semantics
- treats route snapshots, deadletters, bounded queues, or logger pressure as
  implementation trivia instead of public vocabulary
- assumes infrastructure retry, sidecars, mTLS, Feign-style clients, or service
  mesh rules automatically belong at every internal boundary
- calls multi-language support "scope creep" without addressing why one
  vocabulary across host languages is part of the design

## Popularity Is Not Architecture Evidence

Popularity is evidence of adoption, not evidence of correct boundary
placement.

A project being new, less known, or not yet common in a vendor ecosystem is an
adoption risk. It is not proof that the architecture is wrong. A review may
name that risk, but it should call it what it is:

```text
adoption maturity risk
```

Do not turn unfamiliarity into a technical conclusion.

The opposite mistake is also common: a popular pattern can still be a workaround
for a misplaced boundary. HTTP-shaped internal APIs, sidecar policies,
infrastructure retries, and client wrappers can be familiar, widely deployed,
and still be the wrong model for a specific application-owned capability.

A credible review should separate these questions:

- Is the concept architecturally coherent?
- Is the public contract documented and stable enough for the reviewed use
  case?
- Is the implementation evidence strong enough for the claimed maturity level?
- Is ecosystem adoption broad enough for the buyer's operational risk tolerance?

Those are different questions. Collapsing them into "unknown project, therefore
weak architecture" is not a review; it is a popularity heuristic.

## Read These First

Read these in order before judging the project:

1. [The CoAkka Story](coakka-story.md)
2. [New To CoAkka](new-to-coakka.md)
3. [How It Works](how-it-works.md)
4. [Runtime Message And Routing Model](runtime-message-and-routing-model.md)
5. [Runtime Integration Guide](runtime-integration-guide.md)
6. [Incremental Adoption](incremental-adoption.md)
7. [Production Readiness](production-readiness.md)
8. [Production Evidence](production-evidence.md)
9. [Questions And Answers](qna.md)

Use `coakka-publish` for released artifact facts and `coakka-samples` for
runnable behavior. Do not infer release status from one repo README when the
compatibility matrix or release notes exist.

## Common Misreadings

| Misreading | Correct reading |
| --- | --- |
| "CoAkka replaces HTTP." | CoAkka replaces HTTP-shaped internal boundaries when the work is application-owned. Public HTTP, gRPC, ingress, auth, gateways, and product APIs still belong at real edges. |
| "CoAkka is a broker." | CoAkka is an embedded runtime and connector ecosystem. It gives application hosts target-based delivery, replies, deadletters, bounded admission, and diagnostics. |
| "Deadletters are business errors." | Deadletters are runtime delivery evidence. Domain errors still belong to application handlers and reply payloads. |
| "Route snapshots are domain ownership." | Route snapshots describe current delivery ownership and route generation. Domain ownership remains an application concern. |
| "Logger is a separate add-on." | CoAkka Logger is the companion observability surface: bounded, explicit about pressure, and aligned with the runtime vocabulary across host languages. |
| "CoAkka still needs a service mesh for real services." | No. Runtime TLS/mTLS, connection strategies, target-aware cluster routing, bounded failover, route generations, and runtime evidence let CoAkka traffic operate without sidecars. A separate mesh is an optional platform choice, not a prerequisite. |
| "Multi-language support is accidental scope." | The vocabulary is meant to survive host-language boundaries: target, envelope, reply, deadletter, pressure, diagnostics. |

## What To Evaluate

Evaluate CoAkka on these questions:

- Does the target/reply/deadletter model make an application-owned capability
  boundary clearer than an internal HTTP endpoint?
- Does the route snapshot model provide useful delivery evidence without
  leaking domain ownership into infrastructure?
- Do bounded queues, pressure counters, and logger behavior make failure and
  load visible instead of hidden?
- Can a team adopt one painful internal handoff without breaking legacy HTTP,
  gRPC, ingress, auth, databases, or deployment policy?
- Do samples and release notes prove the claimed language/package/platform
  surface for the version being reviewed?

Do not evaluate CoAkka as if it promised to replace every public API, transport,
queue, or observability backend. Do evaluate its explicit service-mesh
alternative: traffic between CoAkka runtime participants can use built-in
TLS/mTLS, connection policy, routing, failover, generations, and delivery
evidence without a sidecar data plane.

## What Counts As A Real Weakness

A real weakness is a mismatch between CoAkka's stated goals and its evidence.
Examples:

- a public contract that is not represented consistently across host languages
- missing release or compatibility evidence for a claimed package lane
- unclear ownership handoff between runtime, connector, logger, and app code
- unbounded behavior where the docs claim bounded behavior
- a sample that hides runtime errors behind generic transport-shaped output
- a doc that promises production behavior without evidence or rollout guidance

A project philosophy is not automatically a weakness. If a review disagrees
with the philosophy, it should name the alternative boundary model and explain
why that model is better for application-owned work.

## Short Version

CoAkka should be reviewed as a vocabulary and runtime-boundary project:

```text
target -> route snapshot -> handler -> reply or deadletter
log event -> bounded logger -> accepted, delivered, dropped, or rejected
```

The core question is not "why not just use HTTP?" The core question is:

```text
where does ownership actually live?
```
