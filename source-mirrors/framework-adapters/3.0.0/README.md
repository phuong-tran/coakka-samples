# CoAkka Runtime framework source 3.0.0

This source contains the Spring Boot and Quarkus adapters for JVM Runtime
3.0.0. The adapters use Java17 and the same CoAkka Runtime package as ordinary
JVM applications. They do not build or replace the native runtime.

This is a prepared source candidate. Public tags and registry uploads require
separate publication. The already published2.x source remains independent.

## Build and verify

Use Java17, Python3.11 or later and the reviewed ProGuard7.10 distribution.
The Gradle wrapper selects9.2.1. Before publication, provide the admitted local
Maven repository containing Runtime3.0.0 and its exact dependency metadata;
Runtime is resolved exclusively from that repository and its digest is checked
before compilation. A registry or a different same-version JAR cannot replace it.

```sh
./gradlew --no-daemon --max-workers=1 \
  -PcoakkaRuntimeRepository=/path/to/admitted-maven \
  -PcoakkaProguardJar=/path/to/proguard/lib/proguard.jar \
  -PcoakkaJava17Home=/path/to/jdk17 \
  :spring-boot-starter:check :quarkus-extension:check \
  :spring-boot-starter:sourcesJar :spring-boot-starter:javadocJar \
  :quarkus-extension:sourcesJar :quarkus-extension:javadocJar
```

Use a cache and build output location appropriate for the workstation. The
optional `coakkaFrameworkBuildRoot` property redirects all module outputs.

The distributable libraries are `build/distribution/spring-boot-starter-3.0.0.jar`
and `build/distribution/quarkus-extension-3.0.0.jar` under their module output
directories. These use the real shrinker while preserving required public and
framework lookup names. Compilation intermediates and `private-shrink` reports
are diagnostic material; do not distribute them with an application.

The checks exercise the adapter tests, strict Java17 compilation, accepted
Runtime identity, required resources, class baseline and public signatures.
The adapters contain no native images of their own. Installed framework
application qualification and supported framework versions remain part of the
matching release record; source compilation alone is not a deployment claim.

## Using the adapters

Use the ordinary [Spring Boot example](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/scenarios/customer-crud/spring-boot-starter-local)
or [Quarkus example](https://github.com/phuong-tran/coakka-samples/tree/main/coakka-runtime/scenarios/customer-crud/quarkus-local).
Core resolves omitted execution and connection options to AUTO and MULTIPLEXED;
monitoring stays off until an application deliberately enables it. All JVM
hosts share these defaults and lifecycle rules.

Keep application handlers and business decisions in the application. The
adapters supply framework integration and delegate runtime decisions to CoAkka
Runtime. See the shared [documentation](https://github.com/phuong-tran/coakka-samples/blob/main/docs/coakka-runtime/README.md)
for configuration, monitoring, ownership and shutdown guidance.

Source uses Apache2.0. Preserve LICENSE and NOTICE. The separately consumed
native Runtime artifacts use the file-scoped terms in NATIVE-LICENSE.md and
PACKAGE-LICENSE.md. `SOURCE-MANIFEST.sha256` binds this complete source projection.
