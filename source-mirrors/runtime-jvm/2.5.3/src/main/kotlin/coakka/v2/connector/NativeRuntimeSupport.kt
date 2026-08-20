package coakka.v2.connector

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val POLLIN: Short = 0x0001
private const val EINTR = 4
private const val EAGAIN_LINUX = 11
private const val EAGAIN_DARWIN = 35
private const val WAIT_OBJECT_0 = 0
private const val WAIT_TIMEOUT = 258
private const val WAIT_FAILED = -1
private const val WINDOWS_INVALID_OSFHANDLE = -1L
private val isWindowsHost = System.getProperty("os.name").lowercase().contains("win")

open class CoakkaException(message: String) : RuntimeException(message)

class DeadletterException(val deadletter: coakka.v2.connector.protocol.ConnectorDeadletter) :
    RuntimeException("deadletter reason=${deadletter.reason} detail=${deadletter.detail}")

internal fun requireStatus(rc: Int, what: String) {
    check(rc == CoakkaStatus.OK) { "$what failed rc=$rc (${statusName(rc)})" }
}

internal fun closeIfOpen(fd: Int) {
    if (fd >= 0) {
        val rc = if (isWindowsHost) {
            WindowsMsvcrt.INSTANCE._close(fd)
        } else {
            PosixLibC.INSTANCE.close(fd)
        }
        check(rc == 0) { "close failed fd=$fd rc=$rc" }
    }
}

internal fun waitReadable(fd: Int, timeoutMs: Int): Boolean {
    if (isWindowsHost) {
        val osHandle = WindowsMsvcrt.INSTANCE._get_osfhandle(fd)
        check(osHandle != WINDOWS_INVALID_OSFHANDLE) {
            "invalid osfhandle for fd=$fd"
        }
        return when (
            val rc = WindowsKernel32.INSTANCE.WaitForSingleObject(
                Pointer.createConstant(osHandle),
                timeoutMs,
            )
        ) {
            WAIT_OBJECT_0 -> true
            WAIT_TIMEOUT -> false
            WAIT_FAILED -> error(
                "WaitForSingleObject failed fd=$fd handle=$osHandle lastError=${Native.getLastError()}",
            )
            else -> error("unexpected WaitForSingleObject result fd=$fd rc=$rc")
        }
    }

    val pollFd = PollFd().apply {
        this.fd = fd
        this.events = POLLIN
        write()
    }
    val rc = PosixLibC.INSTANCE.poll(pollFd.pointer, 1, timeoutMs)
    check(rc >= 0) { "poll failure waiting for fd=$fd rc=$rc" }
    return rc != 0
}

internal fun readFully(fd: Int, length: Int): ByteArray {
    val out = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val chunk = ByteArray(length - offset)
        val rc = if (isWindowsHost) {
            WindowsMsvcrt.INSTANCE._read(fd, chunk, chunk.size)
        } else {
            PosixLibC.INSTANCE.read(fd, chunk, chunk.size.toLong()).toInt()
        }
        if (rc < 0) {
            if (!isWindowsHost) {
                when (Native.getLastError()) {
                    EINTR -> continue
                    EAGAIN_LINUX, EAGAIN_DARWIN -> {
                        waitReadable(fd, 5)
                        continue
                    }
                }
            }
            error("read failed fd=$fd rc=$rc errno=${Native.getLastError()}")
        }
        check(rc > 0) { "read closed fd=$fd rc=$rc" }
        chunk.copyInto(out, destinationOffset = offset, endIndex = rc)
        offset += rc
    }
    return out
}

internal fun readFrameOrNull(fd: Int, timeoutMs: Int): ByteArray? {
    if (!waitReadable(fd, timeoutMs)) {
        return null
    }
    val header = readFully(fd, 8)
    val payloadLength = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).long
    check(payloadLength >= 0 && payloadLength <= Int.MAX_VALUE.toLong()) {
        "invalid frame length=$payloadLength"
    }
    return readFully(fd, payloadLength.toInt())
}

internal class NativeFrameReader(
    private val lib: CoakkaV2Library,
    private val fd: Int,
    maxFrameSize: Int = 64 * 1024,
) : AutoCloseable {
    private val reader = lib.coakka_v2_frame_reader_create(fd, maxFrameSize.toLong())
        ?: error("coakka_v2_frame_reader_create returned null for fd=$fd")

    fun readFrameOrNull(timeoutMs: Int): ByteArray? {
        if (!isWindowsHost && !waitReadable(fd, timeoutMs)) {
            return null
        }

        val deadlineNanos = System.nanoTime() + (timeoutMs.toLong() * 1_000_000L)
        while (true) {
            val outBuf = PointerByReference()
            val outLen = LongByReference()
            when (val rc = lib.coakka_v2_frame_read_try(reader, outBuf, outLen)) {
                CoakkaStatus.OK -> {
                    val buf = outBuf.value ?: error("frame_read_try returned OK with null buffer")
                    val len = outLen.value
                    check(len >= 0 && len <= Int.MAX_VALUE.toLong()) {
                        "invalid frame length from frame_read_try len=$len"
                    }
                    return try {
                        buf.getByteArray(0, len.toInt())
                    } finally {
                        lib.coakka_v2_frame_release(buf)
                    }
                }
                CoakkaStatus.ERR_WOULD_BLOCK -> {
                    if (!isWindowsHost) {
                        return null
                    }
                    val remainingMs = ((deadlineNanos - System.nanoTime()) / 1_000_000L).coerceAtLeast(0L)
                    if (remainingMs == 0L) {
                        return null
                    }
                    Thread.sleep(minOf(5L, remainingMs))
                }
                CoakkaStatus.ERR_CLOSED -> return null
                else -> error("frame_read_try failed fd=$fd rc=$rc (${statusName(rc)})")
            }
        }
    }

    override fun close() {
        lib.coakka_v2_frame_reader_destroy(reader)
    }
}

internal fun safeText(value: String?): String = value.orEmpty()

private fun formatFlags(
    flags: Int,
    formatter: (flags: Int, buf: ByteArray, bufLen: Long) -> Long,
): String {
    val buf = ByteArray(256)
    formatter(flags, buf, buf.size.toLong())
    val zeroIndex = buf.indexOf(0.toByte())
    val textBytes = if (zeroIndex >= 0) buf.copyOf(zeroIndex) else buf
    return textBytes.toString(Charsets.UTF_8)
}

internal fun statusName(status: Int): String =
    when (status) {
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

internal fun transportApplyReasonName(lib: CoakkaV2Library, reason: Int): String =
    safeText(lib.coakka_v2_transport_apply_reason_name(reason))

internal fun runtimeStateName(lib: CoakkaV2Library, state: Int): String =
    safeText(lib.coakka_v2_runtime_state_name(state))

internal fun overloadModeName(lib: CoakkaV2Library, mode: Int): String =
    safeText(lib.coakka_v2_overload_mode_name(mode))

internal fun runtimeFeatureFlagsText(lib: CoakkaV2Library, flags: Int): String =
    formatFlags(flags, lib::coakka_v2_format_runtime_feature_flags)

internal fun healthFlagsText(lib: CoakkaV2Library, flags: Int): String =
    formatFlags(flags, lib::coakka_v2_format_health_flags)

/**
 * Global runtime/library metadata exported independent of any created runtime instance.
 */
data class RuntimeInfoSnapshot(
    val abiVersion: Int,
    val featureFlags: Int,
    val runtimeVersion: String,
    val gitCommit: String,
    val southboundBackend: String,
    val allocatorBackend: String,
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
    val southboundBindHost: String?,
    val southboundBindPort: Int,
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
