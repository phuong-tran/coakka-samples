package coakka.v2.android

import java.io.Closeable
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object FileLaneFlags {
    const val SENDER = 1
    const val RECEIVER = 1 shl 1
}

enum class FileLaneSecurityMode(internal val nativeValue: Int) {
    DIRECT(0),
    TLS(1),
    MUTUAL_TLS(2),
}

enum class FileTransferDirection(internal val nativeValue: Int) {
    SEND(1),
    RECEIVE(2);

    companion object {
        internal fun fromNative(value: Int) =
            entries.firstOrNull { it.nativeValue == value }
                ?: error("unknown file direction=$value")
    }
}

enum class FileTransferState(internal val nativeValue: Int) {
    PREPARED(1),
    QUEUED(2),
    CONNECTING(3),
    TRANSFERRING(4),
    VERIFYING(5),
    COMPLETED(6),
    PAUSED(7),
    REJECTED(8),
    FAILED(9),
    CANCELED(10);

    val terminal: Boolean get() = this == COMPLETED || nativeValue >= PAUSED.nativeValue

    companion object {
        internal fun fromNative(value: Int) =
            entries.firstOrNull { it.nativeValue == value }
                ?: error("unknown file state=$value")
    }
}

enum class FileTransferResult(internal val nativeValue: Int) {
    NONE(0),
    OK(1),
    NOT_PREPARED(2),
    TOKEN_MISMATCH(3),
    METADATA_MISMATCH(4),
    SIZE_LIMIT(5),
    STORAGE_IO(6),
    INTEGRITY_MISMATCH(7),
    NETWORK_IO(8),
    TIMEOUT(9),
    QUEUE_FULL(10),
    PROTOCOL_ERROR(11),
    SOURCE_CHANGED(12),
    INTERNAL_ERROR(13),
    CANCELED_BY_HOST(14),
    TLS_CONFIG_INVALID(15),
    TLS_HANDSHAKE_FAILED(16),
    PEER_CERT_UNTRUSTED(17),
    PEER_CERT_EXPIRED(18),
    PEER_IDENTITY_MISMATCH(19),
    CLIENT_CERT_REQUIRED(20);

    companion object {
        internal fun fromNative(value: Int) =
            entries.firstOrNull { it.nativeValue == value }
                ?: error("unknown file result=$value")
    }
}

data class FileLaneSecurityConfig(
    val mode: FileLaneSecurityMode = FileLaneSecurityMode.DIRECT,
    val credentialGeneration: Long = 0,
    val credentialId: String = "",
    val caCertificateFile: String = "",
    val identityCertificateFile: String = "",
    val privateKeyFile: String = "",
) {
    internal fun requireValid(): FileLaneSecurityConfig {
        require(credentialGeneration >= 0) { "credentialGeneration must be non-negative" }
        return this
    }
}

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
    internal fun requireValid(): FileLaneConfig {
        require(flags != 0 && flags and (FileLaneFlags.SENDER or FileLaneFlags.RECEIVER).inv() == 0) {
            "file lane requires valid sender or receiver flags"
        }
        require(bindHost.isNotBlank()) { "bindHost must not be blank" }
        require(bindPort in 0..65535) { "bindPort must be in [0, 65535]" }
        require(queueCapacity in 0..Int.MAX_VALUE.toLong()) {
            "queueCapacity must fit every packaged Android ABI"
        }
        require(maxFileSize >= 0 && ioTimeoutMs >= 0) { "file limits must be non-negative" }
        require(checkpointBytes >= 0 && progressBytes >= 0 && progressIntervalMs >= 0) {
            "file progress values must be non-negative"
        }
        require(senderWorkerCount in 0..4 && receiverWorkerCount in 0..4) {
            "file worker counts must be in [0, 4]"
        }
        security?.requireValid()
        return this
    }
}

class FileReceiveSpec(
    val transferId: String,
    val authorizationToken: String,
    val destinationFile: File,
    val expectedSize: Long,
    expectedSha256: ByteArray,
) {
    private val digest = expectedSha256.copyOf()
    val expectedSha256: ByteArray get() = digest.copyOf()

    internal fun requireValid(): FileReceiveSpec {
        requireTransferIdentity(transferId, authorizationToken)
        require(expectedSize > 0) { "expectedSize must be positive" }
        require(digest.size == 32) { "expectedSha256 must contain exactly 32 bytes" }
        return this
    }

    override fun toString(): String =
        "FileReceiveSpec(transferId=$transferId, authorizationToken=<redacted>, " +
            "destinationFile=$destinationFile, expectedSize=$expectedSize, " +
            "expectedSha256=<${digest.size} bytes>)"
}

class FileSendSpec(
    val transferId: String,
    val authorizationToken: String,
    val remoteHost: String,
    val remotePort: Int,
    val sourceFile: File,
    val expectedSize: Long,
    expectedSha256: ByteArray,
    val timeoutMs: Int = 0,
) {
    private val digest = expectedSha256.copyOf()
    val expectedSha256: ByteArray get() = digest.copyOf()

    internal fun requireValid(): FileSendSpec {
        requireTransferIdentity(transferId, authorizationToken)
        require(remoteHost.isNotBlank()) { "remoteHost must not be blank" }
        require(remotePort in 1..65535) { "remotePort must be in [1, 65535]" }
        require(expectedSize > 0) { "expectedSize must be positive" }
        require(timeoutMs >= 0) { "timeoutMs must be non-negative" }
        require(digest.size == 32) { "expectedSha256 must contain exactly 32 bytes" }
        return this
    }

    override fun toString(): String =
        "FileSendSpec(transferId=$transferId, authorizationToken=<redacted>, " +
            "remoteHost=$remoteHost, remotePort=$remotePort, sourceFile=$sourceFile, " +
            "expectedSize=$expectedSize, expectedSha256=<${digest.size} bytes>, " +
            "timeoutMs=$timeoutMs)"
}

class FileDigest(sha256: ByteArray, val size: Long) {
    private val digest = sha256.copyOf()
    val sha256: ByteArray get() = digest.copyOf()

    init {
        require(digest.size == 32) { "sha256 must contain exactly 32 bytes" }
        require(size >= 0) { "size must be non-negative" }
    }
}

data class FileTransferSnapshot(
    val direction: FileTransferDirection,
    val state: FileTransferState,
    val result: FileTransferResult,
    val expectedSize: Long,
    val transferredBytes: Long,
    val committedOffset: Long,
    val progressMilli: Int,
    val cancelRequested: Boolean,
    val updateSequence: Long,
    val submittedMonoNs: Long,
    val startedMonoNs: Long,
    val updatedMonoNs: Long,
    val terminalMonoNs: Long,
    val detail: String,
) {
    val terminal: Boolean get() = state.terminal
    val completed: Boolean get() = state == FileTransferState.COMPLETED && result == FileTransferResult.OK
}

data class FileLaneStats(
    val queueCapacity: Long,
    val queuedSends: Long,
    val preparedReceives: Long,
    val activeSends: Long,
    val activeReceives: Long,
    val retainedRecords: Long,
    val submittedSends: Long,
    val preparedReceiveCount: Long,
    val completedSends: Long,
    val completedReceives: Long,
    val failedSends: Long,
    val failedReceives: Long,
    val canceledTransfers: Long,
    val completedSendBytes: Long,
    val completedReceiveBytes: Long,
)

class FileLaneException(operation: String, val status: Int) :
    IllegalStateException("$operation failed with native status=$status")

/** Independent bounded file lane; it does not place file bytes in runtime envelopes. */
class FileLane private constructor(
    private var nativeHandle: Long,
    private val ownerAware: Boolean,
) : Closeable {
    private val lifecycle = ReentrantLock()
    private val drained = lifecycle.newCondition()
    private var closing = false
    private var activeCalls = 0

    val boundPort: Int get() = nativeCall { handle ->
        val output = IntArray(1)
        requireFileOk(NativeRuntimeBridge.nativeFileLaneBoundPort(handle, output), "file_lane_get_bound_port")
        output[0]
    }

    fun prepareReceive(spec: FileReceiveSpec) = nativeCall { handle ->
        spec.requireValid()
        requireFileOk(
            NativeRuntimeBridge.nativeFileLanePrepareReceive(
                handle,
                spec.transferId,
                spec.authorizationToken,
                spec.destinationFile.absolutePath,
                spec.expectedSize,
                spec.expectedSha256,
            ),
            "file_lane_prepare_receive",
        )
    }

    /** Returns a copied owner-pinned grant whose token is redacted from diagnostics. */
    fun prepareReceiveGrant(spec: FileReceiveSpec): FileReceiveGrant {
        check(ownerAware) { "prepareReceiveGrant requires FileLane.openOwned" }
        return nativeCall { handle ->
            spec.requireValid()
            val numeric = LongArray(2)
            val text = arrayOfNulls<String>(4)
            val digest = ByteArray(32)
            requireFileOk(
                NativeRuntimeBridge.nativeFileLanePrepareReceiveGrant(
                    handle,
                    spec.transferId,
                    spec.authorizationToken,
                    spec.destinationFile.absolutePath,
                    spec.expectedSize,
                    spec.expectedSha256,
                    numeric,
                    text,
                    digest,
                ),
                "file_lane_prepare_receive_grant",
            )
            FileReceiveGrant(
                owner = LaneOwnerEndpoint(checkNotNull(text[0]), checkNotNull(text[1]), numeric[0].toInt()),
                transferId = checkNotNull(text[2]),
                authorizationToken = checkNotNull(text[3]),
                expectedSize = numeric[1],
                expectedSha256 = digest,
            )
        }
    }

    fun submitSend(spec: FileSendSpec) = nativeCall { handle ->
        spec.requireValid()
        requireFileOk(
            NativeRuntimeBridge.nativeFileLaneSubmitSend(
                handle,
                spec.transferId,
                spec.authorizationToken,
                spec.remoteHost,
                spec.remotePort,
                spec.sourceFile.absolutePath,
                spec.expectedSize,
                spec.expectedSha256,
                spec.timeoutMs,
            ),
            "file_lane_submit_send",
        )
    }

    fun transfer(transferId: String, direction: FileTransferDirection): FileTransferSnapshot =
        readTransfer(transferId, direction, 0, 0, false)

    fun waitTransfer(
        transferId: String,
        direction: FileTransferDirection,
        afterUpdateSequence: Long = 0,
        timeoutMs: Int = 30_000,
    ): FileTransferSnapshot = readTransfer(
        transferId,
        direction,
        afterUpdateSequence,
        timeoutMs,
        true,
    )

    private fun readTransfer(
        transferId: String,
        direction: FileTransferDirection,
        afterUpdateSequence: Long,
        timeoutMs: Int,
        wait: Boolean,
    ): FileTransferSnapshot = nativeCall { handle ->
        requireUtf8Bytes(transferId, 64, "transferId")
        require(afterUpdateSequence >= 0 && timeoutMs >= 0) { "wait values must be non-negative" }
        val numeric = LongArray(13)
        val text = arrayOfNulls<String>(1)
        requireFileOk(
            NativeRuntimeBridge.nativeFileLaneTransfer(
                handle,
                transferId,
                direction.nativeValue,
                afterUpdateSequence,
                timeoutMs,
                wait,
                numeric,
                text,
            ),
            if (wait) "file_lane_wait_transfer" else "file_lane_get_transfer",
        )
        FileTransferSnapshot(
            direction = FileTransferDirection.fromNative(numeric[0].toInt()),
            state = FileTransferState.fromNative(numeric[1].toInt()),
            result = FileTransferResult.fromNative(numeric[2].toInt()),
            expectedSize = numeric[3],
            transferredBytes = numeric[4],
            committedOffset = numeric[5],
            progressMilli = numeric[6].toInt(),
            cancelRequested = numeric[7] != 0L,
            updateSequence = numeric[8],
            submittedMonoNs = numeric[9],
            startedMonoNs = numeric[10],
            updatedMonoNs = numeric[11],
            terminalMonoNs = numeric[12],
            detail = checkNotNull(text[0]),
        )
    }

    fun cancel(transferId: String, direction: FileTransferDirection) = nativeCall { handle ->
        requireUtf8Bytes(transferId, 64, "transferId")
        requireFileOk(
            NativeRuntimeBridge.nativeFileLaneCancel(handle, transferId, direction.nativeValue),
            "file_lane_cancel_transfer",
        )
    }

    fun forget(transferId: String, direction: FileTransferDirection) = nativeCall { handle ->
        requireUtf8Bytes(transferId, 64, "transferId")
        requireFileOk(
            NativeRuntimeBridge.nativeFileLaneForget(handle, transferId, direction.nativeValue),
            "file_lane_forget_transfer",
        )
    }

    fun stats(): FileLaneStats = nativeCall { handle ->
        val values = LongArray(15)
        requireFileOk(NativeRuntimeBridge.nativeFileLaneStats(handle, values), "file_lane_get_stats")
        FileLaneStats(
            values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7],
            values[8], values[9], values[10], values[11], values[12], values[13], values[14],
        )
    }

    override fun close() {
        val handle = lifecycle.withLock {
            if (nativeHandle == 0L) return
            if (closing) {
                while (nativeHandle != 0L) drained.awaitUninterruptibly()
                return
            }
            closing = true
            nativeHandle
        }
        val stopStatus = NativeRuntimeBridge.nativeFileLaneStop(handle)
        lifecycle.withLock {
            while (activeCalls != 0) drained.awaitUninterruptibly()
            NativeRuntimeBridge.nativeFileLaneDestroy(handle)
            nativeHandle = 0L
            drained.signalAll()
        }
        if (stopStatus != NativeStatus.OK && stopStatus != NativeStatus.CLOSED) {
            throw FileLaneException("file_lane_stop", stopStatus)
        }
    }

    private inline fun <T> nativeCall(block: (Long) -> T): T {
        val handle = lifecycle.withLock {
            check(!closing && nativeHandle != 0L) { "file lane is closed" }
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
        fun open(config: FileLaneConfig = FileLaneConfig()): FileLane =
            openInternal(config.requireValid(), null)

        /** Opens an owner-aware receiver that can issue replica-pinned receive grants. */
        @JvmStatic
        fun openOwned(config: FileLaneConfig, owner: LaneOwnerConfig): FileLane {
            config.requireValid()
            owner.requireValid()
            require(config.flags and FileLaneFlags.RECEIVER != 0) {
                "owner-aware file lane must enable RECEIVER"
            }
            return openInternal(config, owner)
        }

        @JvmStatic
        fun sha256(file: File): FileDigest {
            val digest = ByteArray(32)
            val size = LongArray(1)
            requireFileOk(
                NativeRuntimeBridge.nativeFileSha256(file.absolutePath, digest, size),
                "file_sha256_path",
            )
            return FileDigest(digest, size[0])
        }

        private fun openInternal(config: FileLaneConfig, owner: LaneOwnerConfig?): FileLane {
            val security = config.security
            val numeric = longArrayOf(
                config.flags.toLong(),
                config.bindPort.toLong(),
                config.queueCapacity,
                config.maxFileSize,
                config.ioTimeoutMs.toLong(),
                config.checkpointBytes,
                config.progressBytes,
                config.progressIntervalMs.toLong(),
                config.senderWorkerCount.toLong(),
                config.receiverWorkerCount.toLong(),
                security?.mode?.nativeValue?.toLong() ?: -1L,
                security?.credentialGeneration ?: 0L,
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
            val handle = NativeRuntimeBridge.nativeFileLaneCreate(
                numeric,
                text,
                owner != null,
                status,
            )
            if (handle == 0L || status[0] != NativeStatus.OK) {
                throw FileLaneException("file_lane_open", status[0])
            }
            return FileLane(handle, owner != null)
        }

        private fun requireFileOk(status: Int, operation: String) {
            if (status != NativeStatus.OK) throw FileLaneException(operation, status)
        }
    }
}
