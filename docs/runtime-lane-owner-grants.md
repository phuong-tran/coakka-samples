# Runtime Lane Owner Grants

> **Connector availability:** typed owner grants begin in the corrective
> connector `2.5.2` source train over exact native generation
> `2.5.0+4b65d0b2256037bf7fc180bfa6df8c41efc1dd6a`. Use an exact coordinate
> from [Current Packages](current-packages.md); do not generate these methods
> against `2.5.1` or an older connector merely because it carries that native
> generation. The separately versioned Android AAR begins this surface at
> connector `1.2.0` over exact native generation
> `2.5.1+26f7944de4a4e0598845a54e4775f9463a9e33be`; it must report that
> independently pinned identity rather than the JVM connector generation.

Runtime messages and lane sessions have different ownership laws:

```text
message request: target-owned; any eligible replica may handle it
lane session:    instance-owned; one prepared receiver/publisher must keep it
```

This is an end-user connector contract, not a native-only feature. A receiver
or publisher opens an owner-aware lane, prepares local state, and returns a
typed capability containing the exact owner endpoint. The remote sender or
subscriber reconstructs that capability from its authenticated control-plane
response and derives the send or subscribe specification from it.

## Connector API

Every full connector in the `2.5.2` train projects the same four operations.
Names follow host-language conventions:

| Host surface | Open exact owner | Prepare capability | Derive remote job |
| --- | --- | --- | --- |
| JVM/Kotlin/Java | `FileLane.openOwned`, `StreamLane.openOwned` | `prepareReceiveGrant`, `preparePublishGrant` | `toSendSpec`, `toSubscribeSpec` |
| Android/Kotlin | `FileLane.openOwned`, `StreamLane.openOwned` | `prepareReceiveGrant`, `preparePublishGrant` | `toSendSpec`, `toSubscribeSpec` |
| Python | `FileLane.open_owned`, `StreamLane.open_owned` | `prepare_receive_grant`, `prepare_publish_grant` | `to_send_spec`, `to_subscribe_spec` |
| Node.js | `FileLane.openOwned`, `StreamLane.openOwned` | `prepareReceiveGrant`, `preparePublishGrant` | `toSendSpec`, `toSubscribeSpec` |
| Bun | `FileLane.openOwned`, `StreamLane.openOwned` | `prepareReceiveGrant`, `preparePublishGrant` | `toSendSpec`, mailbox `toSubscribeSpec` |
| Electron main process | Same Node.js exports | Same Node.js methods | Same Node.js helpers; never expose grants to preload/renderer |
| C# | `FileLane.OpenOwned`, `StreamLane.OpenOwned` | `PrepareReceiveGrant`, `PreparePublishGrant` | `ToSendSpec`, `ToSubscribeSpec` |
| Go | `OpenOwnedFileLane`, `OpenOwnedStreamLane` | `PrepareReceiveGrant`, `PreparePublishGrant` | `ToSendSpec`, `ToSubscribeSpec` |
| Swift | `FileLane.openOwned`, `StreamLane.openOwned` | `prepareReceiveGrant`, `preparePublishGrant` | `sendSpec`, `subscribeSpec` |
| Rust | `FileLane::open_owned`, `StreamLane::open_owned` | `prepare_receive_grant`, `prepare_publish_grant` | `to_send_spec`, `to_subscribe_spec` |
| Zig | `openOwnedFileLane`, `openOwnedStreamLane` | `prepareReceiveGrant`, `preparePublishGrant` | `toSendSpec`, `toSubscribeSpec` |

Swift grant values are `Codable`. Node/Bun grants accept the object shape
returned by `toJSON()`. Python dataclasses, Go structs, public JVM/C#
constructors, and Rust `from_control_plane` constructors support explicit
reconstruction. Keep serialization inside the authenticated application
control plane and ensure logs redact the bearer token.

Android uses the same Kotlin operation names but its File API accepts
`java.io.File`, not the desktop JVM connector's `java.nio.file.Path`. Stream
source and consumer callbacks receive borrowed direct `ByteBuffer` views on
bounded native lane workers; copy bytes before returning if the app retains
them. Same-lane `close()` from either callback fails fast because native stop
cannot join its current worker; hand shutdown to another thread. Android Stream
format IDs are positive Kotlin `Long` values (`1..Long.MAX_VALUE`).

Tauri intentionally does not expose lanes to WebView code. Its trusted Rust
host uses the Rust API above. Mojo currently has a C conformance shim proving
the native File and Stream grant path, not a stable high-level Mojo grant API.

The Simple API remains supported for a single stable lane instance or for an
application that already pins the selected process endpoint. Owner-aware is
required when prepare may land on one replica while a later data connection
could otherwise land on another.

For an executable connector example, run the
[`runtime/go/replica-file-fanout`](https://github.com/phuong-tran/coakka-samples/tree/main/runtime/go/replica-file-fanout)
sample. It resolves public Go module `v1.8.2`, prepares three exact owners,
round-trips every grant through JSON, sends one immutable source independently
to all three replicas, and verifies sender terminal state, receiver terminal
state, and destination digest per owner:

```sh
cd runtime/go/replica-file-fanout
bash run.sh
```

## One Owner Workflow

Replica-transparent routing may select a receiver only until one owner accepts
the prepare command:

```mermaid
sequenceDiagram
    participant S as Sender app
    participant C as Authenticated control plane
    participant R as billing-2 control handler
    participant L as billing-2 File Lane

    S->>C: prepare metadata (size, SHA-256, business identity)
    C->>R: authorize on selected replica
    R->>L: openOwned + prepareReceiveGrant
    L-->>R: grant(owner=billing-2, direct host, actual port)
    R-->>C: serialized grant
    C-->>S: serialized grant
    S->>S: reconstruct typed grant
    S->>L: submitSend(grant.toSendSpec(source))
    L-->>S: independent sender terminal result
    L-->>R: independent receiver terminal result
```

The grant contains:

- `ownerInstanceId`: exact pod/process incarnation retaining prepared state;
- `advertisedHost` and the listener's actual bound port;
- transfer/session ID and secret bearer token;
- File Lane size and SHA-256, or Stream Lane format and frame bound.

Never replace the grant endpoint with a ClusterIP, ingress, or another
replica-balancing address. Protocol v1 pins by endpoint. `ownerInstanceId` is a
diagnostic/orchestration identity and is not a second routing key in the File
Offer or Stream Open handshake.

## All Replicas Workflow

One grant always names one owner and one point-to-point transfer or session.
`ALL` is an application topology policy:

```mermaid
flowchart LR
    T["Topology controller"] -->|"enumerate exact owners"| D["Owner directory"]
    D --> B1["billing-1 control endpoint"]
    D --> B2["billing-2 control endpoint"]
    D --> B3["billing-3 control endpoint"]
    B1 --> G1["fresh grant-1"]
    B2 --> G2["fresh grant-2"]
    B3 --> G3["fresh grant-3"]
    G1 ==> X1["transfer/session-1"]
    G2 ==> X2["transfer/session-2"]
    G3 ==> X3["transfer/session-3"]
```

Do not implement `ALL` by calling a load-balancing Service N times. That can
select the same replica repeatedly and skip another replica. Enumerate stable
owner incarnations first, call each exact owner's authenticated control
endpoint once, and require the returned `ownerInstanceId` to match the owner
requested.

File fan-out may reuse one verified immutable source and its size/SHA-256. It
still needs one fresh transfer ID and token, one prepare, one send record, and
one terminal outcome per owner. Partial success, retry, cancellation, and
pressure remain independent.

Live Stream fan-out needs one grant per publisher owner plus an
application-owned bounded tee, journal, or independent source cursor. Stream
Lane does not silently multiplex one callback across several sessions.

## Android AAR Example

The one-owner and all-replicas diagrams above apply unchanged to Android. The
replica selected by the authenticated control request prepares locally and
returns the copied grant; the sender must use the endpoint carried by that
grant:

```kotlin
val source = File(filesDir, "model.bin")
val digest = FileLane.sha256(source)

// This long-lived receiver is owned by the exact Android process or replica.
val receiver = FileLane.openOwned(
    FileLaneConfig(flags = FileLaneFlags.RECEIVER, bindHost = "0.0.0.0"),
    LaneOwnerConfig("worker-2", "10.0.0.12"),
)
val grant = receiver.prepareReceiveGrant(
    FileReceiveSpec(
        transferId = "model-42-worker-2",
        authorizationToken = freshTransferToken,
        destinationFile = File(filesDir, "model-42.bin"),
        expectedSize = digest.size,
        expectedSha256 = digest.sha256,
    ),
)

// After authenticated serialization/reconstruction at the sender, finalize
// both records. submitSend alone is not completion.
FileLane.open(FileLaneConfig(flags = FileLaneFlags.SENDER)).use { sender ->
    var sent: FileTransferSnapshot? = null
    var received: FileTerminalDto? = null
    try {
        sender.submitSend(grant.toSendSpec(source))
        sent = finalizeAndForgetSend(sender, grant.transferId, cancelFirst = false)
    } finally {
        received = finalizeAndForgetReceive(
            receiver,
            grant.transferId,
            cancelFirst = sent?.completed != true,
        )
    }
    check(sent?.completed == true && received?.completed == true)
}
```

The Stream callback surface is also available from Android connector `1.2.0`:

```kotlin
val publisher = StreamLane.openOwned(
    StreamLaneConfig(
        flags = StreamLaneFlags.PUBLISHER,
        bindHost = "0.0.0.0",
        maxFrameBytes = 256 * 1024,
        maxWindowBytes = 512 * 1024,
    ),
    LaneOwnerConfig("camera-3", "10.0.0.23"),
)
val grant = publisher.preparePublishGrant(
    StreamPublishSpec(
        sessionId = "camera-3-session-7",
        authorizationToken = freshSessionToken,
        formatId = cameraFormatId,
        maxFrameBytes = 256 * 1024,
        source = AndroidStreamSource { destination -> nextFrameInto(destination) },
    ),
)

subscriber.subscribe(
    grant.toSubscribeSpec(
        initialWindowBytes = 512 * 1024,
        consumer = AndroidStreamConsumer { borrowedFrame, metadata ->
            consumeBeforeReturn(borrowedFrame, metadata)
            AndroidStreamConsumerDecision.CONTINUE
        },
    ),
)
```

For `ALL`, call each exact Android owner's authenticated control endpoint once,
verify the returned `ownerInstanceId`, and retain one independent terminal
outcome per File transfer or Stream session. Repeating the same request against
a load balancer does not enumerate replicas.

## Connector File Example

This Kotlin reference shows the distributed ownership and cleanup shape.
`FileGrantDto` is the application control-plane payload. The equivalent
language constructor or decode operation from the API table reconstructs the
connector grant at the sender.

The orchestrator creates the transfer ID before the prepare RPC. Therefore it
can ask the same exact owner to cancel and forget a possibly prepared receive
even when the prepare response is lost:

```mermaid
sequenceDiagram
    participant O as Fan-out orchestrator
    participant R as Exact replica control endpoint
    participant S as Sender File Lane
    participant L as Replica-owned receiver File Lane

    O->>R: prepareFile(transferId, size, SHA-256)
    R->>L: prepareReceiveGrant
    L-->>R: owner-pinned grant
    R-->>O: serialized grant
    O->>O: verify ownerInstanceId
    O->>S: submitSend(grant.toSendSpec(source))
    alt sender reaches COMPLETED + OK
        par sender terminal
            S-->>O: SEND terminal
        and receiver terminal
            L-->>R: RECEIVE terminal
        end
        O->>R: finalizeReceive(cancelFirst=false)
    else prepare response, submit, or sender fails
        O->>R: finalizeReceive(cancelFirst=true)
        R->>L: cancel if retained, then bounded terminal wait
    end
    R->>R: persist redacted terminal outcome, then forget RECEIVE
    O->>S: forget terminal SEND if a record exists
    O->>O: retain one outcome for this owner
```

```kotlin
data class FileGrantDto(
    val ownerInstanceId: String,
    val advertisedHost: String,
    val port: Int,
    val transferId: String,
    val authorizationToken: String,
    val expectedSize: Long,
    val expectedSha256: ByteArray,
) {
    override fun toString() =
        "FileGrantDto(ownerInstanceId=$ownerInstanceId, transferId=$transferId, " +
            "authorizationToken=<redacted>, expectedSize=$expectedSize)"
    }

data class FileTerminalDto(
    val state: String,
    val result: String,
    val completed: Boolean,
    val detail: String,
)

fun FileReceiveGrant.toDto() = FileGrantDto(
    owner.ownerInstanceId,
    owner.advertisedHost,
    owner.port,
    transferId,
    authorizationToken,
    expectedSize,
    expectedSha256,
)

fun FileGrantDto.toConnectorGrant() = FileReceiveGrant(
    LaneOwnerEndpoint(ownerInstanceId, advertisedHost, port),
    transferId,
    authorizationToken,
    expectedSize,
    expectedSha256,
)
```

Each replica owns one long-lived receiver lane. Its authenticated handler
chooses the destination and creates a fresh capability locally. It accepts the
caller-generated transfer ID so an uncertain prepare can still be finalized:

```kotlin
class ReplicaFileReceiver(
    ownerInstanceId: String,
    advertisedHost: String,
    private val destinationFor: (String) -> Path,
) : AutoCloseable {
    private val random = SecureRandom()
    private val lane = FileLane.openOwned(
        FileLaneConfig(flags = FileLaneFlags.RECEIVER, bindHost = "0.0.0.0"),
        LaneOwnerConfig(ownerInstanceId, advertisedHost),
    )

    // Called only through this replica's authenticated control endpoint.
    fun prepare(
        transferId: String,
        objectKey: String,
        size: Long,
        sha256: ByteArray,
    ): FileGrantDto {
        val tokenBytes = ByteArray(32).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)
        val grant = lane.prepareReceiveGrant(
            FileReceiveSpec(
                transferId,
                token,
                destinationFor(objectKey), // Never accept a remote raw path.
                size,
                sha256,
            )
        )
        return grant.toDto()
    }

    // Application control-plane operation, not a connector method. It first
    // returns a previously persisted redacted outcome for an idempotent retry.
    // Otherwise it optionally cancels, waits with a monotonic deadline, stores
    // the terminal DTO without the token, forgets RECEIVE, and returns the DTO.
    // It returns null only when neither a retained record nor stored outcome exists.
    fun finalizeReceive(transferId: String, cancelFirst: Boolean): FileTerminalDto? =
        finalizeAndForgetReceive(lane, transferId, cancelFirst)

    override fun close() = lane.close()
}
```

`finalizeAndForgetReceive` must be notification-driven and bounded. If its
normal wait reaches the deadline, it requests cancellation and waits for the
terminal cancellation result before forgetting. The authenticated endpoint
persists only the redacted terminal DTO in a bounded TTL or durable outcome
store before it forgets native state, so a lost finalize response can be
retried without retaining the grant token.

The sender hashes once and processes every exact owner independently. It does
not stop at the first failure and it never returns a successful owner outcome
until both sides report `COMPLETED + OK`:

```kotlin
data class ReplicaControl(
    val expectedOwnerInstanceId: String,
    val prepareFile: (
        transferId: String,
        objectKey: String,
        size: Long,
        sha256: ByteArray,
    ) -> FileGrantDto,
    val finalizeReceive: (transferId: String, cancelFirst: Boolean) -> FileTerminalDto?,
)

data class ReplicaFileOutcome(
    val ownerInstanceId: String,
    val transferId: String,
    val sender: FileTransferSnapshot?,
    val receiver: FileTerminalDto?,
    val failures: List<String>,
) {
    val completed: Boolean
        get() = sender?.completed == true && receiver?.completed == true && failures.isEmpty()
}

fun sendToAllReplicas(
    source: Path,
    objectKey: String,
    owners: List<ReplicaControl>,
): List<ReplicaFileOutcome> {
    require(owners.map { it.expectedOwnerInstanceId }.distinct().size == owners.size) {
        "ALL requires a unique exact owner list"
    }
    val digest = FileLane.sha256(source)
    return FileLane.open(FileLaneConfig(flags = FileLaneFlags.SENDER)).use { sender ->
        owners.map { owner ->
            sendToOneReplica(sender, source, objectKey, digest, owner)
        }
    }
}

private fun sendToOneReplica(
    sender: FileLane,
    source: Path,
    objectKey: String,
    digest: FileDigest,
    owner: ReplicaControl,
): ReplicaFileOutcome {
    val transferId = UUID.randomUUID().toString()
    val failures = mutableListOf<String>()
    var prepareAttempted = false
    var prepareConfirmed = false
    var sendAttempted = false
    var senderTerminal: FileTransferSnapshot? = null
    var receiverTerminal: FileTerminalDto? = null

    try {
        prepareAttempted = true
        val dto = owner.prepareFile(transferId, objectKey, digest.size, digest.sha256)
        prepareConfirmed = true
        require(dto.transferId == transferId) { "control endpoint changed transferId" }
        require(dto.ownerInstanceId == owner.expectedOwnerInstanceId) {
            "control endpoint returned a grant for another replica"
        }

        sendAttempted = true
        sender.submitSend(dto.toConnectorGrant().toSendSpec(source))
    } catch (failure: Exception) {
        failures += redactedFailure(failure)
    } finally {
        if (sendAttempted) {
            runCatching {
                // Application helper: find the SEND record if present; optionally
                // cancel; wait to a bounded terminal state; then forget it.
                finalizeAndForgetSend(
                    sender,
                    transferId,
                    cancelFirst = failures.isNotEmpty(),
                )
            }.onSuccess { senderTerminal = it }
                .onFailure { failures += "sender cleanup: ${redactedFailure(it)}" }
        }

        if (prepareAttempted) {
            runCatching {
                owner.finalizeReceive(
                    transferId,
                    cancelFirst = senderTerminal?.completed != true,
                )
            }.onSuccess { receiverTerminal = it }
                .onFailure { failures += "receiver cleanup: ${redactedFailure(it)}" }
        }
    }

    if (sendAttempted && senderTerminal?.completed != true) {
        failures += "sender did not complete"
    }
    if (prepareConfirmed && receiverTerminal?.completed != true) {
        failures += "receiver did not complete"
    }
    return ReplicaFileOutcome(
        owner.expectedOwnerInstanceId,
        transferId,
        senderTerminal,
        receiverTerminal,
        failures.distinct(),
    )
}
```

`FileTerminalDto`, `finalizeAndForgetSend`, `finalizeAndForgetReceive`, and
`redactedFailure` are application control-plane helpers, not extra connector
APIs. They must never serialize a token or raw destination path. Finalize calls
are blocking and notification-driven; execute at most one per enumerated owner
on a bounded worker pool, never on latency-sensitive request, event-loop, or UI
threads. A cleanup failure remains in that owner's outcome and must be retried;
it must not be converted into success or hide outcomes from other replicas.

## Connector Stream Example

Stream uses the same control-plane handoff, but its grant is single-admission.
The publisher owner prepares its bounded source callback and returns a DTO:

```kotlin
data class StreamGrantDto(
    val ownerInstanceId: String,
    val advertisedHost: String,
    val port: Int,
    val sessionId: String,
    val authorizationToken: String,
    val formatId: Long,
    val maxFrameBytes: Int,
) {
    override fun toString() =
        "StreamGrantDto(ownerInstanceId=$ownerInstanceId, sessionId=$sessionId, " +
            "authorizationToken=<redacted>, formatId=$formatId)"
}

val publisher = StreamLane.openOwned(
    StreamLaneConfig(flags = StreamLaneFlags.PUBLISHER, bindHost = "0.0.0.0"),
    LaneOwnerConfig("camera-3", "camera-3.camera-headless.default.svc.cluster.local"),
)
val localGrant = publisher.preparePublishGrant(
    StreamPublishSpec(sessionId, freshToken, formatId, maxFrameBytes, boundedSource)
)
val response = StreamGrantDto(
    localGrant.owner.ownerInstanceId,
    localGrant.owner.advertisedHost,
    localGrant.owner.port,
    localGrant.sessionId,
    localGrant.authorizationToken,
    localGrant.formatId,
    localGrant.maxFrameBytes,
)
```

The subscriber reconstructs the typed capability and never substitutes a
Service address:

```kotlin
val receivedGrant = StreamPublishGrant(
    LaneOwnerEndpoint(response.ownerInstanceId, response.advertisedHost, response.port),
    response.sessionId,
    response.authorizationToken,
    response.formatId,
    response.maxFrameBytes,
)
subscriber.subscribe(
    receivedGrant.toSubscribeSpec(
        initialWindowBytes = response.maxFrameBytes * 2,
        consumer = boundedConsumer,
    )
)
```

After the first valid `OPEN`, reconnecting requires another prepare and a fresh
grant. For `ALL`, prepare one publisher session per exact owner and give each
session its own bounded source cursor or tee branch and terminal result.

## Token Lifetime

File and Stream grants deliberately have different lifetime laws:

- A File token is scoped to one prepared transfer identity. It may be reused
  only for bounded resume and idempotent completed-status handling while the
  exact owner retains that record. It is not a one-network-attempt token.
- A Stream token is single-admission. The first valid `OPEN` consumes it.
  Transport failure after admission requires a new prepare and fresh grant.
  Invalid authentication or format attempts do not consume it.

Neither token is a user session or general-purpose credential. Do not log it,
persist a complete grant, reuse it for another owner, or expose it to browser,
Electron renderer, or Tauri WebView code.

## Kubernetes Addressing

The ordinary Runtime message route may use a ClusterIP Service. The lane owner
must advertise a directly reachable per-owner address, commonly:

- StatefulSet pod DNS behind a headless Service;
- pod hostname/subdomain DNS;
- Pod IP supplied through the Downward API when network and certificate policy
  permit it.

Bind and advertise are separate. A lane may bind `0.0.0.0:0`; its grant returns
the configured direct host plus the actual listener port. TLS/mTLS validates
the connection host, so certificates need a matching DNS/IP identity. A
separate TLS server-name override is not part of this grant contract.

NetworkPolicy must allow the direct lane connection. A gateway or staging
service is an application/deployment choice only when owner reachability is not
available; it does not change the point-to-point grant law.

## Owner Loss

Stopping or destroying a lane invalidates every grant it issued. Pod loss has
the same effect. CoAkka does not migrate prepared state because another replica
does not own the token record, destination or source callback, committed
offset, or pressure state.

Recovery is explicit:

1. record the failed/lost owner's outcome;
2. select an exact replacement owner;
3. issue a new prepare command and fresh ID/token grant;
4. resume a durable file according to application policy, or start a new stream
   and report discontinuity.

Do not reuse a prior token after process/pod incarnation changes, even when
StatefulSet DNS and port are reused.

## Native Contract

Connectors feature-detect
`COAKKA_V2_RUNTIME_FEATURE_LANE_OWNER_GRANTS` before resolving additive native
symbols. Owner-aware native creation embeds the frozen Simple config in
`coakka_v2_file_lane_owned_config_t` or
`coakka_v2_stream_lane_owned_config_t`, then uses
`coakka_v2_file_lane_prepare_receive_grant()` or
`coakka_v2_stream_lane_prepare_publish_grant()`.

The fixed-size native grant owns its strings and adds no unbounded state beyond
the already admitted transfer/session record. Existing create and prepare APIs
remain available for Simple deployments. Release evidence exercises both
profiles:

```sh
bash run.sh runtime-test file-lane-simple
bash run.sh runtime-test file-lane-owner-aware
bash run.sh runtime-test stream-lane-simple
bash run.sh runtime-test stream-lane-owner-aware
```
