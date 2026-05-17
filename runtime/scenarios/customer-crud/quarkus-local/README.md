# Quarkus Same-Process Customer CRUD

This scenario is the Quarkus/Kotlin counterpart to the Spring Boot
single-process sample.

It keeps HTTP at the browser/API boundary and sends internal customer work
through the Quarkus adapter shape:

```kotlin
coakka.askBlocking(
    "samples.customer.store",
    command,
    CustomerPayloads.CREATE,
    MutationResponse::class.java,
    operation = "create_customer",
    timeoutMs = 5_000,
)
```

The app consumes the public `coakka.quarkus:coakka-quarkus-extension` artifact.
Quarkus owns HTTP/CDI lifecycle, and the adapter starts the CoAkka runtime with
process-owned routes for CDI `CoAkkaLocalHandler` beans.

## Before: Internal REST

A Quarkus team that wants to separate “web” from “store” usually creates an
internal REST resource and calls it with a REST client, even when the store is
not a real external HTTP boundary:

```kotlin
dependencies {
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-rest-client-jackson")
}
```

The store side becomes another HTTP resource:

```kotlin
@Path("/internal/customers")
@ApplicationScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class CustomerStoreInternalResource(private val store: InMemoryCustomerStore) {
    @POST
    fun create(request: CustomerDraft): MutationResponse {
        return store.create(request)
    }

    @GET
    fun list(): ListResponse {
        return store.list()
    }
}
```

The web resource then needs a REST client for an internal call:

```kotlin
@RegisterRestClient(configKey = "customer-store")
@Path("/internal/customers")
interface CustomerStoreRestClient {
    @POST
    fun create(request: CustomerDraft): MutationResponse

    @GET
    fun list(): ListResponse
}
```

And the public HTTP resource forwards browser/API traffic to that internal REST
surface:

```kotlin
@Path("/api/customers")
class CustomerResource(
    @RestClient private val storeClient: CustomerStoreRestClient,
) {
    @POST
    fun createCustomer(request: CustomerDraft): Response {
        val reply = storeClient.create(request)
        return Response.status(Response.Status.CREATED).entity(reply).build()
    }
}
```

That works, but it turns internal business work into an extra HTTP surface. The
app now carries REST-client config, URL wiring, HTTP serialization,
timeout/error mapping, and test setup before it has a real network boundary.

That is the adapter's target problem. The old shape expresses the store through
a Quarkus REST resource and REST client even when the store is just an internal
capability.
CoAkka keeps the public resource as the real HTTP edge and moves internal work
onto a typed runtime target with request/reply, counters, and deadletters.

## After: Same-Process Runtime Capability

With the adapter, Quarkus config owns same-process runtime defaults:

```kotlin
dependencies {
    implementation("coakka.quarkus:coakka-quarkus-extension:0.1.0-ge2dc43a")
    implementation("io.quarkus:quarkus-rest-jackson")
}
```

```properties
coakka.runtime.enabled=true
coakka.runtime.mode=local
coakka.runtime.system-name=customer-quarkus-local
coakka.runtime.node-id=customer-quarkus-local-node
coakka.runtime.source-target=samples.customer.frontend
coakka.runtime.local-endpoint-host=127.0.0.1
coakka.runtime.local-endpoint-port=19182
```

The internal capability is a CDI bean. It still exposes the real runtime boundary,
but no longer owns route table creation or shutdown:

```kotlin
@ApplicationScoped
@CoAkkaHandler(CustomerTargets.STORE)
class CustomerCapabilityHandler(
    private val objectMapper: ObjectMapper,
    private val customerStore: InMemoryCustomerStore,
) : CoAkkaLocalHandler {
    override fun handle(request: ConnectorEnvelope, objectMapper: ObjectMapper): ConnectorEnvelope {
        val customer = objectMapper.readValue(request.payload, CustomerDraft::class.java)
        return CoAkkaReplies.json(
            request,
            CustomerTargets.STORE,
            customerStore.create(customer),
            CustomerPayloads.MUTATION_RESPONSE,
            objectMapper,
        )
    }
}
```

The resource stays focused on HTTP and typed runtime asks:

```kotlin
@POST
fun createCustomer(request: CustomerDraft): Response {
    val reply = coakka.askBlocking(
        CustomerTargets.STORE,
        request,
        CustomerPayloads.CREATE,
        MutationResponse::class.java,
        operation = "create_customer",
        timeoutMs = 5_000,
    )
    return Response.status(Response.Status.CREATED).entity(reply).build()
}
```

That is the Quarkus adapter value: Quarkus keeps CDI and HTTP lifecycle, while
CoAkka remains the internal runtime boundary instead of becoming another
internal REST hop.

## Run

```sh
bash run.sh check
bash run.sh dev
```

Open:

```text
http://localhost:8083
```

Smoke:

```sh
bash run.sh smoke
```

Ports:

- HTTP: `8083`
- runtime diagnostic endpoint: `19182`

## Boundary Shape

The Quarkus resource owns real HTTP ingress at `/api/customers`. It calls the
runtime target `samples.customer.store`.

The customer store remains an ordinary CDI bean. The process-owned runtime handler is a
small `@CoAkkaHandler` CDI bean:

```kotlin
@ApplicationScoped
@CoAkkaHandler("samples.customer.store")
class CustomerCapabilityHandler(...) : CoAkkaLocalHandler
```

The adapter registers that handler during application startup and shuts the
runtime down through Quarkus lifecycle callbacks.

Remote transport, Kubernetes bind/advertise config, service discovery, and
business retry policy are deliberately out of scope for this same-process slice.
