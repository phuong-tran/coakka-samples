# Start With CoAkka Runtime

Choose a language in the [Runtime sample guide](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/README.md).
Run its smallest request/reply example before adding files, streams or remote peers.

## Using a published package

Use the [published sample snapshot](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime) and follow that snapshot's README,
runner and exact package version together. Download or check out the snapshot
before running it; do not copy a 2.x install command into current 3.0.0 source.
The [published package ledger](../common/current-packages.md) records available releases.

A basic example starts a host, registers one handler, sends a request, checks
its reply and closes the host. Its README gives prerequisites, the command
and expected output. Keep those steps together in the selected example.

## Evaluating 3.0.0

The current main-branch Runtime examples use the **unpublished 3.0.0 candidate**.
They require its matching local artifact and catalog. Begin with the
[candidate index](previews/README.md); a registry install is not available yet.
Android and Mojo are evaluation previews. Swift is a release package; only
Swift on Windows ARM has preview scope for this train.

## After the first reply

Read the [integration guide](guides/runtime-integration-guide.md) to move the
same lifecycle into your application. For default settings, monitor use and
examples, read [monitoring](operations/runtime-monitoring.md).
