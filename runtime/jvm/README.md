# JVM Runtime Samples

JVM runtime samples document the `coakka-jvm-native-runtime-v2` jar shape. This
runtime lane consumes the public JVM runtime jar built against native runtime
`0.1.0+a671b3a`.

## Run

```sh
bash run.sh runtime basic
bash run.sh runtime jvm java-basic
bash run.sh runtime jvm deadletter
bash run.sh runtime jvm java-deadletter
```

Direct Gradle runs:

```sh
./gradlew :runtime:jvm:basic:run
./gradlew :runtime:jvm:java-basic:run
./gradlew :runtime:jvm:deadletter:run
./gradlew :runtime:jvm:java-deadletter:run
```

For IDE runs, open the `coakka-samples` repository root as the Gradle project.

## Before: Internal REST

A JVM CRUD service often starts with a clean in-process store call, then grows
an internal REST controller just to make the store feel separated:

```kotlin
@RestController
@RequestMapping("/internal/customers")
class CustomerStoreInternalController(private val store: CustomerStore) {
    @PostMapping
    fun create(@RequestBody request: CustomerDraft): MutationResponse {
        return store.create(request)
    }
}
```

The web-facing controller then calls that fake internal HTTP surface:

```kotlin
@PostMapping("/api/customers")
@ResponseStatus(HttpStatus.CREATED)
fun createCustomer(@RequestBody request: CustomerDraft): MutationResponse {
    return restClient.post()
        .uri("http://customer-store/internal/customers")
        .body(request)
        .retrieve()
        .body(MutationResponse::class.java)
        ?: error("empty store response")
}
```

That adds URL wiring, HTTP parsing, headers, status/error mapping, timeout
policy, and test setup before there is a real product boundary.

## After: Runtime Target

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
    maven {
        url = uri("https://raw.githubusercontent.com/phuong-tran/coakka-publish/main/maven")
    }
}

dependencies {
    implementation("coakka.v2:coakka-jvm-native-runtime-v2:0.1.1-ga671b3a")
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
        separateDeliveredRequestLane = true,
        generation = 1,
        routes = listOf(
            RuntimeRouteSpec(
                target = "samples.customer.store",
                endpoints = listOf(
                    RuntimeEndpointSpec("127.0.0.1", 19102, RuntimeEndpointFlags.LOCAL),
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

This sample sends JSON text because it is easy to inspect. The runtime does not
route by JSON; it routes an envelope by `target` and carries payload bytes plus
`ConnectorPayloadIdentity`. For another payload shape, change the bytes and set
the appropriate `ConnectorPayloadFormat` in the payload identity.

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

Use HTTP where it is a real external boundary. For internal CRUD work, a fake
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
