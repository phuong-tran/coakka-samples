# Rust Runtime Basic

This standalone Cargo app consumes the exact Rust 3.2.1 source archive with
Core 3.2. The basic runner covers macOS ARM64, Linux ARM64 and Windows x64.
Choose the [3.2.1 archive and toolchain](../../../docs/coakka-runtime/previews/3.2.1/runtime-rust-candidate.md),
then run from the Samples root:

```sh
bash run.sh coakka-runtime rust basic
```

The app registers `samples.customer.store`, asks it to create Ada, then checks
the typed route-miss result for `samples.customer.missing`. Both asks retain
their two-second deadline. Core supplies effective settings; no queue/CPU/
connection/monitor tuning or native override is required in application code.

Expected output includes the running Core identity and typed results:

```text
runtime=3.2 git=f9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45 abi=1 cpu_policy=0 connection_mode=3 monitor=false delivery=2/2
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
image, manifest and legal files.
