# Runtime Lane Owner Grants

> **Release status:** the additive native C ABI is released in exact generation
> `2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a` through Runtime npm,
> PyPI, NuGet, Go, and Swift packages. Maven Central Runtime and the promoted
> standalone native artifact pointer remain deferred. Released high-level
> connectors do not yet project typed owner grants.

Runtime messages and lane sessions have different ownership laws:

```text
message request: target-owned; any eligible replica may handle it
lane session:    instance-owned; one prepared receiver/publisher must keep it
```

This distinction matters whenever a target has multiple replicas. A Kubernetes
Service may route a prepare message to `billing-2` and a later TCP connection to
`billing-3`. The second pod does not own the prepared transfer ID, token,
destination, source callback, checkpoint, or pressure state. File Lane and
Stream Lane reject that mismatch, but an application that uses the Service
address for both connections will fail intermittently.

## Choose The API Profile

Both profiles are supported. Simple is not deprecated.

| Profile | Use it when | Endpoint responsibility | Native entrypoints |
| --- | --- | --- | --- |
| Simple | One stable lane instance owns the endpoint, or the application already pins the selected instance. | The application carries the prepared ID, token, host, port, and File/Stream identity together. | `coakka_v2_*_lane_create()` plus `prepare_receive()` or `prepare_publish()` |
| Owner-aware | A logical service has replicas, a pod/process incarnation matters, or an endpoint may be reassigned. | The lane returns one fixed-size capability naming the exact owner and actual listener port. | `coakka_v2_*_lane_create_owned()` plus `prepare_*_grant()` |

Start with Simple for a single process, stable edge device, loopback tool, or
application-managed endpoint. Move to Owner-aware when prepare may land on one
replica while a later data connection could otherwise land on another. Do not
use Owner-aware merely because it is newer; use it when owner pinning is part
of the deployment law.

Availability is explicit:

| Surface | Simple | Owner-aware |
| --- | --- | --- |
| Native C generation `2.5.0+4b65d0b2` | Yes | Yes, feature-gated by `COAKKA_V2_RUNTIME_FEATURE_LANE_OWNER_GRANTS` |
| Published Runtime npm `2.5.0`, PyPI `2.5.0`, NuGet `2.5.0`, Go `v1.8.0`, and SwiftPM `v2.5.0` typed APIs | Yes | Not yet projected as a typed connector API |
| Maven Central Runtime `2.4.1` | Yes where supported by that version | No; Runtime `2.5.0` has not been published there |

Do not reach around a connector's public API to resolve embedded native symbols.
Use Owner-aware through the native C package until a connector explicitly
documents a typed projection.

## Owner-Pinned Workflow

Use replica-transparent routing only until one lane owner accepts the work:

```text
sender -> billing Service -> billing-2 handles the prepare command
billing-2 prepares its local File Lane or Stream Lane state
billing-2 returns a grant naming billing-2 and its direct lane endpoint
sender/subscriber connects to the endpoint from that grant
```

The owner-issued grant contains:

- `owner_instance_id`: diagnostic and orchestration identity of the exact pod,
  process, or host retaining the prepared state;
- `advertised_host` and the listener's actual bound port;
- the transfer/session ID and secret bearer token;
- File Lane size and SHA-256 identity, or Stream Lane format and frame bound.

`advertised_host` must reach that exact owner. Do not replace it with a
replica-load-balancing Service address after the grant is issued.

Protocol v1 pinning is endpoint-level. `owner_instance_id` is a diagnostic and
orchestration label; it is not carried in the File Offer or Stream Open and is
not cryptographically authenticated by the lane handshake. TLS authenticates
the advertised host, not this label. Use a unique incarnation identity and a
fresh unpredictable application token after owner loss. An authenticated owner
generation or a wire-v2 owner binding is not part of Runtime `2.5.0`.

## Native C ABI

The original Simple API lane config structs remain frozen. Owner-aware creation
uses an additive wrapper that embeds the complete Simple config:

```c
coakka_v2_lane_owner_config_t owner = {
    .struct_size = sizeof(owner),
    .owner_instance_id = "billing-2",
    .advertised_host = "billing-2.billing-headless.default.svc.cluster.local",
};

coakka_v2_file_lane_owned_config_t config = {
    .struct_size = sizeof(config),
    .lane = simple_file_lane_config,
    .owner = owner,
};

coakka_v2_file_lane_t *lane =
    coakka_v2_file_lane_create_owned(&config);
```

After the receiver lane starts,
`coakka_v2_file_lane_prepare_receive_grant()` prepares the local receive and
returns `coakka_v2_file_receive_grant_t`. The Stream Lane equivalent is
`coakka_v2_stream_lane_create_owned()` followed by
`coakka_v2_stream_lane_prepare_publish_grant()`.

The fixed-size grant owns its projected strings. It allocates no runtime state
beyond the bounded transfer/session record already admitted by prepare. Treat
its token as a secret: do not log or persist the complete grant.

File and Stream tokens have different lifetime laws. A File grant may be reused
for bounded resume and idempotent completed-status handling while its owning
record remains retained. A Stream grant is consumed when the publisher admits
the first valid `OPEN`; transport failure after that admission requires a new
prepare and grant. Invalid authentication or format attempts do not consume it.

Check `COAKKA_V2_RUNTIME_FEATURE_LANE_OWNER_GRANTS` before resolving or invoking
the additive symbols from a dynamically loaded runtime. Existing create and
prepare APIs remain available for single-instance or application-managed
endpoint workflows.

The release-branch C11 samples run both profiles through the same bounded
lifecycle and terminal checks:

```sh
bash run.sh runtime-test file-lane-simple
bash run.sh runtime-test file-lane-owner-aware
bash run.sh runtime-test stream-lane-simple
bash run.sh runtime-test stream-lane-owner-aware
```

The owner-aware profiles consume the projected endpoint instead of rebuilding
it from separate fields, never print the token, clear their local fixed-size
grant after synchronous submission, stop both lanes, and destroy only after
concurrent calls have returned.

## Kubernetes Addressing

The ordinary Runtime message route may still use one ClusterIP Service DNS
name. The lane owner advertises a pod-specific reachable address, commonly:

- a StatefulSet pod DNS name behind a headless Service;
- a pod hostname/subdomain record;
- a Pod IP supplied through the Downward API when the network and certificate
  policy permit it.

Bind and advertise are different responsibilities. A lane may bind
`0.0.0.0:0`; the grant publishes the configured owner host plus the actual port
chosen by the listener.

Current TLS and mutual-TLS clients validate the certificate against the
advertised host used for the connection. The certificate therefore needs a
matching DNS/IP identity. A separate TLS server-name override is not part of
the current grant contract.

No gateway is required when peers can reach the owner-specific endpoint.
NetworkPolicy must allow the lane connection. A gateway or staging service is
an application/deployment option only when direct owner reachability is not
available.

## One Replica Or All Replicas

One grant always describes one owner and one point-to-point session.

To send work to one replica, route one prepare command and use its returned
grant. To distribute to all replicas, the application or topology controller
must enumerate the intended owners and obtain one independent grant per owner:

```text
billing-1 -> grant-1 -> transfer/session-1
billing-2 -> grant-2 -> transfer/session-2
billing-3 -> grant-3 -> transfer/session-3
```

Calling a load-balancing Service three times does not guarantee one call per
replica. Fan-out has independent tokens, terminal outcomes, retry decisions,
and pressure. Partial success is visible rather than collapsed into one result.

File fan-out may reuse one verified immutable source. Live Stream Lane fan-out
also needs an application-owned bounded tee, journal, or independent source
cursor per subscriber; Stream Lane does not silently multiplex one callback
across sessions.

## Owner Loss

Stopping or destroying the lane invalidates every grant issued by that owner.
Pod loss has the same effect. CoAkka does not migrate a prepared session
silently because another replica does not own its callback, local file state,
token record, committed offset, or pressure state.

Because protocol v1 does not authenticate `owner_instance_id`, address reuse is
an application/deployment boundary. Do not reuse a prior owner's token when a
pod or process incarnation changes, even when StatefulSet DNS or a fixed port
remains the same.

Recovery is explicit:

1. observe the failed or lost session;
2. route a new prepare command;
3. let the selected replacement owner issue a new ID/token grant;
4. resume a durable file according to application policy, or start a new
   stream session and report discontinuity.

The native core establishes this owner/grant boundary. Replica enumeration,
`ONE`/`ALL` distribution policy, durable coordination, and high-level connector
ergonomics remain application and connector responsibilities.
