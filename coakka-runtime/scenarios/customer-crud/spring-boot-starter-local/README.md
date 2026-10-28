# Spring Boot Starter Same-Process Customer CRUD

This Java 17/Kotlin application keeps HTTP in Spring Boot and sends customer
work to local CoAkka handlers. The starter discovers four
`@CoAkkaHandler` methods, registers their routes and provides the
`CoAkkaRuntimeClient` used by the controller.

This sample uses the **unpublished starter 3.0.0 and JVM Runtime 3.0.0 candidate**.
The released packages remain at 2.5.3; see
[Current Packages](../../../../docs/current-packages.md).
Prepare the exact local packages, Gradle cache and work directory using the
[framework candidate guide](../../../docs/runtime-frameworks-candidate.md).

## Run

From this directory:

```sh
bash run.sh check
bash run.sh smoke
bash run.sh dev
```

`check` builds the packaged Spring Boot application. `smoke` builds and starts
its own application on a test port, checks its runtime settings, performs CRUD
and a route-miss request, then closes only that application.

`dev` runs in the foreground at `http://localhost:8082`. Stop it with Ctrl-C.
The sample does not kill processes that happen to own a port. There is no
separate runtime TCP listener in this same-process example.

## Application Configuration

The app names its runtime participant and source:

```yaml
coakka:
  runtime:
    system-name: customer-starter-local
    node-id: customer-starter-local-node
    source-target: samples.customer.frontend
```

CPU, connection and monitor choices stay absent so Core selects its normal
AUTO, MULTIPLEXED and monitor OFF settings. The `/api/customers/runtime`
endpoint reads accepted settings from the runtime. It also reports build
identity, routes and request/reply counters. CPU placement support and selected
IDs are shown as reported by the current platform, without guessing two CPUs
from the AUTO setting.

For optional monitoring, see
[Runtime Monitoring](../../../../docs/runtime-monitoring.md).

## Customer Work

An ordinary Spring bean owns the business operation:

```kotlin
@Component
class CustomerCapabilityHandlers(private val customerStore: InMemoryCustomerStore) {
    @CoAkkaHandler(CustomerTargets.CREATE)
    fun create(command: CustomerDraft): MutationResponse = customerStore.create(command)
}
```

The HTTP controller asks the runtime target:

```kotlin
return runtimeClient.askBlocking(
    CustomerTargets.CREATE,
    request,
    MutationResponse::class.java,
    "create_customer",
    5_000,
)
```

Spring still owns HTTP validation, security, dependency injection and context
lifecycle. The store remains an ordinary application service. Core owns
delivery, replies, timeouts and deadletters. A delivery failure stays visible;
the sample does not retry business work automatically.

## Code Map

- `CustomerStarterLocalApplication.kt` starts Spring and reports startup.
- `CustomerTargets.kt` names the four capabilities and a missing target.
- `CustomerCapabilityHandlers.kt` exposes store operations as handlers.
- `CustomerStarterLocalController.kt` owns the customer HTTP routes.
- `InMemoryCustomerStore.kt` owns the sample's data.
- `CustomerRuntimeDiagnosticsController.kt` reports runtime facts and exercises
  a route miss.
- Shared customer business types remain in the customer-contract project.
  Candidate-only diagnostic views preserve numeric reasons and a missing
  original request.

The dedicated candidate build uses these applications and their customer
contract only. It does not rebuild Core or configure unrelated samples.

## Scope

Customer data stays in an in-memory demo store and is lost when the app exits.
Production storage, data limits and retention remain application choices.

The example demonstrates normal JVM Spring startup, local request/reply,
diagnostics and shutdown. Runtime handler discovery does not establish Spring
native-image/AOT support. Changing handler declarations requires the
application's normal context restart. Remote transport and Kubernetes
deployment are outside this same-process sample.

For the integration model, read
[CoAkka Spring Boot](../../../../docs/coakka-spring-boot.md).
