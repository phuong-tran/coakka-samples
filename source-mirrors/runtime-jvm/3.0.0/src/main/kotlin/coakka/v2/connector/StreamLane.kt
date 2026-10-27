package coakka.v2.connector

import coakka.v2.connector.internal.NativeBridge
import coakka.v2.connector.internal.NativeRecord
import coakka.v2.connector.internal.NativeStreamConsumerAdapter
import coakka.v2.connector.internal.NativeStreamSourceAdapter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object StreamLaneFlags { const val PUBLISHER = 1; const val SUBSCRIBER = 1 shl 1 }
object StreamFrameFlags { const val KEYFRAME = 1; const val DISCONTINUITY = 1 shl 1; const val END_OF_SEGMENT = 1 shl 2 }
object StreamPressureReasons { const val CREDIT_WAIT = 1; const val TRANSPORT_WRITE = 1 shl 1; const val CONSUMER_BUSY = 1 shl 2; const val TRANSPORT_READ = 1 shl 3 }
enum class StreamLaneSecurityMode(val nativeValue: Int) { DIRECT(0), TLS(1), MUTUAL_TLS(2) }
enum class StreamDirection(val nativeValue: Int) { PUBLISH(1), SUBSCRIBE(2) }
enum class StreamState(val nativeValue: Int) {
    PREPARED(1), QUEUED(2), CONNECTING(3), ACTIVE(4), STOPPING(5), ENDED(6), REJECTED(7), FAILED(8), CANCELED(9);
    /** Known SDK vocabulary only; not an observed runtime transition. */
    val terminal: Boolean get() = this == ENDED || this == REJECTED || this == FAILED || this == CANCELED
}
enum class StreamResult(val nativeValue: Int) {
    NONE(0), OK(1), NOT_PREPARED(2), TOKEN_MISMATCH(3), FORMAT_MISMATCH(4), FRAME_LIMIT(5),
    NETWORK_IO(6), TIMEOUT(7), QUEUE_FULL(8), PROTOCOL_ERROR(9), SOURCE_ERROR(10),
    CONSUMER_ERROR(11), INTERNAL_ERROR(12), CANCELED_BY_HOST(13), TLS_CONFIG_INVALID(14),
    TLS_HANDSHAKE_FAILED(15), PEER_CERT_UNTRUSTED(16), PEER_CERT_EXPIRED(17),
    PEER_IDENTITY_MISMATCH(18), CLIENT_CERT_REQUIRED(19),
}
enum class StreamPressureState(val nativeValue: Int) { INACTIVE(0), FLOWING(1), PRESSURED(2), STALLED(3), RECOVERING(4) }

data class StreamLaneSecurityConfig(
    val mode: StreamLaneSecurityMode = StreamLaneSecurityMode.DIRECT,
    val credentialGeneration: Long = 0,
    val credentialId: String = "",
    val caCertificateFile: String = "",
    val identityCertificateFile: String = "",
    val privateKeyFile: String = "",
)

data class StreamLaneConfig(
    val flags: Int = StreamLaneFlags.PUBLISHER or StreamLaneFlags.SUBSCRIBER,
    val bindHost: String = "127.0.0.1",
    val bindPort: Int = 0,
    val capacity: Long = 0,
    val maxFrameBytes: Int = 0,
    val maxWindowBytes: Int = 0,
    val ioTimeoutMs: Int = 0,
    val sourceRetryMs: Int = 0,
    val progressFrames: Int = 0,
    val progressIntervalMs: Int = 0,
    val publisherWorkerCount: Int = 0,
    val subscriberWorkerCount: Int = 0,
    val security: StreamLaneSecurityConfig? = null,
    val pressureAfterMs: Int = 0,
    val stalledAfterMs: Int = 0,
    val recoveryAfterMs: Int = 0,
    val pressureObservationMs: Int = 0,
) {
    internal fun requireValid(): StreamLaneConfig {
        val allowedFlags = StreamLaneFlags.PUBLISHER or StreamLaneFlags.SUBSCRIBER
        require(flags != 0 && flags and allowedFlags.inv() == 0) { "stream lane requires valid publisher or subscriber flags" }
        require(bindPort in 0..65535) { "bindPort must be in [0, 65535]" }
        require(capacity in 0..64) { "capacity must be in [0, 64]" }
        require(maxFrameBytes in 0..4 * 1024 * 1024) { "maxFrameBytes must be in [0, 4194304]" }
        require(maxWindowBytes in 0..16 * 1024 * 1024) { "maxWindowBytes must be in [0, 16777216]" }
        require(maxFrameBytes == 0 || maxWindowBytes == 0 || maxWindowBytes >= maxFrameBytes) { "maxWindowBytes must cover one maximum frame" }
        require(ioTimeoutMs >= 0 && sourceRetryMs in 0..1000) { "stream I/O timings are outside their supported range" }
        require(progressFrames >= 0 && progressIntervalMs >= 0) { "stream progress values must be non-negative" }
        require(publisherWorkerCount in 0..4 && subscriberWorkerCount in 0..4) { "stream worker counts must be in [0, 4]" }
        require(listOf(pressureAfterMs, stalledAfterMs, recoveryAfterMs, pressureObservationMs).all { it in 0..60_000 }) { "pressure timings must be in [0, 60000]" }
        return this
    }
}

sealed interface StreamSourceResult {
    data class Frame(val size: Int, val capturedMonoNs: Long = 0, val droppedBefore: Long = 0, val flags: Int = 0) : StreamSourceResult
    data object WouldBlock : StreamSourceResult
    data object End : StreamSourceResult
}

/** Destination is borrowed only for [next] and must not be retained. */
fun interface StreamSource { fun next(destination: ByteBuffer): StreamSourceResult }
enum class StreamConsumerDecision { CONTINUE, STOP }
data class StreamFrameMetadata(val sequence: Long, val capturedMonoNs: Long, val droppedBefore: Long, val flags: Int)
/** Data is borrowed only for [consume] and must be copied before retention. */
fun interface StreamConsumer { fun consume(data: ByteBuffer, metadata: StreamFrameMetadata): StreamConsumerDecision }

data class StreamPublishSpec(
    val sessionId: String,
    val authorizationToken: String,
    val formatId: Long,
    val maxFrameBytes: Int,
    val source: StreamSource,
) {
    override fun toString(): String = "StreamPublishSpec(sessionId=$sessionId, authorizationToken=<redacted>, formatId=$formatId, maxFrameBytes=$maxFrameBytes, source=$source)"
}
data class StreamSubscribeSpec(
    val sessionId: String,
    val authorizationToken: String,
    val remoteHost: String,
    val remotePort: Int,
    val formatId: Long,
    val maxFrameBytes: Int,
    val initialWindowBytes: Int,
    val timeoutMs: Int = 0,
    val consumer: StreamConsumer,
) {
    override fun toString(): String = "StreamSubscribeSpec(sessionId=$sessionId, authorizationToken=<redacted>, remoteHost=$remoteHost, remotePort=$remotePort, formatId=$formatId, maxFrameBytes=$maxFrameBytes, initialWindowBytes=$initialWindowBytes, timeoutMs=$timeoutMs, consumer=$consumer)"
}

data class StreamSessionSnapshot(
    val direction: StreamDirection, val state: StreamState, val result: StreamResult, val formatId: Long,
    val frames: Long, val bytes: Long, val droppedFrames: Long, val lastSequence: Long,
    val negotiatedMaxFrameBytes: Int, val windowBytes: Int, val cancelRequested: Boolean,
    val updateSequence: Long, val submittedMonoNs: Long, val startedMonoNs: Long,
    val updatedMonoNs: Long, val terminalMonoNs: Long, val detail: String,
) {
    val terminal: Boolean get() = terminalMonoNs != 0L
    val completed: Boolean get() = terminal && state == StreamState.ENDED && result == StreamResult.OK
}
data class StreamPressureSnapshot(
    val direction: StreamDirection, val state: StreamPressureState, val reasonBits: Int,
    val availableCreditBytes: Int, val windowCapacityBytes: Int, val updateSequence: Long,
    val transitionCount: Long, val observedMonoNs: Long, val stateStartedMonoNs: Long,
    val pressureStartedMonoNs: Long, val lastProgressMonoNs: Long, val observedDeliveryBps: Long,
    val currentOperationNs: Long, val lastOperationNs: Long, val totalPressuredNs: Long, val maxPressuredNs: Long,
)
data class StreamLaneStats(
    val capacity: Long, val queuedSubscribers: Long, val preparedPublishers: Long,
    val activePublishers: Long, val activeSubscribers: Long, val retainedRecords: Long,
    val submittedSubscribers: Long, val preparedPublisherCount: Long, val endedPublishers: Long,
    val endedSubscribers: Long, val failedPublishers: Long, val failedSubscribers: Long,
    val canceledSessions: Long, val publishedFrames: Long, val publishedBytes: Long,
    val consumedFrames: Long, val consumedBytes: Long, val sourceReportedDrops: Long,
)

class StreamLaneException(operation: String, val status: Int) : RuntimeException("$operation failed: native status $status")

/** JNI retains callbacks only while Core retains their record; close drains every native borrow. */
class StreamLane private constructor(private var nativeId: Long, private val ownerAware: Boolean) : AutoCloseable {
    private val lifecycle = ReentrantLock()
    private var closing = false

    val boundPort: Int get() = nativeCall { NativeBridge.streamBoundPort(it).checked("stream_lane_get_bound_port").value(1).toInt() }

    fun preparePublish(spec: StreamPublishSpec) = nativeCall { id ->
        requirePublish(spec)
        requireOk(NativeBridge.streamPreparePublish(id, longArrayOf(spec.formatId, spec.maxFrameBytes.toLong()), publishBytes(spec), sourceAdapter(spec)), "stream_lane_prepare_publish")
    }

    fun preparePublishGrant(spec: StreamPublishSpec): StreamPublishGrant {
        check(ownerAware) { "preparePublishGrant requires a lane opened with StreamLane.openOwned" }
        return nativeCall { id ->
            requirePublish(spec)
            val r = NativeBridge.streamPreparePublishGrant(id, longArrayOf(spec.formatId, spec.maxFrameBytes.toLong()), publishBytes(spec), sourceAdapter(spec))
                .checked("stream_lane_prepare_publish_grant")
            StreamPublishGrant(LaneOwnerEndpoint(r.text(0), r.text(1), r.value(1).toInt()), r.text(2), r.text(3), r.value(2), r.value(3).toInt())
        }
    }

    fun subscribe(spec: StreamSubscribeSpec) = nativeCall { id ->
        requireSession(spec.sessionId, spec.authorizationToken)
        require(spec.remoteHost.isNotBlank()) { "remoteHost must not be blank" }
        require(spec.remotePort in 1..65535) { "remotePort must be in [1, 65535]" }
        require(spec.maxFrameBytes in 1..4 * 1024 * 1024) { "maxFrameBytes must be in [1, 4194304]" }
        require(spec.initialWindowBytes in spec.maxFrameBytes..16 * 1024 * 1024) { "initialWindowBytes must cover one frame and stay within 16777216" }
        require(spec.timeoutMs >= 0) { "timeoutMs must be non-negative" }
        val values = longArrayOf(spec.remotePort.toLong(), spec.formatId, spec.maxFrameBytes.toLong(), spec.initialWindowBytes.toLong(), spec.timeoutMs.toLong())
        val bytes: Array<ByteArray?> = arrayOf(spec.sessionId.utf8(), spec.authorizationToken.utf8(), spec.remoteHost.utf8())
        requireOk(NativeBridge.streamSubscribe(id, values, bytes, consumerAdapter(spec)), "stream_lane_subscribe")
    }

    fun session(sessionId: String, direction: StreamDirection): StreamSessionSnapshot = nativeCall { id ->
        requireSessionId(sessionId)
        sessionSnapshot(NativeBridge.streamSession(id, sessionId.utf8(), direction.nativeValue).checked("stream_lane_get_session"))
    }
    fun waitSession(sessionId: String, direction: StreamDirection, afterUpdateSequence: Long = 0, timeoutMs: Int = 30_000): StreamSessionSnapshot = nativeCall { id ->
        requireSessionId(sessionId); require(afterUpdateSequence >= 0 && timeoutMs >= 0)
        sessionSnapshot(NativeBridge.streamWaitSession(id, sessionId.utf8(), direction.nativeValue, afterUpdateSequence, timeoutMs).checked("stream_lane_wait_session"))
    }
    fun pressure(sessionId: String, direction: StreamDirection): StreamPressureSnapshot = nativeCall { id ->
        requireSessionId(sessionId)
        pressureSnapshot(NativeBridge.streamPressure(id, sessionId.utf8(), direction.nativeValue).checked("stream_lane_get_pressure"))
    }
    fun waitPressure(sessionId: String, direction: StreamDirection, afterUpdateSequence: Long = 0, timeoutMs: Int = 30_000): StreamPressureSnapshot = nativeCall { id ->
        requireSessionId(sessionId); require(afterUpdateSequence >= 0 && timeoutMs >= 0)
        pressureSnapshot(NativeBridge.streamWaitPressure(id, sessionId.utf8(), direction.nativeValue, afterUpdateSequence, timeoutMs).checked("stream_lane_wait_pressure"))
    }
    fun cancel(sessionId: String, direction: StreamDirection) = nativeCall { id ->
        requireSessionId(sessionId); requireOk(NativeBridge.streamCancel(id, sessionId.utf8(), direction.nativeValue), "stream_lane_cancel_session")
    }
    fun forget(sessionId: String, direction: StreamDirection) = nativeCall { id ->
        requireSessionId(sessionId); requireOk(NativeBridge.streamForget(id, sessionId.utf8(), direction.nativeValue), "stream_lane_forget_session")
    }
    fun stats(): StreamLaneStats = nativeCall { id ->
        val r = NativeBridge.streamStats(id).checked("stream_lane_get_stats")
        StreamLaneStats(r.value(1), r.value(2), r.value(3), r.value(4), r.value(5), r.value(6), r.value(7), r.value(8), r.value(9), r.value(10), r.value(11), r.value(12), r.value(13), r.value(14), r.value(15), r.value(16), r.value(17), r.value(18))
    }

    override fun close() {
        val id = lifecycle.withLock {
            if (nativeId == 0L) return
            check(!closing) { "stream lane close already in progress" }
            closing = true
            nativeId.also { nativeId = 0L }
        }
        val status = NativeBridge.streamClose(id)
        if (status != CoakkaStatus.OK && status != CoakkaStatus.ERR_CLOSED) throw StreamLaneException("stream_lane_stop", status)
    }

    private inline fun <T> nativeCall(block: (Long) -> T): T = block(lifecycle.withLock {
        check(!closing && nativeId != 0L) { "stream lane is closed" }; nativeId
    })

    companion object {
        @JvmStatic fun open(config: StreamLaneConfig = StreamLaneConfig(), runtimeLibPath: String? = null): StreamLane = openInternal(config, null, runtimeLibPath)
        @JvmStatic fun openOwned(config: StreamLaneConfig, owner: LaneOwnerConfig, runtimeLibPath: String? = null): StreamLane {
            owner.requireValid(); require(config.flags and StreamLaneFlags.PUBLISHER != 0) { "owner-aware stream lane must enable PUBLISHER" }
            return openInternal(config, owner, runtimeLibPath)
        }

        private fun openInternal(config: StreamLaneConfig, owner: LaneOwnerConfig?, runtimeLibPath: String?): StreamLane {
            config.requireValid(); NativeLibraryResolver.resolve(runtimeLibPath)
            val security = config.security
            val values = longArrayOf(
                config.flags.toLong(), config.bindPort.toLong(), config.capacity, config.maxFrameBytes.toLong(), config.maxWindowBytes.toLong(),
                config.ioTimeoutMs.toLong(), config.sourceRetryMs.toLong(), config.progressFrames.toLong(), config.progressIntervalMs.toLong(),
                config.publisherWorkerCount.toLong(), config.subscriberWorkerCount.toLong(), (security?.mode?.nativeValue ?: -1).toLong(),
                security?.credentialGeneration ?: 0, config.pressureAfterMs.toLong(), config.stalledAfterMs.toLong(),
                config.recoveryAfterMs.toLong(), config.pressureObservationMs.toLong(),
            )
            val bytes: Array<ByteArray?> = arrayOf(config.bindHost.utf8(), security?.credentialId?.utf8(), security?.caCertificateFile?.utf8(), security?.identityCertificateFile?.utf8(), security?.privateKeyFile?.utf8())
            val ownerBytes: Array<ByteArray?>? = owner?.let { arrayOf(it.ownerInstanceId.utf8(), it.advertisedHost.utf8()) }
            val r = NativeBridge.streamOpen(values, bytes, ownerBytes).checked(if (owner == null) "stream_lane_create" else "stream_lane_create_owned")
            return StreamLane(r.value(1), owner != null)
        }

        private fun sourceAdapter(spec: StreamPublishSpec) = NativeStreamSourceAdapter { destination, metadata ->
            try {
                val buffer = ByteBuffer.wrap(destination).order(ByteOrder.nativeOrder())
                when (val result = spec.source.next(buffer)) {
                    is StreamSourceResult.Frame -> if (result.size !in 1..destination.size || result.capturedMonoNs < 0 || result.droppedBefore < 0) {
                        CoakkaStatus.ERR_INVALID_ARG
                    } else {
                        metadata[0] = result.capturedMonoNs
                        metadata[1] = result.droppedBefore
                        metadata[2] = result.flags.toLong()
                        metadata[3] = result.size.toLong()
                        CoakkaStatus.OK
                    }
                    StreamSourceResult.WouldBlock -> CoakkaStatus.ERR_WOULD_BLOCK
                    StreamSourceResult.End -> CoakkaStatus.ERR_CLOSED
                }
            } catch (_: Throwable) { CoakkaStatus.ERR_IO }
        }

        private fun consumerAdapter(spec: StreamSubscribeSpec) = NativeStreamConsumerAdapter { data, metadata ->
            try {
                if (metadata.size < 4) CoakkaStatus.ERR_INVALID_ARG else {
                    val view = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder()).asReadOnlyBuffer()
                    val frame = StreamFrameMetadata(metadata[0], metadata[1], metadata[2], metadata[3].toInt())
                    if (spec.consumer.consume(view, frame) == StreamConsumerDecision.CONTINUE) CoakkaStatus.OK else CoakkaStatus.ERR_CLOSED
                }
            } catch (_: Throwable) { CoakkaStatus.ERR_IO }
        }

        private fun requirePublish(spec: StreamPublishSpec) {
            requireSession(spec.sessionId, spec.authorizationToken); require(spec.formatId != 0L) { "formatId must be non-zero" }
            require(spec.maxFrameBytes in 1..4 * 1024 * 1024) { "maxFrameBytes must be in [1, 4194304]" }
        }
        private fun requireSession(sessionId: String, token: String) {
            requireSessionId(sessionId); require(token.isNotEmpty() && token.utf8().size <= 128) { "authorizationToken must contain 1..128 UTF-8 bytes" }
        }
        private fun requireSessionId(sessionId: String) = require(sessionId.isNotEmpty() && sessionId.utf8().size <= 64) { "sessionId must contain 1..64 UTF-8 bytes" }
        private fun publishBytes(spec: StreamPublishSpec): Array<ByteArray?> = arrayOf(spec.sessionId.utf8(), spec.authorizationToken.utf8())
        private fun sessionSnapshot(r: NativeRecord) = StreamSessionSnapshot(
            StreamDirection.entries.first { it.nativeValue == r.value(1).toInt() }, StreamState.entries.first { it.nativeValue == r.value(2).toInt() },
            StreamResult.entries.first { it.nativeValue == r.value(3).toInt() }, r.value(4), r.value(5), r.value(6), r.value(7), r.value(8),
            r.value(9).toInt(), r.value(10).toInt(), r.value(11) != 0L, r.value(12), r.value(13), r.value(14), r.value(15), r.value(16), r.text(0),
        )
        private fun pressureSnapshot(r: NativeRecord) = StreamPressureSnapshot(
            StreamDirection.entries.first { it.nativeValue == r.value(1).toInt() }, StreamPressureState.entries.first { it.nativeValue == r.value(2).toInt() },
            r.value(3).toInt(), r.value(4).toInt(), r.value(5).toInt(), r.value(6), r.value(7), r.value(8), r.value(9), r.value(10), r.value(11), r.value(12), r.value(13), r.value(14), r.value(15), r.value(16),
        )
        private fun NativeRecord.checked(operation: String): NativeRecord { requireOk(value(0).toInt(), operation); return this }
        private fun requireOk(status: Int, operation: String) { if (status != CoakkaStatus.OK) throw StreamLaneException(operation, status) }
        private fun String.utf8(): ByteArray = toByteArray(Charsets.UTF_8)
    }
}
