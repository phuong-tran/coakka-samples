# CoAkka Runtime Inspect Usage Guide

Run the lane check first:

```sh
bash run.sh runtime-inspect check
```

That command verifies the sample docs and reports whether a local inspect
binary is available.

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

## Binary Override

Use a non-default binary path:

```sh
COAKKA_RUNTIME_INSPECT_BIN=/path/to/coakka-runtime-inspect \
  bash run.sh runtime-inspect local-smoke
```
