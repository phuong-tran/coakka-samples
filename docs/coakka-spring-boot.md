# CoAkka Spring Boot

The CoAkka Spring Boot starter lets a Spring application keep Spring at the
HTTP/API edge while moving selected application-owned work behind a CoAkka
runtime target.

It is useful when a Spring controller, scheduled job, or service currently
calls another private backend endpoint only because a handler needed an address.
That private endpoint is often not a real product API. It is a deployment
artifact around work the application still owns.

CoAkka keeps that distinction explicit:

```text
browser or API caller
  -> Spring MVC/WebFlux controller
  -> authentication, authorization, validation, request mapping
  -> CoAkka target
  -> same-process or peer-runtime handler
  -> reply or deadletter
```

Spring still owns HTTP, dependency injection, configuration, application
lifecycle, transactions, validation, security, and the public response. CoAkka
owns runtime delivery: target naming, route selection, bounded admission,
request/reply, timeout, deadletter, health, and diagnostics.

## When To Use It

Use the Spring Boot starter when:

- the caller is already in a Spring Boot application
- the current handoff is app-owned work, not a public service API
- the work should be movable from same-process to another process or host
  without changing the caller's business boundary
- the team wants runtime-level diagnostics instead of private HTTP plumbing

Keep normal Spring HTTP clients, including OpenFeign, when the target is a real
HTTP service API with independent ownership, URL/discovery policy, HTTP status
semantics, headers, interceptors, and platform governance.

## Before: Fake Backend HTTP

The browser/API route is real HTTP. The fake part is adding a second private
backend endpoint only so app-owned store work has something URL-shaped to call:

```kotlin
@RestController
@RequestMapping("/backend/customers")
class CustomerStoreBackendController(private val store: InMemoryCustomerStore) {
    @PostMapping
    fun create(@RequestBody request: CustomerDraft): MutationResponse {
        return store.create(request)
    }
}
```

The browser-facing controller then forwards the same customer command through
that private HTTP client:

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

That is a good shape when `/backend/customers` is a true HTTP service contract.
It becomes fake HTTP when the endpoint exists only to wrap capability code owned
by the same app or team.

## After: Runtime Capability

With the Spring Boot starter, the handler becomes a runtime capability:

```kotlin
@Component
class CustomerCapabilityHandlers(private val store: InMemoryCustomerStore) {
    @CoAkkaHandler("samples.customer.create")
    fun create(command: CustomerDraft): MutationResponse {
        return store.create(command)
    }
}
```

The controller still owns real HTTP ingress, but it asks a runtime target rather
than a backend HTTP endpoint:

```kotlin
@PostMapping("/api/customers")
@ResponseStatus(HttpStatus.CREATED)
fun create(@RequestBody request: CustomerDraft): MutationResponse {
    return runtimeClient.askBlocking(
        "samples.customer.create",
        request,
        MutationResponse::class.java,
        "create_customer",
        5_000,
    )
}
```

The call site keeps a business-level target name instead of private URL config.
The handler can start in the same process and move behind another runtime route
later without promoting it into a fake REST API first.

## Public Artifact

Current public coordinates are listed in the
[compatibility matrix](https://github.com/phuong-tran/coakka-publish/blob/main/docs/compatibility-matrix.md).
The Spring Boot starter lane is published to Maven Central:

```kotlin
implementation("io.github.phuong-tran.coakka:spring-boot-starter:2.5.2")
```

Only Maven Central is required:

```kotlin
repositories {
    mavenCentral()
}
```

Do not mix Spring Boot starter versions with an unrelated CoAkka runtime
generation unless a release note explicitly declares that combination
compatible.

## Sample Path

The runnable Spring Boot starter sample is:

```text
runtime/scenarios/customer-crud/spring-boot-starter-local
```

It shows a Spring Boot web edge, same-process `@CoAkkaHandler` targets, and a
`CoAkkaRuntimeClient` bean at the controller call site.

For a lower-level JVM/Spring shape without the starter's annotation support,
read:

```text
runtime/scenarios/customer-crud/spring-boot-single-process
```

For a real cross-process handoff from Spring Boot to another runtime host, read
the Spring Boot customer scenarios such as `spring-boot-node`, `spring-boot-go`,
`spring-boot-csharp`, and `spring-boot-spring-boot`.

## Related Docs

- [Incremental Adoption](incremental-adoption.md)
- [Runtime Integration Guide](runtime-integration-guide.md)
- [Runtime Message And Routing Model](runtime-message-and-routing-model.md)
- [Questions And Answers](qna.md)
