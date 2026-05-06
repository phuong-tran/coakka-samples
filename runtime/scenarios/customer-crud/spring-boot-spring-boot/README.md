# Spring Boot to Spring Boot Customer CRUD

This scenario runs two Spring Boot processes:

| Service | Role | HTTP | Runtime target | Runtime endpoint |
| --- | --- | --- | --- | --- |
| `customer-web` | browser UI and HTTP API | `8081` | `samples.customer.frontend` | `127.0.0.1:19101` |
| `customer-store` | in-memory customer table | `8082` | `samples.customer.store` | `127.0.0.1:19102` |

The browser talks to `customer-web`. `customer-web` sends typed runtime
requests to `customer-store`, and `customer-store` replies with mutation/list
results.

## Current Runtime Backend Note

The current public runtime v2 artifact used by `coakka-samples` reports
`backend=stub`. That artifact can run local primitive samples, but it cannot
deliver remote cross-process requests yet. The web service still tries the
runtime route first. When the stub backend rejects remote delivery, it falls
back to the store HTTP API so browser CRUD and smoke commands remain runnable.
Runtime diagnostics show the stub backend and deadletter counters.

The fallback is configured explicitly in
`customer-web/src/main/resources/application.yml`:

```yaml
sample:
  connector:
    local-target: samples.customer.frontend
    local-host: 127.0.0.1
    local-port: 19101
    peer-target: samples.customer.store
    peer-host: 127.0.0.1
    peer-port: 19102
    generation: 1
    store-http-base-url: http://127.0.0.1:8082
    store-http-fallback-enabled: true
```

`customer-store` uses the same config shape with local and peer reversed:

```yaml
sample:
  connector:
    local-target: samples.customer.store
    local-host: 127.0.0.1
    local-port: 19102
    peer-target: samples.customer.frontend
    peer-host: 127.0.0.1
    peer-port: 19101
    generation: 1
```

`/api/customers/runtime` shows the route config that was fed into the runtime:
`configuredGeneration`, `localEndpoint`, and `peerEndpoint`.

Business responses include `deliveryMode` so smoke tests cannot hide which path
handled the request:

| Value | Meaning |
| --- | --- |
| `runtime` | The customer request was handled through the runtime route. |
| `runtime-deadletter-http-fallback` | The runtime route returned a deadletter and web used the store HTTP fallback. |
| `store-http-direct` | The store HTTP API was called directly. |

With the current `backend=stub` runtime artifact, a successful Create, Update,
or Delete can still increase `Runtime deadletters` in the web monitor. That
means the runtime remote attempt failed first and the business operation then
succeeded through HTTP fallback. The browser also refreshes the customer list
after a mutation, so one button click can produce a deadletter for the mutation
attempt and another deadletter for the list attempt.

Shared request/response DTOs and message-type strings live in the
`customer-contract` module together with the payload schema version and format.
The web and store services still create their own runtime
`ConnectorPayloadIdentity` values because that type belongs to the JVM runtime
connector jar, but they now use one shared contract source for the message
names, schema metadata, and DTO shapes.

## Run

From this directory:

```sh
bash run.sh
```

The default command is `check`: it builds both Spring Boot jars without keeping
servers running.

To start the services, run:

```sh
bash run.sh store
```

In another terminal:

```sh
bash run.sh web
```

The runner builds the Spring Boot jar first, then starts it with `java -jar`.
That keeps shutdown output simpler than Gradle `bootRun`.

Open:

- Customer Web: `http://localhost:8081`
- Customer Store: `http://localhost:8082`

## Smoke

With both services running:

```sh
bash run.sh smoke
```

The smoke creates, updates, lists, and deletes `cust-001` through the web
service API. With the current `backend=stub` artifact, the web service uses the
HTTP store fallback after the runtime route reports a delivery deadletter.

## Stop

```sh
bash run.sh stop
```

## Expected Browser Flow

1. Open the web page.
2. Create a customer.
3. Edit the same customer.
4. Delete the customer.
5. Watch runtime counters and customer state update.

Both services expose runtime diagnostics beside the business state so the
process boundary is visible without reading source code.
