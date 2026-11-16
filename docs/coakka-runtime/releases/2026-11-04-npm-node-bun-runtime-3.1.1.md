# Node.js and Bun Runtime 3.1.1 on npm

Released on 2026-11-04. In `3.1.1`, `3.1` identifies the Core generation
and `1` identifies the connector's first release on that generation. Each
package includes Core 3.1 r2, build `6789dd16210fc01f17cca4d2b66a592d1ac79121`,
ABI 1.

```sh
npm install coakka-v2-connector-node@3.1.1
bun add coakka-v2-connector-bun@3.1.1
```

The package supports macOS ARM64, Linux x64 and ARM64, and Windows x64 and
ARM64. Node.js requires 22.0.0 or later. Bun requires 1.3.14 or later, with
1.4.0 as the floor on Windows ARM64.

| npm package | Bytes | Archive SHA-256 |
| --- | ---: | --- |
| `coakka-v2-connector-node@3.1.1` | 6263130 | `b7aec3f51157abfea4426151d6fb2eb1d3a5e5a338b9a59d48b3c205b873134c` |
| `coakka-v2-connector-bun@3.1.1` | 5071158 | `f5e5b721d486c7e747aca14a1946b50c920507cd6ad585537d33e131ac3ae996` |

Both registry tarballs matched the qualified archives. Fresh anonymous
installs on macOS ARM64 passed the package surface and request/reply
smokes with the bundled Core. npm reports `latest` and `release-3-1-1`
as `3.1.1` for both packages.

## Documentation correction

The npm tarballs identify version `3.1.1` and include Core `3.1` r2. Their
bundled README and consuming guide still describe an older development
candidate, and the README logo URL returns 404. Use this release record for
the version and supported targets. The [3.1.2 correction](2026-11-04-npm-node-bun-runtime-3.1.2.md) carries the updated package documentation.

[Node.js on npm](https://www.npmjs.com/package/coakka-v2-connector-node/v/3.1.1) ·
[Bun on npm](https://www.npmjs.com/package/coakka-v2-connector-bun/v/3.1.1) ·
[Current packages](../../common/current-packages.md)
