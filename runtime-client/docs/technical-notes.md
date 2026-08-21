# CoAkka Runtime Client Technical Notes

These notes describe the public runtime-client boundary. They do not define
business schemas, topology truth, dashboard behavior, or inspect-product scope.

## Product And Artifact Names

The product lane is `coakka-runtime-client`. The published executable and
archive prefix is `coakka-client`.

The archive intentionally ships `coakka-client` as the command name. A
`coakka-runtime-client` executable is not part of this release surface.

Current release lines:

```text
coakka-runtime-client product lane
coakka-client command and archive prefix
last public release `2.4.0+c2f53117` on all five listed platforms
```

Published CLI archives:

| Platform | Archive |
| --- | --- |
| macOS ARM64 | [coakka-client-v2-2.4.0-macos-aarch64.tar.gz](https://raw.githubusercontent.com/phuong-tran/coakka-publish/da4a5e9c3f1f846970fb84c8f18bca893051c487/coakka-tools/coakka-client/releases/2.4.0+c2f53117/coakka-client-v2-2.4.0-macos-aarch64.tar.gz) |
| Linux x86_64 | [coakka-client-v2-2.4.0-linux-x86_64.tar.gz](https://raw.githubusercontent.com/phuong-tran/coakka-publish/da4a5e9c3f1f846970fb84c8f18bca893051c487/coakka-tools/coakka-client/releases/2.4.0+c2f53117/coakka-client-v2-2.4.0-linux-x86_64.tar.gz) |
| Linux ARM64 | [coakka-client-v2-2.4.0-linux-aarch64.tar.gz](https://raw.githubusercontent.com/phuong-tran/coakka-publish/da4a5e9c3f1f846970fb84c8f18bca893051c487/coakka-tools/coakka-client/releases/2.4.0+c2f53117/coakka-client-v2-2.4.0-linux-aarch64.tar.gz) |
| Windows x86_64 | [coakka-client-v2-2.4.0-windows-x86_64.tar.gz](https://raw.githubusercontent.com/phuong-tran/coakka-publish/da4a5e9c3f1f846970fb84c8f18bca893051c487/coakka-tools/coakka-client/releases/2.4.0+c2f53117/coakka-client-v2-2.4.0-windows-x86_64.tar.gz) |
| Windows ARM64 | [coakka-client-v2-2.4.0-windows-aarch64.tar.gz](https://raw.githubusercontent.com/phuong-tran/coakka-publish/da4a5e9c3f1f846970fb84c8f18bca893051c487/coakka-tools/coakka-client/releases/2.4.0+c2f53117/coakka-client-v2-2.4.0-windows-aarch64.tar.gz) |

Artifact catalog and manifest:
[CoAkka Public Artifacts](https://github.com/phuong-tran/coakka-publish/tree/da4a5e9c3f1f846970fb84c8f18bca893051c487),
[public-artifacts.tsv](https://raw.githubusercontent.com/phuong-tran/coakka-publish/da4a5e9c3f1f846970fb84c8f18bca893051c487/artifacts/public-artifacts.tsv)

Per-lane checksums are stored beside each release directory in
`coakka-publish`.

The macOS ARM64 archive completes matching-host command execution. Linux
ARM64/x86-64 complete matching-architecture Docker build and dependency gates.
All five archives pass dependency, architecture, archive, and checksum gates;
matching-host Linux command execution and Windows execution are not recorded
for this generation.

Artifact layout:

```text
coakka-tools/coakka-client/releases/2.4.0+c2f53117/
  coakka-client-v2-2.4.0-macos-aarch64.tar.gz
  coakka-client-v2-2.4.0-linux-x86_64.tar.gz
  coakka-client-v2-2.4.0-linux-aarch64.tar.gz
  coakka-client-v2-2.4.0-windows-x86_64.tar.gz
  coakka-client-v2-2.4.0-windows-aarch64.tar.gz
```

The matching Docker verification bundle lives under:

```text
coakka-tools/coakka-client/docker-demo/releases/1.3.2+caff6d6d/
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

The last public runtime-client release reports:

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
bash run.sh runtime-client docker-bundle
```

Use `docker-walkthrough` when the goal is a guided CLI experience. It reuses
the same published bundle but adds a temporary Compose override with two native
runtime service containers and prints the service, port, and route before each
`coakka-client` call. This remains sample orchestration; it does not add a new
runtime-core responsibility or a new published artifact lane.

The last public Docker Hub image
`docker.io/gabrielgun1983/coakka-runtime-client-demo:1.3.2-caff6d6d-remote`
prebuilds that walkthrough into one container. It contains the published
`coakka-client` and native demo service artifacts for Linux amd64/arm64 and
starts two native runtime service processes before driving them from the CLI.
It is a sample-image lane, not the canonical binary artifact surface.

The animated walkthrough and MP4 recording in this repository are visual
product evidence. The command surface remains verified by scripts and artifact
checksum checks, not by the recording itself.
