# Zig Runtime Samples

Zig runtime samples use the pinned public source connector package
`2.5.2-source` over native runtime generation
`2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a`. The basic
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

The current public package train includes File Lane and Stream Lane. Use the
exact connector names and lifecycle rules shipped by the `2.5.2-source`
package when integrating either lane.
