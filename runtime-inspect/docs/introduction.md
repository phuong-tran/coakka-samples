# CoAkka Runtime Inspect Introduction

`coakka-runtime-inspect` makes CoAkka Runtime visible in a browser without
turning runtime core into a web server or dashboard backend.

It is close to `coakka-client`:

- both are runtime-facing clients
- both use a caller-supplied runtime address
- both preserve the same `call` / `ask` request metadata shape
- both report explicit replies, timeouts, deadletters, and configuration errors

It differs in presentation:

- `coakka-client` is script-first terminal tooling
- `coakka-runtime-inspect` is read-first visual exploration
- inspect shows runtime identity, route catalog, endpoint topology, health,
  pressure, recent events, and a route-try form
- inspect can copy an equivalent `coakka-client` command from the browser form

The goal is not to clone Swagger for HTTP. The goal is to show runtime-owned
targets, routes, pressure, and outcomes directly.

## Current Sample Status

The inspect binary is implemented in the native v2 runtime repository. Public
native inspect archives are not published yet, so this sample lane starts as a
local/native verification path.

Use `check` for public sample wiring and docs. Use `local-smoke` or `serve`
only when a local `coakka-runtime-inspect` binary is available.
