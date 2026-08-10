# Mojo Runtime Samples

Mojo runtime samples use the pinned public source connector package
`2.3.0-source` over native runtime generation `2.3.0+a83ab412`. The basic
sample keeps its Mojo entrypoint and C shim in this repository and covers
lifecycle/control, raw request/reply, and route-miss deadletter handling.

Run the basic sample:

```sh
bash run.sh runtime mojo basic
```

Watch the Mojo runtime walkthrough:

![CoAkka Runtime Mojo walkthrough](../../docs/assets/coakka-runtime-mojo.gif)

Full recording: [coakka-runtime-mojo.mp4](../../docs/assets/coakka-runtime-mojo.mp4)

## Continue Integrating

Use this lane's runnable sample as the source for package imports and basic
lifecycle names. Before generating connection strategy, TLS/mTLS, File Lane, or
Stream Lane code, follow [AI-Assisted Integration](../../docs/ai-assisted-integration.md).
It links the canonical feature guides, exact package catalog, and platform
evidence, and it defines when only workflow pseudocode is justified.

The current public package train includes File Lane and Stream Lane. Use the
exact connector names and lifecycle rules shipped by the `2.3.0-source`
package when integrating either lane.
