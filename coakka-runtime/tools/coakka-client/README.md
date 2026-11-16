# CoAkka Runtime Client Samples

`coakka-runtime-client` is the CLI runtime client lane for CoAkka Runtime.
The published command and archive names use `coakka-client`.

The native [coakka-client 3.2.1](https://github.com/phuong-tran/coakka-publish/tree/86aa17f3d2af26bfd0faee3c02fbd9e82750592e/coakka-tools/coakka-client/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45) archives use Core 3.2.
The sample runner and Docker walkthrough below exercise the 2.5.1 snapshot.

Run `coakka-client help` after unpacking a published archive. If
`coakka-runtime-client` is not found, that is expected for this release: it is
the product lane and sample directory name, not the executable name.

Detailed docs live under [docs/](../../../docs/coakka-runtime/tools/coakka-client/):

- [Introduction](../../../docs/coakka-runtime/tools/coakka-client/introduction.md)
- [Usage Guide](../../../docs/coakka-runtime/tools/coakka-client/usage.md)
- [Technical Notes](../../../docs/coakka-runtime/tools/coakka-client/technical-notes.md)

This lane is for native CLI workflows that show how an operator or developer
can drive a runtime path without building a web UI:

- print version and runtime build diagnostics
- run doctor checks against a packaged client
- submit request/reply calls to runtime targets
- run bounded shell-script verification
- verify Docker bundles without requiring a host toolchain

It is not an inspect dashboard, a topology authority, or a business schema
registry. Topology, route ownership, delivery semantics, and capability truth
stay in CoAkka Runtime. Sample payloads stay owned by the sample workflow.

## CLI Walkthrough

![CoAkka Runtime Client CLI walkthrough](../../../docs/coakka-runtime/assets/coakka-runtime-client.gif)

The walkthrough shows the packaged `coakka-client` driving a native runtime
host over the TCP frame profile. It opens with CLI help for discoverability,
then runs `version`, `doctor`, `call`, `ask`, a JSON payload-file request, and
`shell --script`.

Full recording: [coakka-runtime-client.mp4](../../../docs/coakka-runtime/assets/coakka-runtime-client.mp4)

## Quick Check

Run the published CLI runtime client smoke:

```sh
bash run.sh coakka-runtime client
bash run.sh coakka-runtime client version
bash run.sh coakka-runtime client doctor
bash run.sh coakka-runtime client docker-walkthrough
bash run.sh coakka-runtime client dockerhub-demo
```

Smoke the current local product binary without resolving a public archive:

```sh
COAKKA_RUNTIME_CLIENT_BIN=/absolute/path/to/coakka-client \
  bash run.sh coakka-runtime client local-smoke
```

The runner resolves the matching archive from a sibling `coakka-publish`
checkout when present, otherwise it falls back to the public raw artifact URL.
Every artifact is verified against `artifacts/public-artifacts.tsv` before it
is unpacked.

Run the zero-install Linux Docker verification bundle when live Linux bundle
verification is needed:

```sh
bash run.sh coakka-runtime client docker-bundle
```

Abbreviated expected shape; Docker Compose status lines and generated message
IDs may vary:

```text
created:customer#42
created:customer#ask
created:customer#script
created:customer#script-ask
{
  "ok": true,
  "message_kind": "MESSAGE_KIND_RESPONSE",
  "payload_text": "created:{\"customer_id\":\"script\",\"tier\":\"violet\"}"
}
```

That command resolves the published Docker verification bundle for the host
architecture, builds the tiny CLI and customer-service images from the staged
artifacts, then verifies `call`, `ask`, and `shell --script` request/reply
round-trips.

Run the guided Docker CLI walkthrough when the goal is to see the runtime-client
experience rather than just verify the bundle:

```sh
bash run.sh coakka-runtime client docker-walkthrough
```

That path starts two native runtime service containers from the same published
bundle:

```text
service=customer-east port=19091 route=customer.east.create
service=customer-west port=19091 route=customer.west.create
```

Then it runs `coakka-client` from the CLI container against both services and
runs one shell script that switches endpoints inside the same CLI session.

Run the published Docker Hub image when the goal is the lowest-friction Docker
path:

```sh
docker run --rm docker.io/gabrielgun1983/coakka-runtime-client-demo:2.5.1-26f7944d-remote
```

or:

```sh
bash run.sh coakka-runtime client dockerhub-demo
```

The image starts two native runtime services inside the container, prints their
service names, ports, and routes, then runs the packaged `coakka-client`
against both services. Passing `client` runs the CLI directly:

```sh
docker run --rm docker.io/gabrielgun1983/coakka-runtime-client-demo:2.5.1-26f7944d-remote client --help
```

## Published Release

Download [coakka-client 3.2.1](https://github.com/phuong-tran/coakka-publish/tree/86aa17f3d2af26bfd0faee3c02fbd9e82750592e/coakka-tools/coakka-client/releases/3.2.1%2Bf9e5a632d2d046a5b4e89d5ce0c9042aee5c7b45) and unpack the archive for your platform.
Run `bin/coakka-client version` on macOS/Linux or
`bin\coakka-client.exe version` on Windows. The command reports Core 3.2.
See the [archive checksums and verification](https://github.com/phuong-tran/coakka-publish/blob/main/docs/coakka-runtime/releases/2026-11-05-runtime-tools-3.2.1.md).

The matching Docker verification release is:

```text
coakka-tools/coakka-client/docker-demo/releases/2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be/
```

The `demo/` path segment is part of the already-published artifact layout.
Public docs and commands describe this as the Linux Docker verification bundle.
The runner still accepts `docker-demo` as a compatibility alias.

The sample runner also provides `docker-walkthrough`, which uses that published
bundle to bring up two native runtime services and drive them with the packaged
`coakka-client` inside Docker. That is a sample experience command, not a
separate published artifact.

The current public Docker Hub demo image is:

```text
docker.io/gabrielgun1983/coakka-runtime-client-demo:2.5.1-26f7944d-remote
```

Its immutable digest is
`sha256:30586188a7a400b8085a3eed475ad086761f1506816ac6d1b70887fe901958e6`.
It is a prebuilt convenience image for the same runtime-client walkthrough.
The public release archives and checksums remain in `coakka-publish`.

## CLI Runtime Path

The product path is the same on every supported platform: run a native CoAkka
Runtime host, then use the packaged `coakka-client` command to drive the
runtime target and inspect the explicit reply, timeout, or deadletter outcome.

Linux, Windows, macOS, and Docker lanes are verification targets for the same
CLI contract. Platform-specific packaging should prove that the same commands
and runtime outcomes work from the published artifact layout.

Future runnable samples in this directory should prefer this shape:

```text
native runtime host
  <- TCP frame profile
coakka-client call or ask
  -> runtime target
  -> reply, timeout, or deadletter
```

Keep the sample small enough that a user can see the CLI contract directly:
start host, run `version`, run `doctor`, submit one request/reply call, and stop
the host cleanly.
