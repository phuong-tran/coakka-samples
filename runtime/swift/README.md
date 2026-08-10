# Swift Runtime Samples

Swift runtime samples consume
`github.com/phuong-tran/coakka-runtime-swift@2.1.1`, built against native
runtime generation `2.1.0+60ddf70d`. The package contains native payloads for
Linux ARM64/x86-64, macOS ARM64, and Windows ARM64/x86-64; matching-host Swift
execution evidence is tracked separately from package contents.

## Run

```sh
bash run.sh runtime swift basic
```

The [basic sample](basic/README.md) resolves the public SwiftPM tag, starts one
runtime owner, registers a local target, performs request/reply, observes
counters, and closes the runtime.

## Continue Integrating

Use the runnable sample as the source for package imports and basic lifecycle
names. Before generating connection strategy, TLS/mTLS, File Lane, or Stream
Lane code, follow
[AI-Assisted Integration](../../docs/ai-assisted-integration.md). It links the
canonical feature guides, exact package catalog, and platform evidence, and it
defines when only workflow pseudocode is justified.

The current SwiftPM package includes File Lane. Stream Lane remains an
exact-source integration until a matching public artifact is promoted; do not
attach Stream Lane imports to SwiftPM `2.1.1`.
