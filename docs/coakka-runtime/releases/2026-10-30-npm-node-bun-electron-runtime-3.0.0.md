# Node.js, Bun and Electron Runtime 3.0.0 on npm

Released on 2026-10-30 as one npm family:

- [coakka-v2-connector-node 3.0.0](https://www.npmjs.com/package/coakka-v2-connector-node/v/3.0.0).
- [coakka-v2-connector-bun 3.0.0](https://www.npmjs.com/package/coakka-v2-connector-bun/v/3.0.0).
- [coakka-v2-connector-electron 3.0.0](https://www.npmjs.com/package/coakka-v2-connector-electron/v/3.0.0).

All three `latest` selectors point to 3.0.0. Electron depends exactly on the
Node 3.0.0 connector. This release includes bundled Core3; C# and Python3.0.0
remain published independently. JVM/Go/Swift/other Runtime lanes, HTTP Runtime
and Logger keep their separately recorded publication status. Tauri follows
the Rust train; Android and Mojo remain evaluation previews.

## Install and first run

```sh
npm install coakka-v2-connector-node@3.0.0
# Bun application:
bun add coakka-v2-connector-bun@3.0.0
# Electron application:
npm install coakka-v2-connector-electron@3.0.0
```

Use the matching [Node.js](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/node/registry-basic),
[Bun](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/bun/registry-basic) or
[Electron](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/electron/registry-basic) app.
Node/Bun print `accepted-hello`; Electron opens an isolated sandboxed window,
verifies a typed reply and closes after Runtime retirement. Native images are
included; ordinary users do not build or separately install Core.

## Compatibility and defaults

3.0.0 introduces a new API baseline; migrate 2.x callers before upgrading.
Node requires 22.0.0+, Bun 1.3.14+ (Windows ARM64:1.4.0+), Electron 42+.
Core is `3.0.0+1522b4a9f30da1039ef8c7d198bbecfc749a2456` / ABI1. Node/Bun include five 64-bit targets:
macOS ARM64, Linux ARM64/x64, Windows ARM64/x64. Electron uses those images
through its exact Node dependency. No 32-bit or macOS Intel target is added.
AUTO CPU selection, MULTIPLEXED connections and monitor OFF remain Core-owned.
Application business handlers remain application-owned.

## Verified public archives

| Package | Bytes | SHA256 |
| --- | ---: | --- |
| `coakka-v2-connector-node@3.0.0` | 5807456 | `9e10098c659b6888f79529c4d30651bcc6cef5f7eabea77f75baea43d7f06da3` |
| `coakka-v2-connector-bun@3.0.0` | 4615756 | `31214d019422d0132f0e3e5798db411d30afb8de8780ca4657d33c5362cb1e18` |
| `coakka-v2-connector-electron@3.0.0` | 16766 | `0bef8cdfe0f8abb6475750407eb73d6c49b2c85a49ae148a99428b4525aa0c14` |

Official npm tarballs match these complete archive identities, SHA1 and SHA512
integrity values. Installed members match the final producer receipts. Normal
producers reproduce the same archives twice. Only package docs, license mapping
and discovery metadata change from the previously qualified selections;
JavaScript, public declarations and native members remain byte-identical.

A fresh anonymous npm-only macOS ARM64 installation passes Node 22.0.0 and
Bun 1.3.14 Runtime, values, File, Stream, shutdown and first-run checks. Electron 42
passes eight boundary tests, a real sandboxed renderer, File, Stream, shutdown
and the ordinary desktop-intent app. Its Node dependency resolves from npm to
exactly 3.0.0. Earlier five-target exact executable qualification is retained;
this is not a new post-upload run on every OS or a broader minimum-OS claim.

The family was uploaded under a release selector, checked through actual
registry installs, then promoted to `latest`. npm does not provide an atomic
transaction across packages; readback verifies each final selector.
Earlier package-manager warehouse receipts remain immutable history.

[Current packages](../../common/current-packages.md) ·
[Package/platform evidence](../reference/runtime-package-platform-evidence.md) ·
[Defaults and monitoring](../operations/runtime-monitoring.md).
