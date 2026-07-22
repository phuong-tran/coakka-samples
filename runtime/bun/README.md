# Bun Runtime Samples

These samples exercise the Bun-facing CoAkka runtime connector lane.

The current Bun lane is still source-adjacent: it consumes the sibling
`coakkaJVMConnector/bun` package checkout when present instead of claiming a
published `coakka-publish` artifact. That keeps the sample useful for connector
validation without pretending the Bun package has joined the public release
surface yet.

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
