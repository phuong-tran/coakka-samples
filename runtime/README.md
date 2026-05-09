# Runtime Samples

Runtime samples demonstrate CoAkka runtime v2 as a shared native runtime
contract consumed through host-language connectors.

The public publish surface exposes logger packages, the sanitized native
runtime C ABI package, runtime JVM/language connector packages, and the Spring
Boot and Quarkus adapters. These samples consume those public artifacts.

The runtime lane is not introduced as a generic framework. It starts from
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

The runtime samples keep a strict boundary rule: HTTP belongs at a real edge
such as a browser/API, partner API, or legacy HTTP dependency. Internal work
should not become a fake REST service just to get a boundary. That fake HTTP
path often pays for request parsing, headers, middleware, status mapping,
client/server lifecycle, timeout policy, and test setup before the application
has crossed a real product boundary. CoAkka keeps that path as typed runtime
messages with request/reply and deadletter behavior.

The runtime contract is not tied to web payloads. Each typed message declares a
message type, schema version, and payload format. The samples mostly use JSON
for readability, while the contract also supports payload shapes such as
Protobuf, Thrift, MessagePack, plain text, and binary.

macOS is a good development path for these samples. Production-like validation
should happen on Linux because native loading, process
supervision, dependency packaging, and memory pressure are part of the runtime
environment.

Current samples:

| Language | Sample | Artifact | Behavior |
| --- | --- | --- | --- |
| JVM | `jvm/basic`, `jvm/deadletter`, `jvm/java-deadletter` | public JVM runtime jar | echo and Kotlin/Java route-miss deadletter observation |
| Python | `python/basic`, `python/deadletter`, `python/hot-reload` | public Python wheel | echo, route-miss deadletter, and route snapshot hot reload |
| Node.js | `node/basic`, `node/deadletter` | public Node package | echo and route-miss deadletter |
| Go | `go/basic`, `go/deadletter` | public Go source package | echo and route-miss deadletter |
| C# | `csharp/basic` | public NuGet package | echo and route-miss deadletter |
| Rust | `rust/basic` | public Rust spike tarball | echo and route-miss deadletter |
| Native C/C++ | `native/basic`, `native/pressure` | native C ABI archive | route snapshot, route-miss deadletter, and bounded pressure counters |

## Runtime Pressure Status

`runtime/native/pressure` is the executable pressure sample for the runtime
intake boundary. It uses the public C ABI directly with `queueCapacity=2` and
`strictNoDrop=true`, submits a burst through the runtime request pipe, and
verifies queue-rejected deadletters plus queue counters.

The higher-level connector samples are listed separately because they exercise a
different boundary. JVM, Python, Node.js, Go, C#, and Rust samples currently
cover request/reply, route-miss deadletters, and route snapshot hot reload where
that connector exposes it. They intentionally do not claim native intake
pressure until the public connector surface exposes a repeatable pressure hook
that can produce the same queue-rejected evidence without bypassing connector
ownership.

Scenario track:

| Scenario | Purpose |
| --- | --- |
| `scenarios/customer-crud` | customer add/edit/delete/list flows across Spring Boot, Quarkus, desktop, Node.js, Go, and C# topologies |

The first scenario implementations are:

- `scenarios/customer-crud/spring-boot-single-process`
- `scenarios/customer-crud/spring-boot-starter-local`
- `scenarios/customer-crud/quarkus-local`
- `scenarios/customer-crud/kotlin-desktop-local`
- `scenarios/customer-crud/python-desktop-local`
- `scenarios/customer-crud/spring-boot-spring-boot`
- `scenarios/customer-crud/spring-boot-node`
- `scenarios/customer-crud/spring-boot-go`
- `scenarios/customer-crud/spring-boot-csharp`
- `scenarios/customer-crud/spring-boot-nodes`

They run web, desktop, store, and audit shapes and expose clear runtime
diagnostics. The single-process, starter-local, Quarkus-local, and desktop
topologies give successful CRUD paths through local runtime capabilities
without an internal store REST API. The cross-process web-to-store path is
runtime-only and is wired for cross-host delivery through the current public TCP
transport candidate. If delivery fails, samples return explicit runtime errors
instead of hiding the failure behind a REST fallback.

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
bash run.sh logger basic
```

Check local toolchains and artifact source:

```sh
bash run.sh doctor
```

Run all runtime lanes:

```sh
bash run.sh runtime
```

From any leaf sample directory, run:

```sh
bash run.sh
```
