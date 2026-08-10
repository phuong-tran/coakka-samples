# Zig Runtime Samples

Zig runtime samples use the pinned public source connector package
`2.1.0-source` over native runtime generation `2.1.0+60ddf70d`. The basic
sample covers lifecycle/control, raw
request/reply, and route-miss deadletter handling.

Run the basic sample:

```sh
bash run.sh runtime zig basic
```

Watch the Zig runtime walkthrough:

![CoAkka Runtime Zig walkthrough](../../docs/assets/coakka-runtime-zig.gif)

Full recording: [coakka-runtime-zig.mp4](../../docs/assets/coakka-runtime-zig.mp4)

## Continue Integrating

Use this lane's runnable sample as the source for package imports and basic
lifecycle names. Before generating connection strategy, TLS/mTLS, File Lane, or
Stream Lane code, follow [AI-Assisted Integration](../../docs/ai-assisted-integration.md).
It links the canonical feature guides, exact package catalog, and platform
evidence, and it defines when only workflow pseudocode is justified.

The current public package train includes File Lane. Stream Lane remains an
exact-source integration until a matching public artifact is promoted; do not
attach Stream Lane imports to this lane's current package coordinate.
