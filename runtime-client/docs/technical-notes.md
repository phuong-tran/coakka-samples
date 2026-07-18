# CoAkka Runtime Client Technical Notes

These notes describe the public runtime-client boundary. They do not define
business schemas, topology truth, dashboard behavior, or inspect-product scope.

## Product And Artifact Names

The product lane is `coakka-runtime-client`. The published executable and
archive prefix is `coakka-client`.

Current release line:

```text
coakka-runtime-client product lane
coakka-client command and archive prefix
1.3.1+2215b0f release id
```

Published CLI archives:

```text
cli/releases/1.3.1+2215b0f/
  coakka-client-v2-1.3.1-macos-aarch64.tar.gz
  coakka-client-v2-1.3.1-linux-x86_64.tar.gz
  coakka-client-v2-1.3.1-linux-aarch64.tar.gz
  coakka-client-v2-1.3.1-windows-x86_64.tar.gz
  coakka-client-v2-1.3.1-windows-aarch64.tar.gz
```

The matching Docker verification bundle lives under:

```text
demo/coakka-client/releases/1.3.1+2215b0f/
```

The `demo/` path segment is an existing artifact layout name. Public wording
should describe the bundle as Docker verification, not as the product identity.

## Runtime Boundary

The CLI speaks to a CoAkka Runtime host over the published request path. The
runtime owns:

- target routing
- route snapshot and generation semantics
- request/reply correlation
- timeout and deadletter outcomes
- runtime build and capability diagnostics

The CLI owns:

- command parsing
- payload source selection
- metadata flags
- output formatting
- shell session state
- scripted command execution

Sample payloads such as `customer.create` are fixtures for visible runtime
behavior. They are not runtime-core schema.

## Transport Profile

The current runtime-client release reports:

```text
southbound_backend=tcp
remote_wire_profile=tcp-frame
remote_wire_profile_version=1
```

Benchmarks and comparisons should stay at this runtime delivery boundary:
route selection, bounded admission, framing, reply matching, timeout, and
deadletter behavior. Do not frame runtime-client numbers as L7 HTTP/gRPC
replacement claims.

## Verification Posture

Use small verification first:

```sh
bash run.sh runtime-client
bash -n run.sh runtime-client/run.sh
```

Use Docker verification only when the Linux bundle path needs a live check:

```sh
bash run.sh runtime-client docker-demo
```

The video asset in this repository is visual product evidence. The command
surface remains verified by scripts and artifact checksum checks, not by the
video itself.

