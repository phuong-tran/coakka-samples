# Spring Boot And Quarkus: Customer Application

Use Runtime and framework packages **3.2.1** from Maven Central.
Choose one application, create a customer and see the list change.

**You need:** JDK 17 and a [Samples checkout](../../../../docs/coakka-runtime/getting-started.md#get-the-examples).
These builds reuse the application sources in the adjacent Spring/Quarkus folders.

## Spring Boot

From the Samples repository root:

~~~sh
./gradlew -p coakka-runtime/scenarios/customer-crud/framework-registry :runtime:scenarios:customer-crud:spring-boot-starter-local:customer-app:bootRun
~~~

Open http://localhost:8082. Create, edit and delete a customer, and check
the updated list. Stop the foreground app with Ctrl-C.

## Quarkus

From the Samples repository root:

~~~sh
./gradlew -p coakka-runtime/scenarios/customer-crud/framework-registry :runtime:scenarios:customer-crud:quarkus-local:customer-app:quarkusDev
~~~

Open http://localhost:8083 and try the same customer workflow.
Stop the foreground app with Ctrl-C.

In Windows PowerShell, replace ./gradlew with .\gradlew.bat in either command.
Run one example at a time and use an available port; the sample does not stop
another application that already owns a port.

## Read the code

| Application | Business handler | Application guide |
| --- | --- | --- |
| Spring Boot | [CustomerCapabilityHandlers.kt](../spring-boot-starter-local/customer-app/src/main/kotlin/coakka/samples/runtime/scenarios/customer/starterlocal/CustomerCapabilityHandlers.kt) | [Spring source and behavior](../spring-boot-starter-local/README.md) |
| Quarkus | [CustomerCapabilityHandler.kt](../quarkus-local/customer-app/src/main/kotlin/coakka/samples/runtime/scenarios/customer/quarkuslocal/CustomerCapabilityHandler.kt) | [Quarkus source and behavior](../quarkus-local/README.md) |

The framework owns HTTP and application lifetime. The application owns the
customer data and handlers; Runtime connects the calls to that work. The store
is in-memory teaching data, not a persistence layer.

CPU, connection and monitoring settings can stay at their defaults.
No local CoAkka Maven repository or separate Core installation is required.
The local-package launchers in adjacent folders are for qualification and
remain separate from these published-package commands.

[Choose another use case](../../../../docs/coakka-runtime/guides/examples-and-use-cases.md) ·
[Runtime integration](../../../../docs/coakka-runtime/guides/runtime-integration-guide.md).
