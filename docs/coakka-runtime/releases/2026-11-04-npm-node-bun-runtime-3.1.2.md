# Node.js and Bun Runtime 3.1.2 on npm

Released on 2026-11-04. Version `3.1.2` is the second connector release
on Core generation `3.1`. Both packages include Core 3.1 r2, build
`6789dd16210fc01f17cca4d2b66a592d1ac79121`, ABI 1.

This release corrects the packaged introduction, consuming guide and
logo URL. The native binaries match 3.1.1 byte for byte, and the executable
JavaScript and declarations differ only in the connector version.

```sh
npm install coakka-v2-connector-node@3.1.2
bun add coakka-v2-connector-bun@3.1.2
```

Node.js requires 22.0.0 or later. Bun requires 1.3.14 or later, with
1.4.0 as the minimum on Windows ARM64. The packages include macOS ARM64,
Linux x64 and ARM64, and Windows x64 and ARM64.

| npm package | Bytes | Archive SHA-256 |
| --- | ---: | --- |
| `coakka-v2-connector-node@3.1.2` | 6261807 | `b0bec4cb9b48a0830883ba87346414029a7ff56fe45341e02088c8d6c1de8364` |
| `coakka-v2-connector-bun@3.1.2` | 5071108 | `2d1efa9074636a2bb09806abb7bdb33a37553ce25bc63240391d7e25ffa3909f` |

Both npm tarballs matched their qualified archives. Fresh anonymous
macOS installs passed the package-surface and request/reply smoke checks.
npm reports `latest` and `release-3-1-2` as `3.1.2` for both packages.

[Node.js on npm](https://www.npmjs.com/package/coakka-v2-connector-node/v/3.1.2) ·
[Bun on npm](https://www.npmjs.com/package/coakka-v2-connector-bun/v/3.1.2) ·
[Current packages](../../common/current-packages.md)
