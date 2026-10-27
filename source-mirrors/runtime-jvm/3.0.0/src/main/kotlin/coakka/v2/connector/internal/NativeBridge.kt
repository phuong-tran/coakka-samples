package coakka.v2.connector.internal

import java.nio.ByteBuffer

/** Copy-owned JNI projection. Its constructor is the sole native record lookup. */
internal class NativeRecord(val values: LongArray, val bytes: Array<ByteArray?>) {
    fun value(index: Int): Long = values.getOrElse(index) {
        error("incomplete native runtime record")
    }
    fun text(index: Int): String = bytes.getOrNull(index)?.toString(Charsets.UTF_8).orEmpty()
    fun payload(index: Int): ByteArray = bytes.getOrNull(index)
        ?: error("native runtime omitted required bytes")
}

/** Allocation-lean result for the request/reply payload fast path. */
internal class NativePayloadResult(
    val status: Int,
    val kind: Int,
    val payload: ByteArray?,
)

/**
 * The bridge owns native resource IDs and their borrow/close leases. Arrays are
 * copied or borrowed only during each synchronous call; no JVM array is pinned
 * while Core waits. Records never contain native addresses or retained views.
 * See v2/jvm/native-package/jni-contract.md for the versioned private projection.
 */
internal object NativeBridge {
    @JvmStatic external fun encodeMessage(values: LongArray, bytes: Array<ByteArray>): NativeRecord
    @JvmStatic external fun decodeMessage(bytes: ByteArray): NativeRecord
    @JvmStatic external fun decodeDeadletter(bytes: ByteArray): NativeRecord
    @JvmStatic external fun encodeControl(values: LongArray, bytes: Array<ByteArray>): NativeRecord
    @JvmStatic external fun runtimeCreate(system: ByteArray, node: ByteArray, queue: Int, strict: Int, startup: LongArray, connection: LongArray?): NativeRecord
    @JvmStatic external fun runtimeApplyNetwork(id: Long, fields: Long, mode: Int, bindHost: ByteArray?, bindPort: Int, advertiseHost: ByteArray?, advertisePort: Int): Int
    @JvmStatic external fun runtimeNetworkConfig(id: Long): NativeRecord
    @JvmStatic external fun runtimeExportHandles(id: Long, flags: Int): NativeRecord
    @JvmStatic external fun runtimeStart(id: Long): Int
    @JvmStatic external fun runtimeStop(id: Long): Int
    @JvmStatic external fun runtimeClose(id: Long)
    @JvmStatic external fun runtimeSubmit(id: Long, frame: ByteArray): Int
    @JvmStatic external fun runtimeControl(id: Long, frame: ByteArray): Int
    @JvmStatic external fun runtimeAskOpen(id: Long): Int
    @JvmStatic external fun runtimeAsk(id: Long, frame: ByteArray, timeout: Int): NativeRecord
    @JvmStatic external fun runtimeAskPayload(
        id: Long,
        messageId: ByteArray,
        source: ByteArray,
        target: ByteArray,
        replyTo: ByteArray,
        payload: ByteArray,
        requestTimeout: Int,
        waitTimeout: Int,
        deliveryHint: Int,
    ): NativePayloadResult
    @JvmStatic external fun runtimeReplyPayload(
        id: Long,
        requestFrame: ByteArray,
        source: ByteArray,
        payload: ByteArray,
    ): Int
    @JvmStatic external fun runtimeAskBegin(id: Long, frame: ByteArray): NativeRecord
    @JvmStatic external fun runtimeAskAwait(id: Long, ticketId: Long, timeout: Int): NativeRecord
    @JvmStatic external fun runtimeAskCancel(id: Long, ticketId: Long)
    @JvmStatic external fun runtimeReadRequest(id: Long, timeout: Int, staging: ByteBuffer): NativeRecord?
    @JvmStatic external fun runtimeMonitorWait(id: Long, timeout: Int): NativeRecord
    @JvmStatic external fun runtimeInfo(): NativeRecord
    @JvmStatic external fun runtimeCapabilities(): NativeRecord
    @JvmStatic external fun runtimeStartupConfig(id: Long): NativeRecord
    @JvmStatic external fun runtimeDeliveryConfig(id: Long): NativeRecord
    @JvmStatic external fun runtimeConfig(id: Long): NativeRecord
    @JvmStatic external fun runtimeHealth(id: Long): NativeRecord
    @JvmStatic external fun runtimeStats(id: Long): NativeRecord
    @JvmStatic external fun runtimeConnectionConfig(id: Long): NativeRecord
    @JvmStatic external fun runtimeSecurityInfo(id: Long): NativeRecord
    @JvmStatic external fun runtimeApplyConnection(id: Long, values: LongArray): NativeRecord
    @JvmStatic external fun runtimeApplySecurity(id: Long, values: LongArray, bytes: Array<ByteArray?>): NativeRecord

    @JvmStatic external fun fileOpen(values: LongArray, bytes: Array<ByteArray?>, owner: Array<ByteArray?>?): NativeRecord
    @JvmStatic external fun fileClose(id: Long): Int
    @JvmStatic external fun fileBoundPort(id: Long): NativeRecord
    @JvmStatic external fun filePrepareReceive(id: Long, values: LongArray, bytes: Array<ByteArray?>): Int
    @JvmStatic external fun filePrepareReceiveGrant(id: Long, values: LongArray, bytes: Array<ByteArray?>): NativeRecord
    @JvmStatic external fun fileSubmitSend(id: Long, values: LongArray, bytes: Array<ByteArray?>): Int
    @JvmStatic external fun fileTransfer(id: Long, transferId: ByteArray, direction: Int): NativeRecord
    @JvmStatic external fun fileWaitTransfer(id: Long, transferId: ByteArray, direction: Int, after: Long, timeout: Int): NativeRecord
    @JvmStatic external fun fileCancel(id: Long, transferId: ByteArray, direction: Int): Int
    @JvmStatic external fun fileForget(id: Long, transferId: ByteArray, direction: Int): Int
    @JvmStatic external fun fileStats(id: Long): NativeRecord
    @JvmStatic external fun fileSha256(path: ByteArray): NativeRecord

    @JvmStatic external fun streamOpen(values: LongArray, bytes: Array<ByteArray?>, owner: Array<ByteArray?>?): NativeRecord
    @JvmStatic external fun streamClose(id: Long): Int
    @JvmStatic external fun streamBoundPort(id: Long): NativeRecord
    @JvmStatic external fun streamPreparePublish(id: Long, values: LongArray, bytes: Array<ByteArray?>, source: NativeStreamSourceAdapter): Int
    @JvmStatic external fun streamPreparePublishGrant(id: Long, values: LongArray, bytes: Array<ByteArray?>, source: NativeStreamSourceAdapter): NativeRecord
    @JvmStatic external fun streamSubscribe(id: Long, values: LongArray, bytes: Array<ByteArray?>, consumer: NativeStreamConsumerAdapter): Int
    @JvmStatic external fun streamSession(id: Long, sessionId: ByteArray, direction: Int): NativeRecord
    @JvmStatic external fun streamWaitSession(id: Long, sessionId: ByteArray, direction: Int, after: Long, timeout: Int): NativeRecord
    @JvmStatic external fun streamPressure(id: Long, sessionId: ByteArray, direction: Int): NativeRecord
    @JvmStatic external fun streamWaitPressure(id: Long, sessionId: ByteArray, direction: Int, after: Long, timeout: Int): NativeRecord
    @JvmStatic external fun streamCancel(id: Long, sessionId: ByteArray, direction: Int): Int
    @JvmStatic external fun streamForget(id: Long, sessionId: ByteArray, direction: Int): Int
    @JvmStatic external fun streamStats(id: Long): NativeRecord
}

/** Narrow callback surfaces retained only while Core retains the matching record. */
internal fun interface NativeStreamSourceAdapter {
    fun next(destination: ByteArray, metadata: LongArray): Int
}

internal fun interface NativeStreamConsumerAdapter {
    fun consume(data: ByteArray, metadata: LongArray): Int
}
