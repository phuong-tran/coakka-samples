# Runtime Go v1.8.3 And SwiftPM v2.5.3

Date: 2026-08-21

This release moves the Go and Swift source-package channels to connector
`2.5.3` over corrected Core generation
`2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be`. The connector APIs and public
C ABI remain unchanged; the native patch corrects Stream Lane cancellation
and control-window liveness.

```text
Go module: github.com/phuong-tran/coakka-runtime-go@v1.8.3
Go annotated tag object: e76e5aaa508501383718bbfdd3108f6d0e19e7e6
Go tag commit: f0e07563b2093c70c040782585b40edd9c72f442
Go module sum: h1:ksxWT7YIXXUV+RbZJfiNG7IMsUPY+utVUdbKBUrnyC4=
Go go.mod sum: h1:YBxjoy2dFSIW9iBvAcZk1NGWQ9yqxjAkjDWv47mJp9M=
Go release: https://github.com/phuong-tran/coakka-runtime-go/releases/tag/v1.8.3
Go Actions run: https://github.com/phuong-tran/coakka-runtime-go/actions/runs/32441304789

SwiftPM repository: https://github.com/phuong-tran/coakka-runtime-swift.git
SwiftPM exact version: 2.5.3
Swift annotated tag object: 74b0ddb90da15126b3aec7df6894cd152778996f
Swift tag commit: 703e76c05f936d36d4566353f103c67d857bf6be
Swift release: https://github.com/phuong-tran/coakka-runtime-swift/releases/tag/v2.5.3
Swift Actions run: https://github.com/phuong-tran/coakka-runtime-swift/actions/runs/32441362358

Connector artifact source: 0ba485e8ff19f3ce23902345cb445a1f652fe3f3
Native generation: 2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be
Swift macOS ARM64 dylib SHA-256: 277d9ff36b017f2eef2e630ac82bb9ba68f112879297e8067521fe665f82368a
```

The public Go proxy resolves `v1.8.3` to the tag commit above. The Go CI run
passes for that commit. The Swift CI run also passes for its exact tag commit.
Both source packages embed all five generation-pinned native payloads. The
GitHub Releases carry no duplicate binary assets because those payloads are
already part of each source package.

The two annotated tags and their package payloads are unchanged. A later
documentation projection had restored the preceding `v1.8.2` and `v2.5.2`
guidance on each repository's default branch; the canonical source and all
generated copies now select the released versions again. The Go GitHub Release
also had an empty title and body, while the Swift tag had no GitHub Release;
both release pages now carry the package identities recorded above.

The immutable Swift `v2.5.3` tag's `RELEASE.md` retained an obsolete dylib
digest. The tagged verifier and the actual tagged macOS ARM64 payload agree on
`277d9ff36b017f2eef2e630ac82bb9ba68f112879297e8067521fe665f82368a`.
Current documentation and release metadata use that digest; the tag was not
rewritten.
