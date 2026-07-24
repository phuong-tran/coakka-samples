# Spring Boot Starter Same-Process Customer CRUD

This scenario is the same-process Spring Boot starter customer CRUD path.

It keeps HTTP at the browser/API boundary and exposes customer work as
runtime capabilities:

```kotlin
@CoAkkaHandler("samples.customer.create")
fun create(command: CustomerDraft): MutationResponse
```

The app does not configure remote endpoints. The starter scans
`@CoAkkaHandler` methods, starts the runtime with process-owned routes for those
targets, registers typed handlers, and exposes a `CoAkkaRuntimeClient` bean for
the controller.

This starter path targets the normal Spring Boot JVM path first. Spring Boot
native/AOT should not depend on runtime annotation scanning as the only handler
registration mechanism; use the explicit runtime connector path today, and
expect a generated or declared handler registry shape before treating the
starter as native-image friendly.

This slice is same-process and is smoked on Linux in CI. Remote/Kubernetes mode
should wait until the starter API shape is boring.

## Dev Loop

Single process should keep the normal Spring Boot CRUD loop. This starter
discovers `@CoAkkaHandler` methods at application startup, so changing the set
of handler methods still requires a restart today.

The intended starter direction is devtools-friendly: after a Spring context
refresh, rebuild the process-owned handler registry from refreshed beans and apply a new
runtime route generation. The HTTP edge should stay unchanged, and local
development should not need a backend HTTP service just to feel comfortable.

The sample consumes the public Spring Boot starter artifact:

```kotlin
implementation("coakka.spring:coakka-spring-boot-starter:1.3.1-g0da8c2d9-8ff6f32")
```

That starter depends on the shared runtime JVM artifact
`coakka.v2:coakka-jvm-native-runtime-v2:1.3.1-g0da8c2d9-8ff6f32`; it does not
bundle or fork a Spring-specific native runtime.

The sample resolves the Maven artifact from the public publish surface.

## Before: Backend HTTP

A Spring Boot team that wants to separate “web” from “store” usually invents an
backend HTTP hop, even when both sides still live in the same development
topology:

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
}
```

The store side becomes another controller just so the web controller has
something HTTP-shaped to call:

```kotlin
@RestController
@RequestMapping("/backend/customers")
class CustomerStoreBackendController(private val store: InMemoryCustomerStore) {
    @PostMapping
    fun create(@RequestBody request: CustomerDraft): MutationResponse {
        return store.create(request)
    }

    @GetMapping
    fun list(): ListResponse {
        return store.list()
    }
}
```

The web side then needs an HTTP client for traffic that is not a real external
API boundary:

```kotlin
@Component
class CustomerStoreRestClient(
    builder: RestClient.Builder,
    @Value("\${customer.store.base-url}") storeBaseUrl: String,
) {
    private val rest = builder.baseUrl(storeBaseUrl).build()

    fun create(request: CustomerDraft): MutationResponse {
        return rest.post()
            .uri("/backend/customers")
            .body(request)
            .retrieve()
            .body(MutationResponse::class.java)
            ?: error("empty store response")
    }
}
```

And the public controller ends up forwarding CRUD work over that backend HTTP
surface:

```kotlin
@RestController
@RequestMapping("/api/customers")
class CustomerController(private val storeClient: CustomerStoreRestClient) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCustomer(@RequestBody request: CustomerDraft): MutationResponse {
        return storeClient.create(request)
    }
}
```

That works, but it makes an application implementation detail look like an HTTP
product boundary. It also adds URL config, HTTP serialization, error mapping,
timeouts, and test setup around a call that is still application work.

That is the shape this starter is meant to avoid. The controller stays the real
HTTP edge, while customer work moves onto a typed runtime target with
request/reply, counters, and deadletters.

## After: Same-Process Runtime Capability

With the starter, the application declares same-process runtime defaults in config and
keeps business work as ordinary Spring beans:

```kotlin
dependencies {
    implementation("coakka.spring:coakka-spring-boot-starter:1.3.1-g0da8c2d9-8ff6f32")
    implementation("org.springframework.boot:spring-boot-starter-web")
}
```

```yaml
coakka:
  runtime:
    enabled: true
    mode: local
    system-name: customer-starter-local
    node-id: customer-starter-local-node
    source-target: samples.customer.frontend
    local-endpoint-host: 127.0.0.1
    local-endpoint-port: 19172
```

```kotlin
@Component
class CustomerCapabilityHandlers(private val customerStore: InMemoryCustomerStore) {
    @CoAkkaHandler(CustomerTargets.CREATE)
    fun create(command: CustomerDraft): MutationResponse {
        return customerStore.create(command)
    }
}
```

The HTTP controller now reads like CRUD code again. It asks a runtime capability
target; it does not construct route tables, decode envelopes, or own native
runtime lifecycle:

```kotlin
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
fun createCustomer(@RequestBody request: CustomerDraft): MutationResponse {
    return runtimeClient.askBlocking(
        CustomerTargets.CREATE,
        request,
        MutationResponse::class.java,
        "create_customer",
        5_000,
    )
}
```

That is the intended starter value: keep the runtime boundary
visible at the controller call-site, but move route derivation, handler
registration, payload mapping, and shutdown into the starter.

## Code Map

Start with `CustomerStarterLocalApplication.kt`; it only boots Spring and logs
the runtime route count. The runtime capability path is split by role:

- `CustomerTargets.kt` names the runtime targets.
- `CustomerCapabilityHandlers.kt` exposes CRUD work with `@CoAkkaHandler`.
- `CustomerStarterLocalController.kt` keeps REST at `/api/customers`.
- `InMemoryCustomerStore.kt` is the ordinary Spring service behind the handlers.
- `CustomerRuntimeDiagnosticsController.kt` keeps smoke-only runtime diagnostics
  away from the CRUD path.

## Run

```sh
bash run.sh check
bash run.sh dev
```

Open:

```text
http://localhost:8082
```

Smoke:

```sh
bash run.sh smoke
```

Ports:

- HTTP: `8082`
- runtime diagnostic endpoint: `19172`

## Boundary Shape

The controller owns real HTTP ingress. It calls runtime targets through
`CoAkkaRuntimeClient`.

The customer store remains an ordinary Spring service. Capability methods are
thin adapters that receive typed command objects and return typed responses.

Remote transport, Kubernetes bind/advertise config, service discovery, and
business retry policy are deliberately out of scope for this same-process slice.
