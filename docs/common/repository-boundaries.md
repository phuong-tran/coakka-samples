# Repository Boundaries

CoAkka keeps runnable examples and released artifacts in separate public
repositories. Neither repository tries to be every CoAkka workspace at once.

## Repositories

| Repository | Role | What to look for |
| --- | --- | --- |
| `coakka-samples` | Runnable public examples that consume published artifacts. | Sample code, container flows, framework scenarios, docs, and smoke workflows. |
| [`coakka-publish`](https://github.com/phuong-tran/coakka-publish) | Public artifact distribution surface. | Pinned packages, native archives, source packages, release notes, manifests, and checksums. |
| Runtime and connector implementation workspaces | Artifact producers consumed through `coakka-publish`. | Source used to build the public artifacts. Public samples should not depend on a local implementation checkout. |

The normal public reader starts in `coakka-samples`. The samples resolve
artifacts from a sibling `coakka-publish` checkout when present, or from the
public raw artifact URL when a local checkout is absent.

Use `coakka-samples` to run examples. Use
[`coakka-publish`](https://github.com/phuong-tran/coakka-publish) to download
released binaries, package archives, release notes, manifests, and checksums.

## Product Naming Boundary

`CoAkka` is the ecosystem and brand prefix. Public sample docs should name the
specific product surface when the distinction matters:

| Name | Boundary |
| --- | --- |
| `CoAkka Runtime` | Runtime product family and runtime delivery model. |
| `coakka-runtime-core` | Native runtime engine and C ABI surface. |
| `coakka-runtime-connector` | Host-language and framework connector packages. |
| `coakka-runtime-client` | CLI runtime client. The published command and archive names use `coakka-client`. |
| `coakka-logger` | Bounded logger product surface. |

For the full wording rule, read
[CoAkka Ecosystem Naming](coakka-ecosystem-naming.md).

## What coakka-samples Owns

- sample entrypoints and docs
- container flows and compose files
- framework adapter usage examples
- language-specific basic, deadletter, hot-reload, pressure, and logger samples
- artifact pin verification against the public manifest
- public smoke workflows

## What coakka-samples Does Not Own

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

The `coakka-runtime-client` lane may drive runtime calls and print runtime
diagnostics, but it must not become an inspect/dashboard product or a business
schema registry. Sample payload schemas belong to the sample that owns the
workflow, not to runtime core or the CLI client.

Transport and package implementation can evolve behind the published artifact
surface. Sample call-sites should stay boring:

```text
ask target -> reply, timeout, or deadletter
```

## Public Wording Rule

Public sample docs should describe behavior, artifact generation, and runtime
vocabulary. Avoid turning implementation choices into the public contract unless
the sample is specifically about a public artifact or public ABI.
