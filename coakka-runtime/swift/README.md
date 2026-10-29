# Swift runtime candidate sample

This private lane uses exactly `3.0.0` and bundled Core3.0.0+1522b4a9,
not the historical public package train. Follow the
[candidate guide](../../docs/coakka-runtime/previews/3.0.0/runtime-swift-candidate.md) for admitted local inputs,
standard SwiftPM, matching toolchain/deployment profiles and installed execution.

```sh
bash run.sh coakka-runtime swift basic
```

The example owns one handler, asks for text, observes a typed route miss and
closes explicitly on success or failure. Core owns accepted defaults, routing,
capabilities and causal outcomes. The connector owns Swift value/ARC/borrow
safety, not OS probes or an alternate response engine. Read the exact package
API for File/Stream; do not translate names from other languages by analogy.
