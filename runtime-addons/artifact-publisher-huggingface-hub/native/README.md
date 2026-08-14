# Native C11 Sample

The runnable path uses the released addon `1.1.0+d1032f6d` with CoAkka Runtime
native `2.4.0`. It starts two native processes:

1. Service B starts a bounded receiver File Lane and installs one short-lived
   transfer grant before publishing readiness.
2. Service A starts a sender lane, creates the huggingface-hub publisher, submits one
   immutable source identity, waits on update sequences, and checks both the
   publisher and sender target terminal outcomes.
3. Service B independently checks `COMPLETED + OK`, byte count, and SHA-256.

Run the deterministic local fixture:

```sh
bash run.sh published
```

Compile every C translation unit with strict warnings against the source
headers without running the fixture:

```sh
bash run.sh check
```

The common lifecycle source lives in
[`runtime-addons/native-artifact-sample/`](../../native-artifact-sample/).
It is shared to keep one audited ownership and cleanup path; the selected
compile-time adapter still uses the exact concrete C types and functions from
this addon's public header.

The fixture is protocol-shaped integration evidence, not live cloud-provider
certification. Production credentials and URLs must come from trusted host
configuration. Never forward an untrusted tenant URL directly into the addon.
