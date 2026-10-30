# Bun Runtime Samples

`coakka-v2-connector-bun@3.0.0` is published on npm.
Start with [the ordinary3.0.0 first run](registry-basic/README.md):

```sh
cd coakka-runtime/bun/registry-basic
npm install
bun main.mjs
```

Expected output: `accepted-hello`. The package carries its matching Core and
host adapters. The basic/deadletter wrappers instead perform
[exact local-archive qualification](../../docs/coakka-runtime/previews/3.0.0/runtime-js-candidate.md).
Use the [package ledger](../../docs/common/current-packages.md) for versions.

## Historical 2.x walkthrough

The recordings and integration snippets below describe npm 2.5.3 and Core
2.5.1+26f7944d. Use the [complete frozen 2.x sample](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime/bun)
with that version's runner and API. These are historical context, not 3.0.0
installation instructions or release evidence.

Watch the Bun runtime walkthrough:

![CoAkka Runtime Bun walkthrough](../../docs/coakka-runtime/assets/coakka-runtime-bun.gif)

Full recording: [coakka-runtime-bun.mp4](../../docs/coakka-runtime/assets/coakka-runtime-bun.mp4)

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
Stream Lane code, follow [AI-Assisted Integration](../../docs/coakka-runtime/guides/ai-assisted-integration.md).
It links the canonical feature guides, exact package catalog, and platform
evidence, and it defines when only workflow pseudocode is justified.

The current public package train includes File Lane and Stream Lane. Use the
exact connector names and lifecycle rules shipped by `2.5.3` when integrating
either lane.
