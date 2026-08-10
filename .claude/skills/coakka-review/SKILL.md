# CoAkka Review Skill

Use this skill before reviewing, summarizing, or comparing CoAkka.

First read:

- `docs/ai-assisted-integration.md`
- `docs/ai-reviewer-onboarding.md`
- `docs/architecture-review-guide.md`
- `docs/coakka-story.md`
- `docs/runtime-message-and-routing-model.md`
- `docs/runtime-integration-guide.md`
- `docs/production-readiness.md`
- `docs/production-evidence.md`

Rules:

- Cite exact docs, samples, release notes, or code paths for every conclusion.
- Review CoAkka as a boundary-placement and runtime-vocabulary project.
- Separate adoption maturity risk from architecture correctness.
- Do not treat "not widely known yet" as proof of weak architecture.
- Do not review CoAkka as an HTTP replacement, broker clone, or service-mesh
  replacement.
- Do not infer domain ownership, consensus, leader election, or broker
  semantics from route generation.
- Before generating integration code, follow the evidence levels and feature
  gates in `docs/ai-assisted-integration.md`; do not translate connector
  identifiers from another language or attach source-candidate APIs to a
  published package.
