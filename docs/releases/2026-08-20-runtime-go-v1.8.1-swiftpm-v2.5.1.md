# Runtime Go v1.8.1 And SwiftPM v2.5.1

Date: 2026-08-20

This scoped patch release publishes the GitHub-backed source-package channels
without advancing Maven Central, npm, PyPI, NuGet, or the promoted native
artifact pointer:

```text
Go module: github.com/phuong-tran/coakka-runtime-go@v1.8.1
Go annotated tag object: ba113aedc1e3ea10040b65ae0cebbd4005765440
Go tag commit: fa0d1d82ae0a0386422e04c3853b91f4f7e0b0fa
Go module sum: h1:HtjLMgPT/uxaApGdGSCC0pnSULFgAPWwqluoQJP3uX8=
Go go.mod sum: h1:YBxjoy2dFSIW9iBvAcZk1NGWQ9yqxjAkjDWv47mJp9M=

SwiftPM repository: https://github.com/phuong-tran/coakka-runtime-swift.git
SwiftPM exact version: 2.5.1
Swift annotated tag object: a74683d88d795a2919b2887008e14221fe9ed3c5
Swift tag commit: 9f2c5b5f61aaebaba6cbe396f17fa1ce9987abe9

Connector artifact source: 11c155586796b0fbe946df273d2bbfe8058eaec5
Native generation: 2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a
```

The patch corrects public documentation and package-license links. Connector
source material remains Apache-2.0. Bundled native files use the CoAkka Native
Artifact License 1.2; `PACKAGE-LICENSE.md` maps both terms by file scope.
Public APIs, runtime ABI, protocol behavior, and all five native payloads are
unchanged from Runtime 2.5.0.

The Go module resolves through the public Go proxy at the tag commit above.
Its source tests and a clean remote-tag request/reply consumer pass on macOS
ARM64. GitHub Actions run `32349915196` passes on Linux with the Go 1.22
compatibility floor and current stable Go.

The Swift package passes package readiness, exact five-payload verification,
runtime, File Lane, Stream Lane, and transport tests. A clean SwiftPM consumer
resolves exact version `2.5.1` and completes request/reply on macOS ARM64.
GitHub Actions run `32349932485` passes on macOS.

Publisher signing remains absent. The GitHub Releases carry no duplicate
binary assets because each source package already embeds its native payloads.
