# Spring Boot And Quarkus Candidate Samples

These examples use the unpublished **3.0.0** Spring Boot starter and Quarkus
extension together with the exact JVM Runtime 3.0.0 package. They are a separate
candidate workflow. The published 2.5.3 packages remain listed in
[Current Packages](../common/current-packages.md).

Both applications need Java 17. The plain JVM connector still supports its
separately verified Java 8 baseline. The framework packages reuse that JVM
connector and its existing Core; they do not bring a framework-specific Core.

## Prepare The Local Packages

Obtain the trusted candidate Maven directory and the matching
`coakka-runtime/artifacts/runtime-frameworks-candidate.json` catalog.
The catalog names the runtime, both framework packages and their required
runtime libraries, with exact sizes and hashes.

Choose existing absolute directories for the candidate, Gradle cache and
sample work. On the development Mac, caches and work belong on the mounted
external APFS storage.

```sh
export JAVA_HOME="/absolute/jdk17"
export GRADLE_USER_HOME="/absolute/gradle-cache"
export COAKKA_FRAMEWORK_CANDIDATE_HOME="/absolute/framework-candidate-maven"
export COAKKA_SAMPLE_WORK_ROOT="/absolute/framework-sample-work"
```

Python 3.11 or newer and the repository's Bash/Gradle wrapper are build tools.
The application itself remains an ordinary Java 17 application. If a separate
Java installation should run it, set `COAKKA_SAMPLE_JAVA_HOME`; otherwise the
sample uses `JAVA_HOME`.

Before building, the sample checks all thirteen catalog entries. It checks the
actual files selected by Gradle again before compiling or packaging the apps.
Missing, changed or unexpected CoAkka files stop the build. CoAkka packages
come only from this admitted local directory; framework/build dependencies use
their normal repositories. There is no source-project replacement or silent
download of another CoAkka runtime.

Use `COAKKA_FRAMEWORK_OFFLINE=1` only when the needed build/framework
dependencies already exist in the chosen Gradle cache.

## Run Either Example

From the Samples repository root:

```sh
bash coakka-runtime/scenarios/customer-crud/spring-boot-starter-local/run.sh check
bash coakka-runtime/scenarios/customer-crud/spring-boot-starter-local/run.sh smoke
bash coakka-runtime/scenarios/customer-crud/quarkus-local/run.sh check
bash coakka-runtime/scenarios/customer-crud/quarkus-local/run.sh smoke
```

The build configures only these two applications and their shared customer
business types. It does not configure the unrelated root sample graph.

`check` builds the packaged application. `smoke` builds it, starts its own
application process on a test HTTP port, checks runtime identity/settings,
performs customer create/update/list/delete and a route-miss check, then closes
only that process. A failure remains a failure; cleanup does not turn it into a
passing test.

For an interactive run:

```sh
bash coakka-runtime/scenarios/customer-crud/spring-boot-starter-local/run.sh dev
# Open http://localhost:8082 and stop with Ctrl-C.

bash coakka-runtime/scenarios/customer-crud/quarkus-local/run.sh dev
# Open http://localhost:8083 and stop with Ctrl-C.
```

The launcher does not kill whichever process owns a port. It creates no
background server for `stop` to find. `stop` prints how to close the
foreground application; each automated smoke closes the child it started.

## What The Applications Show

Spring Boot keeps HTTP and Spring bean lifecycle. Its starter discovers four
`@CoAkkaHandler` customer operations and exposes `CoAkkaRuntimeClient`.
Quarkus keeps HTTP and CDI lifecycle. Its extension registers the application's
local handler for `samples.customer.store`.

Both leave CPU, connection strategy and extra monitor collection absent from
startup input. Their diagnostic endpoint displays the settings accepted by
Core. The normal current settings are `AUTO`, `MULTIPLEXED` and monitor OFF;
CPU placement support and selected IDs are displayed as reported, rather than
guessed from the requested setting. See
[Runtime Monitoring](runtime-monitoring.md).

The diagnostic JSON preserves numeric delivery reasons and an absent original
request. It does not replace an unknown reason with a familiar label or invent
a target when no original request was returned. Business data types remain
application-owned.

## Scope

These are same-process JVM examples with real HTTP at the browser/API edge.
Their demo customer stores keep data in memory; production storage and data
limits belong to the application.
They do not establish remote-network, native-image/AOT or additional platform
support. The Spring starter uses runtime handler discovery; the Quarkus
artifact is a CDI/runtime adapter, not a full build-time extension. Use the
exact recorded candidate and matching-host evidence before making broader
deployment claims.
