# Runtime Go v1.8.2 And SwiftPM v2.5.2

Date: 2026-08-20

This scoped release publishes the connector owner-grant correction on the two
GitHub-backed source-package channels. Maven Central, npm, PyPI, NuGet, and the
promoted native artifact pointer remain unchanged.

```text
Go module: github.com/phuong-tran/coakka-runtime-go@v1.8.2
Go annotated tag object: 4e9716a313619d487a64da9d4cefe3874b051c1c
Go tag commit: 2a4618fe25c2ff6f5c636b1ed3370a3eb6d59c16
Go module sum: h1:hKilkPliCs1yPvE6Xh/wDbo5OjYXP7a4/ge4ZDMhmoU=
Go go.mod sum: h1:YBxjoy2dFSIW9iBvAcZk1NGWQ9yqxjAkjDWv47mJp9M=
Go release: https://github.com/phuong-tran/coakka-runtime-go/releases/tag/v1.8.2

SwiftPM repository: https://github.com/phuong-tran/coakka-runtime-swift.git
SwiftPM exact version: 2.5.2
Swift annotated tag object: 401927f683afa8d2247e2f777baa71812971319d
Swift tag commit: 76f928e901f1621f443164523183908ce487a80e
Swift release: https://github.com/phuong-tran/coakka-runtime-swift/releases/tag/v2.5.2

Connector artifact source: 3ae74f43d061904d3bdc38a1d84d0479cd6c43bf
Native generation: 2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a
```

Both connectors expose typed File receive and Stream publish owner grants,
trusted control-plane reconstruction, exact-owner endpoint pinning, and
owner-aware lane operations. File grants are scoped to one prepared transfer
and may be reused only for bounded resume and idempotent completed-status
handling while that owner retains the record. Stream grants are consumed by
the first valid OPEN admission.

The public Go proxy resolves `v1.8.2` to the tag commit above. Source tests,
package-consumer tests, live File Lane and Stream Lane owner-grant tests, and
the three-owner File Lane fan-out sample pass on macOS ARM64. GitHub Actions
run `32359952151` passes on Linux at the Go 1.22 compatibility floor and the
current stable Go toolchain.

The Swift package passes source tests, package readiness, exact five-payload
verification, runtime, File Lane, Stream Lane, and transport execution on
macOS ARM64. A clean clone of the public `v2.5.2` tag also passes package
readiness. GitHub Actions run `32359950498` passes on macOS.

Publisher signing remains absent. The GitHub Releases carry no duplicate
binary assets because each source package already embeds its native payloads.
