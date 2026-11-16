# CoAkka Runtime Client Technical Notes

These notes describe the public runtime-client boundary. They do not define
business schemas, topology truth, dashboard behavior, or inspect-product scope.

## Product And Artifact Names

The product lane is `coakka-runtime-client`. The published executable and
archive prefix is `coakka-client`.

The archive intentionally ships `coakka-client` as the command name. A
`coakka-runtime-client` executable is not part of this release surface.

Download [coakka-client 3.2.1](https://github.com/phuong-tran/coakka-publish/tree/86aa17f3d2af26bfd0faee3c02fbd9e82750592e/coakka-tools/coakka-client/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45) for the native CLI. The archive includes Core
3.2 and four offline license and notice files. See the [archive checksums and verification](https://github.com/phuong-tran/coakka-publish/blob/main/docs/coakka-runtime/releases/2026-11-05-runtime-tools-3.2.1.md).

The commands below exercise the separately pinned 2.5.1 walkthrough.

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

Run `coakka-client version` to read the bundled Core's transport profile.

Benchmarks and comparisons should stay at this runtime delivery boundary:
route selection, bounded admission, framing, reply matching, timeout, and
deadletter behavior. Do not frame runtime-client numbers as L7 HTTP/gRPC
replacement claims.

## Verification Posture

Use small verification first:

```sh
bash run.sh coakka-runtime client
bash -n run.sh coakka-runtime/tools/coakka-client/run.sh
```

Use Docker verification only when the Linux bundle path needs a live check:

```sh
bash run.sh coakka-runtime client docker-bundle
```

Use `docker-walkthrough` when the goal is a guided CLI experience. It reuses
the same published bundle but adds a temporary Compose override with two native
runtime service containers and prints the service, port, and route before each
`coakka-client` call. This remains sample orchestration; it does not add a new
runtime-core responsibility or a new published artifact lane.

The current public Docker Hub image
`docker.io/gabrielgun1983/coakka-runtime-client-demo:2.5.1-26f7944d-remote`
prebuilds that walkthrough into one container. It contains the published
`coakka-client` and native demo service artifacts for Linux amd64/arm64 and
starts two native runtime service processes before driving them from the CLI.
It is a sample-image lane, not the canonical binary artifact surface.

The animated walkthrough and MP4 recording in this repository are visual
product evidence. The command surface remains verified by scripts and artifact
checksum checks, not by the recording itself.
