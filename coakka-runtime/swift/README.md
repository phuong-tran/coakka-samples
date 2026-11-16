# Swift Runtime Samples

Swift 3.2.1 is [released on GitHub](https://github.com/phuong-tran/coakka-runtime-swift/releases/tag/v3.2.1) with bundled Core 3.2.
Start with [the ordinary remote SwiftPM app](registry-basic/README.md).
Windows ARM64 alone is preview; the Swift package is a published release.

## Historical 3.0.0 local-package qualification

This workflow is retained for reproducing the 3.0.0 qualification. For a new
SwiftPM install, use the first-run guide above. To reproduce 3.0.0, follow the
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
