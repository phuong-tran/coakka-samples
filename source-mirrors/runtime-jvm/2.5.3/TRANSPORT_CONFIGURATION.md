# JVM Transport Configuration

The JVM connector maps the public runtime C ABI without adding framework-specific
transport semantics. Connection strategy and security are configured at startup
through `RuntimeStartSpec`; capability discovery must drive optional UI and
configuration choices.

## Lifecycle And Atomicity

- `RuntimeHandle.open(...)` creates the native runtime, applies the optional
  connection and security specs, exports host handles, then applies the initial
  route snapshot.
- A connection strategy can change only while the runtime is `CREATED`.
  Changing it after `start()` returns a structured `ERR_BAD_STATE` result and
  leaves the effective strategy unchanged.
- The first TLS/mTLS generation is loaded while `CREATED`. A strictly later
  generation of the same mode can be reloaded while `STARTED`.
- Every apply is atomic. Rejection returns the effective connection config or
  active non-secret security identity that remains published.
- Changing between plaintext, TLS, and mTLS requires a new runtime instance.
- Public handle operations, TLS reload, start, stop, and close are serialized by
  the handle lifecycle lock. Result and snapshot data classes own copied JVM
  values and are safe to retain after the native call returns.

Connection apply is a short synchronous native call. TLS apply/reload is also
synchronous and may block on file I/O and certificate/private-key parsing. Do
not call TLS reload on a latency-sensitive application thread.

## Capability Discovery

```kotlin
val capabilities = RuntimeHandle.readRuntimeCapabilities(runtimeLibPath)
if (capabilities.supports(CoakkaRuntimeCapabilities.TCP_TLS)) {
    // Offer TLS configuration.
}
```

`compiledCapabilities` describes the binary, `entitledCapabilities` describes
distribution/license policy, and `effectiveCapabilities` is the set callers may
use. Do not infer support from `edition` alone.

| Strategy | Required capability | Startup fields |
| --- | --- | --- |
| `PER_EXCHANGE` | none | mode only |
| `BOUNDED_POOL` | `TCP_BOUNDED_POOL` | mode; tuning requires `TCP_POOL_TUNING` |
| `PERSISTENT_SINGLE_FLIGHT` | `TCP_PERSISTENT_SINGLE_FLIGHT` | mode only in the current ABI |
| `MULTIPLEXING` | `TCP_MULTIPLEXING` | mode only in the current ABI |

When bounded-pool tuning is unavailable, runtime defaults are 8 connections,
1,024 requests per connection, and 30,000 ms idle timeout. They belong to the
runtime defaults revision, not to the connector. Community builds may expose
bounded pool with fixed defaults while rejecting tuning fields.

TLS requires `TCP_TLS`; mTLS also requires `TCP_MUTUAL_TLS`. Live generation
reload requires `TLS_CREDENTIAL_RELOAD`.

## Startup Examples

Use exactly one of these startup strategy specs:

```kotlin
val perExchange = RuntimeTcpConnectionStrategySpec(
    RuntimeTcpConnectionMode.PER_EXCHANGE,
)

val boundedPool = RuntimeTcpConnectionStrategySpec(
    mode = RuntimeTcpConnectionMode.BOUNDED_POOL,
    maxConnections = 8,
    maxRequestsPerConnection = 1_024,
    idleTimeoutMs = 30_000,
)

val persistentSingleFlight = RuntimeTcpConnectionStrategySpec(
    RuntimeTcpConnectionMode.PERSISTENT_SINGLE_FLIGHT,
)

val multiplexing = RuntimeTcpConnectionStrategySpec(
    RuntimeTcpConnectionMode.MULTIPLEXING,
)
```

Pass the selected spec before starting:

```kotlin
val orchestrator = ConnectorOrchestrator.start(
    runtimeLibPath,
    RuntimeStartSpec(
        systemName = "orders",
        nodeId = "orders-a",
        routes = routes,
        connectionStrategy = boundedPool,
    ),
)

val startup = requireNotNull(orchestrator.startupConnectionResult())
check(startup.applied()) { startup }
```

Null `connectionStrategy` means use the runtime default without recording an
explicit connector choice. `RuntimeTcpConnectionMode.of(raw)` preserves a raw
future/unknown ABI value so native validation can return a structured result.
`fromConfigValue(...)` accepts the documented hyphenated names and underscore
variants for framework configuration.

## TLS And mTLS

```kotlin
val tls = RuntimeTcpSecuritySpec(
    mode = RuntimeTcpSecurityMode.TLS,
    reloadMode = RuntimeTlsReloadMode.GRACEFUL,
    credentialGeneration = 1,
    credentialId = "orders-2026-08-02",
    caCertificateFile = "/run/secrets/coakka/ca.pem",
    identityCertificateFile = "/run/secrets/coakka/node.pem",
    privateKeyFile = "/run/secrets/coakka/node.key",
)
```

For mTLS, use `RuntimeTcpSecurityMode.MUTUAL_TLS` with the same file-backed
fields. `credentialGeneration` must be positive and strictly increase on
reload. `credentialId` is non-secret operator metadata; do not place key bytes,
passwords, tokens, or private paths in it.

The JVM owns the path strings. JNA borrows them only for the synchronous apply.
The native runtime reads them into a private immutable credential context and
does not retain or return the paths. Results expose only mode, generation,
credential ID, protocol/verification facts, certificate bounds, and SHA-256
fingerprint. The private key and PEM bytes never enter public introspection.

Reload example:

```kotlin
val result = orchestrator.applyTcpSecurity(tls.copy(
    credentialGeneration = 2,
    credentialId = "orders-2026-08-03",
))

if (!result.applied()) {
    // result.activeSecurity is still the previously active generation.
    println(
        "TLS reload rejected status=${result.status} " +
            "reason=${result.reasonName} validation=${result.validationCode}",
    )
}
```

`GRACEFUL` lets established sessions finish on their captured generation.
`DRAIN_EXISTING_CONNECTIONS` begins bounded retirement; it is not a synchronous
barrier for an in-flight exchange.

## Public Function Contract

| Function | Purpose and behavior |
| --- | --- |
| `RuntimeHandle.readRuntimeCapabilities(path?)` | Loads the selected library and returns global capability truth without creating a runtime. The optional path uses normal resolver defaults when null. Synchronous, read-only, all editions; loader/status failures throw. |
| `runtimeCapabilities()` | Reads capability truth for the handle's loaded library. Synchronous and read-only; returned values are copied. |
| `tcpConnectionConfig()` | Reads the immutable effective strategy. Valid until replaced in `CREATED`; copied snapshot. Throws on a closed handle or getter failure. |
| `applyTcpConnectionStrategy(spec)` | Synchronously validates and atomically applies in `CREATED`. Returns status, reason, validation bounds, `changed`, runtime state, and the effective config remaining after the attempt. It does not throw for native rejection. |
| `tcpSecurityInfo()` | Reads copy-safe non-secret active TLS identity. It never returns credential files or key material. Throws on a closed handle or getter failure. |
| `applyTcpSecurity(spec)` | Synchronously loads/validates credentials and atomically publishes a first or later same-mode generation. Returns active state after success or rejection; it does not throw for native rejection. Availability follows capability truth. |
| `startupConnectionResult()` | Returns the immutable startup result, or null when `RuntimeStartSpec.connectionStrategy` was null. Non-blocking JVM read. |
| `startupSecurityResult()` | Returns the immutable startup result, or null when `RuntimeStartSpec.security` was null. Non-blocking JVM read. |
| `ConnectorOrchestrator` transport methods | Forward the same contract to its owned `RuntimeHandle`. The orchestrator is already started, so connection reconfiguration normally returns `ERR_BAD_STATE`; TLS same-mode later-generation reload remains valid. |
| `RuntimeCapabilitiesSnapshot.supports(bits)` | Returns true only when every requested bit exists in `effectiveCapabilities`. Pure, non-blocking, thread-safe. |
| `RuntimeTcpConnectionApplyResult.applied()` / `RuntimeTcpSecurityApplyResult.applied()` | Pure check for `status == OK`; callers must still inspect `changed` when they need to distinguish publication from an already-effective apply. |
| mode `of(raw)` functions | Construct raw ABI wrappers without connector-side rejection; native validation owns unknown-value semantics. |
| mode `fromConfigValue(text)` functions | Parse stable adapter names; null is not accepted and unknown text throws `IllegalArgumentException` before native apply. |

`RuntimeHandle.open(...)` converts a rejected startup transport apply into
`RuntimeTcpConnectionApplyException` or `RuntimeTcpSecurityApplyException`.
Each exception retains the full structured result. Later explicit apply calls
return the result instead, which lets operators decide whether and when to
retry a credential reload.

## Framework Boundary

Spring Boot and Quarkus use `runtime-default` to leave the corresponding startup
spec null. A tuning property without an explicit connection mode, or a
non-default reload/credential property without an explicit security mode, is
rejected at the framework configuration boundary. All capability, entitlement,
field, generation, and credential validation remains owned by the native
runtime.

Both adapters are currently local-handler-first. Local handler request/reply
traffic does not traverse the TCP transporter, so selecting TLS does not encrypt
an in-process handler call. TLS applies to runtime TCP traffic when routes and
deployment topology actually use that transport.

For parameters, network placement, rotation, and failure semantics, see the
[canonical TLS/mTLS guide](https://github.com/phuong-tran/coakka-publish/blob/main/docs/tls-and-mtls.md),
[connection strategy guide](https://github.com/phuong-tran/coakka-publish/blob/main/docs/connection-strategies.md),
and [common troubleshooting](https://github.com/phuong-tran/coakka-publish/blob/main/docs/troubleshooting.md).
