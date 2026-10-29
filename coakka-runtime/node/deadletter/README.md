# Node Runtime Deadletter

This private candidate sample uses the exact Node archive and bundled
Core listed in the [candidate guide](../../../docs/coakka-runtime/runtime-js-candidate.md).
Read that guide first and set the archive and work-directory variables.
There is no registry fallback or separate native runtime installation.

```sh
bash run.sh
```

Or from the repository root:

```sh
node coakka-runtime/scripts/runtime-js-candidate.mjs node deadletter
```

The app sends one 2s typed ask to a missing route, requires the typed route-miss
failure and exact terminal counters, observes the matched deadletter through a
one-item iterator, retires that iterator, and awaits runtime shutdown.

Older public-package walkthroughs do not qualify this candidate. Publication
and broader platform/resource gates remain separate.
