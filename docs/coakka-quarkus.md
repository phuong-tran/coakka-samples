# CoAkka Quarkus

The CoAkka Quarkus extension lets a Quarkus application keep Quarkus at the
HTTP/API edge while moving selected application-owned work behind a CoAkka
runtime target.

It is useful when a JAX-RS resource, CDI bean, scheduled task, or application
service needs to call work that is still owned by the same app or deployment
contract, but the team wants a stronger runtime boundary than a direct function
call.

CoAkka keeps the Quarkus shape familiar:

```text
browser or API caller
  -> Quarkus resource
  -> authentication, authorization, validation, request mapping
  -> CoAkka target
  -> CDI-backed local handler or peer-runtime handler
  -> reply or deadletter
```

Quarkus still owns HTTP resources, CDI, configuration, application lifecycle,
native-image policy, validation, security, and the public response. CoAkka owns
runtime delivery: target naming, route selection, bounded admission,
request/reply, timeout, deadletter, health, and diagnostics.

## When To Use It

Use the Quarkus extension when:

- the caller is already in a Quarkus application
- the current handoff is app-owned work, not a public service API
- the target may start in the same process and move to another runtime host
  later
- the team wants route, reply, timeout, and deadletter diagnostics instead of
  private HTTP endpoints

Keep normal HTTP clients when the target is a real HTTP service API with
independent ownership, path/status semantics, headers, interceptors,
authentication propagation, and platform governance.

## Before: Fake Backend HTTP

The browser/API route is real HTTP. The fake part is adding a second private
backend endpoint only so app-owned store work has something URL-shaped to call:

```kotlin
@Path("/backend/customers")
class CustomerStoreResource(private val store: InMemoryCustomerStore) {
    @POST
    fun create(request: CustomerDraft): MutationResponse {
        return store.create(request)
    }
}
```

The public resource then forwards the same customer command through that private
HTTP client:

```kotlin
@Path("/api/customers")
class CustomerResource(private val storeClient: CustomerStoreRestClient) {
    @POST
    fun create(request: CustomerDraft): MutationResponse {
        return storeClient.create(request)
    }
}
```

That is correct when `/backend/customers` is a real service API. It becomes
fake HTTP when the endpoint exists only to provide an address for capability
code owned by the same app or team.

## After: Runtime Capability

With the Quarkus extension, the handler becomes a runtime capability:

```kotlin
@ApplicationScoped
@CoAkkaHandler("samples.customer.store")
class CustomerCapabilityHandler(
    private val store: InMemoryCustomerStore,
) : CoAkkaLocalHandler {
    override fun handle(
        request: ConnectorEnvelope,
        objectMapper: ObjectMapper,
    ): ConnectorEnvelope {
        // Decode the app payload, run the store operation, and return a reply.
    }
}
```

The resource still owns real HTTP ingress, but it asks a runtime target rather
than a backend HTTP endpoint:

```kotlin
@POST
@Path("/api/customers")
fun create(request: CustomerDraft): MutationResponse {
    return coakka.askBlocking(
        "samples.customer.store",
        request,
        CustomerPayloads.CREATE,
        MutationResponse::class.java,
        "create_customer",
        5_000,
    )
}
```

The call site keeps a business-level target name instead of private URL config.
The handler can remain CDI-backed and same-process today, then move behind
another runtime route later without turning it into a fake REST API first.

## Public Artifact

Current public coordinates are listed in the
[compatibility matrix](https://github.com/phuong-tran/coakka-publish/blob/main/docs/compatibility-matrix.md).
The Quarkus extension lane is published to Maven Central:

```kotlin
implementation("io.github.phuong-tran.coakka:quarkus-extension:2.5.2")
```

Only Maven Central is required:

```kotlin
repositories {
    mavenCentral()
}
```

Do not mix Quarkus extension versions with an unrelated CoAkka runtime
generation unless a release note explicitly declares that combination
compatible.

## Sample Path

The runnable Quarkus sample is:

```text
runtime/scenarios/customer-crud/quarkus-local
```

It shows a Quarkus HTTP edge, CDI-backed `@CoAkkaHandler` runtime target, and a
`CoAkkaRuntimeClient` at the resource call site.

For cross-process JVM and polyglot runtime examples, read the Spring Boot
customer scenarios such as `spring-boot-node`, `spring-boot-go`,
`spring-boot-csharp`, and `spring-boot-spring-boot`. They use the same runtime
target, reply, and deadletter vocabulary.

## Related Docs

- [Incremental Adoption](incremental-adoption.md)
- [Runtime Integration Guide](runtime-integration-guide.md)
- [Runtime Message And Routing Model](runtime-message-and-routing-model.md)
- [Questions And Answers](qna.md)
