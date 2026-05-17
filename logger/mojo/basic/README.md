# Mojo Logger Basic

This sample builds a small C shim against the published native logger archive
and calls it from Mojo through `std.ffi.OwnedDLHandle`. The shim keeps the
sample focused on the logger lifecycle while Mojo's direct C ABI surface keeps
evolving.

Run from the repository root:

```sh
bash run.sh logger mojo basic
```
