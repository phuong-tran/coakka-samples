# Electron desktop intent candidate

Main owns a single local runtime route and one visible foreground window. The
sandboxed renderer sends one typed Unicode intent through the package preload,
then reports its reply. Main verifies that reply and prints Core-issued runtime
info/startup facts with connector counters. IPC admission is bounded to8calls;
close seals/joins accepted calls, awaits runtime retirement and normally closes
the window. Renderer abandonment is not inferred Core cancellation.

Use the exact private Node/Electron archives and explicit Node22/Electron42/
esbuild0.27.7 tools in the [candidate guide](../../../docs/coakka-runtime/runtime-electron-candidate.md).
No registry download, dependency-archive patch or separate Core install occurs.

```sh
bash run.sh coakka-runtime electron desktop-intent
```

Set the guide's absolute archive/tool paths and fresh external output first.
`basic` is an alias. Native Windows runs the JavaScript producer and Electron
directly, never WSL as DLL evidence. The installed tree is movable and does not
need the source/build tree or build tools to execute.

Exact sender/main-frame/local-URL authorization, local CSP, context isolation,
renderer sandbox and no Node renderer integration are mandatory. The optional
browser-data directory is app-owned storage only. Keep2s asks/10s completion;
failure/early close stays nonzero after cleanup. This is a private candidate
sample, not signing/support/main/registry promotion.
