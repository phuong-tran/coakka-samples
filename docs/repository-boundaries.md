# Repository Boundaries

This repository is the public runnable sample surface. It intentionally does
not try to be every CoAkka repository at once.

## Repositories

| Repository | Role | What to look for |
| --- | --- | --- |
| `coakka-samples` | Runnable public examples that consume published artifacts. | Sample code, container demos, framework scenarios, docs, and smoke workflows. |
| `coakka-publish` | Public artifact distribution surface. | Pinned packages, native archives, source packages, release notes, manifests, and checksums. |
| Runtime and connector implementation workspaces | Artifact producers consumed through `coakka-publish`. | Source used to build the public artifacts. Public samples should not depend on a local implementation checkout. |

The normal public reader starts here, in `coakka-samples`. The samples resolve
artifacts from a sibling `coakka-publish-public` checkout when present, or from
the public raw artifact URL when a local checkout is absent.

## What This Repo Owns

- sample entrypoints and docs
- container demos and compose files
- framework adapter usage examples
- language-specific basic, deadletter, hot-reload, pressure, and logger samples
- artifact pin verification against the public manifest
- public smoke workflows

## What This Repo Does Not Own

- native runtime implementation
- package publishing pipeline
- service-discovery policy
- deployment control plane
- production capacity claims
- private operator runbooks for a specific deployment

## Artifact Boundary

Samples should consume the public artifact surface in the same way a user
would. That means:

- Maven samples resolve from the public Maven layout.
- Python, Node.js, Go, C#, Rust, native C/C++, Mojo, and Zig samples resolve
  pinned public packages or archives.
- Every resolved artifact is checked against
  `artifacts/public-artifacts.tsv`.
- Samples should not require a local implementation checkout to run.

## Runtime Boundary

CoAkka's runtime contract is expressed through targets, envelopes, route
snapshots, replies, deadletters, timeouts, and stats. The sample code should
show those concepts without exposing transport implementation details as public
application semantics.

Transport and package implementation can evolve behind the published artifact
surface. Sample call-sites should stay boring:

```text
ask target -> reply, timeout, or deadletter
```

## Public Wording Rule

Public sample docs should describe behavior, artifact generation, and runtime
vocabulary. Avoid turning implementation choices into the public contract unless
the sample is specifically about a public artifact or public ABI.
