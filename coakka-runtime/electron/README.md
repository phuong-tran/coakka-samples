# Electron runtime candidate samples

The renderer sends intent through an isolated, sandboxed preload; Electron main
owns the runtime, local route, application handler and awaited shutdown. Core is
the source of effective startup/capability/delivery facts, including the normal
AUTO/multiplex path. No response-pipe choice or OS capability probe is made here.

This branch uses the exact private Node/Electron archives in
[the candidate catalog](../artifacts/runtime-electron-candidate.json),
not old registry coordinates. See the [candidate guide](../docs/runtime-electron-candidate.md)
for both archives, explicit Electron42/Node22/esbuild build tools, fresh external
output and native Windows commands.

```sh
bash run.sh coakka-runtime electron basic
# The same entrypoint:
bash run.sh coakka-runtime electron desktop-intent
```

Set the absolute candidate/tool/output paths from the guide first. The app opens
one visible window, verifies a typed Unicode echo, prints Core-issued startup
facts and retires IPC/Core before normal close. Renderer has no Node/native
resource access. Preload exposes only intent and the sample report, never raw IPC.
Main authorizes its exact window/current main frame/local URL and denies
navigation/new windows. The app admits at most8active IPC calls, retaining2s
asks/10s renderer completion. The optional absolute browser-data argument owns
only browser storage, not native loading.

Historical recordings and the published2.5.3 lane are not evidence for this
candidate. Source, actual matching-host installed execution, artifact privacy/
shrink and finite resource controls are independently tracked. No wider OS,
background scheduling, whole-browser leak or publication claim follows.
