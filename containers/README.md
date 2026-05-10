# Container Samples

Container samples show the runtime boundary without requiring every host
language toolchain on the user's machine.

Available samples:

```sh
bash run.sh containers node-python
```

Direct runtime equivalents:

```sh
docker compose -f containers/node-python/compose.yaml up
podman compose -f containers/node-python/compose.yaml up
podman-compose -f containers/node-python/compose.yaml up
```

The first sample is intentionally small: a Node.js web process sends customer
commands through the CoAkka runtime to a Python store process. The Node UI is
the browser edge; the Python UI is a read-only view of store state changed by
runtime messages.
