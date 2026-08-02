# Runtime TLS And mTLS

CoAkka runtime TLS protects the built-in public TCP path. `PLAINTEXT` remains
the default. `TLS` verifies the outbound server certificate and host identity;
`MUTUAL_TLS` also requires and verifies a client certificate on inbound
connections. TLS 1.2 is the minimum and TLS 1.3 may be negotiated.

## Choose The Security Boundary

For Kubernetes, ingress TLS or a service mesh is often the best place to own
workload identity, certificate automation, and policy when all traffic stays
inside that managed boundary. Runtime TLS/mTLS remains appropriate when traffic
crosses the mesh boundary, when end-to-end runtime identity is required, or
when no mesh exists.

For LAN services, edge gateways, Raspberry Pi, BeagleBone, bare metal Linux,
and industrial Android deployments, runtime TLS/mTLS is a first-class option
when the exact release supports the target architecture. A private address or
isolated VLAN is a controlled network, not proof of confidentiality, peer
identity, or resistance to a compromised host.

Do not enable certificate validation on an embedded device until the host has
a trustworthy wall clock. Certificate issuance, renewal, secret distribution,
and clock readiness belong to the host or platform.

## Parameters

Zero-initialize `coakka_v2_tcp_security_options_t`, set `struct_size`, and set
only values whose presence bits are included in `fields`.

| Parameter | Meaning | Rule |
| --- | --- | --- |
| `mode` | `PLAINTEXT`, `TLS`, or `MUTUAL_TLS` | Required; plaintext needs no credential fields |
| `credential_source_kind` | Source of trust and identity material | `FILE` is implemented; `MEMORY` and `PROVIDER` are reserved and currently unavailable |
| `reload_mode` | `GRACEFUL` or `DRAIN_EXISTING_CONNECTIONS` | Required for TLS/mTLS |
| `credential_generation` | Monotonic credential revision | Nonzero initially; a live reload must be strictly newer |
| `credential_id` | Non-secret operator identifier | Required, at most 127 bytes; do not place a secret in it |
| `ca_certificate_file` | PEM trust roots used to verify peers | Required for TLS/mTLS |
| `identity_certificate_file` | PEM identity certificate chain | Required for TLS/mTLS |
| `private_key_file` | PEM private key matching the identity chain | Required for TLS/mTLS; keep access restricted |

The path strings are borrowed only for the synchronous apply call. The runtime
loads their contents into a private immutable context and does not expose the
paths or credential bytes through public introspection. The caller may release
or reuse the strings after the call returns. The runtime-owned compatibility
credential-ID pointer is valid until the next successful security apply or
runtime destruction; new connectors should copy the inline identity block.

## Use Your Language Connector

TLS/mTLS is not a C-only feature. The C ABI below is the stable contract shared
by the ecosystem; application developers normally use the types exposed by
their host-language connector. Every full runtime connector maps the same
startup security policy, capability discovery, structured apply result, and
same-mode newer-generation credential reload semantics.

Current connector surfaces include native C/C++, JVM/Kotlin, Spring Boot,
Quarkus, Node.js, Bun, Electron, Python, Go, C#, Rust, Swift, Zig, and Mojo.
Availability is determined by the effective capability bits and native platform
matrix of the exact artifact being loaded, not by requiring an application to
call C directly.

## Kotlin/JVM Example

```kotlin
import coakka.v2.connector.*

val security = RuntimeTcpSecuritySpec(
    mode = RuntimeTcpSecurityMode.MUTUAL_TLS,
    reloadMode = RuntimeTlsReloadMode.GRACEFUL,
    credentialGeneration = 1,
    credentialId = "factory-line-a-generation-1",
    caCertificateFile = "/run/coakka/ca.pem",
    identityCertificateFile = "/run/coakka/identity-chain.pem",
    privateKeyFile = "/run/coakka/identity-key.pem",
)

RuntimeHandle.open(
    startSpec = RuntimeStartSpec(
        systemName = "factory-line-a",
        nodeId = "gateway-1",
        routes = loadRoutes(),
        security = security,
    ),
).use { runtime ->
    runtime.start()

    val result = runtime.applyTcpSecurity(
        security.copy(
            credentialGeneration = 2,
            credentialId = "factory-line-a-generation-2",
            identityCertificateFile = "/run/coakka/next/identity-chain.pem",
            privateKeyFile = "/run/coakka/next/identity-key.pem",
        ),
    )
    check(result.applied()) {
        "TLS reload rejected: ${result.reasonName}; " +
            "active generation=${result.activeSecurity.credentialGeneration}"
    }
}
```

`RuntimeHandle.open(...)` applies the initial security policy while the runtime
is still `CREATED`; `start()` begins runtime work after that policy succeeds.
The reload call is synchronous and may perform file I/O and certificate
validation, so invoke it from application control flow rather than a
latency-sensitive request handler.

## C Example

This lower-level example is for native hosts and connector authors. It shows
the common ABI contract; it is not a requirement for Kotlin or other language
applications.

```c
coakka_v2_tcp_security_options_t security = {0};
security.struct_size = sizeof(security);
security.fields = COAKKA_V2_TCP_SECURITY_ALL_FIELDS;
security.mode = COAKKA_V2_TCP_SECURITY_MUTUAL_TLS;
security.credential_source_kind = COAKKA_V2_TLS_CREDENTIAL_SOURCE_FILE;
security.reload_mode = COAKKA_V2_TLS_RELOAD_GRACEFUL;
security.credential_generation = 1;
security.credential_id = "factory-line-a-generation-1";
security.ca_certificate_file = "/run/coakka/ca.pem";
security.identity_certificate_file = "/run/coakka/identity-chain.pem";
security.private_key_file = "/run/coakka/identity-key.pem";

coakka_v2_tcp_security_apply_result_t result = {0};
result.struct_size = sizeof(result);

coakka_v2_status_t status =
    coakka_v2_runtime_apply_tcp_security_options_ex(
        runtime, &security, &result);

if (status != COAKKA_V2_OK || result.changed == 0) {
  /* result.reason and result.active_security describe the rejected attempt
     and the generation that remains active. */
}
```

Apply the initial security mode in `CREATED`. After `STARTED`, only a strictly
newer credential generation for the same TLS/mTLS mode may be published.
Changing between plaintext, TLS, and mTLS requires a new runtime instance and
an application-owned cutover.

## Native C++ Example

```cpp
using namespace coakka::v2::native_cpp;

TcpSecuritySpec security;
security.mode = COAKKA_V2_TCP_SECURITY_MUTUAL_TLS;
security.reload_mode = COAKKA_V2_TLS_RELOAD_GRACEFUL;
security.credential_generation = 1;
security.credential_id = "factory-line-a-generation-1";
security.ca_certificate_file = "/run/coakka/ca.pem";
security.identity_certificate_file = "/run/coakka/identity-chain.pem";
security.private_key_file = "/run/coakka/identity-key.pem";

StartSpec spec;
spec.system_name = "factory-line-a";
spec.node_id = "gateway-1";
spec.routes = load_routes();
spec.tcp_security = security;

ConnectorOrchestrator connector(spec);

TcpSecuritySpec next = security;
next.credential_generation = 2;
next.credential_id = "factory-line-a-generation-2";
const auto result = connector.applyTcpSecurity(next);
if (!result.applied()) {
  // result.active_security is still the generation serving new handshakes.
}
```

The C++ connector copies all non-secret result metadata. The input path strings
remain caller-owned and are borrowed only during synchronous apply. See
[Native C++ Transport Configuration API](https://github.com/phuong-tran/coakka-publish/blob/main/docs/native-cpp-transport-configuration.md)
for per-function thread-safety, blocking, and error contracts.

## Rotation And Failure Semantics

The runtime completely loads and validates a new immutable context before
atomic publication. Invalid paths, malformed PEM, mismatched identity and key,
untrusted material, stale generation, wrong mode, allocation failure, or
terminal lifecycle state leave the previous active configuration unchanged.
`result.changed` remains `0`, and `result.active_security` reports what is still
active.

`GRACEFUL` applies the new generation to new handshakes while existing sessions
finish on their captured generation. `DRAIN_EXISTING_CONNECTIONS` also closes
or marks old-generation sessions for bounded retirement without interrupting
an in-flight request. Apply returning does not mean every old connection has
already closed.

The host should watch certificate expiry, provision the next files atomically,
submit a higher generation, inspect the structured result, and remove old
secret material only after its deployment policy says the old generation is no
longer needed. No runtime reload thread polls files or renews certificates.

## Secret Handling

- Mount or provision key files with the narrowest OS identity and permissions.
- Never put PEM, private keys, access tokens, or source paths in logs,
  deadletters, issue reports, or `credential_id`.
- Treat CA changes as trust-policy changes and test both peers before rollout.
- Keep clock synchronization and secure bootstrapping explicit on edge devices.
- Use release-specific dependency and architecture evidence before deploying
  to a device or Android native ABI.

See [Troubleshooting](troubleshooting.md) for reload, trust, and handshake
failures.
