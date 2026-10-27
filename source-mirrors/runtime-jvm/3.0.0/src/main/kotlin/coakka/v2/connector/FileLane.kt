package coakka.v2.connector

import coakka.v2.connector.internal.NativeBridge
import coakka.v2.connector.internal.NativeRecord
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val FILE_LANE_SHA256_BYTES = 32

object FileLaneFlags { const val SENDER = 1; const val RECEIVER = 1 shl 1 }
enum class FileLaneSecurityMode(val nativeValue: Int) { DIRECT(0), TLS(1), MUTUAL_TLS(2) }
enum class FileTransferDirection(val nativeValue: Int) { SEND(1), RECEIVE(2) }
enum class FileTransferState(val nativeValue: Int) {
    PREPARED(1), QUEUED(2), CONNECTING(3), TRANSFERRING(4), VERIFYING(5), COMPLETED(6),
    PAUSED(7), REJECTED(8), FAILED(9), CANCELED(10);
    /** Known SDK vocabulary only; observe Core transitions through the snapshot. */
    val terminal: Boolean get() = this == COMPLETED || this == REJECTED || this == FAILED || this == CANCELED
}
enum class FileTransferResult(val nativeValue: Int) {
    NONE(0), OK(1), NOT_PREPARED(2), TOKEN_MISMATCH(3), METADATA_MISMATCH(4), SIZE_LIMIT(5),
    STORAGE_IO(6), INTEGRITY_MISMATCH(7), NETWORK_IO(8), TIMEOUT(9), QUEUE_FULL(10), PROTOCOL_ERROR(11),
    SOURCE_CHANGED(12), INTERNAL_ERROR(13), CANCELED_BY_HOST(14), TLS_CONFIG_INVALID(15),
    TLS_HANDSHAKE_FAILED(16), PEER_CERT_UNTRUSTED(17), PEER_CERT_EXPIRED(18),
    PEER_IDENTITY_MISMATCH(19), CLIENT_CERT_REQUIRED(20),
}

data class FileLaneSecurityConfig(
    val mode: FileLaneSecurityMode = FileLaneSecurityMode.DIRECT,
    val credentialGeneration: Long = 0,
    val credentialId: String = "",
    val caCertificateFile: String = "",
    val identityCertificateFile: String = "",
    val privateKeyFile: String = "",
)

/** Bounded lane configuration. Zero tuning values select conservative Core defaults. */
data class FileLaneConfig(
    val flags: Int = FileLaneFlags.SENDER or FileLaneFlags.RECEIVER,
    val bindHost: String = "127.0.0.1",
    val bindPort: Int = 0,
    val queueCapacity: Long = 0,
    val maxFileSize: Long = 0,
    val ioTimeoutMs: Int = 0,
    val checkpointBytes: Long = 0,
    val progressBytes: Long = 0,
    val progressIntervalMs: Int = 0,
    val senderWorkerCount: Int = 0,
    val receiverWorkerCount: Int = 0,
    val security: FileLaneSecurityConfig? = null,
) {
    fun requireValid(): FileLaneConfig {
        require(flags != 0 && flags and (FileLaneFlags.SENDER or FileLaneFlags.RECEIVER).inv() == 0) { "file lane requires valid sender or receiver flags" }
        require(bindPort in 0..65535) { "bindPort must be in [0, 65535]" }
        require(queueCapacity >= 0 && maxFileSize >= 0 && ioTimeoutMs >= 0) { "file lane limits must be non-negative" }
        require(checkpointBytes >= 0 && progressBytes >= 0 && progressIntervalMs >= 0) { "file lane progress values must be non-negative" }
        require(senderWorkerCount in 0..4 && receiverWorkerCount in 0..4) { "file lane worker counts must be in [0, 4]" }
        return this
    }
}

data class FileReceiveSpec(
    val transferId: String,
    val authorizationToken: String,
    val destinationPath: Path,
    val expectedSize: Long,
    val expectedSha256: ByteArray,
) {
    override fun toString(): String =
        "FileReceiveSpec(transferId=$transferId, authorizationToken=<redacted>, destinationPath=$destinationPath, " +
            "expectedSize=$expectedSize, expectedSha256=<${expectedSha256.size} bytes>)"
}

data class FileSendSpec(
    val transferId: String,
    val authorizationToken: String,
    val remoteHost: String,
    val remotePort: Int,
    val sourcePath: Path,
    val expectedSize: Long,
    val expectedSha256: ByteArray,
    val timeoutMs: Int = 0,
) {
    override fun toString(): String =
        "FileSendSpec(transferId=$transferId, authorizationToken=<redacted>, remoteHost=$remoteHost, " +
            "remotePort=$remotePort, sourcePath=$sourcePath, expectedSize=$expectedSize, " +
            "expectedSha256=<${expectedSha256.size} bytes>, timeoutMs=$timeoutMs)"
}

data class FileDigest(val sha256: ByteArray, val size: Long)
data class FileTransferSnapshot(
    val direction: FileTransferDirection, val state: FileTransferState, val result: FileTransferResult,
    val expectedSize: Long, val transferredBytes: Long, val committedOffset: Long, val progressMilli: Int,
    val cancelRequested: Boolean, val updateSequence: Long, val submittedMonoNs: Long, val startedMonoNs: Long,
    val updatedMonoNs: Long, val terminalMonoNs: Long, val detail: String,
) {
    /** Core's observed transition marker; PAUSED has no terminal receipt. */
    val terminal: Boolean get() = terminalMonoNs != 0L
    val completed: Boolean get() = terminal && state == FileTransferState.COMPLETED && result == FileTransferResult.OK
}
data class FileLaneStats(
    val queueCapacity: Long, val queuedSends: Long, val preparedReceives: Long, val activeSends: Long,
    val activeReceives: Long, val retainedRecords: Long, val submittedSends: Long, val preparedReceiveCount: Long,
    val completedSends: Long, val completedReceives: Long, val failedSends: Long, val failedReceives: Long,
    val canceledTransfers: Long, val completedSendBytes: Long, val completedReceiveBytes: Long,
)

class FileLaneException(operation: String, val status: Int) : RuntimeException("$operation failed: native status $status")

/**
 * Independent bulk-transfer lane backed by Core. JNI owns the native handle,
 * drains in-flight calls before destroy, and exposes only copied records.
 */
class FileLane private constructor(private var nativeId: Long, private val ownerAware: Boolean) : AutoCloseable {
    private val lifecycle = ReentrantLock()
    private var closing = false

    val boundPort: Int get() = nativeCall { id ->
        NativeBridge.fileBoundPort(id).checked("file_lane_get_bound_port").value(1).toInt()
    }

    fun prepareReceive(spec: FileReceiveSpec) = nativeCall { id ->
        validateReceive(spec)
        requireOk(NativeBridge.filePrepareReceive(id, longArrayOf(spec.expectedSize), receiveBytes(spec)), "file_lane_prepare_receive")
    }

    fun prepareReceiveGrant(spec: FileReceiveSpec): FileReceiveGrant {
        check(ownerAware) { "prepareReceiveGrant requires a lane opened with FileLane.openOwned" }
        return nativeCall { id ->
            validateReceive(spec)
            val record = NativeBridge.filePrepareReceiveGrant(id, longArrayOf(spec.expectedSize), receiveBytes(spec))
                .checked("file_lane_prepare_receive_grant")
            FileReceiveGrant(
                owner = LaneOwnerEndpoint(record.text(0), record.text(1), record.value(1).toInt()),
                transferId = record.text(2), authorizationToken = record.text(3),
                expectedSize = record.value(2), expectedSha256 = record.payload(4),
            )
        }
    }

    fun submitSend(spec: FileSendSpec) = nativeCall { id ->
        requireDigest(spec.expectedSha256)
        require(spec.expectedSize >= 0 && spec.remotePort in 1..65535 && spec.timeoutMs >= 0)
        val values = longArrayOf(spec.remotePort.toLong(), spec.expectedSize, spec.timeoutMs.toLong())
        val bytes: Array<ByteArray?> = arrayOf(spec.transferId.utf8(), spec.authorizationToken.utf8(), spec.remoteHost.utf8(), spec.sourcePath.toString().utf8(), spec.expectedSha256.copyOf())
        requireOk(NativeBridge.fileSubmitSend(id, values, bytes), "file_lane_submit_send")
    }

    fun transfer(transferId: String, direction: FileTransferDirection): FileTransferSnapshot = nativeCall { id ->
        snapshot(NativeBridge.fileTransfer(id, transferId.utf8(), direction.nativeValue).checked("file_lane_get_transfer"))
    }

    fun waitTransfer(transferId: String, direction: FileTransferDirection, afterUpdateSequence: Long = 0, timeoutMs: Int = 30_000): FileTransferSnapshot = nativeCall { id ->
        require(afterUpdateSequence >= 0 && timeoutMs >= 0)
        snapshot(NativeBridge.fileWaitTransfer(id, transferId.utf8(), direction.nativeValue, afterUpdateSequence, timeoutMs).checked("file_lane_wait_transfer"))
    }

    fun cancel(transferId: String, direction: FileTransferDirection) = nativeCall { id ->
        requireOk(NativeBridge.fileCancel(id, transferId.utf8(), direction.nativeValue), "file_lane_cancel_transfer")
    }
    fun forget(transferId: String, direction: FileTransferDirection) = nativeCall { id ->
        requireOk(NativeBridge.fileForget(id, transferId.utf8(), direction.nativeValue), "file_lane_forget_transfer")
    }
    fun stats(): FileLaneStats = nativeCall { id ->
        val r = NativeBridge.fileStats(id).checked("file_lane_get_stats")
        FileLaneStats(r.value(1), r.value(2), r.value(3), r.value(4), r.value(5), r.value(6), r.value(7), r.value(8), r.value(9), r.value(10), r.value(11), r.value(12), r.value(13), r.value(14), r.value(15))
    }

    override fun close() {
        val id = lifecycle.withLock {
            if (nativeId == 0L) return
            check(!closing) { "file lane close already in progress" }
            closing = true
            nativeId.also { nativeId = 0L }
        }
        val status = NativeBridge.fileClose(id)
        if (status != CoakkaStatus.OK && status != CoakkaStatus.ERR_CLOSED) throw FileLaneException("file_lane_stop", status)
    }

    private inline fun <T> nativeCall(block: (Long) -> T): T = block(lifecycle.withLock {
        check(!closing && nativeId != 0L) { "file lane is closed" }
        nativeId
    })

    companion object {
        @JvmStatic fun open(config: FileLaneConfig = FileLaneConfig(), runtimeLibPath: String? = null): FileLane = openInternal(config, null, runtimeLibPath)
        @JvmStatic fun openOwned(config: FileLaneConfig, owner: LaneOwnerConfig, runtimeLibPath: String? = null): FileLane {
            owner.requireValid()
            require(config.flags and FileLaneFlags.RECEIVER != 0) { "owner-aware file lane must enable RECEIVER" }
            return openInternal(config, owner, runtimeLibPath)
        }
        @JvmStatic fun sha256(path: Path, runtimeLibPath: String? = null): FileDigest {
            NativeLibraryResolver.resolve(runtimeLibPath)
            val r = NativeBridge.fileSha256(path.toString().utf8()).checked("file_sha256_path")
            return FileDigest(r.payload(0), r.value(1))
        }

        private fun openInternal(config: FileLaneConfig, owner: LaneOwnerConfig?, runtimeLibPath: String?): FileLane {
            config.requireValid()
            NativeLibraryResolver.resolve(runtimeLibPath)
            val security = config.security
            val values = longArrayOf(
                config.flags.toLong(), config.bindPort.toLong(), config.queueCapacity, config.maxFileSize,
                config.ioTimeoutMs.toLong(), config.checkpointBytes, config.progressBytes, config.progressIntervalMs.toLong(),
                config.senderWorkerCount.toLong(), config.receiverWorkerCount.toLong(),
                (security?.mode?.nativeValue ?: -1).toLong(), security?.credentialGeneration ?: 0,
            )
            val bytes: Array<ByteArray?> = arrayOf(config.bindHost.utf8(), security?.credentialId?.utf8(), security?.caCertificateFile?.utf8(), security?.identityCertificateFile?.utf8(), security?.privateKeyFile?.utf8())
            val ownerBytes: Array<ByteArray?>? = owner?.let { arrayOf(it.ownerInstanceId.utf8(), it.advertisedHost.utf8()) }
            val r = NativeBridge.fileOpen(values, bytes, ownerBytes).checked(if (owner == null) "file_lane_create" else "file_lane_create_owned")
            return FileLane(r.value(1), owner != null)
        }

        private fun validateReceive(spec: FileReceiveSpec) { requireDigest(spec.expectedSha256); require(spec.expectedSize >= 0) }
        private fun receiveBytes(spec: FileReceiveSpec): Array<ByteArray?> = arrayOf(spec.transferId.utf8(), spec.authorizationToken.utf8(), spec.destinationPath.toString().utf8(), spec.expectedSha256.copyOf())
        private fun snapshot(r: NativeRecord) = FileTransferSnapshot(
            FileTransferDirection.entries.first { it.nativeValue == r.value(1).toInt() },
            FileTransferState.entries.first { it.nativeValue == r.value(2).toInt() },
            FileTransferResult.entries.first { it.nativeValue == r.value(3).toInt() },
            r.value(4), r.value(5), r.value(6), r.value(7).toInt(), r.value(8) != 0L,
            r.value(9), r.value(10), r.value(11), r.value(12), r.value(13), r.text(0),
        )
        private fun requireDigest(value: ByteArray) = require(value.size == FILE_LANE_SHA256_BYTES) { "expectedSha256 must contain exactly $FILE_LANE_SHA256_BYTES bytes" }
        private fun NativeRecord.checked(operation: String): NativeRecord { requireOk(value(0).toInt(), operation); return this }
        private fun requireOk(status: Int, operation: String) { if (status != CoakkaStatus.OK) throw FileLaneException(operation, status) }
        private fun String.utf8(): ByteArray = toByteArray(Charsets.UTF_8)
    }
}
