# Electron Logger Samples

Electron samples consume the published `coakka-logger-electron` tarball from
`coakka-publish`.

Current samples:

- `basic`: install the published tarball into a temporary Electron app, send
  one renderer log intent through preload/IPC, drain it in the main process,
  and print counters

Run:

```sh
bash logger/electron/basic/run.sh
```
