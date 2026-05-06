# JVM Runtime Samples

JVM runtime samples consume the published `coakka-jvm-native-runtime-v2` jar
from the static Maven repository in `coakka-publish`. The jar is all-in-one for
supported platforms and embeds the native runtime libraries directly.

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
    implementation("coakka.v2:coakka-jvm-native-runtime-v2:0.1.0-g0cb644340467-cfb8ee4")
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

## Production Notes

- Keep target names stable and version payload identities deliberately.
- Increment `generation` when applying a new route table.
- Prefer `strictNoDrop=true` while integrating so overload becomes visible.
- Handle `DeadletterException`; do not treat deadletters as generic failures.
- The customer scenarios include HTTP fallback only because the current public
  runtime backend is `stub`.
