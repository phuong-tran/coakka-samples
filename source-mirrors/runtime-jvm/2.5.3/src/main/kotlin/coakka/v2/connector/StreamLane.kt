package coakka.v2.connector

import com.sun.jna.Callback
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.ptr.ShortByReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Direction capabilities enabled when opening a [StreamLane]. */
object StreamLaneFlags {
    const val PUBLISHER = 1
    const val SUBSCRIBER = 1 shl 1
}

/** Application-provided frame annotations transported without media interpretation. */
object StreamFrameFlags {
    const val KEYFRAME = 1
    const val DISCONTINUITY = 1 shl 1
    const val END_OF_SEGMENT = 1 shl 2
}

/** Reasons why transport progress is constrained; several bits may be set. */
object StreamPressureReasons {
    const val CREDIT_WAIT = 1
    const val TRANSPORT_WRITE = 1 shl 1
    const val CONSUMER_BUSY = 1 shl 2
    const val TRANSPORT_READ = 1 shl 3
}

/** Transport protection for stream bytes; authorization tokens remain required in every mode. */
enum class StreamLaneSecurityMode(val nativeValue: Int) { DIRECT(0), TLS(1), MUTUAL_TLS(2) }

/** Side of a retained stream session used by observation and lifecycle operations. */
enum class StreamDirection(val nativeValue: Int) { PUBLISH(1), SUBSCRIBE(2) }

/** Observable lifecycle state for one side of a stream session. */
enum class StreamState(val nativeValue: Int) {
    PREPARED(1), QUEUED(2), CONNECTING(3), ACTIVE(4), STOPPING(5), ENDED(6),
    REJECTED(7), FAILED(8), CANCELED(9);

    val terminal: Boolean get() = nativeValue >= ENDED.nativeValue
}

/** Stable terminal outcome reported independently by publisher and subscriber. */
enum class StreamResult(val nativeValue: Int) {
    NONE(0), OK(1), NOT_PREPARED(2), TOKEN_MISMATCH(3), FORMAT_MISMATCH(4), FRAME_LIMIT(5),
    NETWORK_IO(6), TIMEOUT(7), QUEUE_FULL(8), PROTOCOL_ERROR(9), SOURCE_ERROR(10),
    CONSUMER_ERROR(11), INTERNAL_ERROR(12), CANCELED_BY_HOST(13), TLS_CONFIG_INVALID(14),
    TLS_HANDSHAKE_FAILED(15), PEER_CERT_UNTRUSTED(16), PEER_CERT_EXPIRED(17),
    PEER_IDENTITY_MISMATCH(18), CLIENT_CERT_REQUIRED(19),
}

/** Coalesced transport-pressure state. It is a signal to the app-host, not media policy. */
enum class StreamPressureState(val nativeValue: Int) {
    INACTIVE(0), FLOWING(1), PRESSURED(2), STALLED(3), RECOVERING(4),
}

/**
 * TLS material read when a lane starts.
 *
 * @property mode direct, server-authenticated TLS, or mutual TLS.
 * @property credentialGeneration app-host generation used for diagnostics.
 * @property credentialId non-secret credential identifier; never place key material here.
 * @property caCertificateFile trusted CA bundle path.
 * @property identityCertificateFile local certificate-chain path.
 * @property privateKeyFile local private-key path; callers must not log this value.
 */
data class StreamLaneSecurityConfig(
    val mode: StreamLaneSecurityMode = StreamLaneSecurityMode.DIRECT,
    val credentialGeneration: Long = 0,
    val credentialId: String = "",
    val caCertificateFile: String = "",
    val identityCertificateFile: String = "",
    val privateKeyFile: String = "",
)

/**
 * Bounded stream-lane configuration. Zero tuning values select native defaults.
 *
 * @property flags publisher/subscriber capabilities enabled on this lane.
 * @property bindHost publisher listener address; ignored by subscriber-only lanes.
 * @property bindPort publisher listener port, or zero for an ephemeral port.
 * @property capacity maximum prepared/queued session capacity, or zero for the native default.
 * @property maxFrameBytes lane-wide frame ceiling, or zero for the native default.
 * @property maxWindowBytes lane-wide flow-control window ceiling, or zero for the native default.
 * @property ioTimeoutMs bounded transport I/O timeout, or zero for the native default.
 * @property sourceRetryMs delay after a publisher source reports [StreamSourceResult.WouldBlock].
 * @property progressFrames frames between retained progress snapshots, or zero for the native default.
 * @property progressIntervalMs maximum time between retained progress snapshots.
 * @property publisherWorkerCount bounded publisher workers, or zero for the native default.
 * @property subscriberWorkerCount bounded subscriber workers, or zero for the native default.
 * @property security optional TLS configuration copied while the lane opens.
 * @property pressureAfterMs operation duration before pressure is reported.
 * @property stalledAfterMs duration without progress before stalled is reported.
 * @property recoveryAfterMs stable progress duration before flowing is restored.
 * @property pressureObservationMs minimum coalescing interval for pressure observations.
 */
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

/** Result of one bounded publisher callback. */
sealed interface StreamSourceResult {
    /**
     * Publishes [size] bytes already written into the callback destination.
     *
     * @property size number of bytes written; it must be positive and no larger than buffer capacity.
     * @property capturedMonoNs capture time from a monotonic clock, or zero for native observation time.
     * @property droppedBefore source-side frames dropped before this frame.
     * @property flags transport-neutral [StreamFrameFlags] annotations.
     */
    data class Frame(
        val size: Int,
        val capturedMonoNs: Long = 0,
        val droppedBefore: Long = 0,
        val flags: Int = 0,
    ) : StreamSourceResult

    /** No frame is ready; the native lane retries after its bounded source-retry interval. */
    data object WouldBlock : StreamSourceResult

    /** The source ended normally; the publisher reports `ENDED + OK`. */
    data object End : StreamSourceResult
}

/**
 * Supplies one frame into runtime-owned storage.
 *
 * The `destination` view is borrowed and valid only during [next]. The callback
 * must return promptly so lane cancellation and shutdown remain observable.
 */
fun interface StreamSource {
    fun next(destination: ByteBuffer): StreamSourceResult
}

/** Subscriber action after one borrowed frame has been consumed. */
enum class StreamConsumerDecision { CONTINUE, STOP }

/**
 * Immutable metadata for one received frame.
 *
 * @property sequence runtime-assigned contiguous wire sequence.
 * @property capturedMonoNs publisher monotonic capture timestamp; it is not wall-clock time.
 * @property droppedBefore source-reported drops before this frame.
 * @property flags transport-neutral [StreamFrameFlags] annotations.
 */
data class StreamFrameMetadata(
    val sequence: Long,
    val capturedMonoNs: Long,
    val droppedBefore: Long,
    val flags: Int,
)

/**
 * Consumes one borrowed frame.
 *
 * The read-only `data` view is valid only during [consume]. Copy it if the
 * application must retain bytes. Return [StreamConsumerDecision.STOP] for a
 * clean subscriber-requested stop. The callback must remain bounded.
 */
fun interface StreamConsumer {
    fun consume(data: ByteBuffer, metadata: StreamFrameMetadata): StreamConsumerDecision
}

/**
 * Service B publisher authorization and source.
 *
 * @property sessionId application correlation ID, up to 64 UTF-8 bytes.
 * @property authorizationToken opaque single-admission grant, up to 128 UTF-8 bytes.
 * @property formatId opaque app-host format contract; CoAkka does not interpret it.
 * @property maxFrameBytes maximum frame produced by [source].
 * @property source bounded callback that writes frames into native-owned storage.
 */
data class StreamPublishSpec(
    val sessionId: String,
    val authorizationToken: String,
    val formatId: Long,
    val maxFrameBytes: Int,
    val source: StreamSource,
) {
    override fun toString(): String =
        "StreamPublishSpec(sessionId=$sessionId, authorizationToken=<redacted>, formatId=$formatId, " +
            "maxFrameBytes=$maxFrameBytes, source=$source)"
}

/**
 * Service A subscription request and consumer.
 *
 * @property sessionId correlation ID matching the prepared publisher.
 * @property authorizationToken opaque grant matching the prepared publisher.
 * @property remoteHost publisher endpoint returned through the trusted control plane.
 * @property remotePort publisher port returned through the trusted control plane.
 * @property formatId opaque format contract expected by the app-host.
 * @property maxFrameBytes maximum accepted frame size.
 * @property initialWindowBytes bounded receive credit; it must cover at least one maximum frame.
 * @property timeoutMs connect and transport timeout, or zero for the lane default.
 * @property consumer bounded callback invoked with one borrowed frame at a time.
 */
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
    override fun toString(): String =
        "StreamSubscribeSpec(sessionId=$sessionId, authorizationToken=<redacted>, remoteHost=$remoteHost, " +
            "remotePort=$remotePort, formatId=$formatId, maxFrameBytes=$maxFrameBytes, " +
            "initialWindowBytes=$initialWindowBytes, timeoutMs=$timeoutMs, consumer=$consumer)"
}

/**
 * Copied stream-session progress. Byte/frame counters are cumulative for this session side.
 * `*MonoNs` fields use a process-local monotonic clock and [updateSequence] advances on changes.
 */
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

/**
 * Copied transport-pressure signal for app-host policy decisions.
 * Durations and timestamps are nanoseconds; [observedDeliveryBps] is bytes per second.
 * [reasonBits] is a combination of [StreamPressureReasons] values.
 */
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

/**
 * Bounded queue, active-session, terminal, frame, byte, and source-drop counters.
 * Counters are copied at observation time and cumulative since lane start.
 */
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

/** Native status failure for a named stream-lane operation. */
class StreamLaneException(operation: String, val status: Int) : RuntimeException("$operation failed: native status $status")

/**
 * Independent streaming lane backed by the CoAkka core-runtime.
 *
 * A publisher prepares one authorized [StreamSource], starts its lane, and
 * returns the session grant plus [boundPort] through an authenticated control
 * API. A subscriber opens its lane and submits the matching [StreamConsumer].
 * CoAkka transports bounded ordered bytes and pressure signals; the app-host
 * owns format, bitrate, resolution, frame-drop, and activation policy.
 *
 * [close] requests stop, waits for native calls and callbacks to drain, then
 * destroys the lane. This resource is independent from `RuntimeHost`.
 */
class StreamLane private constructor(
    private val lib: CoakkaV2Library,
    private var handle: Pointer?,
    private val ownerAware: Boolean,
) : AutoCloseable {
    private data class SessionKey(val id: String, val direction: StreamDirection)

    private val lifecycle = ReentrantLock()
    private val drained = lifecycle.newCondition()
    private val callbacks = mutableMapOf<SessionKey, Callback>()
    private var closing = false
    private var activeCalls = 0

    /** Publisher port selected at start; valid only for publisher-capable lanes. */
    val boundPort: Int get() = nativeCall { lane ->
        ShortByReference().let { out ->
            requireOk(lib.coakka_v2_stream_lane_get_bound_port(lane, out), "stream_lane_get_bound_port")
            out.value.toInt() and 0xffff
        }
    }

    /**
     * Prepares one publisher callback before the subscriber connects.
     *
     * @param spec session identity, authorization, format contract, frame bound, and source callback.
     */
    fun preparePublish(spec: StreamPublishSpec) {
        requirePublish(spec)
        val callback = publisherCallback(spec)
        nativeCall { lane ->
            val native = nativePublishSpec(spec, callback)
            requireOk(lib.coakka_v2_stream_lane_prepare_publish(lane, native), "stream_lane_prepare_publish")
            lifecycle.withLock { callbacks[SessionKey(spec.sessionId, StreamDirection.PUBLISH)] = callback }
        }
    }

    /**
     * Prepares one publisher and returns a single-admission grant pinned to this exact owner.
     * The returned value owns all projected data and does not borrow native memory.
     */
    fun preparePublishGrant(spec: StreamPublishSpec): StreamPublishGrant {
        check(ownerAware) { "preparePublishGrant requires a lane opened with StreamLane.openOwned" }
        requirePublish(spec)
        val callback = publisherCallback(spec)
        return nativeCall { lane ->
            val native = nativePublishSpec(spec, callback)
            val grant = NativeStreamPublishGrant().apply {
                struct_size = size().toLong()
                write()
            }
            requireOk(
                lib.coakka_v2_stream_lane_prepare_publish_grant(lane, native, grant),
                "stream_lane_prepare_publish_grant",
            )
            grant.read()
            lifecycle.withLock { callbacks[SessionKey(spec.sessionId, StreamDirection.PUBLISH)] = callback }
            StreamPublishGrant(
                owner = ownerEndpoint(grant.owner),
                sessionId = nativeFixedText(grant.session_id),
                authorizationToken = nativeFixedText(grant.authorization_token),
                formatId = grant.format_id,
                maxFrameBytes = grant.max_frame_bytes,
            )
        }
    }

    /**
     * Queues one subscriber connection and retains its consumer until [forget] or [close].
     *
     * @param spec endpoint, authorization, format contract, flow-control window, timeout, and consumer callback.
     */
    fun subscribe(spec: StreamSubscribeSpec) {
        requireSession(spec.sessionId, spec.authorizationToken)
        require(spec.remoteHost.isNotBlank()) { "remoteHost must not be blank" }
        require(spec.remotePort in 1..65535) { "remotePort must be in [1, 65535]" }
        require(spec.maxFrameBytes in 1..4 * 1024 * 1024) { "maxFrameBytes must be in [1, 4194304]" }
        require(spec.initialWindowBytes in spec.maxFrameBytes..16 * 1024 * 1024) { "initialWindowBytes must cover one frame and stay within 16777216" }
        require(spec.timeoutMs >= 0) { "timeoutMs must be non-negative" }
        val callback = NativeStreamConsumer { _, data, frame ->
            try {
                frame.read()
                if (frame.size <= 0 || frame.size > Int.MAX_VALUE.toLong()) {
                    CoakkaStatus.ERR_INVALID_ARG
                } else {
                    val view = data.getByteBuffer(0, frame.size).order(ByteOrder.nativeOrder()).asReadOnlyBuffer()
                    val metadata = StreamFrameMetadata(frame.sequence, frame.captured_mono_ns, frame.dropped_before, frame.flags)
                    when (spec.consumer.consume(view, metadata)) {
                        StreamConsumerDecision.CONTINUE -> CoakkaStatus.OK
                        StreamConsumerDecision.STOP -> CoakkaStatus.ERR_CLOSED
                    }
                }
            } catch (_: Throwable) {
                CoakkaStatus.ERR_IO
            }
        }
        nativeCall { lane ->
            val native = NativeStreamSubscribeSpec().apply {
                struct_size = size().toLong()
                session_id = spec.sessionId
                authorization_token = spec.authorizationToken
                remote_host = spec.remoteHost
                remote_port = spec.remotePort.toShort()
                format_id = spec.formatId
                max_frame_bytes = spec.maxFrameBytes
                initial_window_bytes = spec.initialWindowBytes
                timeout_ms = spec.timeoutMs
                consume = callback
                write()
            }
            requireOk(lib.coakka_v2_stream_lane_subscribe(lane, native), "stream_lane_subscribe")
            lifecycle.withLock { callbacks[SessionKey(spec.sessionId, StreamDirection.SUBSCRIBE)] = callback }
        }
    }

    /**
     * Returns the current copied session snapshot without waiting.
     *
     * @param sessionId application correlation ID supplied to prepare or subscribe.
     * @param direction publisher or subscriber record to observe.
     */
    fun session(sessionId: String, direction: StreamDirection): StreamSessionSnapshot = nativeCall { lane ->
        requireSessionId(sessionId)
        sessionSnapshot(NativeStreamSessionSnapshot().apply {
            struct_size = size().toLong()
            write()
            requireOk(lib.coakka_v2_stream_lane_get_session(lane, sessionId, direction.nativeValue, this), "stream_lane_get_session")
            read()
        })
    }

    /**
     * Blocks until session progress advances, timeout expires, or the lane stops.
     *
     * @param sessionId correlation ID supplied to prepare or subscribe.
     * @param direction publisher or subscriber record to observe.
     * @param afterUpdateSequence last observed sequence; zero requests the current state.
     * @param timeoutMs bounded wait duration in milliseconds.
     */
    fun waitSession(
        sessionId: String,
        direction: StreamDirection,
        afterUpdateSequence: Long = 0,
        timeoutMs: Int = 30_000,
    ): StreamSessionSnapshot = nativeCall { lane ->
        requireSessionId(sessionId)
        require(afterUpdateSequence >= 0 && timeoutMs >= 0) { "wait parameters must be non-negative" }
        sessionSnapshot(NativeStreamSessionSnapshot().apply {
            struct_size = size().toLong()
            write()
            requireOk(lib.coakka_v2_stream_lane_wait_session(lane, sessionId, direction.nativeValue, afterUpdateSequence, timeoutMs, this), "stream_lane_wait_session")
            read()
        })
    }

    /**
     * Returns the current copied transport-pressure signal without waiting.
     *
     * @param sessionId application correlation ID supplied to prepare or subscribe.
     * @param direction publisher or subscriber signal to observe.
     */
    fun pressure(sessionId: String, direction: StreamDirection): StreamPressureSnapshot = nativeCall { lane ->
        requireSessionId(sessionId)
        pressureSnapshot(NativeStreamPressureSnapshot().apply {
            struct_size = size().toLong()
            write()
            requireOk(lib.coakka_v2_stream_lane_get_pressure(lane, sessionId, direction.nativeValue, this), "stream_lane_get_pressure")
            read()
        })
    }

    /**
     * Blocks for a coalesced pressure update newer than [afterUpdateSequence].
     *
     * @param sessionId correlation ID supplied to prepare or subscribe.
     * @param direction publisher or subscriber signal to observe.
     * @param afterUpdateSequence last pressure sequence already handled by app policy.
     * @param timeoutMs bounded wait duration in milliseconds.
     */
    fun waitPressure(
        sessionId: String,
        direction: StreamDirection,
        afterUpdateSequence: Long = 0,
        timeoutMs: Int = 30_000,
    ): StreamPressureSnapshot = nativeCall { lane ->
        requireSessionId(sessionId)
        require(afterUpdateSequence >= 0 && timeoutMs >= 0) { "wait parameters must be non-negative" }
        pressureSnapshot(NativeStreamPressureSnapshot().apply {
            struct_size = size().toLong()
            write()
            requireOk(lib.coakka_v2_stream_lane_wait_pressure(lane, sessionId, direction.nativeValue, afterUpdateSequence, timeoutMs, this), "stream_lane_wait_pressure")
            read()
        })
    }

    /**
     * Requests cooperative cancellation; observe the terminal snapshot before forgetting it.
     *
     * @param sessionId application correlation ID supplied to prepare or subscribe.
     * @param direction local retained record to cancel.
     */
    fun cancel(sessionId: String, direction: StreamDirection) = nativeCall { lane ->
        requireSessionId(sessionId)
        requireOk(lib.coakka_v2_stream_lane_cancel_session(lane, sessionId, direction.nativeValue), "stream_lane_cancel_session")
    }

    /**
     * Releases a retained terminal record and its connector-owned callback reference.
     *
     * @param sessionId application correlation ID supplied to prepare or subscribe.
     * @param direction local terminal record to release.
     */
    fun forget(sessionId: String, direction: StreamDirection) = nativeCall { lane ->
        requireSessionId(sessionId)
        requireOk(lib.coakka_v2_stream_lane_forget_session(lane, sessionId, direction.nativeValue), "stream_lane_forget_session")
        lifecycle.withLock { callbacks.remove(SessionKey(sessionId, direction)) }
    }

    /** Returns a copied lane-level observability snapshot. */
    fun stats(): StreamLaneStats = nativeCall { lane ->
        NativeStreamLaneStats().apply {
            struct_size = size().toLong()
            write()
            requireOk(lib.coakka_v2_stream_lane_get_stats(lane, this), "stream_lane_get_stats")
            read()
        }.let(::laneStats)
    }

    override fun close() {
        val lane = lifecycle.withLock {
            if (handle == null) return
            if (closing) {
                while (handle != null) drained.awaitUninterruptibly()
                return
            }
            closing = true
            handle!!
        }
        val stopStatus = lib.coakka_v2_stream_lane_stop(lane)
        lifecycle.withLock {
            while (activeCalls != 0) drained.awaitUninterruptibly()
            lib.coakka_v2_stream_lane_destroy(lane)
            callbacks.clear()
            handle = null
            drained.signalAll()
        }
        if (stopStatus != CoakkaStatus.OK && stopStatus != CoakkaStatus.ERR_CLOSED) {
            throw StreamLaneException("stream_lane_stop", stopStatus)
        }
    }

    private inline fun <T> nativeCall(block: (Pointer) -> T): T {
        val lane = lifecycle.withLock {
            check(!closing && handle != null) { "stream lane is closed" }
            activeCalls += 1
            handle!!
        }
        try {
            return block(lane)
        } finally {
            lifecycle.withLock {
                activeCalls -= 1
                if (activeCalls == 0) drained.signalAll()
            }
        }
    }

    private fun requirePublish(spec: StreamPublishSpec) {
        requireSession(spec.sessionId, spec.authorizationToken)
        require(spec.formatId != 0L) { "formatId must be non-zero" }
        require(spec.maxFrameBytes in 1..4 * 1024 * 1024) { "maxFrameBytes must be in [1, 4194304]" }
    }

    private fun publisherCallback(spec: StreamPublishSpec) = NativeStreamSourceNext { _, destination, capacity, outFrame ->
        try {
            val boundedCapacity = capacity.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val buffer = destination.getByteBuffer(0, capacity).order(ByteOrder.nativeOrder())
            buffer.clear()
            when (val result = spec.source.next(buffer)) {
                is StreamSourceResult.Frame -> {
                    if (result.size !in 1..boundedCapacity || result.capturedMonoNs < 0 || result.droppedBefore < 0) {
                        CoakkaStatus.ERR_INVALID_ARG
                    } else {
                        outFrame.read()
                        outFrame.captured_mono_ns = result.capturedMonoNs
                        outFrame.dropped_before = result.droppedBefore
                        outFrame.flags = result.flags
                        outFrame.size = result.size.toLong()
                        outFrame.write()
                        CoakkaStatus.OK
                    }
                }
                StreamSourceResult.WouldBlock -> CoakkaStatus.ERR_WOULD_BLOCK
                StreamSourceResult.End -> CoakkaStatus.ERR_CLOSED
            }
        } catch (_: Throwable) {
            CoakkaStatus.ERR_IO
        }
    }

    private fun nativePublishSpec(spec: StreamPublishSpec, callback: NativeStreamSourceNext) = NativeStreamPublishSpec().apply {
        struct_size = size().toLong()
        session_id = spec.sessionId
        authorization_token = spec.authorizationToken
        format_id = spec.formatId
        max_frame_bytes = spec.maxFrameBytes
        source_next = callback
        write()
    }

    companion object {
        private var processLibraryPath: String? = null
        private var processLibrary: CoakkaV2Library? = null
        private val streamLaneSymbols = listOf(
            "coakka_v2_stream_lane_create_ex", "coakka_v2_stream_lane_destroy", "coakka_v2_stream_lane_start",
            "coakka_v2_stream_lane_stop", "coakka_v2_stream_lane_get_bound_port",
            "coakka_v2_stream_lane_prepare_publish", "coakka_v2_stream_lane_subscribe",
            "coakka_v2_stream_lane_get_session", "coakka_v2_stream_lane_wait_session",
            "coakka_v2_stream_lane_get_pressure", "coakka_v2_stream_lane_wait_pressure",
            "coakka_v2_stream_lane_cancel_session", "coakka_v2_stream_lane_forget_session",
            "coakka_v2_stream_lane_get_stats",
        )
        private val ownerGrantSymbols = listOf(
            "coakka_v2_stream_lane_create_owned_ex",
            "coakka_v2_stream_lane_prepare_publish_grant",
        )

        private fun library(path: String): CoakkaV2Library = synchronized(this) {
            val canonical = Paths.get(path).toAbsolutePath().normalize().toString()
            processLibraryPath?.let { existing ->
                check(existing == canonical) { "a different CoAkka runtime library is already loaded: $existing" }
            }
            processLibrary ?: CoakkaV2Library.load(canonical).also {
                processLibraryPath = canonical
                processLibrary = it
            }
        }

        private fun requireCompleteStreamLane(path: String) {
            val native = NativeLibrary.getInstance(path)
            val missing = streamLaneSymbols.firstOrNull { symbol ->
                try {
                    native.getFunction(symbol)
                    false
                } catch (_: UnsatisfiedLinkError) {
                    true
                }
            }
            if (missing != null) {
                throw UnsupportedOperationException("native runtime does not export the complete stream-lane ABI; missing $missing")
            }
        }

        private fun requireOwnerGrantSupport(path: String, lib: CoakkaV2Library) {
            val info = RuntimeInfo().apply {
                struct_size = size().toLong()
                write()
            }
            requireOk(lib.coakka_v2_runtime_get_info(info), "runtime_get_info")
            info.read()
            if ((info.feature_flags and CoakkaRuntimeFeatures.LANE_OWNER_GRANTS) == 0) {
                throw UnsupportedOperationException("native runtime does not advertise lane_owner_grants feature bit 25")
            }
            val native = NativeLibrary.getInstance(path)
            val missing = ownerGrantSymbols.firstOrNull { symbol ->
                try { native.getFunction(symbol); false } catch (_: UnsatisfiedLinkError) { true }
            }
            if (missing != null) {
                throw UnsupportedOperationException("native runtime advertises lane_owner_grants but is missing $missing")
            }
        }

        /**
         * Opens and starts an independent lane.
         *
         * @param config bounded capability, worker, flow-control, security, and pressure settings.
         * @param runtimeLibPath explicit core-runtime path, or `null` for normal connector resolution.
         */
        @JvmStatic
        fun open(config: StreamLaneConfig = StreamLaneConfig(), runtimeLibPath: String? = null): StreamLane {
            config.requireValid()
            val path = NativeLibraryResolver.resolve(runtimeLibPath)
            val lib = library(path)
            requireCompleteStreamLane(path)
            val security = config.security?.let { value ->
                NativeStreamLaneSecurityConfig().apply {
                    struct_size = size().toLong()
                    mode = value.mode.nativeValue
                    credential_generation = value.credentialGeneration
                    credential_id = value.credentialId.ifEmpty { null }
                    ca_certificate_file = value.caCertificateFile.ifEmpty { null }
                    identity_certificate_file = value.identityCertificateFile.ifEmpty { null }
                    private_key_file = value.privateKeyFile.ifEmpty { null }
                    write()
                }
            }
            val native = NativeStreamLaneConfig().apply {
                struct_size = size().toLong()
                flags = config.flags
                bind_host = config.bindHost
                bind_port = config.bindPort.toShort()
                capacity = config.capacity
                max_frame_bytes = config.maxFrameBytes
                max_window_bytes = config.maxWindowBytes
                io_timeout_ms = config.ioTimeoutMs
                source_retry_ms = config.sourceRetryMs
                progress_frames = config.progressFrames
                progress_interval_ms = config.progressIntervalMs
                publisher_worker_count = config.publisherWorkerCount
                subscriber_worker_count = config.subscriberWorkerCount
                this.security = security?.pointer
                pressure_after_ms = config.pressureAfterMs
                stalled_after_ms = config.stalledAfterMs
                recovery_after_ms = config.recoveryAfterMs
                pressure_observation_ms = config.pressureObservationMs
                write()
            }
            val out = PointerByReference()
            requireOk(lib.coakka_v2_stream_lane_create_ex(native, out), "stream_lane_create")
            val lane = out.value ?: throw StreamLaneException("stream_lane_create", CoakkaStatus.ERR_NOMEM)
            val status = lib.coakka_v2_stream_lane_start(lane)
            if (status != CoakkaStatus.OK) {
                lib.coakka_v2_stream_lane_destroy(lane)
                throw StreamLaneException("stream_lane_start", status)
            }
            return StreamLane(lib, lane, ownerAware = false)
        }

        /**
         * Opens an owner-aware publisher lane that can issue replica-pinned publish grants.
         * `owner.advertisedHost` must reach this exact process or pod, not a load-balancing Service.
         */
        @JvmStatic
        fun openOwned(
            config: StreamLaneConfig,
            owner: LaneOwnerConfig,
            runtimeLibPath: String? = null,
        ): StreamLane {
            config.requireValid()
            owner.requireValid()
            require(config.flags and StreamLaneFlags.PUBLISHER != 0) { "owner-aware stream lane must enable PUBLISHER" }
            val path = NativeLibraryResolver.resolve(runtimeLibPath)
            val lib = library(path)
            requireCompleteStreamLane(path)
            requireOwnerGrantSupport(path, lib)
            val security = nativeSecurity(config.security)
            val laneConfig = nativeConfig(config, security)
            val ownerConfig = NativeLaneOwnerConfig().apply {
                struct_size = size().toLong()
                owner_instance_id = owner.ownerInstanceId
                advertised_host = owner.advertisedHost
                write()
            }
            val owned = NativeStreamLaneOwnedConfig().apply {
                struct_size = size().toLong()
                lane = laneConfig
                this.owner = ownerConfig
                write()
            }
            val out = PointerByReference()
            requireOk(lib.coakka_v2_stream_lane_create_owned_ex(owned, out), "stream_lane_create_owned")
            val lane = out.value ?: throw StreamLaneException("stream_lane_create_owned", CoakkaStatus.ERR_NOMEM)
            val status = lib.coakka_v2_stream_lane_start(lane)
            if (status != CoakkaStatus.OK) {
                lib.coakka_v2_stream_lane_destroy(lane)
                throw StreamLaneException("stream_lane_start", status)
            }
            return StreamLane(lib, lane, ownerAware = true)
        }

        private fun requireSession(sessionId: String, authorizationToken: String) {
            requireSessionId(sessionId)
            require(authorizationToken.isNotEmpty() && authorizationToken.toByteArray().size <= 128) { "authorizationToken must contain 1..128 UTF-8 bytes" }
        }

        private fun requireSessionId(sessionId: String) {
            require(sessionId.isNotEmpty() && sessionId.toByteArray().size <= 64) { "sessionId must contain 1..64 UTF-8 bytes" }
        }

        private fun nativeSecurity(value: StreamLaneSecurityConfig?): NativeStreamLaneSecurityConfig? = value?.let {
            NativeStreamLaneSecurityConfig().apply {
                struct_size = size().toLong(); mode = it.mode.nativeValue; credential_generation = it.credentialGeneration
                credential_id = it.credentialId.ifEmpty { null }; ca_certificate_file = it.caCertificateFile.ifEmpty { null }
                identity_certificate_file = it.identityCertificateFile.ifEmpty { null }; private_key_file = it.privateKeyFile.ifEmpty { null }; write()
            }
        }

        private fun nativeConfig(config: StreamLaneConfig, security: NativeStreamLaneSecurityConfig?) = NativeStreamLaneConfig().apply {
            struct_size = size().toLong(); flags = config.flags; bind_host = config.bindHost; bind_port = config.bindPort.toShort()
            capacity = config.capacity; max_frame_bytes = config.maxFrameBytes; max_window_bytes = config.maxWindowBytes
            io_timeout_ms = config.ioTimeoutMs; source_retry_ms = config.sourceRetryMs; progress_frames = config.progressFrames
            progress_interval_ms = config.progressIntervalMs; publisher_worker_count = config.publisherWorkerCount
            subscriber_worker_count = config.subscriberWorkerCount; this.security = security?.pointer
            pressure_after_ms = config.pressureAfterMs; stalled_after_ms = config.stalledAfterMs
            recovery_after_ms = config.recoveryAfterMs; pressure_observation_ms = config.pressureObservationMs; write()
        }

        private fun ownerEndpoint(value: NativeLaneOwnerEndpoint): LaneOwnerEndpoint {
            value.read()
            return LaneOwnerEndpoint(
                ownerInstanceId = nativeFixedText(value.owner_instance_id),
                advertisedHost = nativeFixedText(value.advertised_host),
                port = value.port.toInt() and 0xffff,
            )
        }

        private fun sessionSnapshot(value: NativeStreamSessionSnapshot) = StreamSessionSnapshot(
            StreamDirection.entries.first { it.nativeValue == value.direction },
            StreamState.entries.first { it.nativeValue == value.state },
            StreamResult.entries.first { it.nativeValue == value.result },
            value.format_id, value.frames, value.bytes, value.dropped_frames, value.last_sequence,
            value.negotiated_max_frame_bytes, value.window_bytes, value.cancel_requested != 0,
            value.update_sequence, value.submitted_mono_ns, value.started_mono_ns, value.updated_mono_ns,
            value.terminal_mono_ns, nativeText(value.detail),
        )

        private fun pressureSnapshot(value: NativeStreamPressureSnapshot) = StreamPressureSnapshot(
            StreamDirection.entries.first { it.nativeValue == value.direction },
            StreamPressureState.entries.first { it.nativeValue == value.state },
            value.reason_bits, value.available_credit_bytes, value.window_capacity_bytes, value.update_sequence,
            value.transition_count, value.observed_mono_ns, value.state_started_mono_ns,
            value.pressure_started_mono_ns, value.last_progress_mono_ns, value.observed_delivery_bps,
            value.current_operation_ns, value.last_operation_ns, value.total_pressured_ns, value.max_pressured_ns,
        )

        private fun laneStats(value: NativeStreamLaneStats) = StreamLaneStats(
            value.capacity, value.queued_subscribers, value.prepared_publishers, value.active_publishers,
            value.active_subscribers, value.retained_records, value.submitted_subscribers,
            value.prepared_publisher_count, value.ended_publishers, value.ended_subscribers,
            value.failed_publishers, value.failed_subscribers, value.canceled_sessions, value.published_frames,
            value.published_bytes, value.consumed_frames, value.consumed_bytes, value.source_reported_drops,
        )

        private fun nativeText(value: ByteArray): String = value.takeWhile { it.toInt() != 0 }.toByteArray().toString(Charsets.UTF_8)

        private fun requireOk(status: Int, operation: String) {
            if (status != CoakkaStatus.OK) throw StreamLaneException(operation, status)
        }
    }
}
