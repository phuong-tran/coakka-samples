# Node.js Runtime 3.0.0: First Run

Use Node.js 22 or newer and `coakka-v2-connector-node@3.0.0`.
See the [current package ledger](../../../docs/common/current-packages.md)
for registry availability. Install the published package, or evaluate the exact
local archive below before publication; a2.x package cannot run this API.

From the Samples checkout:

```sh
cd coakka-runtime/node/registry-basic
npm install
node main.mjs
```

Expected output: `accepted-hello`. The app starts one host, registers a
bytes handler, sends one request and awaits close. The installed package carries
its matching Core and host adapters; no separate native setup is needed.

CPU, connection and monitor defaults stay owned by Core.

For local archive evaluation, copy this app to a fresh external directory and
install the admitted tarball there with npm. Do not select native libraries from a source checkout.
Local installation is not proof of registry availability.

[Runtime examples](../../README.md) ·
[Integration guide](../../../docs/coakka-runtime/guides/runtime-integration-guide.md).
