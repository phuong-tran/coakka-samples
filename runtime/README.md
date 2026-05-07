# Runtime Samples

Runtime samples demonstrate CoAkka runtime v2 as a shared native runtime
contract consumed through host-language connectors.

The runtime lane is not introduced as a generic actor framework. It starts from
the connector-boundary problem:

- multiple languages
- multiple processes
- gradual migration from working legacy systems
- repeated reinvention of framing, routing, request/reply, lifecycle,
  deadletter handling, and observability

CoAkka runtime v2 is intended to sit at system boundaries without requiring a
large rewrite. A connector can be embedded in an existing service, used by a
desktop application, or placed beside a legacy process as a sidecar. Integration
adapters for systems such as Camel, MQTT, brokers, files, serial devices, or
HTTP services can use the runtime contract as the common routing and diagnostic
surface.

The runtime contract is not tied to web payloads. Each typed message declares a
message type, schema version, and payload format. The samples mostly use JSON
for readability, while the contract also supports payload shapes such as
Protobuf, Thrift, MessagePack, plain text, and binary.

macOS is a good development path for these samples. Production-like validation
should happen on Linux because native loading, scheduler behavior, process
supervision, dependency packaging, and memory pressure are part of the runtime
environment.

Current samples:

| Language | Sample | Artifact | Behavior |
| --- | --- | --- | --- |
| JVM | `jvm/basic`, `jvm/deadletter`, `jvm/java-deadletter` | published JVM runtime jar | echo and Kotlin/Java route-miss deadletter observation |
| Python | `python/basic`, `python/deadletter`, `python/hot-reload` | published Python wheel | echo, route-miss deadletter, and route snapshot hot reload |
| Node.js | `node/basic`, `node/deadletter` | published Node package | echo and route-miss deadletter |
| Go | `go/basic`, `go/deadletter` | published Go source package | echo and route-miss deadletter |
| Native C/C++ | `native/basic` | published native C/C++ archive | route snapshot and route-miss deadletter |

Scenario track:

| Scenario | Purpose |
| --- | --- |
| `scenarios/customer-crud` | web-style customer add/edit/delete/list flows across Spring Boot, Node.js, and Go processes |

The first scenario implementations are:

- `scenarios/customer-crud/spring-boot-single-process`
- `scenarios/customer-crud/spring-boot-starter-local`
- `scenarios/customer-crud/spring-boot-spring-boot`
- `scenarios/customer-crud/spring-boot-node`
- `scenarios/customer-crud/spring-boot-go`
- `scenarios/customer-crud/spring-boot-nodes`

They boot web/store/audit services and expose clear runtime diagnostics. The
single-process topology gives a successful CRUD path through a local runtime
store target. The starter-local topology is an experimental macOS/Linux proof
where a Spring Boot starter derives local routes from `@CoAkkaHandler` methods
and is smoked in CI on Ubuntu. The cross-process web-to-store path is
runtime-only and uses the remote runtime backend. If delivery fails, samples
return explicit runtime errors instead of hiding the failure behind a REST
fallback.

For production-facing integration guidance, read:

```text
docs/runtime-integration-guide.md
```

List scenario topologies from the repository root:

```sh
bash run.sh scenarios
```

Run a scenario check without changing directories:

```sh
bash run.sh scenario customer-crud spring-boot-nodes check
```

Run:

```sh
bash run.sh runtime basic
```

Check local toolchains and artifact source:

```sh
bash run.sh doctor
```

Run every runtime sample currently available:

```sh
bash run.sh runtime
```

From any leaf sample directory, run:

```sh
bash run.sh
```
