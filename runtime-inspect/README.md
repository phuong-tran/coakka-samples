# CoAkka Runtime Inspect Samples

`coakka-runtime-inspect` is the browser runtime explorer for CoAkka Runtime.
It is the visual sibling of `coakka-client`: both point at a caller-supplied
runtime address, both use runtime-owned truth, and both keep route try behavior
aligned with `call` / `ask`.

This sample lane can verify the published macOS ARM64, Linux x86_64, Linux
ARM64, and Windows x86_64 inspect archives from `coakka-publish`. Windows
ARM64 remains a local/native verification lane until its archive is cut.

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

On macOS ARM64, Linux x86_64/ARM64, or Windows x86_64, run the published
archive smoke:

```sh
bash run.sh runtime-inspect published-smoke
```

If a sibling native build is available, smoke the real inspect binary:

```sh
bash run.sh runtime-inspect local-smoke
```

To start the browser UI from that local binary:

```sh
bash run.sh runtime-inspect serve
```

For local/native smoke, the sample expects:

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

## Published Release

The current published inspect release is:

```text
coakka-runtime-inspect native UI
1.3.1+e664986 release id for macOS ARM64, Linux x86_64, Linux ARM64
1.3.1+6c63864 release id for Windows x86_64
```

Direct download:

[coakka-runtime-inspect-v2-1.3.1-linux-aarch64.tar.gz](https://github.com/phuong-tran/coakka-publish/releases/download/coakka-public-artifacts-v1.3.1/coakka-runtime-inspect-v2-1.3.1-linux-aarch64.tar.gz)

[coakka-runtime-inspect-v2-1.3.1-linux-x86_64.tar.gz](https://github.com/phuong-tran/coakka-publish/releases/download/coakka-public-artifacts-v1.3.1/coakka-runtime-inspect-v2-1.3.1-linux-x86_64.tar.gz)

[coakka-runtime-inspect-v2-1.3.1-macos-aarch64.tar.gz](https://github.com/phuong-tran/coakka-publish/releases/download/coakka-public-artifacts-v1.3.1/coakka-runtime-inspect-v2-1.3.1-macos-aarch64.tar.gz)

[coakka-runtime-inspect-v2-1.3.1-windows-x86_64.tar.gz](https://github.com/phuong-tran/coakka-publish/releases/download/coakka-public-artifacts-v1.3.1/coakka-runtime-inspect-v2-1.3.1-windows-x86_64.tar.gz)

Full release page and manifest:
[CoAkka Public Artifacts 1.3.1](https://github.com/phuong-tran/coakka-publish/releases/tag/coakka-public-artifacts-v1.3.1),
[public-artifacts.tsv](https://github.com/phuong-tran/coakka-publish/releases/download/coakka-public-artifacts-v1.3.1/public-artifacts.tsv)
