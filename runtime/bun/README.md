# Bun Runtime Samples

These samples exercise the Bun-facing CoAkka runtime connector lane.

The current Bun lane consumes `coakka-v2-connector-bun@2.4.1` from npm and
loads native runtime generation `2.4.0+c2f53117`.

The package carries the native runtime resources. A sample user installs the
published Bun package and runs `RuntimeHost`; there is no separate native
runtime install step.

Watch the Bun runtime walkthrough:

![CoAkka Runtime Bun walkthrough](../../docs/assets/coakka-runtime-bun.gif)

Full recording: [coakka-runtime-bun.mp4](../../docs/assets/coakka-runtime-bun.mp4)

## Samples

- [basic](basic/README.md): one same-process JSON request/reply through the
  native runtime.

## Integration Recipe

The Bun connector shape mirrors Node.js but exposes Bun-named aliases:

```js
import {
  BunRuntimeClient,
  BunRuntimeHost,
  DeliveryHint,
  localRoute,
  PayloadFormat,
  PayloadIdentity,
} from "coakka-v2-connector-bun";
```

Use one `BunRuntimeHost` per process, register handlers only for targets owned
by that process, and send typed asks/events to target names rather than URLs.

## Continue Integrating

Use this lane's runnable sample as the source for package imports and basic
lifecycle names. Before generating connection strategy, TLS/mTLS, File Lane, or
Stream Lane code, follow [AI-Assisted Integration](../../docs/ai-assisted-integration.md).
It links the canonical feature guides, exact package catalog, and platform
evidence, and it defines when only workflow pseudocode is justified.

The current public package train includes File Lane and Stream Lane. Use the
exact connector names and lifecycle rules shipped by `2.4.0` when integrating
either lane.
