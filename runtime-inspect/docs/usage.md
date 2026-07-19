# CoAkka Runtime Inspect Usage Guide

Run the lane check first:

```sh
bash run.sh runtime-inspect check
```

That command verifies the sample docs and reports whether a local inspect
binary is available. On macOS ARM64, Linux x86_64/ARM64, or Windows
x86_64/ARM64, it also resolves the published inspect archive from
`coakka-publish` and verifies its checksum against
`artifacts/public-artifacts.tsv`.

## Published Archive Smoke

Run the published macOS ARM64, Linux x86_64/ARM64, or Windows x86_64/ARM64
archive smoke:

```sh
bash run.sh runtime-inspect published-smoke
```

The smoke extracts the published archive, runs `version`, `doctor`,
`help serve`, and reads one `local-linked-runtime` snapshot with a diagnostic
route.

All five current inspect native platforms are published.

## Docker Smoke

Build and smoke a local Docker image from the published Linux inspect archive:

```sh
bash run.sh runtime-inspect docker-smoke
```

The image runs `version`, `doctor`, `help serve`, and a
`local-linked-runtime` snapshot check inside the container.

## Local Native Smoke

Build the inspect binary in the sibling core repository:

```sh
cd ../coakkaCoreNativeDev
cmake --build build-v2 --target coakka_v2_coakka_runtime_inspect
cd ../coakka-samples
```

Then run:

```sh
bash run.sh runtime-inspect local-smoke
```

The smoke runs:

- `coakka-runtime-inspect version`
- `coakka-runtime-inspect doctor`
- `coakka-runtime-inspect help serve`
- `coakka-runtime-inspect snapshot --output json --local-route inspect.echo=127.0.0.1:19001`

It checks that the snapshot is labelled `local-linked-runtime` and that the
runtime-owned route catalog includes `inspect.echo`.

## Start The Browser UI

Start inspect from the local native binary:

```sh
bash run.sh runtime-inspect serve
```

The default UI address is:

```text
http://127.0.0.1:18080
```

Override host or port:

```sh
COAKKA_RUNTIME_INSPECT_HOST=0.0.0.0 \
COAKKA_RUNTIME_INSPECT_PORT=18081 \
  bash run.sh runtime-inspect serve
```

Pass extra inspect serve flags after `serve`:

```sh
bash run.sh runtime-inspect serve --connect 127.0.0.1:19091
```

`--connect` configures the remote runtime request path for the Try Route panel.
It does not make `/api/snapshot` a remote runtime reader in this release.

## Start Through Docker

Run inspect through the local Docker image:

```sh
bash run.sh runtime-inspect docker-serve
```

The default host URL is still:

```text
http://127.0.0.1:18080
```

Override the host port:

```sh
COAKKA_RUNTIME_INSPECT_DOCKER_PORT=18081 \
  bash run.sh runtime-inspect docker-serve
```

Pass route-try flags after `docker-serve`:

```sh
bash run.sh runtime-inspect docker-serve --connect host.docker.internal:19091
```

## Binary Override

Use a non-default binary path:

```sh
COAKKA_RUNTIME_INSPECT_BIN=/path/to/coakka-runtime-inspect \
  bash run.sh runtime-inspect local-smoke
```
