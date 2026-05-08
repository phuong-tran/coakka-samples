# Spring Boot Starter Local Customer CRUD

This scenario is the local-first Spring Boot starter customer CRUD path.

It keeps HTTP at the browser/API boundary and exposes internal customer work as
runtime capabilities:

```kotlin
@CoAkkaHandler("samples.customer.create")
fun create(command: CustomerDraft): MutationResponse
```

The app does not configure remote endpoints. The starter scans
`@CoAkkaHandler` methods, starts the runtime with local routes for those
targets, registers typed handlers, and exposes a `CoAkkaRuntimeClient` bean for
the controller.

This starter path targets the normal Spring Boot JVM path first. Spring Boot
native/AOT should not depend on runtime annotation scanning as the only handler
registration mechanism; use the explicit runtime connector path today, and
expect a generated or declared handler registry shape before treating the
starter as native-image friendly.

This slice is local-first and is smoked on Linux in CI. Remote/Kubernetes mode
should wait until the local API shape is boring.

## Dev Loop

Single process should keep the normal Spring Boot CRUD loop. This starter
discovers `@CoAkkaHandler` methods at application startup, so changing the set
of handler methods still requires a restart today.

The intended starter direction is devtools-friendly: after a Spring context
refresh, rebuild the local handler registry from refreshed beans and apply a new
local runtime route generation. The HTTP edge should stay unchanged, and local
development should not need an internal REST service just to feel comfortable.

The sample consumes the published starter artifact:

```kotlin
implementation("coakka.spring:coakka-spring-boot-starter:0.1.0-g432bd75d3e4b")
```

That starter depends on the shared runtime JVM artifact
`coakka.v2:coakka-jvm-native-runtime-v2:0.1.1-g22f571fd955c`; it does not bundle
or fork a Spring-specific native runtime.

The starter source lives in the connector workspace. This sample only consumes
the published Maven artifact from `coakka-publish`.

## Before: Internal REST

A Spring Boot team that wants to separate “web” from “store” usually invents an
internal REST hop, even when both sides still live in the same local development
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
@RequestMapping("/internal/customers")
class CustomerStoreInternalController(private val store: InMemoryCustomerStore) {
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
            .uri("/internal/customers")
            .body(request)
            .retrieve()
            .body(MutationResponse::class.java)
            ?: error("empty store response")
    }
}
```

And the public controller ends up forwarding CRUD work over that internal REST
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

That works, but it makes an internal implementation detail look like an HTTP
product boundary. It also adds URL config, HTTP serialization, error mapping,
timeouts, and test setup around a call that is still just local business work.

That is the cost this starter is meant to remove. The old shape pays for the
Spring MVC/WebClient stack even when the store is just an internal capability.
CoAkka keeps the controller as the real HTTP edge and moves internal work onto
a typed runtime target with request/reply, counters, and deadletters.

## After: Local Runtime Capability

With the starter, the application declares local runtime defaults in config and
keeps business work as ordinary Spring beans:

```kotlin
dependencies {
    implementation("coakka.spring:coakka-spring-boot-starter:0.1.0-g432bd75d3e4b")
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

The HTTP controller now reads like CRUD code again. It asks a local capability
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

That is the intended local-first starter value: keep the runtime boundary
visible at the controller call-site, but move route derivation, handler
registration, payload mapping, and shutdown into the starter.

## Code Map

Start with `CustomerStarterLocalApplication.kt`; it only boots Spring and logs
the runtime route count. The local capability path is split by role:

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
- local runtime diagnostic endpoint: `19172`

## Boundary Shape

The controller owns real HTTP ingress. It calls runtime targets through
`CoAkkaRuntimeClient`.

The customer store remains an ordinary Spring service. Capability methods are
thin adapters that receive typed command objects and return typed responses.

Remote transport, Kubernetes bind/advertise config, service discovery, TLS, and
business retry policy are deliberately out of scope for this local-first slice.
