# Spring Boot to C# Customer CRUD

This scenario runs:

| Service | Language | Role | HTTP | Runtime target | Runtime endpoint |
| --- | --- | --- | --- | --- | --- |
| `customer-web` | JVM / Spring Boot | browser UI and HTTP API | `8081` | `samples.customer.frontend` | `127.0.0.1:19141` |
| `customer-store-csharp` | C# / .NET | headless in-memory customer table | none | `samples.customer.store` | `127.0.0.1:19142` |

The Spring Boot web service reuses the same customer UI/API from the
Spring-to-Spring scenario. The C# store uses the published
`CoAkka.Runtime==1.4.5` NuGet package and runs as a headless message handler.

## Runtime Transport Note

This scenario expects a runtime with cross-process delivery enabled v2 artifact. The web service
sends business requests only through the runtime route. There is no C# store
REST fallback, so delivery failures are returned explicitly instead of being
hidden by HTTP fallback behavior.

The web UI is shared with the Spring-to-Spring scenario. Store state is visible
only through the web service list action, which is also a runtime message.

## Run

From this directory:

```sh
bash run.sh
```

The default command is `check`: it builds the Spring Boot web jar and compiles
the C# store against the published connector package without keeping servers
running.

To start the services, run:

```sh
bash run.sh dev
```

This prepares the C# store, builds the Spring Boot web jar, and starts both
processes. To run services manually, use separate terminals:

```sh
bash run.sh store
bash run.sh web
```

Open:

- Customer Web: `http://localhost:8081`

## Smoke

With both services running:

```sh
bash run.sh smoke
```

The smoke creates, updates, lists, and deletes a customer through the web API.
It also verifies diagnostics and the intentional route-miss behavior.

## Check Without Running Servers

```sh
bash run.sh check
```

This builds the Spring Boot web jar and compiles the C# store against the
published NuGet package inside a temporary workspace.

## Stop

```sh
bash run.sh stop
```
