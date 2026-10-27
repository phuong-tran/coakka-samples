package coakka.v2.connector

import java.nio.file.Path

/** Stable identity and directly reachable address advertised by one lane-owning process or pod. */
data class LaneOwnerConfig(
    val ownerInstanceId: String,
    val advertisedHost: String,
) {
    internal fun requireValid(): LaneOwnerConfig {
        requireVisibleAscii(ownerInstanceId, 127, "ownerInstanceId")
        requireVisibleAscii(advertisedHost, 255, "advertisedHost")
        return this
    }
}

/** Copied endpoint for the exact process or pod that owns prepared lane state. */
data class LaneOwnerEndpoint(
    val ownerInstanceId: String,
    val advertisedHost: String,
    val port: Int,
) {
    init {
        requireVisibleAscii(ownerInstanceId, 127, "ownerInstanceId")
        requireVisibleAscii(advertisedHost, 255, "advertisedHost")
        require(port in 1..65535) { "port must be in [1, 65535]" }
    }
}

/**
 * Receiver-issued capability scoped to one prepared file transfer on one exact owner.
 * It may be reused only for that transfer's bounded resume and idempotent completed-status handling.
 */
class FileReceiveGrant(
    val owner: LaneOwnerEndpoint,
    val transferId: String,
    val authorizationToken: String,
    val expectedSize: Long,
    expectedSha256: ByteArray,
) {
    private val digest = expectedSha256.copyOf()
    val expectedSha256: ByteArray get() = digest.copyOf()

    init {
        require(transferId.isNotEmpty()) { "transferId must not be empty" }
        require(authorizationToken.isNotEmpty()) { "authorizationToken must not be empty" }
        require(expectedSize >= 0) { "expectedSize must be non-negative" }
        require(digest.size == 32) { "expectedSha256 must contain exactly 32 bytes" }
    }

    /** Builds the sender job without allowing the owner endpoint or transfer identity to drift. */
    fun toSendSpec(sourcePath: Path, timeoutMs: Int = 0): FileSendSpec = FileSendSpec(
        transferId = transferId,
        authorizationToken = authorizationToken,
        remoteHost = owner.advertisedHost,
        remotePort = owner.port,
        sourcePath = sourcePath,
        expectedSize = expectedSize,
        expectedSha256 = digest.copyOf(),
        timeoutMs = timeoutMs,
    )

    override fun toString(): String =
        "FileReceiveGrant(owner=$owner, transferId=$transferId, authorizationToken=<redacted>, " +
            "expectedSize=$expectedSize, expectedSha256=<${digest.size} bytes>)"
}

/** Publisher-issued single-admission capability for one exact stream owner. */
class StreamPublishGrant(
    val owner: LaneOwnerEndpoint,
    val sessionId: String,
    val authorizationToken: String,
    val formatId: Long,
    val maxFrameBytes: Int,
) {
    init {
        require(sessionId.isNotEmpty()) { "sessionId must not be empty" }
        require(authorizationToken.isNotEmpty()) { "authorizationToken must not be empty" }
        require(formatId != 0L) { "formatId must be non-zero" }
        require(maxFrameBytes > 0) { "maxFrameBytes must be positive" }
    }
    /** Builds the subscriber job from the immutable owner-pinned grant. */
    fun toSubscribeSpec(
        initialWindowBytes: Int,
        timeoutMs: Int = 0,
        consumer: StreamConsumer,
    ): StreamSubscribeSpec = StreamSubscribeSpec(
        sessionId = sessionId,
        authorizationToken = authorizationToken,
        remoteHost = owner.advertisedHost,
        remotePort = owner.port,
        formatId = formatId,
        maxFrameBytes = maxFrameBytes,
        initialWindowBytes = initialWindowBytes,
        timeoutMs = timeoutMs,
        consumer = consumer,
    )

    override fun toString(): String =
        "StreamPublishGrant(owner=$owner, sessionId=$sessionId, authorizationToken=<redacted>, " +
            "formatId=$formatId, maxFrameBytes=$maxFrameBytes)"
}

private fun requireVisibleAscii(value: String, maxBytes: Int, field: String) {
    require(value.isNotEmpty() && value.length <= maxBytes && value.all { it.code in 0x21..0x7e }) {
        "$field must contain 1..$maxBytes visible ASCII bytes"
    }
}
