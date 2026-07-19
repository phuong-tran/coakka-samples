# CoAkka Runtime Inspect Samples

`coakka-runtime-inspect` is the browser runtime explorer for CoAkka Runtime.
It is the visual sibling of `coakka-client`: both point at a caller-supplied
runtime address, both use runtime-owned truth, and both keep route try behavior
aligned with `call` / `ask`.

This sample lane is currently wired for local/native verification from the
sibling `coakkaCoreNativeDev` repository. The public inspect archive is not yet
published in `coakka-publish`, so this lane does not pretend there is a direct
download path yet.

Detailed docs live under [docs/](docs/):

- [Introduction](docs/introduction.md)
- [Usage Guide](docs/usage.md)
- [Technical Notes](docs/technical-notes.md)

## Quick Check

Run the public sample wiring check:

```sh
bash run.sh runtime-inspect
bash run.sh runtime-inspect check
```

If a sibling native build is available, smoke the real inspect binary:

```sh
bash run.sh runtime-inspect local-smoke
```

To start the browser UI from that local binary:

```sh
bash run.sh runtime-inspect serve
```

By default, the sample expects:

```text
../coakkaCoreNativeDev/build-v2/coakka-runtime-inspect
```

Override it with:

```sh
COAKKA_RUNTIME_INSPECT_BIN=/path/to/coakka-runtime-inspect \
  bash run.sh runtime-inspect local-smoke
```

## Boundary

`coakka-runtime-inspect` is not an admin dashboard, schema registry, service
mesh console, or topology authority. Runtime core owns topology, health,
pressure, route generation, and delivery outcomes. Inspect renders those facts
and provides a browser route-try form that can copy an equivalent
`coakka-client` command.

V1 `serve --connect host:port` configures the route-try request path. Remote
read/observe snapshots are still a future runtime surface; current snapshots
are explicitly labelled `local-linked-runtime`.
