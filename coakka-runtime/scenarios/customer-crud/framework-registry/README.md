# Spring Boot and Quarkus — Maven Central first run

Use JDK17+ from the coakka-samples checkout. This dedicated build resolves
Runtime, Spring Boot starter and Quarkus adapter3.0.0 from Maven Central.
It reuses the customer CRUD sources and does not require a local candidate
catalog, source mirror or separately installed native library.

Run one application at a time on the default HTTP port:

    ./gradlew -p coakka-runtime/scenarios/customer-crud/framework-registry :runtime:scenarios:customer-crud:spring-boot-starter-local:customer-app:bootRun

Or:

    ./gradlew -p coakka-runtime/scenarios/customer-crud/framework-registry :runtime:scenarios:customer-crud:quarkus-local:customer-app:quarkusDev

See the [Spring application](../spring-boot-starter-local/README.md) or
[Quarkus application](../quarkus-local/README.md) for the HTTP routes and business
flow. This build supplies public registry dependencies; the original local-package
launcher remains a separate reproduction path. Stop the foreground app with Ctrl-C.

Spring owns HTTP and bean lifecycle; Quarkus owns HTTP and CDI lifecycle. The
application owns customer data and handlers. CoAkka supplies the shared runtime;
AUTO/MULTIPLEXED and monitor-off defaults remain owned by Core. Plain JVM Java 8
and framework Java 17 are separate application baselines. This is normal JVM/CDI
integration and does not establish native-image support.
