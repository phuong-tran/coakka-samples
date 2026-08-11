# Runtime Network Modes

CoAkka Runtime is embedded in the application process. Embedding the runtime
does not mean the process must open a TCP listener. Network participation is a
separate startup decision and must not be inferred from route metadata.

## Choose One Mode

| Mode | Inbound listener | Remote routes | Typical use |
| --- | --- | --- | --- |
| `EMBEDDED` | no | rejected | One app process with local handlers, desktop/mobile apps, tests. |
| `OUTBOUND_ONLY` | no | allowed | A client, edge device, or worker that calls remote runtime nodes but accepts no inbound runtime traffic. |
| `NETWORK_NODE` | explicit | allowed | A supervised service or industrial device that peers must reach directly. |

Connector generation `2.4.0` and newer defaults to `EMBEDDED`. Existing
binaries that do not
apply a network policy retain the legacy route-derived behavior for ABI
compatibility. Do not use that compatibility path in new integrations.

## Embedded Means No Port

A local endpoint describes same-process placement. In an explicit network
mode, this is valid and preferred:

```text
host = current node id or 127.0.0.1
port = 0
flags = LOCAL
```

`port = 0` does not allocate a port, start a listener, connect through
loopback, or move local messages through TCP. The app host and runtime exchange
framed data through the connector-owned native ABI/fd boundary; the runtime
dispatches local work through its bounded local queue.

Do not reserve a free loopback port for an embedded runtime. Port reservation
has a race between closing the reservation socket and opening the real
listener, and it falsely implies that local delivery depends on TCP.

## Outbound Only

`OUTBOUND_ONLY` accepts route snapshots containing remote endpoints and can
initiate runtime TCP connections. It never creates an inbound listener.

Choose it when:

- the process only calls services owned by other runtime nodes;
- mobile or edge policy forbids inbound connections;
- NAT, firewall, or device supervision makes inbound reachability unsuitable.

The destination runtime must run as `NETWORK_NODE`, and route endpoints must
use its reachable advertised host and port.

## Network Node

`NETWORK_NODE` requires both a bind endpoint and an advertised endpoint:

```text
bindHost       = 0.0.0.0
bindPort       = 19301
advertiseHost  = 192.168.1.40
advertisePort  = 19301
```

`bindHost` is the numeric IPv4 address passed to the operating system. It may
be `0.0.0.0` when deployment policy intentionally allows all interfaces.
`advertiseHost` is the concrete address peers use and must never be wildcard.

Explicit bind is fail-closed. If the configured bind address is unavailable,
startup fails; the runtime does not silently widen the listener to all
interfaces. Binding `127.0.0.1` limits inbound connections to the same host.

## Connector Examples

The names follow each language's normal conventions, but the three modes have
the same meaning.

Kotlin/JVM:

```kotlin
RuntimeStartSpec(
    systemName = "factory-floor",
    nodeId = "station-17",
    routes = RuntimeClient.localRoutes(listOf("device.scan")),
    network = RuntimeNetworkConfig.embedded(),
)
```

JavaScript/TypeScript, including Node.js, Bun, and Electron:

```ts
const host = RuntimeHost.start({
  systemName: "factory-floor",
  nodeId: "station-17",
  routes: [localRoute("device.scan")],
  network: outboundOnlyNetwork(),
});
```

Python:

```python
spec = ConnectorStartSpec(
    system_name="factory-floor",
    node_id="station-17",
    routes=[local_route("device.scan")],
    network=RuntimeNetworkConfig.embedded(),
)
```

Go:

```go
spec := coakka.ConnectorStartSpec{
    SystemName: "factory-floor",
    NodeID: "station-17",
    Routes: []coakka.RouteSpec{coakka.LocalRouteDefault("device.scan")},
    Network: coakka.OutboundOnlyNetwork(),
}
```

C#:

```csharp
var spec = ConnectorStartSpec.Local("factory-floor", "device.scan") with
{
    Network = RuntimeNetworkConfig.Embedded(),
};
```

Rust:

```rust
let mut spec = ConnectorStartSpec::new("factory-floor", "station-17");
spec.routes.push(RouteSpec::single_local("device.scan", "station-17", 0));
spec.network = RuntimeNetworkConfig::outbound_only();
```

Swift:

```swift
let spec = ConnectorStartSpec(
    systemName: "factory-floor",
    nodeID: "station-17",
    routes: [.local("device.scan")],
    network: .embedded()
)
```

Zig:

```zig
var spec = runtime.localStartSpec("factory-floor", "station-17", "device.scan");
spec.network = runtime.NetworkConfig.networkNode(
    "0.0.0.0", 19301, "192.168.1.40", 19301,
);
```

The current Mojo lane is an executable C-shim smoke rather than a stable
general-purpose Mojo host API. Its same-process smoke applies `EMBEDDED`
explicitly; do not invent a Mojo network configuration surface until that
connector graduates from the shim boundary.

## Startup Order

Every connector follows one transactional order:

1. Load a native runtime from the same release generation as the connector.
2. Create the runtime.
3. Apply exactly one explicit network policy.
4. Apply optional connection strategy and TLS/mTLS policy.
5. Apply the initial route snapshot and export host handles in the connector's
   established order.
6. Start the runtime.
7. On failure, close exported handles and destroy the partial runtime.

Network policy is immutable after the first control snapshot or runtime start.
Changing bind ownership requires creating a new runtime instance.

## Android

Android uses the same three modes through `RuntimeNetworkConfig`. `EMBEDDED`
does not require the Android `INTERNET` permission. Add that permission only
when the app uses `OUTBOUND_ONLY` or `NETWORK_NODE`.

An Android app that must remain reachable while its UI is absent also needs an
appropriate service/supervision lifecycle. Runtime network mode does not
override Android background execution policy.

## Release Compatibility

The connector and native library must come from the same published generation.
A connector that calls `coakka_v2_runtime_apply_network_options` cannot run
against an older native package that lacks that symbol. Verify the package
manifest, native generation, OS, and CPU architecture before deployment.

This is an additive native ABI change: existing connectors retain the legacy
behavior, while new connectors select an explicit policy. Published native
artifacts are immutable and are not patched in place.

## Review Checklist

- Local-only app: `EMBEDDED`, local endpoint port `0`, no listener.
- Client-only app: `OUTBOUND_ONLY`, reachable remote route endpoints.
- Reachable node: `NETWORK_NODE`, explicit bind and non-wildcard advertise host.
- Bind failure stops startup instead of widening the listener.
- Connector and native runtime come from one release generation.
- Firewall, TLS/mTLS, credentials, and authorization match the chosen network exposure.
