# Python Runtime Transport Configuration

This guide describes the unpublished Python `3.0.0` candidate API. For released
`2.x` packages, use the documentation bundled with the selected release; the
constructor and inspection contracts below require the candidate API.

The public CoAkka C ABI is the semantic authority. The Python connector copies
its results into frozen Python values; it does not reinterpret lifecycle,
capability, validation, or edition behavior.

## Startup Contract

Set `connection_strategy` and `security` on `ConnectorStartSpec` or
`ConnectorConfig`. The candidate submits connection intent to Core's native
constructor, applies optional security while the owner is still `CREATED`,
installs host handles and starts Core. Core resolves defaults and reports the
accepted startup state. Connection validation refusal raises
`RuntimeTcpConnectionStartupError` with the original status and validation
fields. Security apply refusal raises `RuntimeTcpSecurityApplyError`, whose
`.result` retains Core's structured outcome. Failed startup unwinds the owner.

Omit connection intent to select Core `AUTO`; current effective connection
mode is `MULTIPLEXING`. Omit security to preserve Core defaults. Call
`RuntimeHost.read_runtime_capabilities(...)` before selecting an optional mode.
Package names and binary presence do not prove entitlement.

The candidate keeps optional intent nullable: `enable_monitor=None` omits the
group (Core currently OFF), while `False` explicitly supplies OFF. `network=None`
does not apply a network policy (Core native default EMBEDDED); explicit modes
and every supplied host/port field are submitted with their own presence bit,
including zero. The connector does not select masks by mode or validate
wildcards, route policy or generations itself. `queue_capacity=None` delegates
to Core's zero selector and accepted normalized capacity. The required base-ABI
`strict_no_drop` boolean still carries the builder's explicit safe `True`
intent, not an inferred Core default. Models are plain intent, without a local
`require_valid()` admission API; FFI type/width/C-string safety remains local.

```python
from coakka_v2_connector import (
    ConnectorStartSpec,
    RuntimeCapability,
    RuntimeHost,
    RuntimeTcpConnectionMode,
    RuntimeTcpConnectionStrategySpec,
    RuntimeTcpSecurityMode,
    RuntimeTcpSecuritySpec,
    local_route,
)

library = "/opt/coakka/lib/libcoakka_runtime_v2.so"
capabilities = RuntimeHost.read_runtime_capabilities(library)
mode = (
    RuntimeTcpConnectionMode.BOUNDED_POOL
    if capabilities.supports(RuntimeCapability.TCP_BOUNDED_POOL)
    else RuntimeTcpConnectionMode.PER_EXCHANGE
)

spec = ConnectorStartSpec(
    system_name="orders",
    node_id="orders-1",
    routes=[local_route("orders.create")],
    connection_strategy=RuntimeTcpConnectionStrategySpec(mode=mode),
    security=RuntimeTcpSecuritySpec(mode=RuntimeTcpSecurityMode.PLAINTEXT),
)

with RuntimeHost.start(spec, library) as runtime:
    assert runtime.runtime_startup_config()["connection"].mode == mode
```

## Public Functions

| Function | Purpose and parameters | Default/result | Ownership, thread safety, blocking, lifecycle, atomicity, errors, edition |
| --- | --- | --- | --- |
| `RuntimeHost.read_runtime_capabilities(runtime_lib_path=None)` | Load a runtime library and read compiled, entitled, and effective capability bits. The path follows normal resolver rules when omitted. | Returns a copied `RuntimeCapabilitiesSnapshot`. | No runtime handle is created. The native call is synchronous and thread-safe. Loader errors propagate. Available in all editions. |
| `capabilities.supports(bits)` | Check whether every requested capability bit is effective. | Returns `True` for a complete bit-set match, including the empty set. | Pure copied-value operation; non-blocking and thread-safe. It does not infer capability from edition or package identity. |
| `RuntimeHost.start(start_spec, runtime_lib_path=None)` | Own one process-local runtime. `connection_strategy` and `security` are optional startup policies. | Omitted intent preserves Core defaults; accepted startup receipt and any real security apply result are retained. | Core constructs connection state; optional security applies before handles/start. Refusal destroys the new owner and preserves Core status/validation or actual apply result. Credential file reads can block on local I/O. One active host per process. |
| `runtime.runtime_startup_config()` | Read Core's accepted defaults, CPU selection, connection and optional I/O facts, monitor permission and delivery composition. | Copied startup receipt, not inferred configuration or current liveness. | Retained after close as historical startup evidence. No connector OS probe or manufactured apply outcome. |
| `runtime.runtime_network_config()` | Read Core's effective network mode, explicit-policy fact and accepted endpoints. | Copied `mode`, `explicitPolicy`, `bindHost`, `advertiseHost`, `bindPort`, `advertisePort`; missing addresses remain `None`. | Synchronous and guarded against close. No input preference, descriptor or OS inspection is used to fill state. |
| `runtime.runtime_capabilities()` | Read capability truth from the loaded runtime. | Copied `RuntimeCapabilitiesSnapshot`. | Synchronous; contains no secret. Available before selecting optional features. |
| `runtime.tcp_connection_config()` | Read the effective connection mode and tuning provenance. | Copied `RuntimeTcpConnectionConfigSnapshot`. | Serialized with apply operations on this host. Synchronous and valid only while open. Available in all editions; effective modes/tuning depend on capabilities. |
| `runtime.tcp_security_info()` | Read active mode, generation, credential ID, protocol/verification metadata, validity, and certificate fingerprint. | Copied `RuntimeTcpSecurityInfoSnapshot`. | Serialized with apply operations. It excludes certificate/key paths, PEM bytes, keys, and provider diagnostics. TLS fields depend on edition/capability. |
| `runtime.startup_security_result()` | Inspect the startup security apply. | `None` when no explicit security policy was selected; otherwise copied result. | Non-blocking Python field read. Valid after start and after close; contains no secret paths/material. |
| `runtime.apply_tcp_connection_strategy(spec)` | Attempt a connection policy apply and return the effective state after the attempt. | Returns `RuntimeTcpConnectionApplyResult`; it does not throw for a native policy rejection. | Per-handle calls are serialized. Native call is synchronous and atomic. The runtime accepts connection changes only in `CREATED`, so a normally started Python host returns structured `ERR_BAD_STATE` and preserves active config. Throws only for closed handle, local conversion error, or ABI inconsistency. |
| `runtime.apply_tcp_security(spec)` | Attempt a security apply or same-mode newer-generation TLS/mTLS reload. | Returns `RuntimeTcpSecurityApplyResult`; it does not throw for a native policy rejection. | Per-handle calls are serialized. Paths are borrowed only for the synchronous call; the native runtime loads/validates credentials before atomic publication. Failed/stale reload keeps active generation. File I/O may block. TLS/mTLS/reload require effective capabilities. |
| `connection_result.applied()` / `security_result.applied()` | Test whether the native apply status is `OK`. | Returns a boolean; `changed` remains a separate fact. | Pure copied-value operation; non-blocking and thread-safe. A successful no-op can be applied without changing state. |

`ConnectorOrchestrator` is the compatibility name for `RuntimeHost` and exposes
the same functions.

## Specs And Defaults

`RuntimeTcpConnectionStrategySpec` fields default to `None`. At startup, an
entirely empty spec omits the nullable native group, selecting Core AUTO and
current effective MULTIPLEXING. AUTO is omission, not a connection enum alias:
enum zero remains explicit PER_EXCHANGE. Supplying any field preserves its
presence, including zero. Core's explicit options/apply contract requires mode;
tuning without mode receives its stable MODE_REQUIRED validation result.
Inapplicable tuning is not silently discarded. The example above deliberately
selects an optional mode; a normal first-run host omits connection strategy.

The new candidate removes `startup_connection_result()`: constructor acceptance
does not provide a `changed`/reason apply outcome. Use the Core startup receipt
for accepted facts or `tcp_connection_config()` for live inspection. A live
inspection refusal is never replaced by cached startup values. Actual
post-construction connection/security apply results retain Core's changed/reason
fields without reconstruction.

`RuntimeTcpSecuritySpec` defaults to `PLAINTEXT`, graceful reload, generation
zero, and empty credential fields. TLS/mTLS requires file-backed CA,
certificate, and private-key paths plus a positive generation and a stable
non-secret credential ID. Supplying credential fields in plaintext mode is an
error, not an ignored configuration.

See the canonical [connection strategy guide](https://github.com/phuong-tran/coakka-publish/blob/main/docs/coakka-runtime/reference/connection-strategies.md)
for `PER_EXCHANGE`, `BOUNDED_POOL`, `PERSISTENT_SINGLE_FLIGHT`, and
`MULTIPLEXING` defaults and availability.

## TLS Reload

```python
from coakka_v2_connector import RuntimeTcpSecurityMode, RuntimeTcpSecuritySpec

generation_2 = RuntimeTcpSecuritySpec(
    mode=RuntimeTcpSecurityMode.TLS,
    credential_generation=2,
    credential_id="orders-edge-2026-08-b",
    ca_certificate_file="/run/secrets/coakka/ca.pem",
    identity_certificate_file="/run/secrets/coakka/tls.crt",
    private_key_file="/run/secrets/coakka/tls.key",
)
result = runtime.apply_tcp_security(generation_2)
if not result.applied():
    # result.active_security is still the configuration serving traffic.
    raise RuntimeError(
        f"reload rejected: {result.reason_name}; "
        f"active generation={result.active_security.credential_generation}"
    )
```

The connector never returns secret file paths or material in snapshots. The
caller owns path strings and on-disk secret lifecycle. The runtime validates
and adopts credential content during the synchronous apply; a failed reload
does not replace active credentials.

See the canonical [TLS/mTLS guide](https://github.com/phuong-tran/coakka-publish/blob/main/docs/coakka-runtime/reference/tls-and-mtls.md)
for Kubernetes ingress/service-mesh guidance, controlled networks, LAN/edge,
RPi, BeagleBone, bare metal, industrial Android, and rotation semantics.

## Errors And Troubleshooting

Candidate calls that require `OK` raise `CoreCallError(RuntimeError)` with the
unchanged integer `status` and the failed `operation`. Unknown codes remain
integers. No retry, NULL-owner diagnosis or error-text classification is
performed. This generic status contract does not replace structured validation
and apply results below, and an `OK` result with a missing owner is a separate
contract error without a fabricated Core status.

Post-construction apply rejections preserve `status`, stable numeric `reason`,
`reason_name`, validation code/field/range, runtime state, `changed`, and the
active snapshot. Constructor validation preserves status and validation
code/field/range without fabricating an active configuration.
Unknown enum values remain numeric so a newer runtime does not crash an older
Python projection.

Use [common troubleshooting](https://github.com/phuong-tran/coakka-publish/blob/main/docs/coakka-runtime/operations/troubleshooting.md)
for native loading, capability, TLS material, platform trust/signing, and
reload failures. Publisher signing is currently absent and reported separately
from digest and execution evidence. Contact
`gabrielgun1983@gmail.com` or use the public issue tracker for support.
