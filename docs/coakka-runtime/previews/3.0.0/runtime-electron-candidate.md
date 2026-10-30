# Electron Runtime 3.0.0 local-archive qualification

Electron 3.0.0 is [published on npm](../../releases/2026-10-30-npm-node-bun-electron-runtime-3.0.0.md).
Ordinary applications should use [the registry first-run app](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/electron/registry-basic).
The controlled path below still takes exact Electron/Node tarballs from
`artifacts/runtime-electron-candidate.json`, rather than resolving npm versions.
Core is `3.0.0+1522b4a9f30da1039ef8c7d198bbecfc749a2456`, ABI1; baselines are Node 22.0.0 and Electron 42.0.0.

All five Node native payloads and Electron JS/declarations remain exact from
their qualified package selections. Fresh registry execution on Mac covers
sandboxed renderer, Runtime/File/Stream and shutdown. Earlier matching-target
execution and resource campaigns are retained for unchanged executable bytes;
this is not a new post-upload run on every OS or a new minimum-OS claim.
Electron depends exactly on Node 3.0.0; Tauri follows the separate Rust train.

## Prepare and run

Supply the two exact local archives, an installed matching-host Electron and
the qualified esbuild0.27.7 CommonJS module (`lib/main.js`). The latter is a
preload build tool only; neither it nor Python/Cargo/native build tools are
needed by the installed app. Core and its private providers are already bundled.
Use a fresh absolute output directory; its parent must exist. On the developer
Mac, use mounted external APFS/T7 storage, never a default temp/internal path.

```sh
export COAKKA_RUNTIME_CANDIDATE_PACKAGE="/absolute/coakka-v2-connector-electron-3.0.0.tgz"
export COAKKA_NODE_CANDIDATE_PACKAGE="/absolute/coakka-v2-connector-node-3.0.0.tgz"
export COAKKA_ESBUILD_MODULE="/absolute/esbuild/lib/main.js"
export COAKKA_ELECTRON_EXECUTABLE="/absolute/installed-electron-executable"
export COAKKA_SAMPLE_WORK_ROOT="/absolute/fresh-output"
bash run.sh coakka-runtime electron basic
```

`desktop-intent` is the same sample. The entrypoint prepares the installed app,
then opens one visible foreground window, verifies a Unicode typed echo and
normally closes it after awaiting IPC and Core retirement. It prints one JSON
sample record containing Core-issued info/startup facts and connector counters.
Renderer values never fabricate effective settings or receive native resources.

On native Windows use native Node/Electron paths, not WSL executables:

```powershell
node coakka-runtime/scripts/runtime-electron-candidate.mjs D:/CoAkka/fresh-output
& $env:COAKKA_ELECTRON_EXECUTABLE D:/CoAkka/fresh-output/installed/main.mjs --sample-data-dir D:/CoAkka/fresh-output/browser-data
```

The JavaScript producer is also directly usable on Mac/Linux. Move the complete
`installed/` tree and launch `main.mjs` with Electron from any CWD. Omit the
app-only `--sample-data-dir` argument for normal OS browser-cache placement, or
supply an absolute browser directory. It is not a Core/native-loader override.

## Boundaries, failures and cleanup

Cold preparation verifies both archive lengths/hashes before output creation,
installs offline without hooks or peer downloads and verifies every installed
member. The external Electron host deliberately satisfies the peer boundary;
`--legacy-peer-deps` never changes either connector archive or its exact Node
dependency. No registry fallback or source/native override is accepted.

Catalog1MiB; each archive32MiB; each package<=64files/8MiB per file/64MiB total,
tree<=256entries/depth8; five app sources<=128KiB each; preload<=32KiB.
Install and preload build children retain60s bounds. Handled cancellation
terminates/reaps the one owned build child before removing reproducible staging.
Caller-owned output parents/tools/packages are never removed. A failed
transaction removes its incomplete installed tree, retains a failed receipt and
reports cleanup failure; success retains installed output/receipt for inspection.
SIGKILL, power loss, hostile concurrent input/ancestor mutation and an unkillable
OS child are outside graceful cleanup guarantees. This is trusted local build
orchestration, not a hostile filesystem/package-manager sandbox.

Main owns one bridge/route/window. Its app-only admission cap is8active IPC
calls; no runtime queue/default table is duplicated. Shutdown seals admission,
joins accepted promises and awaits bridge.close before normal window close.
The echo is immediate; abandoning UI does not forcibly cancel business work.
Exact window/current-main-frame/local-URL authorization applies to all sample
IPC. Navigation/new windows are refused. CSP, renderer sandbox, context isolation
and disabled Node renderer integration remain enabled. The preload bundles only
the exact package helper, with Electron as its sole external; no unrestricted
IPC, File/Stream grants or native path is exposed.

The sample retains2s asks/10s renderer completion. A supervising verification
run retains60s process bound; timeout/failed renderer/early close remains failed
evidence. Normal success uses framework quit; failure is explicit nonzero only
after owned IPC/Core/window cleanup. No OS process kill or security workaround
is part of the connector or sample lifecycle.

Historical walkthroughs are not this candidate's evidence. Matching-host source,
installed package, negative controls, privacy/shrink, resource and cleanup gates
are tracked separately. This sample does not itself promote all connectors,
tools/client/inspect/Android/camera, signing/legal/main or registry publication.
