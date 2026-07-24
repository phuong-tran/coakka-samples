# CoAkka Runtime Client Usage Guide

The sample runner resolves and verifies the published `coakka-client` archive
before running it:

```sh
bash run.sh runtime-client
bash run.sh runtime-client version
bash run.sh runtime-client doctor
```

For direct CLI usage after unpacking a published archive, run:

```sh
coakka-client --help
coakka-client call --help
coakka-client shell --help
```

The installed command is `coakka-client`. `coakka-runtime-client` is the
product lane and sample directory name, not a command shipped in this release.

## Command Discovery

Top-level help lists the public command surface:

```sh
coakka-client --help
```

Primary commands:

- `version`: print linked runtime/client build profile.
- `doctor`: check whether the binary can drive the current CLI request path.
- `call`: send one request and return one result.
- `ask`: alias for `call`, using request/reply semantics.
- `shell`: run an interactive or script-driven session over the same request
  surface.
- `help`: show command help.

## Diagnostics

Use diagnostics first when verifying a package:

```sh
coakka-client version --output json
coakka-client doctor --output json
```

Useful fields include:

- `runtime_version`
- `git_commit`
- `abi_version`
- `feature_flags_text`
- `southbound_backend`
- `remote_wire_profile`
- `remote_wire_profile_version`

## Request/Reply

Use `call` when you want one request and one explicit result:

```sh
coakka-client call \
  --connect 127.0.0.1:19091 \
  --route customer.create \
  --payload customer#42
```

`ask` is the request/reply alias for the same path:

```sh
coakka-client ask \
  --connect 127.0.0.1:19091 \
  --route customer.create \
  --payload customer#ask
```

The endpoint can also be split:

```sh
coakka-client call \
  --host 127.0.0.1 \
  --port 19091 \
  --route customer.create \
  --payload customer#42
```

## Payloads And Metadata

Choose one payload source:

- `--payload <text>`
- `--payload-file <path>`
- `--payload-file -` for stdin
- `--json <text>`
- `--json-file <path>`
- `--json-file -` for stdin

Common metadata options:

- `--content-type <type>`
- `--message-type <type>` or `--type <type>`
- `--schema-version <n>`
- `--header key=value`, repeatable
- `--timeout-ms <ms>`
- `--output raw|json`

Example JSON payload file:

```sh
coakka-client call \
  --connect 127.0.0.1:19091 \
  --route customer.create \
  --payload-file customer-create.json \
  --content-type application/json \
  --output json
```

## Shell Script Mode

Use `shell --script` when the same request path should be repeatable in CI,
release verification, or local diagnostics:

```sh
coakka-client shell \
  --connect 127.0.0.1:19091 \
  --script customer-create-shell.coakka
```

Shell sessions can set and reuse state:

```text
route customer.create
payload customer#script
call
payload customer#script-ask
ask
output json
content-type application/json
payload {"customer_id":"script-json","tier":"gold"}
call
\quit
```

Useful shell commands include `connect`, `host`, `port`, `route`, `payload`,
`json`, `payload-file`, `json-file`, `header`, `content-type`, `message-type`,
`schema-version`, `timeout`, `output`, `source`, `show`, `reset`, `call`,
`ask`, `help`, and `\quit`.

## Docker Verification Bundle

Use the Docker path when live Linux bundle verification is needed without a
host toolchain:

```sh
bash run.sh runtime-client docker-bundle
```

The command resolves the published Docker verification bundle, builds the tiny
CLI and customer-service images from the staged artifacts, then verifies
`call`, `ask`, and `shell --script` request/reply round-trips.

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

The older `docker-demo` runner command remains a compatibility alias for the
existing artifact layout.

Use the guided walkthrough when the goal is to see the Docker CLI workflow:

```sh
bash run.sh runtime-client docker-walkthrough
```

That command resolves the same published Docker bundle, starts two native
runtime service containers, prints the service names, ports, and routes, then
uses the CLI container to call both runtimes:

```text
service=customer-east port=19091 route=customer.east.create
service=customer-west port=19091 route=customer.west.create
```

The final check is a `coakka-client shell --script` run that switches from
`customer-east:19091` to `customer-west:19091` inside one CLI shell session.

## Docker Hub Demo Image

Use the Docker Hub image when the goal is a one-command runtime-client demo:

```sh
docker run --rm docker.io/gabrielgun1983/coakka-runtime-client-demo:1.3.1-0da8c2d9-remote
```

The default command starts two native runtime services inside the container and
uses `coakka-client` to call both. To run the packaged CLI directly:

```sh
docker run --rm docker.io/gabrielgun1983/coakka-runtime-client-demo:1.3.1-0da8c2d9-remote client --help
docker run --rm docker.io/gabrielgun1983/coakka-runtime-client-demo:1.3.1-0da8c2d9-remote client version --output json
```
