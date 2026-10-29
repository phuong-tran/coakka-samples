# Electron basic candidate

This alias runs the [desktop intent sample](../desktop-intent/README.md): one
typed Unicode renderer intent through isolated sandboxed preload/authorized IPC,
a main-owned Core route and awaited shutdown.

Follow the [candidate guide](../../../docs/coakka-runtime/runtime-electron-candidate.md)
to supply exact local Node/Electron archives, Node22/Electron42/esbuild build
tools and a fresh absolute output on suitable storage.

```sh
bash run.sh coakka-runtime electron basic
```

No registry fallback, source/native override or separate Core install. The
installed app prints Core-issued facts, not guessed configuration. Private
candidate evidence does not open main or registry publication.
