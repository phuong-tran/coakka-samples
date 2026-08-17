# JVM Runtime Samples

JVM runtime samples consume the `coakka.runtime` Maven Central distribution at
`io.github.phuong-tran.coakka:runtime:2.4.1`, built against native runtime
generation `2.4.0+c2f53117`.

## Run

```sh
bash run.sh runtime basic
bash run.sh runtime jvm java-basic
bash run.sh runtime jvm deadletter
bash run.sh runtime jvm java-deadletter
```

Watch the JVM runtime walkthrough:

![CoAkka Runtime JVM walkthrough](../../docs/assets/coakka-runtime-jvm.gif)

Full recording: [coakka-runtime-jvm.mp4](../../docs/assets/coakka-runtime-jvm.mp4)

Direct Gradle runs:

```sh
./gradlew :runtime:jvm:basic:run
./gradlew :runtime:jvm:java-basic:run
./gradlew :runtime:jvm:deadletter:run
./gradlew :runtime:jvm:java-deadletter:run
```

For IDE runs, open the `coakka-samples` repository root as the Gradle project.

## Before: Backend HTTP

A JVM CRUD service often starts with a clean in-process store call, then grows
a backend HTTP controller just to make the store feel separated:

```kotlin
@RestController
@RequestMapping("/backend/customers")
class CustomerStoreBackendController(private val store: CustomerStore) {
    @PostMapping
    fun create(@RequestBody request: CustomerDraft): MutationResponse {
        return store.create(request)
    }
}
```

The web-facing controller then calls that fake backend HTTP surface:

```kotlin
@PostMapping("/api/customers")
@ResponseStatus(HttpStatus.CREATED)
fun createCustomer(@RequestBody request: CustomerDraft): MutationResponse {
    return restClient.post()
        .uri("http://customer-store/backend/customers")
        .body(request)
        .retrieve()
        .body(MutationResponse::class.java)
        ?: error("empty store response")
}
```

That adds URL wiring, HTTP parsing, headers, status/error mapping, timeout
policy, and test setup before there is a real product boundary.

## After: Runtime Target

Read the address change like this:

```text
Before backend HTTP:
  POST /backend/customers -> controller method

After CoAkka:
  target = "samples.customer.store" -> registered handler
```

The target plays a similar addressing role to a backend HTTP path, but it is
runtime routing vocabulary, not an HTTP URL.

With CoAkka, the store is a runtime target owned by the process that actually
handles the work:

```kotlin
orchestrator.registerHandler("samples.customer.store") { request ->
    RuntimeClient.replyTypedTo(
        request = request,
        source = "samples.customer.store",
        payloadUtf8 = store.createJson(request.payload),
    )
}
```

The web/API side keeps HTTP at the edge and sends a typed runtime request:

```kotlin
val response = orchestrator.kotlin.ask(
    source = "samples.customer.frontend",
    target = "samples.customer.store",
    payloadUtf8 = objectMapper.writeValueAsString(request),
    payloadIdentity = CustomerPayloads.CREATE,
    timeoutMs = 5_000,
    operation = "create_customer",
    deliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
)
```

## Integration Recipe

The sample uses the same Maven dependency shape an application should use:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.phuong-tran.coakka:runtime:2.4.1")
}
```

Start one orchestrator per process:

```kotlin
val orchestrator = ConnectorOrchestrator.start(
    startSpec = RuntimeStartSpec(
        systemName = "customer-store",
        nodeId = "customer-store-node-1",
        queueCapacity = 128,
        strictNoDrop = true,
        generation = 1,
        routes = listOf(
            RuntimeRouteSpec(
                target = "samples.customer.store",
                endpoints = listOf(
                    RuntimeEndpointSpec(
                        host = "127.0.0.1",
                        port = 19102,
                        flags = RuntimeEndpointFlags.LOCAL,
                    ),
                ),
            ),
        ),
    ),
)
```

Name roles in this snippet:

- `systemName` says this process belongs to the logical `customer-store`
  runtime participant.
- `nodeId` says this concrete process is `customer-store-node-1`.
- `target` says which capability the runtime can route to:
  `samples.customer.store`.

`target` is not `systemName` and not `nodeId`. A process can own multiple
targets; the route table maps those target names to endpoints.

`samples.customer.store` is the sample's capability target name. In your app,
choose your own target name, then use the same value in the route table, the
process-owned `registerHandler(...)`, and every caller `target`. For example,
`billing.invoice.create` is valid if that is the capability contract you want
to expose.

The same string appears again in the reply helper as `source`: the response is
coming from the target handler that produced it.

This sample registers one handler to stay small. A real app-host can register
multiple handlers if it owns multiple targets, such as
`samples.customer.create`, `samples.customer.update`, and
`samples.customer.list`. The route table and caller target must use the same
names.

Register handlers only for targets this process owns:

```kotlin
orchestrator.registerHandler("samples.customer.store") { request ->
    RuntimeClient.replyTypedTo(
        request = request,
        source = "samples.customer.store",
        payloadUtf8 = """{"status":"ACCEPTED"}""",
    )
}
```

This sample sends JSON text because it is easy to inspect. The runtime does not
route by JSON; it routes an envelope by `target` and carries payload bytes plus
`ConnectorPayloadIdentity`. For another payload shape, change the bytes and set
the appropriate `ConnectorPayloadFormat` in the payload identity.

Send typed requests with an explicit timeout and operation name:

```kotlin
val response = orchestrator.kotlin.ask(
    source = "customer-web",
    target = "samples.customer.store",
    payloadUtf8 = """{"id":"cust-001"}""",
    payloadIdentity = ConnectorPayloadIdentity(
        messageType = "samples.customer.create.request.v1",
        payloadSchemaVersion = 1,
        payloadFormat = ConnectorPayloadFormat.JSON,
    ),
    timeoutMs = 5_000,
    operation = "create_customer",
    deliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
)
```

Java applications use the Java facade:

```java
ConnectorEnvelope response = orchestrator.getJava().ask(
    "customer-web",
    "samples.customer.store",
    "{\"id\":\"cust-001\"}",
    new ConnectorPayloadIdentity(
        "samples.customer.create.request.v1",
        1,
        ConnectorPayloadFormat.JSON
    ),
    5_000,
    "create_customer",
    ConnectorDeliveryHint.ROUTER_DEFAULT
).get();
```

Close the orchestrator during application shutdown:

```kotlin
runBlocking { orchestrator.kotlin.shutdown() }
```

Spring Boot examples wrap this in an `AutoCloseable` bean.

Observe deadletters from Kotlin with a `Flow`:

```kotlin
orchestrator.kotlin.deadletters()
    .collect { deadletter ->
        println("deadletter reason=${deadletter.deadletter.reason}")
    }
```

Observe deadletters from Java 8 with a listener subscription:

```java
DeadletterSubscription subscription =
    orchestrator.getJava().subscribeDeadletters(deadletter -> {
        System.out.println("deadletter target=" +
            deadletter.getDeadletter().getOriginalEnvelope().getTarget());
    });
```

## Boundary Note

Use HTTP where it is a real external boundary. For CRUD work, a fake
REST hop usually adds web-stack work before there is a real network contract:
URL wiring, headers, request parsing, middleware, status/error mapping,
timeouts, retries, and extra tests. The JVM runtime path keeps that work as a
typed envelope, route target, request/reply, and deadletter result. The value is
not a fragile speed claim; it is that the code uses the right boundary
semantics.

## Production Notes

- Keep target names stable and version payload identities deliberately.
- Increment `generation` when applying a new route table.
- Prefer `strictNoDrop=true` while integrating so overload becomes visible.
- Handle `DeadletterException`; do not treat deadletters as generic failures.
- Customer scenarios keep inter-service business traffic runtime-only and avoid a store REST fallback.

## Continue Integrating

Use this lane's runnable sample as the source for package imports and basic
lifecycle names. Before generating connection strategy, TLS/mTLS, File Lane, or
Stream Lane code, follow [AI-Assisted Integration](../../docs/ai-assisted-integration.md).
It links the canonical feature guides, exact package catalog, and platform
evidence, and it defines when only workflow pseudocode is justified.

The current public package train includes File Lane and Stream Lane. Use the
exact connector names and lifecycle rules shipped by JVM distribution `2.4.1`
over native generation `2.4.0+c2f53117` when integrating either lane.
