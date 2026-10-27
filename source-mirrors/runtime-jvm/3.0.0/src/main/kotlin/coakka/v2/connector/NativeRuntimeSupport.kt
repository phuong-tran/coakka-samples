package coakka.v2.connector

import coakka.v2.connector.internal.NativeBridge
import coakka.v2.connector.internal.NativeRecord
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

open class CoakkaException(message: String) : RuntimeException(message)

class DeadletterException(val deadletter: coakka.v2.connector.protocol.ConnectorDeadletter) :
    RuntimeException("deadletter reason=${deadletter.reason} detail=${deadletter.detail}")

internal fun requireStatus(rc: Int, what: String) {
    check(rc == CoakkaStatus.OK) { "$what failed rc=$rc (${statusName(rc)})" }
}

internal fun statusName(status: Int): String = when (status) {
    CoakkaStatus.OK -> "ok"
    CoakkaStatus.ERR_INVALID_ARG -> "invalid_arg"
    CoakkaStatus.ERR_NOMEM -> "nomem"
    CoakkaStatus.ERR_BAD_STATE -> "bad_state"
    CoakkaStatus.ERR_STALE_GENERATION -> "stale_generation"
    CoakkaStatus.ERR_IO -> "io"
    CoakkaStatus.ERR_WOULD_BLOCK -> "would_block"
    CoakkaStatus.ERR_CLOSED -> "closed"
    CoakkaStatus.ERR_FEATURE_UNAVAILABLE -> "feature_unavailable"
    CoakkaStatus.ERR_FEATURE_NOT_ENTITLED -> "feature_not_entitled"
    else -> "unknown_status"
}

/**
 * Core validates the request and projects the only facts JVM dispatch needs.
 *
 * One private 64 KiB direct scratch belongs to this sole reader. JNI borrows it
 * only for one synchronous read and Kotlin materializes independent arrays
 * before reuse. A larger record falls back to owned arrays in that same read;
 * neither representation leaves a Core frame lease or scratch view live.
 */
internal class NativeDeliveredRequestReader(private val handle: RuntimeHandle) : AutoCloseable {
    @Volatile private var closed = false
    private val reading = AtomicBoolean()
    private val staging = ByteBuffer.allocateDirect(STAGING_CAPACITY).order(ByteOrder.LITTLE_ENDIAN)

    fun readOrNull(timeoutMs: Int): NativeDeliveredRequest? {
        require(timeoutMs >= 0) { "timeoutMs must be nonnegative" }
        if (closed) return null
        check(reading.compareAndSet(false, true)) { "delivered-request reader is already active" }
        try {
            staging.clear()
            staging.putInt(0, 0)
            val separate = NativeBridge.runtimeReadRequest(handle.nativeId, timeoutMs, staging)
            val used = staging.getInt(0)
            if (separate != null) {
                check(used == 0) { "ambiguous delivered-request representation" }
                return when (val status = separate.value(0).toInt()) {
                    CoakkaStatus.OK -> NativeDeliveredRequest(
                        frame = separate.payload(0),
                        target = separate.payload(1),
                        payload = separate.payload(2),
                        oneWay = separate.value(1) != 0L,
                    )
                    CoakkaStatus.ERR_WOULD_BLOCK, CoakkaStatus.ERR_CLOSED -> null
                    else -> {
                        requireStatus(status, "runtime_read_request")
                        null
                    }
                }
            }
            if (used == 0) return null
            return decodeStaged(used)
        } finally {
            reading.set(false)
        }
    }

    override fun close() { closed = true }

    private fun decodeStaged(used: Int): NativeDeliveredRequest {
        check(used in HEADER_BYTES..staging.capacity()) { "invalid delivered-request extent" }
        staging.limit(used)
        staging.position(Int.SIZE_BYTES)
        val oneWay = staging.int
        val frameLength = staging.int
        val targetLength = staging.int
        val payloadLength = staging.int
        check(oneWay in 0..1 && frameLength > 0 && targetLength > 0 && payloadLength >= 0) {
            "invalid delivered-request projection"
        }
        val expected = HEADER_BYTES.toLong() + frameLength + targetLength + payloadLength
        check(expected == used.toLong()) { "truncated delivered-request projection" }
        fun bytes(length: Int): ByteArray = ByteArray(length).also(staging::get)
        return NativeDeliveredRequest(
            frame = bytes(frameLength),
            target = bytes(targetLength),
            payload = bytes(payloadLength),
            oneWay = oneWay != 0,
        )
    }

    private companion object {
        const val STAGING_CAPACITY = 65_536
        const val HEADER_BYTES = Int.SIZE_BYTES * 5
    }
}

internal class NativeDeliveredRequest(
    val frame: ByteArray,
    val target: ByteArray,
    val payload: ByteArray,
    val oneWay: Boolean,
)

internal fun NativeRecord.infoSnapshot(): RuntimeInfoSnapshot {
    requireStatus(value(0).toInt(), "runtime_get_info")
    return RuntimeInfoSnapshot(value(1).toInt(), value(2).toInt(), text(0), text(1),
        text(2), text(3), text(4), text(5))
}

internal fun NativeRecord.configSnapshot(): RuntimeConfigSnapshot {
    requireStatus(value(0).toInt(), "runtime_get_config")
    return RuntimeConfigSnapshot(
        systemName = text(0), nodeId = text(1), strictNoDrop = value(1) != 0L,
        queueCapacity = value(2).toInt(), requestMaxFrameSize = value(3), localDispatchBatchLimit = value(4),
        runtimeState = value(5).toInt(), runtimeStateName = text(3), snapshotPresent = value(6) != 0L,
        appliedGeneration = value(7), routeCount = value(8),
        transportBindHost = bytes.getOrNull(2)?.toString(Charsets.UTF_8), transportBindPort = value(9).toInt(),
        configuredIngressOverloadMode = value(10).toInt(), configuredIngressOverloadModeName = text(4),
        configuredLocalDeliveryOverloadMode = value(11).toInt(), configuredLocalDeliveryOverloadModeName = text(5),
        configuredRemoteOutboundOverloadMode = value(12).toInt(), configuredRemoteOutboundOverloadModeName = text(6),
        configuredRemoteOutboundReplyReserveSlots = value(13),
        effectiveIngressOverloadMode = value(14).toInt(), effectiveIngressOverloadModeName = text(7),
        effectiveLocalDeliveryOverloadMode = value(15).toInt(), effectiveLocalDeliveryOverloadModeName = text(8),
        effectiveRemoteOutboundOverloadMode = value(16).toInt(), effectiveRemoteOutboundOverloadModeName = text(9),
        effectiveRemoteOutboundReplyReserveSlots = value(17),
    )
}

internal fun NativeRecord.healthSnapshot(): RuntimeHealthSnapshot {
    requireStatus(value(0).toInt(), "runtime_get_health")
    return RuntimeHealthSnapshot(value(1).toInt(), text(0), value(2).toInt(), text(1), value(3))
}

/** Indexes follow frozen public runtime_stats field order, excluding struct_size. */
internal fun NativeRecord.statsSnapshot(): RuntimeStatsSnapshot {
    requireStatus(value(0).toInt(), "runtime_get_stats")
    return RuntimeStatsSnapshot(
        appliedGeneration = value(1), routeCount = value(2), runtimeState = value(3).toInt(),
        runtimeStateName = text(0), queueRejectedCount = value(7), routeMissCount = value(8),
        deadletterCount = value(9), deliveryFailedCount = value(10), controlRejectedCount = value(40),
        localWorkQueueCapacity = value(54), localWorkQueueDepth = value(55), localWorkQueueHighWatermark = value(56),
        deliveredRequestOutboundQueueCapacity = value(17), deliveredRequestOutboundQueueHighWatermark = value(19),
        deliveredRequestOutboundEnqueueBlockCount = value(20), deliveredRequestOutboundDirectWriteCount = value(57),
        responseOutboundQueueCapacity = value(21), responseOutboundQueueHighWatermark = value(23),
        responseOutboundEnqueueBlockCount = value(24), responseOutboundDirectWriteCount = value(58),
        deadletterOutboundQueueCapacity = value(25), deadletterOutboundQueueHighWatermark = value(27),
        deadletterOutboundEnqueueBlockCount = value(28), deadletterOutboundDirectWriteCount = value(59),
        remoteOutboundQueueCapacity = value(31), remoteOutboundQueueDepth = value(32),
        remoteOutboundQueueHighWatermark = value(33), remoteOutboundQueueRejectedCount = value(34),
        remoteOutboundExpiredDropCount = value(35), remoteOutboundReplyReserveSlots = value(45),
        remoteOutboundReplyReservationRejectCount = value(46), ingressOverloadMode = value(47).toInt(),
        ingressOverloadModeName = text(1), localDeliveryOverloadMode = value(48).toInt(),
        localDeliveryOverloadModeName = text(2), remoteOutboundOverloadMode = value(49).toInt(),
        remoteOutboundOverloadModeName = text(3), monitorEventEmittedCount = value(50),
        monitorEventDroppedCount = value(51), monitorEventEmittedLifetimeCount = value(52),
        monitorEventDroppedLifetimeCount = value(53), remoteOutboundOneWayDropCount = value(60),
    )
}

/**
 * Global runtime/library metadata exported independent of any created runtime instance.
 */
data class RuntimeInfoSnapshot(
    val abiVersion: Int,
    val featureFlags: Int,
    val runtimeVersion: String,
    val gitCommit: String,
    val transportProvider: String,
    val allocatorProvider: String,
    val docsHint: String,
    val featureFlagsText: String,
)

/**
 * Effective runtime-owned config/state for one created runtime instance.
 */
data class RuntimeConfigSnapshot(
    val systemName: String,
    val nodeId: String,
    val strictNoDrop: Boolean,
    val queueCapacity: Int,
    val requestMaxFrameSize: Long,
    val localDispatchBatchLimit: Long,
    val runtimeState: Int,
    val runtimeStateName: String,
    val snapshotPresent: Boolean,
    val appliedGeneration: Long,
    val routeCount: Long,
    val transportBindHost: String?,
    val transportBindPort: Int,
    val configuredIngressOverloadMode: Int,
    val configuredIngressOverloadModeName: String,
    val configuredLocalDeliveryOverloadMode: Int,
    val configuredLocalDeliveryOverloadModeName: String,
    val configuredRemoteOutboundOverloadMode: Int,
    val configuredRemoteOutboundOverloadModeName: String,
    val configuredRemoteOutboundReplyReserveSlots: Long,
    val effectiveIngressOverloadMode: Int,
    val effectiveIngressOverloadModeName: String,
    val effectiveLocalDeliveryOverloadMode: Int,
    val effectiveLocalDeliveryOverloadModeName: String,
    val effectiveRemoteOutboundOverloadMode: Int,
    val effectiveRemoteOutboundOverloadModeName: String,
    val effectiveRemoteOutboundReplyReserveSlots: Long,
)

/**
 * Reduced health view exported by the native runtime.
 *
 * @property runtimeState Native runtime lifecycle state.
 * @property flags Bitset of health flags from the runtime ABI.
 * @property appliedGeneration Last accepted control generation.
 */
data class RuntimeHealthSnapshot(
    val runtimeState: Int,
    val runtimeStateName: String,
    val flags: Int,
    val flagsText: String,
    val appliedGeneration: Long,
)

/**
 * Reduced stats view exported by the native runtime.
 *
 * @property appliedGeneration Last accepted control generation.
 * @property routeCount Number of active routes in the current snapshot.
 * @property runtimeState Native runtime lifecycle state.
 * @property queueRejectedCount Count of ingress queue rejections.
 * @property routeMissCount Count of route lookup misses.
 * @property deadletterCount Count of emitted deadletters.
 * @property deliveryFailedCount Count of delivery failures after route resolution.
 * @property controlRejectedCount Count of rejected control updates.
 * @property localWorkQueueCapacity Capacity of the bounded runtime local-work queue.
 * @property localWorkQueueDepth Current depth of the bounded runtime local-work queue.
 * @property localWorkQueueHighWatermark Observed peak depth of the runtime local-work queue.
 */
data class RuntimeStatsSnapshot(
    val appliedGeneration: Long,
    val routeCount: Long,
    val runtimeState: Int,
    val runtimeStateName: String,
    val queueRejectedCount: Long,
    val routeMissCount: Long,
    val deadletterCount: Long,
    val deliveryFailedCount: Long,
    val controlRejectedCount: Long,
    val localWorkQueueCapacity: Long,
    val localWorkQueueDepth: Long,
    val localWorkQueueHighWatermark: Long,
    val deliveredRequestOutboundQueueCapacity: Long,
    val deliveredRequestOutboundQueueHighWatermark: Long,
    val deliveredRequestOutboundEnqueueBlockCount: Long,
    val deliveredRequestOutboundDirectWriteCount: Long,
    val responseOutboundQueueCapacity: Long,
    val responseOutboundQueueHighWatermark: Long,
    val responseOutboundEnqueueBlockCount: Long,
    val responseOutboundDirectWriteCount: Long,
    val deadletterOutboundQueueCapacity: Long,
    val deadletterOutboundQueueHighWatermark: Long,
    val deadletterOutboundEnqueueBlockCount: Long,
    val deadletterOutboundDirectWriteCount: Long,
    val remoteOutboundQueueCapacity: Long,
    val remoteOutboundQueueDepth: Long,
    val remoteOutboundQueueHighWatermark: Long,
    val remoteOutboundQueueRejectedCount: Long,
    val remoteOutboundExpiredDropCount: Long,
    val remoteOutboundReplyReserveSlots: Long,
    val remoteOutboundReplyReservationRejectCount: Long,
    val ingressOverloadMode: Int,
    val ingressOverloadModeName: String,
    val localDeliveryOverloadMode: Int,
    val localDeliveryOverloadModeName: String,
    val remoteOutboundOverloadMode: Int,
    val remoteOutboundOverloadModeName: String,
    val monitorEventEmittedCount: Long,
    val monitorEventDroppedCount: Long,
    val monitorEventEmittedLifetimeCount: Long,
    val monitorEventDroppedLifetimeCount: Long,
    val remoteOutboundOneWayDropCount: Long,
)
