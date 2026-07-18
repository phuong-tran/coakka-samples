# CoAkka Runtime Client Samples

`coakka-runtime-client` is the CLI runtime client lane for CoAkka Runtime.
The current public command and archive names use `coakka-client`.

This lane is for native CLI workflows that show how an operator or developer
can drive a runtime path without building a web UI:

- print version and runtime build diagnostics
- run doctor checks against a packaged client
- submit request/reply calls to runtime targets
- run bounded shell-script verification
- verify Docker demo bundles without recording a video

It is not an inspect dashboard, a topology authority, or a business schema
registry. Topology, route ownership, delivery semantics, and capability truth
stay in CoAkka Runtime. Sample payloads stay owned by the sample workflow.

## Quick Check

Run the published CLI runtime client smoke:

```sh
bash run.sh runtime-client
bash run.sh runtime-client version
bash run.sh runtime-client doctor
```

The runner resolves the matching archive from a sibling `coakka-publish`
checkout when present, otherwise it falls back to the public raw artifact URL.
Every artifact is verified against `artifacts/public-artifacts.tsv` before it
is unpacked.

Run the zero-install Linux Docker demo bundle only when Docker live verification
is needed:

```sh
bash run.sh runtime-client docker-demo
```

That command resolves the published Docker demo bundle for the host architecture,
builds the tiny CLI and customer-service images from the staged artifacts, then
verifies `call`, `ask`, and `shell --script` request/reply round-trips.

## Current Public Release

The current public CLI runtime client release is:

```text
coakka-runtime-client product lane
coakka-client command and archive prefix
1.3.1+2215b0f release id
```

Published artifacts are resolved from `coakka-publish`:

```text
cli/releases/1.3.1+2215b0f/
  coakka-client-v2-1.3.1-macos-aarch64.tar.gz
  coakka-client-v2-1.3.1-linux-x86_64.tar.gz
  coakka-client-v2-1.3.1-linux-aarch64.tar.gz
  coakka-client-v2-1.3.1-windows-x86_64.tar.gz
  coakka-client-v2-1.3.1-windows-aarch64.tar.gz
```

The matching Docker demo release is:

```text
demo/coakka-client/releases/1.3.1+2215b0f/
```

## Demo Direction

The main video/demo path should run on macOS with a native CoAkka Runtime host
and the native `coakka-client` command. That makes the CLI behavior the center
of the proof instead of relying on a browser UI.

Docker remains useful as a verification lane for Linux bundles. Docker does not
need a recorded video unless a later release specifically needs it.

Future runnable samples in this directory should prefer this shape:

```text
native runtime host
  <- TCP frame profile
coakka-client call or ask
  -> runtime target
  -> reply, timeout, or deadletter
```

Keep the sample small enough that a user can see the CLI contract directly:
start host, run `version`, run `doctor`, submit one request/reply call, and stop
the host cleanly.
