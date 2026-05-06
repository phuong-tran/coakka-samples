# Spring Boot to Spring Boot Customer CRUD

This scenario runs two Spring Boot processes:

| Service | Role | HTTP | Runtime target | Runtime endpoint |
| --- | --- | --- | --- | --- |
| `customer-web` | browser UI and HTTP API | `8081` | `samples.customer.frontend` | `127.0.0.1:19101` |
| `customer-store` | headless in-memory customer table | none | `samples.customer.store` | `127.0.0.1:19102` |

The browser talks to `customer-web`. `customer-web` sends typed runtime
requests to `customer-store`, and `customer-store` replies with mutation/list
results.

`customer-store` is a non-web Spring Boot process
(`spring.main.web-application-type: none`). It does not start Tomcat or expose
a store REST API; it stays alive only to serve the runtime handler.

## Current Runtime Backend Note

The current public runtime v2 artifact used by `coakka-samples` reports
`backend=stub`. That artifact can run local primitive samples, but it cannot
deliver remote cross-process requests yet. The web service still sends customer
business requests only through the runtime route. There is no store REST
fallback, so CRUD requests return `RUNTIME_DELIVERY_FAILED` until a
remote-capable backend is published. Runtime diagnostics show the stub backend
and deadletter counters.

The route table is configured in `customer-web/src/main/resources/application.yml`:

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

`customer-web` exposes `/api/customers/runtime`, which shows the route config
that was fed into the runtime:
`configuredGeneration`, `localEndpoint`, and `peerEndpoint`.

Business responses include `deliveryMode` so smoke tests cannot hide which path
handled the request:

| Value | Meaning |
| --- | --- |
| `runtime` | The customer request was handled through the runtime route. |

With the current `backend=stub` runtime artifact, Create, Update, Delete, and
List from the web API return a runtime delivery failure instead of using the
store REST API. The separate `Route miss` action is the intentional deadletter
diagnostic.

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
bash run.sh dev
```

This builds both jars, starts `customer-store` headless, starts `customer-web`,
and leaves the single UI/API at `http://localhost:8081`.

To run services manually, use separate terminals:

```sh
bash run.sh store
bash run.sh web
```

The runner builds the Spring Boot jar first, then starts it with `java -jar`.
That keeps shutdown output simpler than Gradle `bootRun`.

Open:

- Customer Web: `http://localhost:8081`

## Smoke

With both services running:

```sh
bash run.sh smoke
```

The smoke creates, updates, lists, and deletes `cust-001` through the web
service API only after a remote-capable runtime backend is available. With the
current `backend=stub` artifact, `bash run.sh smoke` verifies diagnostics,
route-miss behavior, and that create returns `503 RUNTIME_DELIVERY_FAILED`
instead of falling back to store REST.

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

The UI explains the process boundary directly: browser-to-web is HTTP,
web-to-store is CoAkka message/reply, and REST fallback is disabled.
