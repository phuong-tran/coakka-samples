# Shared Native Artifact Sample

This directory owns the reusable C11 and fixture mechanics for the released
artifact publisher samples. It is sample code, not a new product library and
does not add a public ABI.

`service_a.c` selects one concrete addon header at compile time. The lifecycle
in `service_a_lifecycle.inc` therefore calls the exact typed C functions; it
does not erase handles or cast incompatible function pointers. `service_b.c`
uses only Runtime File Lane and never links an addon.

`run-addon.sh` resolves exact public archives, verifies them through the sample
artifact resolver, compiles with strict warnings, starts a bounded local
fixture, and requires independent sender/publisher and receiver terminal
success. Per-addon entrypoints live under
`runtime-addons/artifact-publisher-<name>/native/run.sh`.

See [SYSTEMS_AUDIT.md](SYSTEMS_AUDIT.md) for ownership, bounds, failure rules,
evidence, and limitations.
