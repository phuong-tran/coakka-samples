package coakka.v2.android

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object StreamLaneFlags {
    const val PUBLISHER = 1
    const val SUBSCRIBER = 1 shl 1
}

object StreamFrameFlags {
    const val KEYFRAME = 1
    const val DISCONTINUITY = 1 shl 1
    const val END_OF_SEGMENT = 1 shl 2
}

object StreamPressureReasons {
    const val CREDIT_WAIT = 1
    const val TRANSPORT_WRITE = 1 shl 1
    const val CONSUMER_BUSY = 1 shl 2
    const val TRANSPORT_READ = 1 shl 3
}

enum class StreamLaneSecurityMode(internal val nativeValue: Int) {
    DIRECT(0),
    TLS(1),
    MUTUAL_TLS(2),
}

enum class StreamDirection(internal val nativeValue: Int) {
    PUBLISH(1),
    SUBSCRIBE(2);

    companion object {
        internal fun fromNative(value: Int) =
            entries.firstOrNull { it.nativeValue == value }
                ?: error("unknown stream direction=$value")
    }
}

enum class StreamState(internal val nativeValue: Int) {
    PREPARED(1),
    QUEUED(2),
    CONNECTING(3),
    ACTIVE(4),
    STOPPING(5),
    ENDED(6),
    REJECTED(7),
    FAILED(8),
    CANCELED(9);

    val terminal: Boolean get() = nativeValue >= ENDED.nativeValue

    companion object {
        internal fun fromNative(value: Int) =
            entries.firstOrNull { it.nativeValue == value }
                ?: error("unknown stream state=$value")
    }
}

enum class StreamResult(internal val nativeValue: Int) {
    NONE(0),
    OK(1),
    NOT_PREPARED(2),
    TOKEN_MISMATCH(3),
    FORMAT_MISMATCH(4),
    FRAME_LIMIT(5),
    NETWORK_IO(6),
    TIMEOUT(7),
    QUEUE_FULL(8),
    PROTOCOL_ERROR(9),
    SOURCE_ERROR(10),
    CONSUMER_ERROR(11),
    INTERNAL_ERROR(12),
    CANCELED_BY_HOST(13),
    TLS_CONFIG_INVALID(14),
    TLS_HANDSHAKE_FAILED(15),
    PEER_CERT_UNTRUSTED(16),
    PEER_CERT_EXPIRED(17),
    PEER_IDENTITY_MISMATCH(18),
    CLIENT_CERT_REQUIRED(19);

    companion object {
        internal fun fromNative(value: Int) =
            entries.firstOrNull { it.nativeValue == value }
                ?: error("unknown stream result=$value")
    }
}

enum class StreamPressureState(internal val nativeValue: Int) {
    INACTIVE(0),
    FLOWING(1),
    PRESSURED(2),
    STALLED(3),
    RECOVERING(4);

    companion object {
        internal fun fromNative(value: Int) =
            entries.firstOrNull { it.nativeValue == value }
                ?: error("unknown stream pressure state=$value")
    }
}

data class StreamLaneSecurityConfig(
    val mode: StreamLaneSecurityMode = StreamLaneSecurityMode.DIRECT,
    val credentialGeneration: Long = 0,
    val credentialId: String = "",
    val caCertificateFile: String = "",
    val identityCertificateFile: String = "",
    val privateKeyFile: String = "",
) {
    internal fun requireValid(): StreamLaneSecurityConfig {
        require(credentialGeneration >= 0) { "credentialGeneration must be non-negative" }
        return this
    }
}

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
        require(flags != 0 && flags and allowedFlags.inv() == 0) {
            "stream lane requires valid publisher or subscriber flags"
        }
        require(bindHost.isNotBlank()) { "bindHost must not be blank" }
        require(bindPort in 0..65535) { "bindPort must be in [0, 65535]" }
        require(capacity in 0..64) { "capacity must be in [0, 64]" }
        require(maxFrameBytes in 0..4 * 1024 * 1024) { "maxFrameBytes must be in [0, 4194304]" }
        require(maxWindowBytes in 0..16 * 1024 * 1024) {
            "maxWindowBytes must be in [0, 16777216]"
        }
        require(maxFrameBytes == 0 || maxWindowBytes == 0 || maxWindowBytes >= maxFrameBytes) {
            "maxWindowBytes must cover one maximum frame"
        }
        require(ioTimeoutMs >= 0 && sourceRetryMs in 0..1000) {
            "stream I/O timings are outside their supported range"
        }
        require(progressFrames >= 0 && progressIntervalMs >= 0) {
            "stream progress values must be non-negative"
        }
        require(publisherWorkerCount in 0..4 && subscriberWorkerCount in 0..4) {
            "stream worker counts must be in [0, 4]"
        }
        require(listOf(pressureAfterMs, stalledAfterMs, recoveryAfterMs, pressureObservationMs).all { it in 0..60_000 }) {
            "pressure timings must be in [0, 60000]"
        }
        security?.requireValid()
        return this
    }
}

sealed interface AndroidStreamSourceResult {
    data class Frame(
        val size: Int,
        val capturedMonoNs: Long = 0,
        val droppedBefore: Long = 0,
        val flags: Int = 0,
    ) : AndroidStreamSourceResult

    data object WouldBlock : AndroidStreamSourceResult
    data object End : AndroidStreamSourceResult
}

/**
 * Called on a bounded native publisher worker; the destination is borrowed for this call.
 * Closing the same StreamLane from this callback fails fast; close it from another thread.
 */
fun interface AndroidStreamSource {
    fun next(destination: ByteBuffer): AndroidStreamSourceResult
}

enum class AndroidStreamConsumerDecision {
    CONTINUE,
    STOP,
}

data class StreamFrameMetadata(
    val sequence: Long,
    val capturedMonoNs: Long,
    val droppedBefore: Long,
    val flags: Int,
)

/**
 * Called on a bounded native subscriber worker; copy data before returning if it must survive.
 * Closing the same StreamLane from this callback fails fast; close it from another thread.
 */
fun interface AndroidStreamConsumer {
    fun consume(data: ByteBuffer, metadata: StreamFrameMetadata): AndroidStreamConsumerDecision
}

class StreamPublishSpec(
    val sessionId: String,
    val authorizationToken: String,
    val formatId: Long,
    val maxFrameBytes: Int,
    val source: AndroidStreamSource,
) {
    internal fun requireValid(): StreamPublishSpec {
        requireSessionIdentity(sessionId, authorizationToken)
        require(formatId > 0L) { "formatId must be positive" }
        require(maxFrameBytes in 1..4 * 1024 * 1024) {
            "maxFrameBytes must be in [1, 4194304]"
        }
        return this
    }

    override fun toString(): String =
        "StreamPublishSpec(sessionId=$sessionId, authorizationToken=<redacted>, " +
            "formatId=$formatId, maxFrameBytes=$maxFrameBytes, source=$source)"
}

class StreamSubscribeSpec(
    val sessionId: String,
    val authorizationToken: String,
    val remoteHost: String,
    val remotePort: Int,
    val formatId: Long,
    val maxFrameBytes: Int,
    val initialWindowBytes: Int,
    val timeoutMs: Int = 0,
    val consumer: AndroidStreamConsumer,
) {
    internal fun requireValid(): StreamSubscribeSpec {
        requireSessionIdentity(sessionId, authorizationToken)
        require(remoteHost.isNotBlank()) { "remoteHost must not be blank" }
        require(remotePort in 1..65535) { "remotePort must be in [1, 65535]" }
        require(formatId > 0L) { "formatId must be positive" }
        require(maxFrameBytes in 1..4 * 1024 * 1024) {
            "maxFrameBytes must be in [1, 4194304]"
        }
        require(initialWindowBytes in maxFrameBytes..16 * 1024 * 1024) {
            "initialWindowBytes must cover one frame and stay within 16777216"
        }
        require(timeoutMs >= 0) { "timeoutMs must be non-negative" }
        return this
    }

    override fun toString(): String =
        "StreamSubscribeSpec(sessionId=$sessionId, authorizationToken=<redacted>, " +
            "remoteHost=$remoteHost, remotePort=$remotePort, formatId=$formatId, " +
            "maxFrameBytes=$maxFrameBytes, initialWindowBytes=$initialWindowBytes, " +
            "timeoutMs=$timeoutMs, consumer=$consumer)"
}

data class StreamSessionSnapshot(
    val direction: StreamDirection,
    val state: StreamState,
    val result: StreamResult,
    val formatId: Long,
    val frames: Long,
    val bytes: Long,
    val droppedFrames: Long,
    val lastSequence: Long,
    val negotiatedMaxFrameBytes: Int,
    val windowBytes: Int,
    val cancelRequested: Boolean,
    val updateSequence: Long,
    val submittedMonoNs: Long,
    val startedMonoNs: Long,
    val updatedMonoNs: Long,
    val terminalMonoNs: Long,
    val detail: String,
) {
    val terminal: Boolean get() = state.terminal
    val completed: Boolean get() = state == StreamState.ENDED && result == StreamResult.OK
}

data class StreamPressureSnapshot(
    val direction: StreamDirection,
    val state: StreamPressureState,
    val reasonBits: Int,
    val availableCreditBytes: Int,
    val windowCapacityBytes: Int,
    val updateSequence: Long,
    val transitionCount: Long,
    val observedMonoNs: Long,
    val stateStartedMonoNs: Long,
    val pressureStartedMonoNs: Long,
    val lastProgressMonoNs: Long,
    val observedDeliveryBps: Long,
    val currentOperationNs: Long,
    val lastOperationNs: Long,
    val totalPressuredNs: Long,
    val maxPressuredNs: Long,
)

data class StreamLaneStats(
    val capacity: Long,
    val queuedSubscribers: Long,
    val preparedPublishers: Long,
    val activePublishers: Long,
    val activeSubscribers: Long,
    val retainedRecords: Long,
    val submittedSubscribers: Long,
    val preparedPublisherCount: Long,
    val endedPublishers: Long,
    val endedSubscribers: Long,
    val failedPublishers: Long,
    val failedSubscribers: Long,
    val canceledSessions: Long,
    val publishedFrames: Long,
    val publishedBytes: Long,
    val consumedFrames: Long,
    val consumedBytes: Long,
    val sourceReportedDrops: Long,
)

class StreamLaneException(operation: String, val status: Int) :
    IllegalStateException("$operation failed with native status=$status")

internal object StreamCallbackGuard {
    private const val MAX_NESTED_CALLBACKS = 4

    private class State {
        val laneHandles = LongArray(MAX_NESTED_CALLBACKS)
        var depth = 0
    }

    private val current = ThreadLocal<State>()

    fun enter(laneHandle: Long) {
        require(laneHandle != 0L) { "stream callback requires a live lane" }
        val state = current.get() ?: State().also(current::set)
        check(state.depth < state.laneHandles.size) { "stream callback nesting limit exceeded" }
        state.laneHandles[state.depth] = laneHandle
        state.depth += 1
    }

    fun exit(laneHandle: Long) {
        val state = checkNotNull(current.get()) { "stream callback guard is not active" }
        check(state.depth > 0 && state.laneHandles[state.depth - 1] == laneHandle) {
            "stream callback guard ownership mismatch"
        }
        state.depth -= 1
        state.laneHandles[state.depth] = 0L
    }

    fun requireCloseAllowed(laneHandle: Long) {
        val state = current.get() ?: return
        check((0 until state.depth).none { state.laneHandles[it] == laneHandle }) {
            "StreamLane.close cannot run from this lane's source or consumer callback"
        }
    }
}

/** Independent bounded stream lane with Simple and replica-pinned owner-aware creation. */
class StreamLane private constructor(
    private var nativeHandle: Long,
    private val ownerAware: Boolean,
) : Closeable {
    private val lifecycle = ReentrantLock()
    private val drained = lifecycle.newCondition()
    private var closing = false
    private var activeCalls = 0

    val boundPort: Int get() = nativeCall { handle ->
        val output = IntArray(1)
        requireStreamOk(NativeRuntimeBridge.nativeStreamLaneBoundPort(handle, output), "stream_lane_get_bound_port")
        output[0]
    }

    fun preparePublish(spec: StreamPublishSpec) = nativeCall { handle ->
        spec.requireValid()
        requireStreamOk(
            NativeRuntimeBridge.nativeStreamLanePreparePublish(
                handle,
                spec.sessionId,
                spec.authorizationToken,
                spec.formatId,
                spec.maxFrameBytes,
                spec.source,
                null,
                null,
            ),
            "stream_lane_prepare_publish",
        )
    }

    /** Returns a copied single-admission grant pinned to this exact publisher owner. */
    fun preparePublishGrant(spec: StreamPublishSpec): StreamPublishGrant {
        check(ownerAware) { "preparePublishGrant requires StreamLane.openOwned" }
        return nativeCall { handle ->
            spec.requireValid()
            val numeric = LongArray(3)
            val text = arrayOfNulls<String>(4)
            requireStreamOk(
                NativeRuntimeBridge.nativeStreamLanePreparePublish(
                    handle,
                    spec.sessionId,
                    spec.authorizationToken,
                    spec.formatId,
                    spec.maxFrameBytes,
                    spec.source,
                    numeric,
                    text,
                ),
                "stream_lane_prepare_publish_grant",
            )
            StreamPublishGrant(
                owner = LaneOwnerEndpoint(checkNotNull(text[0]), checkNotNull(text[1]), numeric[0].toInt()),
                sessionId = checkNotNull(text[2]),
                authorizationToken = checkNotNull(text[3]),
                formatId = numeric[1],
                maxFrameBytes = numeric[2].toInt(),
            )
        }
    }

    fun subscribe(spec: StreamSubscribeSpec) = nativeCall { handle ->
        spec.requireValid()
        requireStreamOk(
            NativeRuntimeBridge.nativeStreamLaneSubscribe(
                handle,
                spec.sessionId,
                spec.authorizationToken,
                spec.remoteHost,
                spec.remotePort,
                spec.formatId,
                spec.maxFrameBytes,
                spec.initialWindowBytes,
                spec.timeoutMs,
                spec.consumer,
            ),
            "stream_lane_subscribe",
        )
    }

    fun session(sessionId: String, direction: StreamDirection): StreamSessionSnapshot =
        readSession(sessionId, direction, 0, 0, false)

    fun waitSession(
        sessionId: String,
        direction: StreamDirection,
        afterUpdateSequence: Long = 0,
        timeoutMs: Int = 30_000,
    ): StreamSessionSnapshot = readSession(
        sessionId,
        direction,
        afterUpdateSequence,
        timeoutMs,
        true,
    )

    private fun readSession(
        sessionId: String,
        direction: StreamDirection,
        afterUpdateSequence: Long,
        timeoutMs: Int,
        wait: Boolean,
    ): StreamSessionSnapshot = nativeCall { handle ->
        requireUtf8Bytes(sessionId, 64, "sessionId")
        require(afterUpdateSequence >= 0 && timeoutMs >= 0) { "wait values must be non-negative" }
        val numeric = LongArray(16)
        val text = arrayOfNulls<String>(1)
        requireStreamOk(
            NativeRuntimeBridge.nativeStreamLaneSession(
                handle,
                sessionId,
                direction.nativeValue,
                afterUpdateSequence,
                timeoutMs,
                wait,
                numeric,
                text,
            ),
            if (wait) "stream_lane_wait_session" else "stream_lane_get_session",
        )
        StreamSessionSnapshot(
            direction = StreamDirection.fromNative(numeric[0].toInt()),
            state = StreamState.fromNative(numeric[1].toInt()),
            result = StreamResult.fromNative(numeric[2].toInt()),
            formatId = numeric[3],
            frames = numeric[4],
            bytes = numeric[5],
            droppedFrames = numeric[6],
            lastSequence = numeric[7],
            negotiatedMaxFrameBytes = numeric[8].toInt(),
            windowBytes = numeric[9].toInt(),
            cancelRequested = numeric[10] != 0L,
            updateSequence = numeric[11],
            submittedMonoNs = numeric[12],
            startedMonoNs = numeric[13],
            updatedMonoNs = numeric[14],
            terminalMonoNs = numeric[15],
            detail = checkNotNull(text[0]),
        )
    }

    fun pressure(sessionId: String, direction: StreamDirection): StreamPressureSnapshot =
        readPressure(sessionId, direction, 0, 0, false)

    fun waitPressure(
        sessionId: String,
        direction: StreamDirection,
        afterUpdateSequence: Long = 0,
        timeoutMs: Int = 30_000,
    ): StreamPressureSnapshot = readPressure(
        sessionId,
        direction,
        afterUpdateSequence,
        timeoutMs,
        true,
    )

    private fun readPressure(
        sessionId: String,
        direction: StreamDirection,
        afterUpdateSequence: Long,
        timeoutMs: Int,
        wait: Boolean,
    ): StreamPressureSnapshot = nativeCall { handle ->
        requireUtf8Bytes(sessionId, 64, "sessionId")
        require(afterUpdateSequence >= 0 && timeoutMs >= 0) { "wait values must be non-negative" }
        val values = LongArray(16)
        requireStreamOk(
            NativeRuntimeBridge.nativeStreamLanePressure(
                handle,
                sessionId,
                direction.nativeValue,
                afterUpdateSequence,
                timeoutMs,
                wait,
                values,
            ),
            if (wait) "stream_lane_wait_pressure" else "stream_lane_get_pressure",
        )
        StreamPressureSnapshot(
            direction = StreamDirection.fromNative(values[0].toInt()),
            state = StreamPressureState.fromNative(values[1].toInt()),
            reasonBits = values[2].toInt(),
            availableCreditBytes = values[3].toInt(),
            windowCapacityBytes = values[4].toInt(),
            updateSequence = values[5],
            transitionCount = values[6],
            observedMonoNs = values[7],
            stateStartedMonoNs = values[8],
            pressureStartedMonoNs = values[9],
            lastProgressMonoNs = values[10],
            observedDeliveryBps = values[11],
            currentOperationNs = values[12],
            lastOperationNs = values[13],
            totalPressuredNs = values[14],
            maxPressuredNs = values[15],
        )
    }

    fun cancel(sessionId: String, direction: StreamDirection) = nativeCall { handle ->
        requireUtf8Bytes(sessionId, 64, "sessionId")
        requireStreamOk(
            NativeRuntimeBridge.nativeStreamLaneCancel(handle, sessionId, direction.nativeValue),
            "stream_lane_cancel_session",
        )
    }

    fun forget(sessionId: String, direction: StreamDirection) = nativeCall { handle ->
        requireUtf8Bytes(sessionId, 64, "sessionId")
        requireStreamOk(
            NativeRuntimeBridge.nativeStreamLaneForget(handle, sessionId, direction.nativeValue),
            "stream_lane_forget_session",
        )
    }

    fun stats(): StreamLaneStats = nativeCall { handle ->
        val values = LongArray(18)
        requireStreamOk(NativeRuntimeBridge.nativeStreamLaneStats(handle, values), "stream_lane_get_stats")
        StreamLaneStats(
            values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7], values[8],
            values[9], values[10], values[11], values[12], values[13], values[14], values[15], values[16], values[17],
        )
    }

    override fun close() {
        val handle = lifecycle.withLock {
            StreamCallbackGuard.requireCloseAllowed(nativeHandle)
            if (nativeHandle == 0L) return
            if (closing) {
                while (nativeHandle != 0L) drained.awaitUninterruptibly()
                return
            }
            closing = true
            nativeHandle
        }
        val stopStatus = NativeRuntimeBridge.nativeStreamLaneStop(handle)
        lifecycle.withLock {
            while (activeCalls != 0) drained.awaitUninterruptibly()
            NativeRuntimeBridge.nativeStreamLaneDestroy(handle)
            nativeHandle = 0L
            drained.signalAll()
        }
        if (stopStatus != NativeStatus.OK && stopStatus != NativeStatus.CLOSED) {
            throw StreamLaneException("stream_lane_stop", stopStatus)
        }
    }

    private inline fun <T> nativeCall(block: (Long) -> T): T {
        val handle = lifecycle.withLock {
            check(!closing && nativeHandle != 0L) { "stream lane is closed" }
            activeCalls += 1
            nativeHandle
        }
        try {
            return block(handle)
        } finally {
            lifecycle.withLock {
                activeCalls -= 1
                if (activeCalls == 0) drained.signalAll()
            }
        }
    }

    companion object {
        @JvmStatic
        fun open(config: StreamLaneConfig = StreamLaneConfig()): StreamLane =
            openInternal(config.requireValid(), null)

        /** Opens an owner-aware publisher that can issue replica-pinned grants. */
        @JvmStatic
        fun openOwned(config: StreamLaneConfig, owner: LaneOwnerConfig): StreamLane {
            config.requireValid()
            owner.requireValid()
            require(config.flags and StreamLaneFlags.PUBLISHER != 0) {
                "owner-aware stream lane must enable PUBLISHER"
            }
            return openInternal(config, owner)
        }

        private fun openInternal(config: StreamLaneConfig, owner: LaneOwnerConfig?): StreamLane {
            val security = config.security
            val numeric = longArrayOf(
                config.flags.toLong(),
                config.bindPort.toLong(),
                config.capacity,
                config.maxFrameBytes.toLong(),
                config.maxWindowBytes.toLong(),
                config.ioTimeoutMs.toLong(),
                config.sourceRetryMs.toLong(),
                config.progressFrames.toLong(),
                config.progressIntervalMs.toLong(),
                config.publisherWorkerCount.toLong(),
                config.subscriberWorkerCount.toLong(),
                security?.mode?.nativeValue?.toLong() ?: -1L,
                security?.credentialGeneration ?: 0L,
                config.pressureAfterMs.toLong(),
                config.stalledAfterMs.toLong(),
                config.recoveryAfterMs.toLong(),
                config.pressureObservationMs.toLong(),
            )
            val text = arrayOf(
                config.bindHost,
                security?.credentialId?.ifEmpty { null },
                security?.caCertificateFile?.ifEmpty { null },
                security?.identityCertificateFile?.ifEmpty { null },
                security?.privateKeyFile?.ifEmpty { null },
                owner?.ownerInstanceId,
                owner?.advertisedHost,
            )
            val status = IntArray(1)
            val handle = NativeRuntimeBridge.nativeStreamLaneCreate(
                numeric,
                text,
                owner != null,
                status,
            )
            if (handle == 0L || status[0] != NativeStatus.OK) {
                throw StreamLaneException("stream_lane_open", status[0])
            }
            return StreamLane(handle, owner != null)
        }

        private fun requireStreamOk(status: Int, operation: String) {
            if (status != NativeStatus.OK) throw StreamLaneException(operation, status)
        }
    }
}

/** JNI-only callback mapper. Native worker threads never retain borrowed buffers past these calls. */
internal object NativeStreamCallbacks {
    @JvmStatic
    fun sourceNext(
        source: AndroidStreamSource,
        destination: ByteBuffer,
        outMetadata: LongArray,
        laneHandle: Long,
    ): Int {
        StreamCallbackGuard.enter(laneHandle)
        return try {
            try {
                require(outMetadata.size == 4)
                destination.order(ByteOrder.nativeOrder()).clear()
                when (val result = source.next(destination)) {
                    is AndroidStreamSourceResult.Frame -> {
                        require(result.size in 1..destination.capacity())
                        require(result.capturedMonoNs >= 0 && result.droppedBefore >= 0)
                        outMetadata[0] = result.capturedMonoNs
                        outMetadata[1] = result.droppedBefore
                        outMetadata[2] = result.flags.toLong()
                        outMetadata[3] = result.size.toLong()
                        NativeStatus.OK
                    }
                    AndroidStreamSourceResult.WouldBlock -> NativeStatus.WOULD_BLOCK
                    AndroidStreamSourceResult.End -> NativeStatus.CLOSED
                }
            } catch (_: Throwable) {
                NativeStatus.IO
            }
        } finally {
            StreamCallbackGuard.exit(laneHandle)
        }
    }

    @JvmStatic
    fun consume(
        consumer: AndroidStreamConsumer,
        data: ByteBuffer,
        metadata: LongArray,
        laneHandle: Long,
    ): Int {
        StreamCallbackGuard.enter(laneHandle)
        return try {
            try {
                require(metadata.size == 4)
                val frame = StreamFrameMetadata(
                    sequence = metadata[0],
                    capturedMonoNs = metadata[1],
                    droppedBefore = metadata[2],
                    flags = metadata[3].toInt(),
                )
                when (consumer.consume(data.order(ByteOrder.nativeOrder()).asReadOnlyBuffer(), frame)) {
                    AndroidStreamConsumerDecision.CONTINUE -> NativeStatus.OK
                    AndroidStreamConsumerDecision.STOP -> NativeStatus.CLOSED
                }
            } catch (_: Throwable) {
                NativeStatus.IO
            }
        } finally {
            StreamCallbackGuard.exit(laneHandle)
        }
    }
}
