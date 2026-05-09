# Two-Machine Linux Walkthrough

This walkthrough runs the customer CRUD Spring Boot scenario across two Linux
machines:

| Machine | Process | Runtime target | Runtime port | HTTP |
| --- | --- | --- | --- | --- |
| A | `customer-store` | `samples.customer.store` | `19102` | none |
| B | `customer-web` | `samples.customer.frontend` | `19101` | `8081` |

The browser or smoke command talks to `customer-web`. Customer create, update,
delete, and list requests are intended to move from `customer-web` to
`customer-store` through the runtime route.

Current public artifact note: this walkthrough uses the public TCP transport
candidate package. It is intended to validate Linux setup, route configuration,
process ownership, and end-to-end runtime delivery without a store REST API
fallback.

## Requirements

- Same `coakka-samples` commit on both machines.
- JDK 17 or newer on both machines.
- `curl` on machine B for smoke checks.
- Machine A accepts TCP `19102` from machine B.
- Machine B accepts TCP `19101` from machine A.
- Machine B accepts TCP `8081` from the browser or smoke client.

The examples below use:

```sh
STORE_HOST=10.0.0.10
WEB_HOST=10.0.0.11
```

Replace those values with the routable Linux addresses for the two machines.

## Build

Run on both machines from the repository root:

```sh
bash run.sh scenario customer-crud spring-boot-spring-boot build
```

## Start Machine A

On machine A, start the headless store process:

```sh
export STORE_HOST=10.0.0.10
export WEB_HOST=10.0.0.11

SAMPLE_CONNECTOR_LOCAL_HOST="$STORE_HOST" \
SAMPLE_CONNECTOR_PEER_HOST="$WEB_HOST" \
bash run.sh runtime/scenarios/customer-crud/spring-boot-spring-boot store
```

Keep this process running. It owns the `samples.customer.store` runtime target.

## Start Machine B

On machine B, start the web process:

```sh
export STORE_HOST=10.0.0.10
export WEB_HOST=10.0.0.11

SAMPLE_CONNECTOR_LOCAL_HOST="$WEB_HOST" \
SAMPLE_CONNECTOR_PEER_HOST="$STORE_HOST" \
SERVER_PORT=8081 \
bash run.sh runtime/scenarios/customer-crud/spring-boot-spring-boot web
```

Open the UI from a browser that can reach machine B:

```text
http://10.0.0.11:8081
```

## Smoke

Run on machine B while both processes are up:

```sh
bash run.sh runtime/scenarios/customer-crud/spring-boot-spring-boot smoke
```

The smoke command should return customer business responses with runtime
delivery. If delivery fails, the sample should surface an explicit runtime
error instead of falling back to a store REST API.

Inspect the runtime route config exposed by the web process:

```sh
curl -fsS http://127.0.0.1:8081/api/customers/runtime | python3 -m json.tool
```

The response should show `localEndpoint` on machine B and `peerEndpoint` on
machine A.

Successful business responses should include:

```json
"deliveryMode": "runtime"
```

## Stop

Stop the foreground Java processes with `Ctrl-C`.

If a previous local run left ports open, run this on the relevant machine:

```sh
bash run.sh runtime/scenarios/customer-crud/spring-boot-spring-boot stop
```

## Troubleshooting

If a cross-host delivery-capable runtime package is installed and smoke returns
a runtime delivery failure, check these first:

- `STORE_HOST` and `WEB_HOST` are the addresses the other machine can reach.
- TCP `19101`, `19102`, and `8081` are allowed by the host firewall.
- Both machines are running the same sample commit and artifact pins.
- The route generation remains the same on both processes for this static
  scenario.

This walkthrough is a manual Linux setup guide. It does not publish benchmark
numbers or production capacity claims.
