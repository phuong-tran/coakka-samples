# Mojo Logger Samples

Mojo logger samples use the published native logger archive through a small
sample-local C shim. The current basic sample emits one record, drains it
manually, and reads the bounded logger counters.

Run the basic sample:

```sh
bash run.sh logger mojo basic
```
