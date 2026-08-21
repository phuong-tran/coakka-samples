package coakka.v2.android

internal object NativeRuntimeBridge {
    init {
        System.loadLibrary("coakka_runtime_v2")
        System.loadLibrary("coakka_android_jni")
    }

    external fun nativeAbiVersion(): Int

    external fun nativeReadRuntimeInfo(numeric: LongArray, text: Array<String?>): Int

    external fun nativeCreate(
        systemName: String,
        nodeId: String,
        queueCapacity: Int,
        strictNoDrop: Boolean,
    ): Long

    external fun nativeApplyNetwork(
        handle: Long,
        mode: Int,
        bindHost: String?,
        bindPort: Int,
        advertiseHost: String?,
        advertisePort: Int,
    ): Int

    external fun nativeApplyInitialControl(handle: Long, envelope: ByteArray): Int

    external fun nativeOpenHostHandles(handle: Long, flags: Int): IntArray?

    external fun nativeStart(handle: Long): Int

    external fun nativeStop(handle: Long): Int

    external fun nativeConsumeMonitor(fd: Int): Long

    external fun nativeReadHealth(handle: Long): LongArray?

    external fun nativeDestroy(handle: Long)

    external fun nativeFileLaneCreate(
        numeric: LongArray,
        text: Array<String?>,
        ownerAware: Boolean,
        outStatus: IntArray,
    ): Long

    external fun nativeFileLaneStop(handle: Long): Int

    external fun nativeFileLaneDestroy(handle: Long)

    external fun nativeFileLaneBoundPort(handle: Long, outPort: IntArray): Int

    external fun nativeFileLanePrepareReceive(
        handle: Long,
        transferId: String,
        authorizationToken: String,
        destinationPath: String,
        expectedSize: Long,
        expectedSha256: ByteArray,
    ): Int

    external fun nativeFileLanePrepareReceiveGrant(
        handle: Long,
        transferId: String,
        authorizationToken: String,
        destinationPath: String,
        expectedSize: Long,
        expectedSha256: ByteArray,
        outNumeric: LongArray,
        outText: Array<String?>,
        outSha256: ByteArray,
    ): Int

    external fun nativeFileLaneSubmitSend(
        handle: Long,
        transferId: String,
        authorizationToken: String,
        remoteHost: String,
        remotePort: Int,
        sourcePath: String,
        expectedSize: Long,
        expectedSha256: ByteArray,
        timeoutMs: Int,
    ): Int

    external fun nativeFileLaneTransfer(
        handle: Long,
        transferId: String,
        direction: Int,
        afterUpdateSequence: Long,
        timeoutMs: Int,
        wait: Boolean,
        outNumeric: LongArray,
        outText: Array<String?>,
    ): Int

    external fun nativeFileLaneCancel(handle: Long, transferId: String, direction: Int): Int

    external fun nativeFileLaneForget(handle: Long, transferId: String, direction: Int): Int

    external fun nativeFileLaneStats(handle: Long, outNumeric: LongArray): Int

    external fun nativeFileSha256(path: String, outSha256: ByteArray, outSize: LongArray): Int

    external fun nativeStreamLaneCreate(
        numeric: LongArray,
        text: Array<String?>,
        ownerAware: Boolean,
        outStatus: IntArray,
    ): Long

    external fun nativeStreamLaneStop(handle: Long): Int

    external fun nativeStreamLaneDestroy(handle: Long)

    external fun nativeStreamLaneBoundPort(handle: Long, outPort: IntArray): Int

    external fun nativeStreamLanePreparePublish(
        handle: Long,
        sessionId: String,
        authorizationToken: String,
        formatId: Long,
        maxFrameBytes: Int,
        source: AndroidStreamSource,
        outNumeric: LongArray?,
        outText: Array<String?>?,
    ): Int

    external fun nativeStreamLaneSubscribe(
        handle: Long,
        sessionId: String,
        authorizationToken: String,
        remoteHost: String,
        remotePort: Int,
        formatId: Long,
        maxFrameBytes: Int,
        initialWindowBytes: Int,
        timeoutMs: Int,
        consumer: AndroidStreamConsumer,
    ): Int

    external fun nativeStreamLaneSession(
        handle: Long,
        sessionId: String,
        direction: Int,
        afterUpdateSequence: Long,
        timeoutMs: Int,
        wait: Boolean,
        outNumeric: LongArray,
        outText: Array<String?>,
    ): Int

    external fun nativeStreamLanePressure(
        handle: Long,
        sessionId: String,
        direction: Int,
        afterUpdateSequence: Long,
        timeoutMs: Int,
        wait: Boolean,
        outNumeric: LongArray,
    ): Int

    external fun nativeStreamLaneCancel(handle: Long, sessionId: String, direction: Int): Int

    external fun nativeStreamLaneForget(handle: Long, sessionId: String, direction: Int): Int

    external fun nativeStreamLaneStats(handle: Long, outNumeric: LongArray): Int
}

internal object NativeStatus {
    const val OK = 0
    const val BAD_STATE = -3
    const val IO = -5
    const val WOULD_BLOCK = -6
    const val CLOSED = -7
}

class CoAkkaNativeException(operation: String, val status: Int) :
    IllegalStateException("$operation failed with native status=$status")

internal fun requireNativeOk(status: Int, operation: String) {
    if (status != NativeStatus.OK) {
        throw CoAkkaNativeException(operation, status)
    }
}
