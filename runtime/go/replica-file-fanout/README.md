# Go Replica File Fan-Out

This sample sends one immutable file to three exact File Lane owners. It proves
the application-level `ALL` policy without pretending File Lane is broadcast.

The sample:

- enumerates three stable owner identities;
- opens one owner-aware receiver lane per identity;
- creates a fresh transfer ID and token per owner;
- JSON-encodes and decodes every grant as an authenticated control plane would;
- checks that the returned owner matches the requested owner;
- reuses one immutable source and SHA-256 for three independent sends;
- checks sender and receiver terminal outcomes separately for every owner.

Run after Go module `v1.8.2` is published:

```sh
bash run.sh
```

Release maintainers can verify the candidate before publication:

```sh
COAKKA_GO_MODULE_REPLACE=/path/to/coakkaJVMConnector/go \
COAKKA_FILE_LANE_RUNTIME_LIB=/path/to/libcoakka_runtime_v2.dylib \
bash run.sh
```

Expected output:

```text
coakka_replica_file_fanout owners=3 bytes_per_owner=9437915 ok
```

Read [Runtime Lane Owner Grants](../../../docs/runtime-lane-owner-grants.md)
before adapting this sample to Kubernetes. `ALL` must enumerate exact pod or
process owners. Calling a load-balancing Service three times is incorrect.
