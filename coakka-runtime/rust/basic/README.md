# Rust Runtime Basic

A standalone Cargo application consumes the exact frozen Rust 3.0.0 candidate
and native Core 3.0.0. The selected archive carries five native targets; this
basic runner remains qualified for Mac ARM64, Linux ARM64 and Windows x64. It does not run a packaged smoke binary or download an
older public archive. Read the [candidate guide](../../../docs/coakka-runtime/previews/3.0.0/runtime-rust-candidate.md)
and select explicit artifact, toolchain, offline cache and fresh external work
paths before running:

```sh
bash run.sh coakka-runtime rust basic
```

The app registers `samples.customer.store`, asks it to create Ada, then checks
the typed route-miss result for `samples.customer.missing`. Both asks retain
their two-second deadline. Core supplies effective settings; no queue/CPU/
connection/monitor tuning or native override is required in application code.

Expected facts for the frozen candidate:

```text
runtime=3.0.0 git=1522b4a9f30da1039ef8c7d198bbecfc749a2456 abi=1 cpu_policy=0 connection_mode=3 monitor=false delivery=2/2
response={"customer":"Ada"} deadletter=2 original=samples.customer.missing pending=0
CoAkka Rust sample ok
```

Those numbers are printed from Core snapshots, not a connector-owned default
table. Deadletter matching uses its typed variant, exact reason number and
original target; it does not classify diagnostic strings.

RAII retires the host on normal return, `?` and app unwind. The callback owns
no reference back to the host. Run the resulting installed executable without
Python, Cargo, build environment variables or source trees; move the complete
installed directory rather than the executable alone. Keep its exact native
image, manifest and legal files. This private sample is not a public release.
