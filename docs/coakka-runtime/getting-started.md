# Start With CoAkka Runtime

Choose a language in the [Runtime sample guide](https://github.com/phuong-tran/coakka-samples/blob/main/coakka-runtime/README.md).
Run its smallest request/reply example before adding files, streams or remote peers.

## Using a published package

For the other released 2.x language lanes, use the [published sample snapshot](https://github.com/phuong-tran/coakka-samples/tree/d401e4a62359dcf245ffda093f2cbea21fe5f39a/runtime) and follow that snapshot's README,
runner and exact package version together. Download or check out the snapshot
before running it; do not copy a 2.x install command into current 3.0.0 source.
The [published package ledger](../common/current-packages.md) records available releases.

A basic example starts a host, registers one handler, sends a request, checks
its reply and closes the host. Its README gives prerequisites, the command
and expected output. Keep those steps together in the selected example.

## C# Runtime 3.0.0 from NuGet

C# 3.0.0 is published. Use .NET 8 or newer and the
[matching first-run example](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/csharp/registry-basic):

```sh
dotnet run --project coakka-runtime/csharp/registry-basic/CoAkkaRuntimeFirstRun.csproj
```

Run from the Samples checkout; expected output is `accepted-hello`. The package
includes its matching native Core. Existing applications install with
`dotnet add package CoAkka.Runtime --version 3.0.0`; migrate and recompile 2.x
callers against the new API.

## Python Runtime 3.0.0 from PyPI

Use GIL-enabled CPython3.11 or newer and the
[matching Python first-run example](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/python/registry-basic):

```sh
python -m pip install coakka-v2-connector==3.0.0
python coakka-runtime/python/registry-basic/main.py
```

Run from the Samples checkout in a virtual environment; expected output is
`accepted-hello`. The wheel includes Core and its matching Python adapter.
Migrate 2.x applications to the new API.

## Node.js, Bun and Electron 3.0.0 from npm

Choose the package for your application host:

```sh
npm install coakka-v2-connector-node@3.0.0
# Bun application:
bun add coakka-v2-connector-bun@3.0.0
# Electron application:
npm install coakka-v2-connector-electron@3.0.0
```

Use Node.js 22+, Bun 1.3.14+ (Windows ARM64:1.4.0+), or Electron 42+.
Electron includes an exact dependency on the Node connector; native ownership
stays in the main process. The package includes its matching native Core.
Start with the [Node.js](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/node/registry-basic),
[Bun](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/bun/registry-basic) or
[Electron](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/electron/registry-basic) first-run app.
See the [no-checkout npm walkthrough](https://github.com/phuong-tran/coakka-samples/blob/main/docs/coakka-runtime/guides/first-npm-smoke.md)
for a small application-owned customer operation. Migrate 2.x callers before upgrading.

## Go 3.0.0 from GitHub

```sh
go get github.com/phuong-tran/coakka-runtime-go/v3@v3.0.0
```

Use a C11 cgo compiler and Go 1.22.12 or newer (native Windows ARM64:
Go 1.25.5). Import the new `/v3` module path. Start with
[the ordinary first-run app](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/go/registry-basic).
The module includes its matching native Core; no separate Core installation
or module replacement is needed.

## Evaluating 3.0.0

Other main-branch Runtime examples still use **local 3.0.0 candidates**.
They require the matching artifact and catalog from the
[candidate index](previews/README.md). Their registry releases remain separate.
Android and Mojo are evaluation previews. Swift is a release package; only
Swift on Windows ARM has preview scope for this train.

## After the first reply

Read the [integration guide](guides/runtime-integration-guide.md) to move the
same lifecycle into your application. For default settings, monitor use and
examples, read [monitoring](operations/runtime-monitoring.md).
