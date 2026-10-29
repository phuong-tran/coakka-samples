package coakka.v2.android

import java.io.File

/** Stable identity and directly reachable address advertised by one lane owner. */
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

/** Copied endpoint for the exact Android process or replica that owns prepared state. */
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

/** Receiver-issued capability scoped to one prepared transfer on one exact owner. */
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
        requireTransferIdentity(transferId, authorizationToken)
        require(expectedSize > 0) { "expectedSize must be positive" }
        require(digest.size == 32) { "expectedSha256 must contain exactly 32 bytes" }
    }

    /** Builds a sender job without allowing owner endpoint or transfer identity drift. */
    fun toSendSpec(sourceFile: File, timeoutMs: Int = 0): FileSendSpec = FileSendSpec(
        transferId = transferId,
        authorizationToken = authorizationToken,
        remoteHost = owner.advertisedHost,
        remotePort = owner.port,
        sourceFile = sourceFile,
        expectedSize = expectedSize,
        expectedSha256 = digest,
        timeoutMs = timeoutMs,
    )

    override fun toString(): String =
        "FileReceiveGrant(owner=$owner, transferId=$transferId, " +
            "authorizationToken=<redacted>, expectedSize=$expectedSize, " +
            "expectedSha256=<${digest.size} bytes>)"
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
        requireSessionIdentity(sessionId, authorizationToken)
        require(formatId > 0L) { "formatId must be positive" }
        require(maxFrameBytes > 0) { "maxFrameBytes must be positive" }
    }

    /** Builds a subscriber job pinned to the publisher that issued this grant. */
    fun toSubscribeSpec(
        initialWindowBytes: Int,
        consumer: AndroidStreamConsumer,
        timeoutMs: Int = 0,
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
        "StreamPublishGrant(owner=$owner, sessionId=$sessionId, " +
            "authorizationToken=<redacted>, formatId=$formatId, " +
            "maxFrameBytes=$maxFrameBytes)"
}

internal fun requireTransferIdentity(transferId: String, authorizationToken: String) {
    requireUtf8Bytes(transferId, 64, "transferId")
    requireUtf8Bytes(authorizationToken, 128, "authorizationToken")
}

internal fun requireSessionIdentity(sessionId: String, authorizationToken: String) {
    requireUtf8Bytes(sessionId, 64, "sessionId")
    requireUtf8Bytes(authorizationToken, 128, "authorizationToken")
}

internal fun requireUtf8Bytes(value: String, maxBytes: Int, field: String) {
    val size = value.toByteArray(Charsets.UTF_8).size
    require(size in 1..maxBytes) { "$field must contain 1..$maxBytes UTF-8 bytes" }
}

private fun requireVisibleAscii(value: String, maxBytes: Int, field: String) {
    require(value.isNotEmpty() && value.length <= maxBytes && value.all { it.code in 0x21..0x7e }) {
        "$field must contain 1..$maxBytes visible ASCII bytes"
    }
}
