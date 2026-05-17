# Zig Logger Basic

This sample builds a small Zig program against the published native logger
archive. It loads the public C header with `@cImport`, emits one info record,
drains the record through the manual sink path, and prints counters.

Run from the repository root:

```sh
bash run.sh logger zig basic
```
