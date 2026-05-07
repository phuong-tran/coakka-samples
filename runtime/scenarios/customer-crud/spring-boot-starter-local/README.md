# Spring Boot Starter Local Prototype

This scenario is an experimental local-first Spring Boot starter proof.

It keeps HTTP at the browser/API boundary and exposes internal customer work as
runtime capabilities:

```kotlin
@CoAkkaHandler("samples.customer.create")
fun create(command: CustomerDraft): MutationResponse
```

The app does not configure remote endpoints. The prototype starter scans
`@CoAkkaHandler` methods, starts the runtime with local routes for those
targets, registers typed handlers, and exposes a `CoAkkaRuntimeClient` bean for
the controller.

This slice runs on macOS for local development and is smoked on Linux in CI.
Remote/Kubernetes mode should wait until the local API shape is boring.

The sample consumes the published prototype starter artifact:

```kotlin
implementation("coakka.spring:coakka-spring-boot-starter:0.1.0-g432bd75d3e4b")
```

That starter depends on the shared runtime JVM artifact
`coakka.v2:coakka-jvm-native-runtime-v2:0.1.1-g22f571fd955c`; it does not bundle
or fork a Spring-specific native runtime.

The starter source lives in the connector workspace. This sample only consumes
the published Maven artifact from `coakka-publish`.

## Run

```sh
bash run.sh check
bash run.sh dev
```

Open:

```text
http://localhost:8082
```

Smoke:

```sh
bash run.sh smoke
```

Ports:

- HTTP: `8082`
- local runtime diagnostic endpoint: `19172`

## Boundary Shape

The controller owns real HTTP ingress. It calls runtime targets through
`CoAkkaRuntimeClient`.

The customer store remains an ordinary Spring service. Capability methods are
thin adapters that receive typed command objects and return typed responses.

Remote transport, Kubernetes bind/advertise config, service discovery, TLS, and
business retry policy are deliberately out of scope for this prototype.
