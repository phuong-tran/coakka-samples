# JVM Runtime 3.0.0: First Run

Choose Kotlin or Java. Both examples use Runtime 3.0.0 from Maven Central.

**You need:** JDK 17 for the sample build and a
[Samples checkout](../../../docs/coakka-runtime/getting-started.md#get-the-examples).
Use the Gradle wrapper included in the repository.

## Kotlin

From the Samples repository root:

~~~sh
./gradlew -p coakka-runtime/jvm/registry-basic run
~~~

Look for:

~~~text
coakka_runtime_response payload=hello-runtime-jvm
~~~

Read [Main.kt](../basic/src/main/kotlin/coakka/samples/runtime/jvm/basic/Main.kt).
The application starts a local Runtime in a use block, registers a handler,
asks for work and closes when the block ends.

## Java

From the Samples repository root:

~~~sh
./gradlew -p coakka-runtime/jvm/registry-basic runJava
~~~

Look for:

~~~text
coakka_runtime_response payload=hello-runtime-java
~~~

Read [Main.java](../java-basic/src/main/java/coakka/samples/runtime/jvm/javabasic/Main.java).
It registers a text handler, sends a request, checks the reply and awaits
shutdown while preserving operation and close failures.

## Windows and application requirements

In PowerShell, replace ./gradlew with .\gradlew.bat in either command.
Use an ARM64 JDK on native Windows ARM64.

The sample build uses JDK 17; the plain Runtime library retains Java 8
compatibility, with ARM64 JDK 11 or later required on native Windows ARM64.
A separate Core installation is not needed.

## Continue

- [Run a Spring Boot or Quarkus customer application](../../scenarios/customer-crud/framework-registry/README.md).
- [Choose another use case](../../../docs/coakka-runtime/guides/examples-and-use-cases.md).
- [Integrate Runtime in an application](../../../docs/coakka-runtime/guides/runtime-integration-guide.md).

Local-package checks have [separate evaluation instructions](../../../docs/coakka-runtime/previews/README.md);
they are not needed for the commands above.
