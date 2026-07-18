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
bash run.sh runtime-client docker-demo
```

The command resolves the published Docker verification bundle, builds the tiny
CLI and customer-service images from the staged artifacts, then verifies
`call`, `ask`, and `shell --script` request/reply round-trips.
