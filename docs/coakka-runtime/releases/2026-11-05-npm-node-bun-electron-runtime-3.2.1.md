# Node.js, Bun and Electron Runtime 3.2.1 on npm

Released on 2026-11-05. Version `3.2.1` is the first connector release on
Core generation `3.2`, build `f9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45`, ABI 1.
Node.js and Bun include Core. Electron installs `coakka-v2-connector-node@3.2.1`
as its exact dependency and uses the Core supplied by Node.js.

## Install

```sh
npm install coakka-v2-connector-node@3.2.1
bun add coakka-v2-connector-bun@3.2.1
npm install coakka-v2-connector-electron@3.2.1
```

Node.js requires 22.0.0 or later. Bun requires 1.3.14 or later, with 1.4.0
as the minimum on Windows ARM64. Electron requires Electron 42 or later.
Core images cover macOS ARM64, Linux ARM64 and x64, and Windows ARM64
and x64. See the [2.x to 3.x migration guidance](../CHANGELOG.md#breaking-changes-and-migration).

## Registry verification

| npm package | Bytes | Tarball SHA-256 |
| --- | ---: | --- |
| [`coakka-v2-connector-node@3.2.1`](https://www.npmjs.com/package/coakka-v2-connector-node/v/3.2.1) | 6023816 | `13950c3e4f8887b6502baa0f5be60c2be31e48899c0546988bd2b6566e1851b8` |
| [`coakka-v2-connector-bun@3.2.1`](https://www.npmjs.com/package/coakka-v2-connector-bun/v/3.2.1) | 4833451 | `b43dd6a82db7354d56dc9ffddeaa2d778d401b59823afb36612b0987e3a7a7ef` |
| [`coakka-v2-connector-electron@3.2.1`](https://www.npmjs.com/package/coakka-v2-connector-electron/v/3.2.1) | 16348 | `7ecb098f4bd7bb5c44af12c1f8f6e196543582b434d48381bd55df90f634fb44` |

Each public tarball matched the qualified archive. A clean npm install
resolved all three versions, including the Electron dependency on Node.js
3.2.1. Both `latest` and `release-3-2-1` resolve to 3.2.1 for all three
packages. On macOS ARM64, the exact installed Node.js and Bun packages
passed request/reply, shutdown, values, file transfer and streaming.
The Electron gate covered its sandboxed renderer, file transfer,
streaming and shutdown. The Core SDK manifest SHA-256 is `16d8239b6f6f0d113e7059429400a055a7a79500bc5ab38970328c030b5fc677`.
