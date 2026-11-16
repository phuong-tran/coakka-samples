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
[compatibility matrix](https://github.com/phuong-tran/coakka-publish/blob/main/docs/coakka-runtime/reference/compatibility-matrix.md).
The Spring Boot starter lane is published to Maven Central:

```kotlin
implementation("io.github.phuong-tran.coakka:spring-boot-starter:3.2.1")
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

## JVM 3.2.1

The framework adapter and Runtime 3.2.1 are published on Maven Central.
Use Java 17 for the framework application. Run the
[framework customer application](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/scenarios/customer-crud/framework-registry)
without preparing a local CoAkka Maven repository.

The business samples below are written in Kotlin; the
[plain JVM first run](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/jvm/registry-basic)
also includes separate Java source and a Java run command. Use the
[local-package guide](../previews/3.0.0/runtime-frameworks-candidate.md) only
when intentionally qualifying a selected local artifact.

Core chooses CPU placement. Connection and monitor settings keep Core's
defaults until the application supplies a documented option. Read accepted settings through
`startupConfig()`; do not reconstruct them from application properties.
Monitor is OFF by default. To request collection for a new runtime instance,
set `coakka.runtime.monitor-enabled=true` in the framework's configuration.
Changing that setting requires a new instance. See
[Runtime Monitoring](../operations/runtime-monitoring.md) for the reasons and usage model.

## Sample Path

Use the registry build linked above to launch this source with published 3.2.1
dependencies. The source folder's local launcher is a separate qualification path.

The application handler source is in:

```text
coakka-runtime/scenarios/customer-crud/spring-boot-starter-local
```

It shows a Spring Boot web edge, same-process `@CoAkkaHandler` targets, and a
`CoAkkaRuntimeClient` bean at the controller call site.

For a lower-level JVM/Spring shape without the starter's annotation support,
read:

```text
coakka-runtime/scenarios/customer-crud/spring-boot-single-process
```

For a real cross-process handoff from Spring Boot to another runtime host, read
the Spring Boot customer scenarios such as `spring-boot-node`, `spring-boot-go`,
`spring-boot-csharp`, and `spring-boot-spring-boot`.

## Related Docs

- [Incremental Adoption](../explanation/incremental-adoption.md)
- [Runtime Integration Guide](runtime-integration-guide.md)
- [Runtime Message And Routing Model](../explanation/runtime-message-and-routing-model.md)
- [Questions And Answers](../reference/qna.md)
