# Bun Runtime Basic

This private candidate sample uses the exact Bun archive and bundled
Core listed in the [candidate guide](../../../docs/coakka-runtime/previews/3.0.0/runtime-js-candidate.md).
Read that guide first and set the archive and work-directory variables.
There is no registry fallback or separate native runtime installation.

```sh
bash run.sh
```

Or from the repository root:

```sh
bun coakka-runtime/scripts/runtime-js-candidate.mjs bun basic
```

The app owns one route and handler, checks one typed JSON echo with the original
2s request deadline, prints Core-issued startup/runtime facts and route/client
counters, and awaits shutdown. Incidental runtime tuning is omitted.

Older public-package walkthroughs do not qualify this candidate. Publication
and broader platform/resource gates remain separate.
