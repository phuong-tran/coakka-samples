# Bun Runtime Samples

These samples exercise the Bun-facing CoAkka runtime connector lane.

The current Bun lane consumes the published `coakka-v2-connector-bun` tarball
from `coakka-publish`.

The package carries the native runtime resources. A sample user installs the
published Bun package and runs `RuntimeHost`; there is no separate native
runtime install step.

Walkthrough recording status: planned. This lane should get the same short
GIF/MP4 treatment as `coakka-runtime-client` and `coakka-runtime-inspect` once
the Bun sample wording is stable.

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
