# CoAkka Ecosystem Naming

This repository treats `CoAkka` as the ecosystem and brand prefix. Public docs
should avoid using `coakka` alone as a concrete package identity when a more
specific product name is available.

## Product Names

| Name | Meaning | Sample responsibility |
| --- | --- | --- |
| `CoAkka` | The ecosystem and brand family. | Use for the whole sample repository, project identity, and cross-product docs. |
| `CoAkka Runtime` | The runtime product family. | Use when explaining runtime delivery, targets, route snapshots, replies, deadletters, and diagnostics. |
| `coakka-runtime-core` | The native runtime engine and C ABI surface. | Consume only through published artifacts; do not place sink, dashboard, or business schema semantics here. |
| `coakka-runtime-connector` | Host-language connector packages that bind app-host code to the runtime core. | Show language/framework integration and app-owned handlers. |
| `coakka-runtime-client` | The CLI runtime client used to drive and diagnose runtime paths. | Show local/native CLI workflows, Docker verification smokes, and scripted request/reply verification. |
| `coakka-logger` | The bounded logger product surface. | Show logger behavior separately from runtime routing unless a sample intentionally combines both. |

## Repository Path Names

Existing sample paths are stable runner categories, not final product package
names:

| Path | Current meaning |
| --- | --- |
| `runtime/` | Runtime connector and runtime scenario samples. |
| `runtime-client/` | CLI runtime client sample lane. |
| `logger/` | Logger product samples. |
| `containers/` | Containerized public sample flows. |

Do not rename `runtime/` or `logger/` paths casually. They are referenced by
sample commands, docs, and CI. A path migration should be a separate slice that
updates runner commands, workflows, and user-facing docs together.

## Runtime Client Naming

The product lane is `coakka-runtime-client`. The published binary and
archive names still use `coakka-client` because that is the established command
and release artifact name.

When documenting the CLI, use this shape:

```text
coakka-runtime-client is the CLI runtime client.
The published command is coakka-client.
The published runtime-client release is 1.3.1+2215b0f.
```

The CLI runtime client is not the dashboard, inspect surface, or a business
schema registry. It may print runtime diagnostics and call runtime targets, but
topology truth, capability ownership, and message delivery semantics stay owned
by CoAkka Runtime.

## Public Wording Rules

- Use `CoAkka` for the ecosystem.
- Use `CoAkka Runtime` for runtime behavior and the runtime product family.
- Use `coakka-runtime-core` for native engine or C ABI packaging.
- Use `coakka-runtime-connector` for language and framework connectors.
- Use `coakka-runtime-client` for CLI-driven runtime workflows.
- Use `coakka-logger` for logger samples and logger packages.
- Avoid calling every package simply `CoAkka`; that hides which boundary owns
  behavior.
