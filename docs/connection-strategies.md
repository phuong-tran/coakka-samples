# Runtime Connection Strategies

Connection strategy is selected per runtime instance before startup. It is
independent from transport security: every compiled strategy can use plaintext,
TLS, or mTLS when the selected runtime profile provides that security
capability.

The runtime owns connection setup, reuse, retirement, concurrency bounds, and
security directly. CoAkka participants do not need a sidecar or service-mesh
proxy to supply those mechanics.

## Modes

| Mode | Connection behavior | Concurrency on one connection | Required effective capability |
| --- | --- | ---: | --- |
| `PER_EXCHANGE` | Connect, send one request, receive one terminal result, close | 1 | Baseline TCP remote transport |
| `BOUNDED_POOL` | Borrow and reuse one of a bounded set of retained connections | 1 | `TCP_CONNECTION_BOUNDED_POOL` |
| `PERSISTENT_SINGLE_FLIGHT` | Retain at most one connection per exact endpoint and serialize exchanges | 1 | `TCP_PERSISTENT_SINGLE_FLIGHT` |
| `MULTIPLEXED` | Retain an endpoint connection and correlate bounded streams | Bounded, currently 16 | `TCP_MULTIPLEXING` |

The current native startup contract resolves omitted execution and connection
groups inside Core to CPU `AUTO` and persistent `MULTIPLEXED`; omitted security
remains `PLAINTEXT`. These are accepted Core facts, not connector-side default
tables. The frozen legacy `coakka_v2_runtime_create` entrypoint retains
`PER_EXCHANGE + PLAINTEXT` for compatibility. Nothing silently enables TLS or
mTLS, and documentation for a legacy package must identify that constructor
scope instead of presenting its connection mode as the universal default.

`AUTO` selects CPU execution policy; it is independent of connection strategy.
For why `AUTO` and `MULTIPLEXED` are the normal defaults, why optional monitor
collection defaults to OFF, and how to opt in and observe accepted settings,
see [Runtime Monitoring](runtime-monitoring.md).

## Defaults And Tuning

Bounded-pool defaults revision 1 uses:

| Setting | Default |
| --- | ---: |
| Maximum retained connections | 8 |
| Maximum requests before connection retirement | 1024 |
| Idle timeout | 30000 ms |

`BOUNDED_POOL` uses those fixed defaults when its effective capability is
present and rejects unavailable numeric tuning with
`COAKKA_V2_ERR_FEATURE_UNAVAILABLE`. Runtime capability metadata is the
authority for whether tuning is available. The initial advanced-mode limits
are runtime-owned defaults, not public tuning fields.

## Lifecycle And Atomicity

Connectors on the current native startup contract pass nullable connection
intent into construction. Omitting the group selects Core's `MULTIPLEXED`
default; supplying it makes the chosen mode immutable from successful
construction. Changing strategy requires a new runtime instance and an
application-owned cutover. `STOPPED` is terminal.

The legacy constructor alone permits the existing `_ex` apply function while
the runtime is `CREATED`; repeated successful legacy applies publish the last
complete configuration. It returns one coherent result:

- `apply_status`: the same stable status returned by the function
- `changed`: `1` only when a different effective configuration was published
- `reason`: stable lifecycle, capability, entitlement, or resource reason
- `validation`: field-level shape and range detail
- `effective_config`: the configuration that remains active after the attempt

A rejected apply does not partially publish fields. The connector should
serialize lifecycle/configuration ownership for one runtime rather than racing
multiple configuration writers.

## Use Your Language Connector

Connection strategy is not a C-only feature. The C ABI below is the stable
contract shared by the ecosystem; application developers normally submit
optional strategy intent through their host-language connector. A current
connector projects Core's accepted startup/effective snapshots and does not
fabricate a structured apply result for constructor-owned intent. Historical
connectors that still use the legacy constructor retain its real apply result.

Current connector surfaces include native C/C++, JVM/Kotlin, Spring Boot,
Quarkus, Node.js, Bun, Electron, Python, Go, C#, Rust, Swift, Zig, and Mojo.
Availability of optional modes and tuning is determined by the effective
capability bits of the exact native artifact being loaded, not by requiring an
application to call C directly.

## Kotlin/JVM Example

This example selects bounded connection reuse and leaves numeric tuning absent
so the runtime owns its published defaults:

```kotlin
import coakka.v2.connector.*

val capabilities = RuntimeHandle.readRuntimeCapabilities()
require(
    capabilities.supports(CoakkaRuntimeCapabilities.TCP_BOUNDED_POOL),
) { "This runtime artifact does not provide bounded-pool connections" }

val strategy = RuntimeTcpConnectionStrategySpec(
    mode = RuntimeTcpConnectionMode.BOUNDED_POOL,
)

ConnectorOrchestrator.start(
    startSpec = RuntimeStartSpec(
        systemName = "orders",
        nodeId = "orders-a",
        routes = loadRoutes(),
        connectionStrategy = strategy,
    ),
).use { runtime ->
    val accepted = runtime.startupConfig().connection
    check(accepted.mode == strategy.mode)

    // Register handlers and send requests through the normal connector API.
}
```

`ConnectorOrchestrator.start(...)` submits the strategy to Core construction
and throws if Core rejects it; no partial runtime or synthetic apply result is
published. Omit `connectionStrategy` for the normal Core `MULTIPLEXED` default.
Use explicit `PER_EXCHANGE` without an optional capability check; check the
matching capability before explicitly selecting another optional mode. Add
numeric pool tuning only when `TCP_POOL_TUNING` is effective and measurements
justify overriding the runtime defaults.

## C Example

This lower-level example is for a host intentionally retaining the legacy
constructor/setter contract. It is not the current native-construction default
and is not a requirement for Kotlin or other language applications. New native
hosts pass connection intent to `coakka_v2_runtime_create_native`, or pass NULL
to select Core `AUTO`/`MULTIPLEXED` defaults.

```c
static coakka_v2_status_t select_connection_mode(
    coakka_v2_runtime_t *runtime,
    coakka_v2_tcp_connection_mode_t mode,
    coakka_v2_tcp_connection_apply_result_t *result) {
  coakka_v2_tcp_connection_options_t options = {0};
  options.struct_size = sizeof(options);
  options.fields = COAKKA_V2_TCP_CONNECTION_FIELD_MODE;
  options.mode = (uint32_t)mode;

  *result = (coakka_v2_tcp_connection_apply_result_t){0};
  result->struct_size = sizeof(*result);
  return coakka_v2_runtime_apply_tcp_connection_options_ex(
      runtime, &options, result);
}

coakka_v2_tcp_connection_apply_result_t result;

/* One connection per exchange. */
select_connection_mode(runtime, COAKKA_V2_TCP_CONNECTION_PER_EXCHANGE,
                       &result);

/* Fixed defaults unless capability discovery says tuning is available. */
select_connection_mode(runtime, COAKKA_V2_TCP_CONNECTION_BOUNDED_POOL,
                       &result);

/* Requires TCP_PERSISTENT_SINGLE_FLIGHT. */
select_connection_mode(
    runtime, COAKKA_V2_TCP_CONNECTION_PERSISTENT_SINGLE_FLIGHT, &result);

/* Requires TCP_MULTIPLEXING with bounded correlated streams. */
select_connection_mode(runtime, COAKKA_V2_TCP_CONNECTION_MULTIPLEXED,
                       &result);
```

Check the return status and `result` after each call; the example shows four
independent selections, not four calls to make on the same production runtime.
Query runtime capabilities before presenting a mode to an end user.

## Native C++ Example

The native connector applies strategy from `StartSpec` before runtime start:

```cpp
using namespace coakka::v2::native_cpp;

const auto capabilities = ConnectorOrchestrator::readRuntimeCapabilities();
StartSpec spec;
spec.system_name = "orders";
spec.node_id = "orders-a";
spec.routes = load_routes();

TcpConnectionStrategySpec strategy;
strategy.mode = COAKKA_V2_TCP_CONNECTION_PER_EXCHANGE;
spec.tcp_connection_strategy = strategy;

// For another runtime instance:
strategy.mode = COAKKA_V2_TCP_CONNECTION_BOUNDED_POOL;

if (capabilities.supports(
        COAKKA_V2_CAPABILITY_TCP_PERSISTENT_SINGLE_FLIGHT)) {
  strategy.mode = COAKKA_V2_TCP_CONNECTION_PERSISTENT_SINGLE_FLIGHT;
}

if (capabilities.supports(COAKKA_V2_CAPABILITY_TCP_MULTIPLEXING)) {
  strategy.mode = COAKKA_V2_TCP_CONNECTION_MULTIPLEXED;
}
```

These assignments demonstrate four independent selections. Construct one
`ConnectorOrchestrator` only after choosing the desired mode. For complete
ownership, lifecycle, error, and result semantics, see
[Native C++ Transport Configuration API](https://github.com/phuong-tran/coakka-publish/blob/main/docs/native-cpp-transport-configuration.md).

## Operational Selection

- Keep the Core `MULTIPLEXED` default for the normal concurrent request/reply
  path when the selected artifact reports the required capability.
- Choose explicit `PER_EXCHANGE` for short exchanges or when connection churn
  is deliberately preferred.
- Choose `BOUNDED_POOL` when connection setup is material and bounded reuse is
  useful without concurrent streams on one connection.
- Choose `PERSISTENT_SINGLE_FLIGHT` when one retained endpoint connection and
  serialized exchanges match the workload.
- An explicit `MULTIPLEXED` selection has the same bounded concurrency and
  connection-scoped fail-all law as the omitted native default.

CoAkka never automatically replays a request after business bytes may have
been written. See [Troubleshooting](troubleshooting.md) for configuration and
capability failures.
