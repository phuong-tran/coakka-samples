# Python Runtime Hot Reload

This sample shows route snapshot hot reload without adding another UI or
business workflow.

It runs one Python process with one CoAkka runtime handle and demonstrates:

- generation 1 routes `samples.runtime.python.hot_reload.v1`
- a stale generation 1 snapshot rejected by the runtime
- a valid generation 2 snapshot replacing the active route table
- the old generation 1 target becoming a route miss
- an invalid generation 3 snapshot rejected by the runtime
- final diagnostics for active generation, route count, route misses,
  deadletters, and matched deadletters

Run:

```sh
bash run.sh runtime python hot-reload
```

The sample installs the published Python wheel into a disposable virtual
environment and removes that environment when the process exits.
