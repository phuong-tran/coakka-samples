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

## Runtime Transport Note

This scenario expects a remote-capable runtime v2 artifact. The web service
sends customer business requests only through the runtime route; there is no
store REST fallback. If the runtime cannot deliver to `customer-store`, the web
API returns an explicit runtime delivery failure so the issue is visible in the
UI and smoke output.

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

It also exposes `POST /api/customers/runtime/reload-routes` for applying a
newer route snapshot while both processes are running. The local development
snapshot lives in `routes.yml`:

```sh
bash run.sh reload-routes
```

The command posts the YAML snapshot to `customer-web`; `customer-web` validates
that the snapshot generation is newer than the active generation, applies it
atomically, and returns the generation before and after the apply.

Business responses include `deliveryMode` so smoke tests cannot hide which path
handled the request:

| Value | Meaning |
| --- | --- |
| `runtime` | The customer request was handled through the runtime route. |

Create, Update, Delete, and List from the web API should return `deliveryMode:
runtime`. The separate `Route miss` action remains the intentional deadletter
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

The smoke reloads routes from `routes.yml`, creates, updates, lists, and deletes
`cust-001` through the web service API. It also checks the intentional route-miss
diagnostic and fails if customer traffic returns a runtime delivery failure.

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
