package coakka.v2.connector

import com.sun.jna.Pointer
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.ptr.ShortByReference
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Direction capabilities enabled when opening a [FileLane]. */
object FileLaneFlags {
    const val SENDER = 1
    const val RECEIVER = 1 shl 1
}

/** Transport protection for file bytes; authorization tokens remain required in every mode. */
enum class FileLaneSecurityMode(val nativeValue: Int) { DIRECT(0), TLS(1), MUTUAL_TLS(2) }
/** Side of a transfer record used by query, wait, cancel, and forget operations. */
enum class FileTransferDirection(val nativeValue: Int) { SEND(1), RECEIVE(2) }
/** Observable file-transfer lifecycle state. */
enum class FileTransferState(val nativeValue: Int) {
    PREPARED(1), QUEUED(2), CONNECTING(3), TRANSFERRING(4), VERIFYING(5), COMPLETED(6),
    PAUSED(7), REJECTED(8), FAILED(9), CANCELED(10);
    val terminal: Boolean get() = nativeValue >= PAUSED.nativeValue || this == COMPLETED
}
/** Stable terminal outcome reported independently by the sender and receiver. */
enum class FileTransferResult(val nativeValue: Int) {
    NONE(0), OK(1), NOT_PREPARED(2), TOKEN_MISMATCH(3), METADATA_MISMATCH(4), SIZE_LIMIT(5),
    STORAGE_IO(6), INTEGRITY_MISMATCH(7), NETWORK_IO(8), TIMEOUT(9), QUEUE_FULL(10), PROTOCOL_ERROR(11),
    SOURCE_CHANGED(12), INTERNAL_ERROR(13), CANCELED_BY_HOST(14), TLS_CONFIG_INVALID(15),
    TLS_HANDSHAKE_FAILED(16), PEER_CERT_UNTRUSTED(17), PEER_CERT_EXPIRED(18),
    PEER_IDENTITY_MISMATCH(19), CLIENT_CERT_REQUIRED(20),
}

/**
 * TLS material read when a lane starts; private-key paths and tokens must never be logged.
 *
 * @property mode direct, server-authenticated TLS, or mutual TLS.
 * @property credentialGeneration app-host credential generation used for diagnostics.
 * @property credentialId non-secret credential identifier.
 * @property caCertificateFile trusted CA bundle path.
 * @property identityCertificateFile local certificate-chain path.
 * @property privateKeyFile local private-key path.
 */
data class FileLaneSecurityConfig(
    val mode: FileLaneSecurityMode = FileLaneSecurityMode.DIRECT,
    val credentialGeneration: Long = 0,
    val credentialId: String = "",
    val caCertificateFile: String = "",
    val identityCertificateFile: String = "",
    val privateKeyFile: String = "",
)

/**
 * Bounded lane configuration. Zero tuning values select conservative native defaults.
 * Normal callers usually set only [flags], receiver bind settings, and [security].
 *
 * @property flags sender/receiver capabilities enabled on this lane.
 * @property bindHost receiver listener address; ignored by sender-only lanes.
 * @property bindPort receiver listener port, or zero for an ephemeral port.
 * @property queueCapacity maximum queued/prepared work, or zero for the core-runtime default.
 * @property maxFileSize maximum accepted file size in bytes, or zero for the default.
 * @property ioTimeoutMs bounded transport I/O timeout in milliseconds, or zero for the default.
 * @property checkpointBytes committed bytes between durable resume checkpoints.
 * @property progressBytes transferred bytes between retained progress updates.
 * @property progressIntervalMs maximum milliseconds between retained progress updates.
 * @property senderWorkerCount bounded sender workers, or zero for the default.
 * @property receiverWorkerCount bounded receiver workers, or zero for the default.
 * @property security optional TLS configuration copied while the lane opens.
 */
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

/**
 * Service B's authorization for one receive. The destination remains local to
 * Service B; only the ID, token, size, digest, host, and port cross its trusted
 * control plane to Service A.
 */
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
/**
 * Service A's source plus the grant returned by Service B after
 * [FileLane.prepareReceive] succeeds.
 */
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
/** SHA-256 plus exact byte count returned by [FileLane.sha256]. */
data class FileDigest(val sha256: ByteArray, val size: Long)
/**
 * Immutable progress projection. Byte counts are unsigned semantic values stored in [Long].
 * `*MonoNs` fields use a process-local monotonic clock and are not wall-clock timestamps.
 * [progressMilli] ranges from 0 to 100000 (100.000%); [updateSequence] advances whenever retained state changes.
 */
data class FileTransferSnapshot(
    val direction: FileTransferDirection, val state: FileTransferState, val result: FileTransferResult,
    val expectedSize: Long, val transferredBytes: Long, val committedOffset: Long, val progressMilli: Int,
    val cancelRequested: Boolean, val updateSequence: Long, val submittedMonoNs: Long, val startedMonoNs: Long,
    val updatedMonoNs: Long, val terminalMonoNs: Long, val detail: String,
) { val terminal: Boolean get() = state.terminal; val completed: Boolean get() = state == FileTransferState.COMPLETED && result == FileTransferResult.OK }
/**
 * Bounded queue, active-work, terminal-count, and completed-byte counters.
 * All values are copied at observation time; byte fields are cumulative since lane start.
 */
data class FileLaneStats(
    val queueCapacity: Long, val queuedSends: Long, val preparedReceives: Long, val activeSends: Long,
    val activeReceives: Long, val retainedRecords: Long, val submittedSends: Long, val preparedReceiveCount: Long,
    val completedSends: Long, val completedReceives: Long, val failedSends: Long, val failedReceives: Long,
    val canceledTransfers: Long, val completedSendBytes: Long, val completedReceiveBytes: Long,
)

/** Native status failure for a named file-lane operation. */
class FileLaneException(operation: String, val status: Int) : RuntimeException("$operation failed: native status $status")

/**
 * Independent bulk-transfer lane backed by the CoAkka core-runtime.
 *
 * Service B keeps a receiver lane alive, authorizes and prepares the receive,
 * then returns the transfer ID, transfer-scoped token, size, digest, host, and bound
 * port to Service A through an authenticated control API. Service A submits
 * the matching send from its own lane. Each service continues [waitTransfer]
 * from the last snapshot sequence until its own side reports `COMPLETED + OK`,
 * then calls [forget]. [close] stops the lane, wakes blocked waits, drains
 * in-flight native calls, and destroys it.
 *
 * This resource is not owned by `RuntimeHost` and file bytes must not be put in
 * a runtime `Envelope`. See the repository `FILE_LANE.md` for a full example.
 */
class FileLane private constructor(
    private val lib: CoakkaV2Library,
    private var handle: Pointer?,
    private val ownerAware: Boolean,
) : AutoCloseable {
    private val lifecycle = ReentrantLock()
    private val drained = lifecycle.newCondition()
    private var closing = false
    private var activeCalls = 0

    /** Receiver port selected at start; valid after opening a receiver-capable lane. */
    val boundPort: Int get() = nativeCall { lane -> ShortByReference().let { out -> requireOk(lib.coakka_v2_file_lane_get_bound_port(lane, out), "file_lane_get_bound_port"); out.value.toInt() and 0xffff } }

    /**
     * Service B authorizes exactly one local destination and expected content
     * identity before it returns a transfer grant to Service A.
     */
    fun prepareReceive(spec: FileReceiveSpec) = nativeCall { lane ->
        requireDigest(spec.expectedSha256); require(spec.expectedSize >= 0)
        val native = NativeFileReceiveSpec().apply { struct_size = size().toLong(); transfer_id = spec.transferId; authorization_token = spec.authorizationToken; destination_path = spec.destinationPath.toString(); expected_size = spec.expectedSize; expected_sha256 = spec.expectedSha256.copyOf(); write() }
        requireOk(lib.coakka_v2_file_lane_prepare_receive(lane, native), "file_lane_prepare_receive")
    }

    /**
     * Prepares one receive and returns the exact owner endpoint plus its transfer-scoped capability.
     * The returned value owns all projected data and does not borrow native memory.
     */
    fun prepareReceiveGrant(spec: FileReceiveSpec): FileReceiveGrant {
        check(ownerAware) { "prepareReceiveGrant requires a lane opened with FileLane.openOwned" }
        return nativeCall { lane ->
            requireDigest(spec.expectedSha256)
            require(spec.expectedSize >= 0)
            val nativeSpec = NativeFileReceiveSpec().apply {
                struct_size = size().toLong()
                transfer_id = spec.transferId
                authorization_token = spec.authorizationToken
                destination_path = spec.destinationPath.toString()
                expected_size = spec.expectedSize
                expected_sha256 = spec.expectedSha256.copyOf()
                write()
            }
            val grant = NativeFileReceiveGrant().apply {
                struct_size = size().toLong()
                write()
            }
            requireOk(
                lib.coakka_v2_file_lane_prepare_receive_grant(lane, nativeSpec, grant),
                "file_lane_prepare_receive_grant",
            )
            grant.read()
            FileReceiveGrant(
                owner = ownerEndpoint(grant.owner),
                transferId = nativeFixedText(grant.transfer_id),
                authorizationToken = nativeFixedText(grant.authorization_token),
                expectedSize = grant.expected_size,
                expectedSha256 = grant.expected_sha256,
            )
        }
    }

    /**
     * Service A queues a send using the ID, token, endpoint, size, and digest
     * returned by Service B after the matching receive was prepared.
     */
    fun submitSend(spec: FileSendSpec) = nativeCall { lane ->
        requireDigest(spec.expectedSha256); require(spec.expectedSize >= 0); require(spec.remotePort in 1..65535); require(spec.timeoutMs >= 0)
        val native = NativeFileSendSpec().apply { struct_size = size().toLong(); transfer_id = spec.transferId; authorization_token = spec.authorizationToken; remote_host = spec.remoteHost; remote_port = spec.remotePort.toShort(); source_path = spec.sourcePath.toString(); expected_size = spec.expectedSize; expected_sha256 = spec.expectedSha256.copyOf(); timeout_ms = spec.timeoutMs; write() }
        requireOk(lib.coakka_v2_file_lane_submit_send(lane, native), "file_lane_submit_send")
    }

    /**
     * Returns the current copied snapshot without waiting for another update.
     *
     * @param transferId application correlation ID used by both peers.
     * @param direction sender or receiver record to observe.
     */
    fun transfer(transferId: String, direction: FileTransferDirection): FileTransferSnapshot = nativeCall { lane -> snapshot(NativeFileTransferSnapshot().apply { struct_size = size().toLong(); write(); requireOk(lib.coakka_v2_file_lane_get_transfer(lane, transferId, direction.nativeValue, this), "file_lane_get_transfer"); read() }) }
    /**
     * Blocks until the sequence advances, the timeout expires, or the lane
     * stops. Pass the last snapshot's sequence to wait without busy-polling.
     *
     * @param transferId application correlation ID used by both peers.
     * @param direction sender or receiver record to observe.
     * @param afterUpdateSequence last sequence already handled; zero requests current state.
     * @param timeoutMs bounded wait duration in milliseconds; zero performs a non-blocking check.
     */
    fun waitTransfer(transferId: String, direction: FileTransferDirection, afterUpdateSequence: Long = 0, timeoutMs: Int = 30_000): FileTransferSnapshot = nativeCall { lane ->
        require(afterUpdateSequence >= 0 && timeoutMs >= 0)
        snapshot(NativeFileTransferSnapshot().apply { struct_size = size().toLong(); write(); requireOk(lib.coakka_v2_file_lane_wait_transfer(lane, transferId, direction.nativeValue, afterUpdateSequence, timeoutMs, this), "file_lane_wait_transfer"); read() })
    }
    /**
     * Requests cooperative cancellation; observe the terminal snapshot before forgetting it.
     *
     * @param transferId application correlation ID used by both peers.
     * @param direction local retained record to cancel.
     */
    fun cancel(transferId: String, direction: FileTransferDirection) = nativeCall { requireOk(lib.coakka_v2_file_lane_cancel_transfer(it, transferId, direction.nativeValue), "file_lane_cancel_transfer") }
    /**
     * Releases one retained terminal record after the application records its outcome.
     *
     * @param transferId application correlation ID used by both peers.
     * @param direction local terminal record to release.
     */
    fun forget(transferId: String, direction: FileTransferDirection) = nativeCall { requireOk(lib.coakka_v2_file_lane_forget_transfer(it, transferId, direction.nativeValue), "file_lane_forget_transfer") }
    /** Returns a copied lane-level observability snapshot. */
    fun stats(): FileLaneStats = nativeCall { lane -> NativeFileLaneStats().apply { struct_size = size().toLong(); write(); requireOk(lib.coakka_v2_file_lane_get_stats(lane, this), "file_lane_get_stats"); read() }.let { FileLaneStats(it.queue_capacity, it.queued_sends, it.prepared_receives, it.active_sends, it.active_receives, it.retained_records, it.submitted_sends, it.prepared_receive_count, it.completed_sends, it.completed_receives, it.failed_sends, it.failed_receives, it.canceled_transfers, it.completed_send_bytes, it.completed_receive_bytes) } }

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
        val stopStatus = lib.coakka_v2_file_lane_stop(lane)
        lifecycle.withLock { while (activeCalls != 0) drained.awaitUninterruptibly(); lib.coakka_v2_file_lane_destroy(lane); handle = null; drained.signalAll() }
        if (stopStatus != CoakkaStatus.OK && stopStatus != CoakkaStatus.ERR_CLOSED) throw FileLaneException("file_lane_stop", stopStatus)
    }

    private inline fun <T> nativeCall(block: (Pointer) -> T): T {
        val lane = lifecycle.withLock { check(!closing && handle != null) { "file lane is closed" }; activeCalls += 1; handle!! }
        try { return block(lane) } finally { lifecycle.withLock { activeCalls -= 1; if (activeCalls == 0) drained.signalAll() } }
    }

    companion object {
        private var processLibraryPath: String? = null
        private var processLibrary: CoakkaV2Library? = null
        private val fileLaneSymbols = listOf(
            "coakka_v2_file_lane_create_ex", "coakka_v2_file_lane_destroy", "coakka_v2_file_lane_start",
            "coakka_v2_file_lane_stop", "coakka_v2_file_lane_get_bound_port", "coakka_v2_file_lane_prepare_receive",
            "coakka_v2_file_lane_submit_send", "coakka_v2_file_lane_get_transfer", "coakka_v2_file_lane_wait_transfer",
            "coakka_v2_file_lane_cancel_transfer", "coakka_v2_file_lane_forget_transfer",
            "coakka_v2_file_lane_get_stats", "coakka_v2_file_sha256_path",
        )
        private val ownerGrantSymbols = listOf(
            "coakka_v2_file_lane_create_owned_ex",
            "coakka_v2_file_lane_prepare_receive_grant",
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

        private fun requireCompleteFileLane(path: String) {
            val native = com.sun.jna.NativeLibrary.getInstance(path)
            val missing = fileLaneSymbols.firstOrNull { symbol ->
                try { native.getFunction(symbol); false } catch (_: UnsatisfiedLinkError) { true }
            }
            if (missing != null) {
                throw UnsupportedOperationException("native runtime does not export the complete file-lane ABI; missing $missing")
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
            val native = com.sun.jna.NativeLibrary.getInstance(path)
            val missing = ownerGrantSymbols.firstOrNull { symbol ->
                try { native.getFunction(symbol); false } catch (_: UnsatisfiedLinkError) { true }
            }
            if (missing != null) {
                throw UnsupportedOperationException("native runtime advertises lane_owner_grants but is missing $missing")
            }
        }

        /**
         * Opens and starts a lane, failing if the selected core-runtime lacks file transfer.
         *
         * @param config bounded capabilities, workers, progress, and security settings.
         * @param runtimeLibPath explicit core-runtime path, or `null` for connector resolution.
         */
        @JvmStatic fun open(config: FileLaneConfig = FileLaneConfig(), runtimeLibPath: String? = null): FileLane {
            config.requireValid(); val path = NativeLibraryResolver.resolve(runtimeLibPath); val lib = library(path); requireCompleteFileLane(path); val security = config.security?.let { value -> NativeFileLaneSecurityConfig().apply { struct_size = size().toLong(); mode = value.mode.nativeValue; credential_generation = value.credentialGeneration; credential_id = value.credentialId.ifEmpty { null }; ca_certificate_file = value.caCertificateFile.ifEmpty { null }; identity_certificate_file = value.identityCertificateFile.ifEmpty { null }; private_key_file = value.privateKeyFile.ifEmpty { null }; write() } }
            val native = NativeFileLaneConfig().apply { struct_size = size().toLong(); flags = config.flags; bind_host = config.bindHost; bind_port = config.bindPort.toShort(); queue_capacity = config.queueCapacity; max_file_size = config.maxFileSize; io_timeout_ms = config.ioTimeoutMs; checkpoint_bytes = config.checkpointBytes; progress_bytes = config.progressBytes; progress_interval_ms = config.progressIntervalMs; sender_worker_count = config.senderWorkerCount; receiver_worker_count = config.receiverWorkerCount; this.security = security?.pointer; write() }
            val out = PointerByReference(); requireOk(lib.coakka_v2_file_lane_create_ex(native, out), "file_lane_create"); val lane = out.value ?: throw FileLaneException("file_lane_create", CoakkaStatus.ERR_NOMEM)
            val status = lib.coakka_v2_file_lane_start(lane); if (status != CoakkaStatus.OK) { lib.coakka_v2_file_lane_destroy(lane); throw FileLaneException("file_lane_start", status) }
            return FileLane(lib, lane, ownerAware = false)
        }

        /**
         * Opens an owner-aware receiver lane that can issue replica-pinned receive grants.
         * `owner.advertisedHost` must address this exact process or pod, never a replica-balancing Service.
         */
        @JvmStatic
        fun openOwned(
            config: FileLaneConfig,
            owner: LaneOwnerConfig,
            runtimeLibPath: String? = null,
        ): FileLane {
            config.requireValid()
            owner.requireValid()
            require(config.flags and FileLaneFlags.RECEIVER != 0) { "owner-aware file lane must enable RECEIVER" }
            val path = NativeLibraryResolver.resolve(runtimeLibPath)
            val lib = library(path)
            requireCompleteFileLane(path)
            requireOwnerGrantSupport(path, lib)
            val security = nativeSecurity(config.security)
            val laneConfig = nativeConfig(config, security)
            val ownerConfig = NativeLaneOwnerConfig().apply {
                struct_size = size().toLong()
                owner_instance_id = owner.ownerInstanceId
                advertised_host = owner.advertisedHost
                write()
            }
            val owned = NativeFileLaneOwnedConfig().apply {
                struct_size = size().toLong()
                lane = laneConfig
                this.owner = ownerConfig
                write()
            }
            val out = PointerByReference()
            requireOk(lib.coakka_v2_file_lane_create_owned_ex(owned, out), "file_lane_create_owned")
            val lane = out.value ?: throw FileLaneException("file_lane_create_owned", CoakkaStatus.ERR_NOMEM)
            val status = lib.coakka_v2_file_lane_start(lane)
            if (status != CoakkaStatus.OK) {
                lib.coakka_v2_file_lane_destroy(lane)
                throw FileLaneException("file_lane_start", status)
            }
            return FileLane(lib, lane, ownerAware = true)
        }

        /**
         * Computes the exact source identity through the same core-runtime used by the lane.
         *
         * @param path readable regular file to hash.
         * @param runtimeLibPath explicit core-runtime path, or `null` for connector resolution.
         */
        @JvmStatic fun sha256(path: Path, runtimeLibPath: String? = null): FileDigest { val runtime = NativeLibraryResolver.resolve(runtimeLibPath); val lib = library(runtime); requireCompleteFileLane(runtime); val digest = ByteArray(FILE_LANE_SHA256_BYTES); val size = LongByReference(); requireOk(lib.coakka_v2_file_sha256_path(path.toString(), digest, size), "file_sha256_path"); return FileDigest(digest, size.value) }
        private fun nativeSecurity(value: FileLaneSecurityConfig?): NativeFileLaneSecurityConfig? = value?.let {
            NativeFileLaneSecurityConfig().apply {
                struct_size = size().toLong(); mode = it.mode.nativeValue; credential_generation = it.credentialGeneration
                credential_id = it.credentialId.ifEmpty { null }; ca_certificate_file = it.caCertificateFile.ifEmpty { null }
                identity_certificate_file = it.identityCertificateFile.ifEmpty { null }; private_key_file = it.privateKeyFile.ifEmpty { null }; write()
            }
        }
        private fun nativeConfig(config: FileLaneConfig, security: NativeFileLaneSecurityConfig?) = NativeFileLaneConfig().apply {
            struct_size = size().toLong(); flags = config.flags; bind_host = config.bindHost; bind_port = config.bindPort.toShort()
            queue_capacity = config.queueCapacity; max_file_size = config.maxFileSize; io_timeout_ms = config.ioTimeoutMs
            checkpoint_bytes = config.checkpointBytes; progress_bytes = config.progressBytes
            progress_interval_ms = config.progressIntervalMs; sender_worker_count = config.senderWorkerCount
            receiver_worker_count = config.receiverWorkerCount; this.security = security?.pointer; write()
        }
        private fun ownerEndpoint(value: NativeLaneOwnerEndpoint): LaneOwnerEndpoint {
            value.read()
            return LaneOwnerEndpoint(
                ownerInstanceId = nativeFixedText(value.owner_instance_id),
                advertisedHost = nativeFixedText(value.advertised_host),
                port = value.port.toInt() and 0xffff,
            )
        }
        private fun snapshot(value: NativeFileTransferSnapshot) = FileTransferSnapshot(FileTransferDirection.entries.first { it.nativeValue == value.direction }, FileTransferState.entries.first { it.nativeValue == value.state }, FileTransferResult.entries.first { it.nativeValue == value.result }, value.expected_size, value.transferred_bytes, value.committed_offset, value.progress_milli, value.cancel_requested != 0, value.update_sequence, value.submitted_mono_ns, value.started_mono_ns, value.updated_mono_ns, value.terminal_mono_ns, value.detail.takeWhile { it.toInt() != 0 }.toByteArray().toString(Charsets.UTF_8))
        private fun requireDigest(value: ByteArray) = require(value.size == FILE_LANE_SHA256_BYTES) { "expectedSha256 must contain exactly $FILE_LANE_SHA256_BYTES bytes" }
        private fun requireOk(status: Int, operation: String) { if (status != CoakkaStatus.OK) throw FileLaneException(operation, status) }
    }
}
