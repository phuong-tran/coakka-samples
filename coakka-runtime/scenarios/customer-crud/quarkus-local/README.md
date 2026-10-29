# Quarkus Same-Process Customer CRUD

This Java 17/Kotlin application keeps HTTP and CDI lifecycle in Quarkus while
customer work uses the local CoAkka target `samples.customer.store`.
A CDI handler owns the store operation; the extension starts and closes the
runtime for the application.

This sample uses the **unpublished extension 3.0.0 and JVM Runtime 3.0.0 candidate**.
The released packages remain at 2.5.3; see
[Current Packages](../../../../docs/common/current-packages.md).
Prepare the exact local packages, Gradle cache and work directory using the
[framework candidate guide](../../../../docs/coakka-runtime/runtime-frameworks-candidate.md).

## Run

From this directory:

```sh
bash run.sh check
bash run.sh smoke
bash run.sh dev
```

`check` builds the packaged Quarkus JVM application. `smoke` builds and starts
its own application on a test port, checks its runtime settings, performs CRUD
and a route-miss request, then closes only that application.

`dev` runs in the foreground at `http://localhost:8083`. Stop it with Ctrl-C.
The sample does not kill processes that happen to own a port. There is no
separate runtime TCP listener in this same-process example.

## Application Configuration

The app names its runtime participant and source:

```properties
coakka.runtime.system-name=customer-quarkus-local
coakka.runtime.node-id=customer-quarkus-local-node
coakka.runtime.source-target=samples.customer.frontend
```

CPU, connection and monitor choices stay absent so Core selects its normal
AUTO, MULTIPLEXED and monitor OFF settings. The `/api/customers/runtime`
endpoint reads accepted settings from the runtime. It also reports build
identity, routes and request/reply counters. CPU placement support and selected
IDs are shown as reported by the platform, without guessing two CPUs from AUTO.

For optional monitoring, see
[Runtime Monitoring](../../../../docs/coakka-runtime/runtime-monitoring.md).

## Customer Work

The application declares one CDI handler:

```kotlin
@ApplicationScoped
@CoAkkaHandler(CustomerTargets.STORE)
class CustomerCapabilityHandler(
    private val objectMapper: ObjectMapper,
    private val customerStore: InMemoryCustomerStore,
) : CoAkkaLocalHandler {
    override fun handle(request: ConnectorEnvelope, objectMapper: ObjectMapper): ConnectorEnvelope {
        val command = objectMapper.readValue(request.payload, CustomerDraft::class.java)
        return CoAkkaReplies.json(
            request,
            CustomerTargets.STORE,
            customerStore.create(command),
            CustomerPayloads.MUTATION_RESPONSE,
            objectMapper,
        )
    }
}
```

The resource sends a typed request:

```kotlin
return coakka.askBlocking(
    CustomerTargets.STORE,
    request,
    CustomerPayloads.CREATE,
    MutationResponse::class.java,
    "create_customer",
    5_000,
)
```

The sample's full handler selects create, update, delete or list from the
application payload identity. The HTTP resource and store remain application
code. Core owns delivery, replies, timeouts and deadletters; the sample does
not automatically retry business work after a failure.

## Code Map

- `CustomerResource.kt` owns HTTP operations and runtime diagnostics.
- `CustomerCapabilityHandler.kt` adapts customer requests to store operations.
- `CustomerPayloads.kt` declares application payload identities.
- `InMemoryCustomerStore.kt` owns the sample's data.
- `RuntimeErrorMapper.kt` maps a delivery failure into an explicit HTTP error.
- Shared customer business types remain in the customer-contract project.
  Candidate-only diagnostic views preserve numeric reasons and a missing
  original request.

The dedicated candidate build configures only these applications and their
customer contract. It checks actual local package/library files before
compilation and packaging. Quarkus uses a normal platform BOM so required
runtime libraries are not silently replaced.

## Scope

Customer data stays in an in-memory demo store and is lost when the app exits.
Production storage, data limits and retention remain application choices.

This is a normal JVM application packaged as a Quarkus fast-jar. The CoAkka
artifact supplies CDI/runtime integration; it does not establish a full
Quarkus build-time extension or native-image support. Remote transport and
Kubernetes deployment are outside this same-process sample.

For the integration model, read
[CoAkka Quarkus](../../../../docs/coakka-runtime/coakka-quarkus.md).
