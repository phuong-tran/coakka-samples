# Logger Samples

These samples demonstrate the CoAkka logger as a bounded native logging core
consumed through host-language connectors.

Start with JVM:

```sh
bash run.sh logger basic
```

Check local toolchains and artifact source:

```sh
bash run.sh doctor
```

Run all logger samples:

```sh
bash run.sh logger
```

From any leaf sample directory, run:

```sh
bash run.sh
```

If a sibling `../coakka-publish-public` checkout is not present, the samples download
the required artifacts from the public `coakka-publish` repository.

The basic samples print:

- native logger ABI/version/git commit
- one emitted and manually drained record
- basic emitted/delivered/dropped counters

The pressure samples fill a queue with capacity `2`, verify later writes are
rejected with queue pressure, then drain the accepted records and check dropped
counters.

The point is not to replace every language logging framework. The point is to
show one small, predictable logging contract that can be carried across language
ports.

The logger is system-facing, not just developer-console-facing. macOS is useful
for local development and first-run checks, but production-like validation
should happen on Linux where service supervision, native loading, filesystem
behavior, queue pressure, and deployment packaging match the target system more
closely.

Current samples:

| Language | Sample | Artifact |
| --- | --- | --- |
| JVM | `jvm/basic`, `jvm/pressure` | published JVM logger jar |
| Python | `python/basic`, `python/pressure` | published Python wheel |
| Node.js | `node/basic`, `node/pressure` | published Node package |
| Go | `go/basic`, `go/pressure` | published Go source package |
| Native C/C++ | `native/basic`, `native/pressure` | published native C/C++ archive |
