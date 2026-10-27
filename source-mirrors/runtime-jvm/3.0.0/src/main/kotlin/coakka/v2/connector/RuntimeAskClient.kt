package coakka.v2.connector

import coakka.v2.connector.protocol.ConnectorBusinessStatus
import coakka.v2.connector.protocol.ConnectorDeliveryHint
import coakka.v2.connector.protocol.ConnectorEnvelope
import coakka.v2.connector.protocol.ConnectorMessageKind
import coakka.v2.connector.protocol.EnvelopeMapper
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A bounded wait expired while Core retained no result for this caller. */
class RuntimeAskTimeoutException(timeoutMs: Int) :
    CoakkaException("runtime ask did not complete within ${timeoutMs}ms")

/** Outcome counters projected from completed Core ask calls. */
data class RuntimeAskClientStats(
    val pendingRequests: Int,
    val matchedResponses: Long,
    val matchedDeadletters: Long,
    val timedOutRequests: Long,
)

/**
 * Ask-only host surface whose submission, matching, timeout and cancellation
 * mechanics are owned by Core. It never reads descriptors or probes the OS.
 *
 * [askBlocking] is the Java/thread-oriented surface. [ask] moves that bounded
 * native wait onto Kotlin's I/O dispatcher without adding another matching map.
 */
class RuntimeAskClient private constructor(
    private val handle: RuntimeHandle,
) : AutoCloseable {
    private val nextMessageId = AtomicLong(1)
    private val payloadEncoding = PayloadEncodingCache()
    private val pendingRequests = AtomicInteger(0)
    private val matchedResponses = AtomicLong(0)
    private val matchedDeadletters = AtomicLong(0)
    private val timedOutRequests = AtomicLong(0)
    @Volatile private var closed = false

    suspend fun ask(
        source: String,
        target: String,
        payloadUtf8: String,
        timeoutMs: Int = 1_000,
        operation: String = "ask",
        deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
    ): ConnectorEnvelope = withContext(Dispatchers.IO) {
        askBlocking(source, target, payloadUtf8, timeoutMs, operation, deliveryHint)
    }

    /**
     * Submits one Core request deadline and uses a separately bounded caller wait.
     * These are distinct Core contracts; the connector forwards both without
     * manufacturing a deadletter when only the caller wait expires.
     */
    suspend fun ask(
        source: String,
        target: String,
        payloadUtf8: String,
        requestTimeoutMs: Int,
        waitTimeoutMs: Int,
        operation: String = "ask",
        deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
    ): ConnectorEnvelope = withContext(Dispatchers.IO) {
        askBlocking(
            source,
            target,
            payloadUtf8,
            requestTimeoutMs,
            waitTimeoutMs,
            operation,
            deliveryHint,
        )
    }

    @JvmOverloads
    fun askBlocking(
        source: String,
        target: String,
        payloadUtf8: String,
        timeoutMs: Int = 1_000,
        operation: String = "ask",
        deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
    ): ConnectorEnvelope = askBlockingBounded(
        source,
        target,
        payloadUtf8,
        timeoutMs,
        timeoutMs,
        operation,
        deliveryHint,
    )

    /**
     * Bytes-first request/reply through Core's raw envelope helpers. Core builds
     * and validates the wire request and extracts the response payload; JVM
     * receives no schema object or effective-policy responsibility.
     */
    @JvmOverloads
    fun askPayloadBlocking(
        source: String,
        target: String,
        payload: ByteArray,
        timeoutMs: Int = 1_000,
        deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
    ): ByteArray = askPayloadBlockingBounded(
        source,
        target,
        payload,
        timeoutMs,
        timeoutMs,
        deliveryHint,
    )

    @JvmOverloads
    fun askPayloadBlocking(
        source: String,
        target: String,
        payload: ByteArray,
        requestTimeoutMs: Int,
        waitTimeoutMs: Int,
        deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
    ): ByteArray = askPayloadBlockingBounded(
        source,
        target,
        payload,
        requestTimeoutMs,
        waitTimeoutMs,
        deliveryHint,
    )

    @JvmOverloads
    fun askBlocking(
        source: String,
        target: String,
        payloadUtf8: String,
        requestTimeoutMs: Int,
        waitTimeoutMs: Int,
        operation: String = "ask",
        deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
    ): ConnectorEnvelope = askBlockingBounded(
        source,
        target,
        payloadUtf8,
        requestTimeoutMs,
        waitTimeoutMs,
        operation,
        deliveryHint,
    )

    private fun askBlockingBounded(
        source: String,
        target: String,
        payloadUtf8: String,
        requestTimeoutMs: Int,
        waitTimeoutMs: Int,
        operation: String,
        deliveryHint: ConnectorDeliveryHint,
    ): ConnectorEnvelope {
        require(requestTimeoutMs in 1..60_000) { "requestTimeoutMs must be in 1..60000" }
        require(waitTimeoutMs in 1..60_000) { "waitTimeoutMs must be in 1..60000" }
        check(!closed) { "runtime ask client already closed" }
        val messageId = "$source-${nextMessageId.getAndIncrement()}"
        val request = ConnectorEnvelope(
            messageId = messageId,
            correlationId = messageId,
            source = source,
            target = target,
            replyTo = "$source/replies",
            kind = ConnectorMessageKind.REQUEST,
            oneWay = false,
            timeoutMs = requestTimeoutMs,
            payload = payloadUtf8.toByteArray(),
            headers = mapOf("method" to "POST", "operation" to operation),
            status = ConnectorBusinessStatus.OK,
            deliveryHint = deliveryHint,
        )
        pendingRequests.incrementAndGet()
        try {
            val result = handle.askEnvelope(
                EnvelopeMapper.encode(request),
                waitTimeoutMs,
            )
            return when (val status = result.value(0).toInt()) {
                CoakkaStatus.OK -> when (result.value(1).toInt()) {
                    CORE_RESULT_RESPONSE -> {
                        matchedResponses.incrementAndGet()
                        EnvelopeMapper.decode(result.payload(0))
                    }
                    CORE_RESULT_DEADLETTER -> {
                        matchedDeadletters.incrementAndGet()
                        throw DeadletterException(
                            EnvelopeMapper.decodeDeadletter(result.payload(0)),
                        )
                    }
                    else -> error("Core returned an unknown ask result kind")
                }
                CoakkaStatus.ERR_WOULD_BLOCK -> {
                    timedOutRequests.incrementAndGet()
                    throw RuntimeAskTimeoutException(waitTimeoutMs)
                }
                else -> {
                    requireStatus(status, "ask_ticket_await")
                    error("unreachable ask status")
                }
            }
        } finally {
            pendingRequests.decrementAndGet()
        }
    }

    private fun askPayloadBlockingBounded(
        source: String,
        target: String,
        payload: ByteArray,
        requestTimeoutMs: Int,
        waitTimeoutMs: Int,
        deliveryHint: ConnectorDeliveryHint,
    ): ByteArray {
        require(requestTimeoutMs in 1..60_000) { "requestTimeoutMs must be in 1..60000" }
        require(waitTimeoutMs in 1..60_000) { "waitTimeoutMs must be in 1..60000" }
        check(!closed) { "runtime ask client already closed" }
        val encodedSource = payloadEncoding.source(source)
        val encodedTarget = payloadEncoding.target(target)
        val messageId = payloadMessageId(encodedSource.source, nextMessageId.getAndIncrement())
        pendingRequests.incrementAndGet()
        try {
            val result = handle.askPayload(
                messageId = messageId,
                source = encodedSource.source,
                target = encodedTarget,
                replyTo = encodedSource.replyTo,
                payload = payload,
                requestTimeoutMs = requestTimeoutMs,
                waitTimeoutMs = waitTimeoutMs,
                deliveryHint = deliveryHint.value,
            )
            return when (val status = result.status) {
                CoakkaStatus.OK -> when (result.kind) {
                    CORE_RESULT_RESPONSE -> {
                        matchedResponses.incrementAndGet()
                        checkNotNull(result.payload) { "Core omitted response payload" }
                    }
                    CORE_RESULT_DEADLETTER -> {
                        matchedDeadletters.incrementAndGet()
                        throw DeadletterException(
                            EnvelopeMapper.decodeDeadletter(checkNotNull(result.payload)),
                        )
                    }
                    else -> error("Core returned an unknown ask result kind")
                }
                CoakkaStatus.ERR_WOULD_BLOCK -> {
                    timedOutRequests.incrementAndGet()
                    throw RuntimeAskTimeoutException(waitTimeoutMs)
                }
                else -> {
                    requireStatus(status, "ask_payload")
                    error("unreachable ask status")
                }
            }
        } finally {
            pendingRequests.decrementAndGet()
        }
    }

    private fun payloadMessageId(source: ByteArray, sequence: Long): ByteArray {
        check(sequence > 0) { "payload message id sequence exhausted" }
        var remaining = sequence
        var digits = 1
        while (remaining >= 10) {
            remaining /= 10
            digits++
        }
        val result = ByteArray(source.size + 1 + digits)
        source.copyInto(result)
        result[source.size] = '-'.code.toByte()
        remaining = sequence
        var cursor = result.lastIndex
        while (cursor > source.size) {
            result[cursor--] = ('0'.code + (remaining % 10).toInt()).toByte()
            remaining /= 10
        }
        return result
    }

    fun snapshotStats(): RuntimeAskClientStats = RuntimeAskClientStats(
        pendingRequests = pendingRequests.get(),
        matchedResponses = matchedResponses.get(),
        matchedDeadletters = matchedDeadletters.get(),
        timedOutRequests = timedOutRequests.get(),
    )

    /** Returns Core's accepted startup facts without reconstructing defaults. */
    fun startupConfig(): RuntimeStartupConfigSnapshot = handle.startupConfig()

    /** Returns Core's accepted delivery composition without selecting a mode. */
    fun deliveryConfig(): RuntimeDeliveryConfigSnapshot = handle.deliveryConfig()

    override fun close() {
        if (closed) return
        closed = true
        payloadEncoding.retire()
        handle.close()
    }

    companion object {
        private const val CORE_RESULT_RESPONSE = 1
        private const val CORE_RESULT_DEADLETTER = 2

        /** Creates one ask-only runtime and transfers terminal-lane ownership to Core. */
        @JvmStatic
        @JvmOverloads
        fun start(runtimeLibPath: String? = null, startSpec: RuntimeStartSpec): RuntimeAskClient {
            val handle = RuntimeHandle.open(runtimeLibPath, startSpec)
            try {
                handle.start()
                handle.openCoreAskClient()
                return RuntimeAskClient(handle)
            } catch (failure: Throwable) {
                handle.close()
                throw failure
            }
        }
    }
}
