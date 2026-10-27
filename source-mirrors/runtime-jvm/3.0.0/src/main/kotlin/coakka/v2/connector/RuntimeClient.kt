package coakka.v2.connector

import coakka.v2.connector.protocol.ConnectorBusinessStatus
import coakka.v2.connector.protocol.ConnectorDeliveryHint
import coakka.v2.connector.protocol.ConnectorEnvelope
import coakka.v2.connector.protocol.ConnectorMessageKind
import coakka.v2.connector.protocol.ConnectorPayloadIdentity
import coakka.v2.connector.protocol.EnvelopeMapper
import coakka.v2.connector.protocol.ObservedDeadletter
import coakka.v2.connector.protocol.RequestTerminalEvent
import coakka.v2.connector.protocol.SubmittedRequest
import coakka.v2.connector.internal.NativeRecord
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Connector-side counters for request/reply bookkeeping.
 *
 * @property pendingRequests Number of unresolved asks waiting for reply or deadletter.
 * @property deliveredRequests Number of locally delivered requests observed on the main channel.
 * @property matchedResponses Number of responses matched to pending asks.
 * @property matchedDeadletters Number of deadletters matched to pending asks.
 * @property lateResponses Number of responses that arrived after pending state was gone.
 * @property unhandledDeadletters Number of deadletters that did not match a pending ask.
 * @property terminalEventDropCount Number of terminal events dropped for slow flow subscribers.
 * @property deadletterObservationDropCount Number of deadletter observations dropped for slow subscribers.
 */
data class RuntimeClientStats(
    val pendingRequests: Int,
    val deliveredRequests: Long,
    val matchedResponses: Long,
    val matchedDeadletters: Long,
    val lateResponses: Long,
    val unhandledDeadletters: Long,
    val terminalEventDropCount: Long,
    val deadletterObservationDropCount: Long = 0,
)

/** Synchronous bytes-first handler suitable for Kotlin lambdas and Java SAM use. */
fun interface RuntimePayloadHandler {
    fun handle(payload: ByteArray): ByteArray?
}

/**
 * Host-facing request client layered on top of the native runtime.
 *
 * It owns the JVM handler registry and one delivered-request reader. Core owns
 * response/deadletter matching, terminal waits, timeout state and cancellation.
 *
 * Runtime still exposes one request/reply contract underneath.
 * This client can surface that contract either as suspend-and-wait [ask] or as submit-first [submitRequest].
 */
class RuntimeClient(
    private val handle: RuntimeHandle,
) : AutoCloseable {
    private data class TrackedRequest(
        val requestMessageId: String,
        val correlationId: String,
    )

    private sealed class LocalRequestHandler {
        class Envelope(
            val call: suspend (ConnectorEnvelope) -> ConnectorEnvelope?,
        ) : LocalRequestHandler()

        class Payload(
            val responseSource: ByteArray,
            val call: RuntimePayloadHandler,
        ) : LocalRequestHandler()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nextMessageId = AtomicLong(1)
    private val activeAskTickets = ConcurrentHashMap.newKeySet<Long>()
    private val requestHandlers = ConcurrentHashMap<String, LocalRequestHandler>()
    private val terminalEventSubscribers = ConcurrentHashMap<Long, Channel<RequestTerminalEvent>>()
    private val deadletterSubscribers = ConcurrentHashMap<Long, Channel<ObservedDeadletter>>()
    private val deliveredRequests = AtomicLong(0)
    private val matchedResponses = AtomicLong(0)
    private val matchedDeadletters = AtomicLong(0)
    private val lateResponses = AtomicLong(0)
    private val unhandledDeadletters = AtomicLong(0)
    private val nextTerminalSubscriberId = AtomicLong(1)
    private val nextDeadletterSubscriberId = AtomicLong(1)
    private val terminalEventDropCount = AtomicLong(0)
    private val deadletterObservationDropCount = AtomicLong(0)
    private val deliveredRequestReader = NativeDeliveredRequestReader(handle)
    private var lastDispatchTarget: ByteArray? = null
    private var lastDispatchHandler: LocalRequestHandler? = null
    private val requestJob: Job
    private var closed = false

    init {
        handle.openCoreAskClient()
        requestJob = scope.launch {
            while (isActive) {
                val request = deliveredRequestReader.readOrNull(100) ?: continue
                deliveredRequests.incrementAndGet()
                when (val handler = handlerFor(request.target)) {
                    is LocalRequestHandler.Envelope ->
                        dispatchEnvelopeRequest(request, handler)
                    is LocalRequestHandler.Payload ->
                        dispatchPayloadRequest(request, handler)
                    null -> Unit
                }
            }
        }
    }

    /** Registers a local handler for requests delivered to [target]. */
    fun registerHandler(target: String, handler: suspend (ConnectorEnvelope) -> ConnectorEnvelope?) {
        check(requestHandlers.putIfAbsent(target, LocalRequestHandler.Envelope(handler)) == null) {
            "handler already registered for target=$target"
        }
    }

    /**
     * Registers a bytes-first handler for one delivered target. JVM owns only
     * target-to-handler dispatch and byte-array lifetime. Core validates the
     * retained request frame and builds the reply; the connector does not
     * reconstruct response identity, timeout, routing, or delivery defaults.
     * Returning null suppresses a reply, as on the envelope handler surface.
     * The handler runs on the sole delivered-request reader turn and must not
     * block; use [registerHandler] when suspension is required.
     */
    @JvmOverloads
    fun registerPayloadHandler(
        target: String,
        responseSource: String = target,
        handler: RuntimePayloadHandler,
    ) {
        require(target.isNotEmpty()) { "target must not be empty" }
        require(responseSource.isNotEmpty()) { "responseSource must not be empty" }
        val registration = LocalRequestHandler.Payload(responseSource.utf8(), handler)
        check(requestHandlers.putIfAbsent(target, registration) == null) {
            "handler already registered for target=$target"
        }
    }

    /**
     * Returns a hot per-subscriber flow of terminal outcomes for reply-capable requests submitted through this client.
     *
     * Delivered local `REQUEST` traffic stays on the handler lane and never appears here.
     */
    fun terminalEvents(bufferCapacity: Int = DEFAULT_TERMINAL_EVENT_BUFFER_CAPACITY): Flow<RequestTerminalEvent> {
        require(bufferCapacity > 0) { "terminalEvents requires bufferCapacity > 0" }
        val subscriberId = nextTerminalSubscriberId.getAndIncrement()
        val subscriberChannel = Channel<RequestTerminalEvent>(bufferCapacity)
        check(terminalEventSubscribers.putIfAbsent(subscriberId, subscriberChannel) == null) {
            "duplicate terminal event subscriber id=$subscriberId"
        }
        return subscriberChannel.receiveAsFlow()
            .onCompletion {
                terminalEventSubscribers.remove(subscriberId, subscriberChannel)
                subscriberChannel.close()
            }
    }

    /**
     * Returns a hot per-subscriber flow of matched ask deadletters projected by Core.
     *
     * `ask(...)` still fails with [DeadletterException] when its own request is deadlettered.
     * This observation lane is for logging, metrics, and operator diagnostics.
     */
    fun deadletters(bufferCapacity: Int = DEFAULT_DEADLETTER_BUFFER_CAPACITY): Flow<ObservedDeadletter> {
        require(bufferCapacity > 0) { "deadletters requires bufferCapacity > 0" }
        val subscriberId = nextDeadletterSubscriberId.getAndIncrement()
        val subscriberChannel = Channel<ObservedDeadletter>(bufferCapacity)
        check(deadletterSubscribers.putIfAbsent(subscriberId, subscriberChannel) == null) {
            "duplicate deadletter subscriber id=$subscriberId"
        }
        return subscriberChannel.receiveAsFlow()
            .onCompletion {
                deadletterSubscribers.remove(subscriberId, subscriberChannel)
                subscriberChannel.close()
            }
    }

    /**
     * Sends a reply-capable request and waits inline for its terminal outcome.
     *
     * This is the wait-in-place host API shape over the same runtime request/reply contract that
     * [submitRequest] can expose as submit-first, consume-later.
     *
     * @param source Logical caller identity.
     * @param target Logical runtime target.
     * @param payloadUtf8 UTF-8 payload for text-first integration paths.
     * @param timeoutMs Connector-side wait timeout in milliseconds.
     * @param operation Optional operation name placed into request headers.
     * @param deliveryHint Routing preference forwarded to the runtime.
     */
    suspend fun ask(
        source: String,
        target: String,
        payloadUtf8: String,
        timeoutMs: Long = 1_000,
        operation: String = "ask",
        deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
    ): ConnectorEnvelope =
        askRaw(
            source = source,
            target = target,
            payloadUtf8 = payloadUtf8,
            timeoutMs = timeoutMs,
            operation = operation,
            deliveryHint = deliveryHint,
        )

    /** Typed variant of [ask] for callers that want inline wait plus declared payload identity. */
    suspend fun ask(
        source: String,
        target: String,
        payloadUtf8: String,
        payloadIdentity: ConnectorPayloadIdentity,
        timeoutMs: Long = 1_000,
        operation: String = "ask",
        deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
    ): ConnectorEnvelope {
        val request = buildRequestEnvelope(
            source = source,
            target = target,
            payloadUtf8 = payloadUtf8,
            timeoutMs = timeoutMs,
            operation = operation,
            deliveryHint = deliveryHint,
            payloadIdentity = payloadIdentity.requireTyped("ask"),
        )
        return submitAskAndAwait(request, timeoutMs)
    }

    /**
     * Submits a reply-capable request without waiting inline for its terminal outcome.
     *
     * The caller can later observe the matching response or deadletter through [terminalEvents].
     * This is a different host API shape, not a different runtime message semantic.
     */
    suspend fun submitRequest(
        source: String,
        target: String,
        payloadUtf8: String,
        timeoutMs: Long = 1_000,
        operation: String = "ask",
        deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
    ): SubmittedRequest =
        submitRequest(
            buildRequestEnvelope(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                timeoutMs = timeoutMs,
                operation = operation,
                deliveryHint = deliveryHint,
            ),
        )

    /** Typed variant of [submitRequest] for callers that want submit-first plus declared payload identity. */
    suspend fun submitRequest(
        source: String,
        target: String,
        payloadUtf8: String,
        payloadIdentity: ConnectorPayloadIdentity,
        timeoutMs: Long = 1_000,
        operation: String = "ask",
        deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
    ): SubmittedRequest =
        submitRequest(
            buildRequestEnvelope(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                timeoutMs = timeoutMs,
                operation = operation,
                deliveryHint = deliveryHint,
                payloadIdentity = payloadIdentity.requireTyped("submitRequest"),
            ),
        )

    /**
     * Submits a raw reply-capable request without waiting inline for its terminal outcome.
     *
     * This keeps the text-first path available as an explicit raw-envelope escape hatch.
     */
    suspend fun submitRequestRaw(
        source: String,
        target: String,
        payloadUtf8: String,
        timeoutMs: Long = 1_000,
        operation: String = "ask",
        deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
    ): SubmittedRequest =
        submitRequest(
            source = source,
            target = target,
            payloadUtf8 = payloadUtf8,
            timeoutMs = timeoutMs,
            operation = operation,
            deliveryHint = deliveryHint,
        )

    /**
     * Submits an already constructed reply-capable request without waiting inline for its terminal outcome.
     *
     * Use this overload when the caller needs full control over message shape but still wants terminal events.
     */
    suspend fun submitRequest(envelope: ConnectorEnvelope): SubmittedRequest {
        val normalized = normalizeReplyCapableRequest(envelope)
        val trackedRequest = TrackedRequest(
            requestMessageId = normalized.messageId,
            correlationId = normalized.correlationId,
        )
        val ticketId = beginCoreAsk(normalized)
        try {
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        handle.awaitAskEnvelope(ticketId, normalized.timeoutMs)
                    }
                    // Core has consumed the terminal result (or ended the caller
                    // wait), so the JVM lifecycle projection is no longer pending
                    // before observers receive a terminal event.
                    activeAskTickets.remove(ticketId)
                    completeSubmittedAsk(result, trackedRequest)
                } finally {
                    handle.cancelAskEnvelope(ticketId)
                    activeAskTickets.remove(ticketId)
                }
            }
            return SubmittedRequest(
                messageId = trackedRequest.requestMessageId,
                correlationId = trackedRequest.correlationId,
            )
        } catch (t: Throwable) {
            handle.cancelAskEnvelope(ticketId)
            activeAskTickets.remove(ticketId)
            throw t
        }
    }

    /**
     * Sends a raw request and waits for either a response envelope or a deadletter-derived failure.
     *
     * This keeps the text-first path available as an explicit raw-envelope escape hatch.
     */
    suspend fun askRaw(
        source: String,
        target: String,
        payloadUtf8: String,
        timeoutMs: Long = 1_000,
        operation: String = "ask",
        deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
    ): ConnectorEnvelope {
        val request = buildRequestEnvelope(
            source = source,
            target = target,
            payloadUtf8 = payloadUtf8,
            timeoutMs = timeoutMs,
            operation = operation,
            deliveryHint = deliveryHint,
        )
        return submitAskAndAwait(request, timeoutMs)
    }

    private suspend fun submitAskAndAwait(
        request: ConnectorEnvelope,
        timeoutMs: Long,
    ): ConnectorEnvelope {
        require(timeoutMs in 1..60_000) { "timeoutMs must be in 1..60000" }
        val normalized = normalizeReplyCapableRequest(request)
        val trackedRequest = TrackedRequest(normalized.messageId, normalized.correlationId)
        val ticketId = beginCoreAsk(normalized)
        try {
            val result = withContext(Dispatchers.IO) {
                handle.awaitAskEnvelope(ticketId, timeoutMs.toInt())
            }
            return completeInlineAsk(result, trackedRequest, timeoutMs.toInt())
        } finally {
            handle.cancelAskEnvelope(ticketId)
            activeAskTickets.remove(ticketId)
        }
    }

    /**
     * Sends a fire-and-forget request that does not expect a response.
     *
     * @param source Logical caller identity.
     * @param target Logical runtime target.
     * @param payloadUtf8 UTF-8 payload for text-first integration paths.
     * @param deliveryHint Routing preference forwarded to the runtime.
     */
    suspend fun sendOneWay(
        source: String,
        target: String,
        payloadUtf8: String,
        deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
    ) {
        sendOneWayRaw(
            source = source,
            target = target,
            payloadUtf8 = payloadUtf8,
            deliveryHint = deliveryHint,
        )
    }

    /** Sends a typed fire-and-forget request with declared payload identity. */
    suspend fun sendOneWay(
        source: String,
        target: String,
        payloadUtf8: String,
        payloadIdentity: ConnectorPayloadIdentity,
        deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
    ) {
        submitRawEnvelope(
            buildOneWayEnvelope(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                deliveryHint = deliveryHint,
                payloadIdentity = payloadIdentity.requireTyped("sendOneWay"),
            ),
        )
    }

    /** Sends a raw fire-and-forget request without enforcing typed payload identity. */
    suspend fun sendOneWayRaw(
        source: String,
        target: String,
        payloadUtf8: String,
        deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
    ) {
        submitRawEnvelope(
            buildOneWayEnvelope(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                deliveryHint = deliveryHint,
            ),
        )
    }

    /**
     * Submits an already constructed connector envelope to the native runtime.
     *
     * Use this when the caller needs full control over message shape instead of the helper APIs.
     */
    suspend fun submitEnvelope(envelope: ConnectorEnvelope) {
        submitRawEnvelope(envelope)
    }

    /** Submits a raw connector envelope without enforcing typed payload identity. */
    suspend fun submitRawEnvelope(envelope: ConnectorEnvelope) {
        handle.submitEnvelope(EnvelopeMapper.encode(envelope))
    }

    /** Submits an envelope through the typed path after enforcing payload identity. */
    suspend fun submitTypedEnvelope(envelope: ConnectorEnvelope) {
        submitRawEnvelope(envelope.requireTypedPayloadIdentity("submitTypedEnvelope"))
    }

    /** Returns connector-local bookkeeping stats, not the full native runtime stats set. */
    fun snapshotStats(): RuntimeClientStats =
        RuntimeClientStats(
            pendingRequests = activeAskTickets.size,
            deliveredRequests = deliveredRequests.get(),
            matchedResponses = matchedResponses.get(),
            matchedDeadletters = matchedDeadletters.get(),
            lateResponses = lateResponses.get(),
            unhandledDeadletters = unhandledDeadletters.get(),
            terminalEventDropCount = terminalEventDropCount.get(),
            deadletterObservationDropCount = deadletterObservationDropCount.get(),
        )

    /** Returns Core's accepted startup facts without reconstructing defaults. */
    fun startupConfig(): RuntimeStartupConfigSnapshot = handle.startupConfig()

    /** Returns Core's accepted delivery composition without selecting a mode. */
    fun deliveryConfig(): RuntimeDeliveryConfigSnapshot = handle.deliveryConfig()

    suspend fun shutdown() {
        if (closed) {
            return
        }
        closed = true
        requestJob.cancel()
        deliveredRequestReader.close()
        handle.close()
        requestJob.join()
        scope.coroutineContext[Job]?.cancelAndJoin()
    }

    override fun close() {
        error("use suspend shutdown() to close RuntimeClient")
    }

    private fun dispatchEnvelopeRequest(
        request: NativeDeliveredRequest,
        handler: LocalRequestHandler.Envelope,
    ) {
        val envelope = EnvelopeMapper.decode(request.frame)
        // Enter non-blocking handlers on the sole reader's current turn. A handler
        // that suspends resumes through its own coroutine context; an immediate
        // reply avoids an otherwise unconditional scheduler handoff per request.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val reply = handler.call(envelope)
            if (!request.oneWay && reply != null) {
                submitEnvelope(reply)
            }
        }
    }

    private fun dispatchPayloadRequest(
        request: NativeDeliveredRequest,
        handler: LocalRequestHandler.Payload,
    ) {
        // Request bytes are an owning JVM copy. Core borrows the original frame
        // only through the synchronous reply call and copies its output before
        // this reader turn continues.
        try {
            val replyPayload = handler.call.handle(request.payload)
            if (!request.oneWay && replyPayload != null) {
                handle.replyPayload(request.frame, handler.responseSource, replyPayload)
            }
        } catch (failure: Throwable) {
            // Preserve the existing SupervisorJob failure isolation without a
            // coroutine allocation on every successful request.
            scope.launch { throw failure }
        }
    }

    private fun handlerFor(target: ByteArray): LocalRequestHandler? {
        val cachedTarget = lastDispatchTarget
        if (cachedTarget != null && cachedTarget.contentEquals(target)) {
            return lastDispatchHandler
        }
        val handler = requestHandlers[target.toString(Charsets.UTF_8)] ?: return null
        // The sole reader owns this one-entry exact-byte cache. Handlers cannot
        // be removed, so a byte-equal Core-issued target keeps the same owner.
        lastDispatchTarget = target
        lastDispatchHandler = handler
        return handler
    }

    private fun beginCoreAsk(envelope: ConnectorEnvelope): Long {
        val ticketId = handle.beginAskEnvelope(EnvelopeMapper.encode(envelope))
        check(activeAskTickets.add(ticketId)) { "duplicate Core ask ticket id=$ticketId" }
        return ticketId
    }

    private fun completeInlineAsk(
        result: NativeRecord,
        trackedRequest: TrackedRequest,
        timeoutMs: Int,
    ): ConnectorEnvelope = when (val status = result.value(0).toInt()) {
        CoakkaStatus.OK -> when (result.value(1).toInt()) {
            CORE_RESULT_RESPONSE -> {
                matchedResponses.incrementAndGet()
                EnvelopeMapper.decode(result.payload(0))
            }
            CORE_RESULT_DEADLETTER -> {
                matchedDeadletters.incrementAndGet()
                val deadletter = EnvelopeMapper.decodeDeadletter(result.payload(0))
                publishObservedDeadletter(deadletter, trackedRequest)
                throw DeadletterException(deadletter)
            }
            else -> error("Core returned an unknown ask result kind")
        }
        CoakkaStatus.ERR_WOULD_BLOCK -> throw RuntimeAskTimeoutException(timeoutMs)
        else -> {
            requireStatus(status, "ask_ticket_await")
            error("unreachable ask status")
        }
    }

    private fun completeSubmittedAsk(result: NativeRecord, trackedRequest: TrackedRequest) {
        when (val status = result.value(0).toInt()) {
            CoakkaStatus.OK -> when (result.value(1).toInt()) {
                CORE_RESULT_RESPONSE -> {
                    matchedResponses.incrementAndGet()
                    val response = EnvelopeMapper.decode(result.payload(0))
                    publishTerminalEvent(
                        RequestTerminalEvent.Response(
                            trackedRequest.requestMessageId,
                            trackedRequest.correlationId,
                            response,
                        ),
                    )
                }
                CORE_RESULT_DEADLETTER -> {
                    matchedDeadletters.incrementAndGet()
                    val deadletter = EnvelopeMapper.decodeDeadletter(result.payload(0))
                    publishObservedDeadletter(deadletter, trackedRequest)
                    publishTerminalEvent(
                        RequestTerminalEvent.Deadletter(
                            trackedRequest.requestMessageId,
                            trackedRequest.correlationId,
                            deadletter,
                        ),
                    )
                }
                else -> error("Core returned an unknown ask result kind")
            }
            CoakkaStatus.ERR_WOULD_BLOCK -> Unit
            CoakkaStatus.ERR_CLOSED -> if (!closed) requireStatus(status, "ask_ticket_await")
            else -> requireStatus(status, "ask_ticket_await")
        }
    }

    private fun normalizeReplyCapableRequest(envelope: ConnectorEnvelope): ConnectorEnvelope {
        require(envelope.kind == ConnectorMessageKind.REQUEST) {
            "submitRequest requires REQUEST kind"
        }
        require(!envelope.oneWay) {
            "submitRequest requires oneWay=false"
        }
        require(envelope.messageId.isNotBlank()) {
            "submitRequest requires messageId"
        }
        require(envelope.correlationId.isBlank() || envelope.correlationId == envelope.messageId) {
            "submitRequest correlationId must be empty or equal messageId"
        }
        require(envelope.timeoutMs in 1..60_000) {
            "submitRequest requires timeoutMs in 1..60000"
        }
        return if (envelope.correlationId.isBlank()) {
            envelope.copy(correlationId = envelope.messageId)
        } else {
            envelope
        }
    }

    private fun publishTerminalEvent(event: RequestTerminalEvent) {
        for ((subscriberId, subscriberChannel) in terminalEventSubscribers) {
            val result = subscriberChannel.trySend(event)
            if (result.isSuccess) {
                continue
            }
            if (result.isClosed) {
                terminalEventSubscribers.remove(subscriberId, subscriberChannel)
                continue
            }
            terminalEventDropCount.incrementAndGet()
        }
    }

    private fun publishObservedDeadletter(
        deadletter: coakka.v2.connector.protocol.ConnectorDeadletter,
        trackedRequest: TrackedRequest?,
    ) {
        val observed = ObservedDeadletter(
            deadletter = deadletter,
            requestMessageId = trackedRequest?.requestMessageId ?: deadletter.originalEnvelope?.messageId?.takeIf { it.isNotBlank() },
            correlationId = trackedRequest?.correlationId ?: deadletter.originalEnvelope?.correlationId?.takeIf { it.isNotBlank() },
            matchedPendingRequest = trackedRequest != null,
        )
        for ((subscriberId, subscriberChannel) in deadletterSubscribers) {
            val result = subscriberChannel.trySend(observed)
            if (result.isSuccess) {
                continue
            }
            if (result.isClosed) {
                deadletterSubscribers.remove(subscriberId, subscriberChannel)
                continue
            }
            deadletterObservationDropCount.incrementAndGet()
        }
    }

    private fun buildRequestEnvelope(
        source: String,
        target: String,
        payloadUtf8: String,
        timeoutMs: Long,
        operation: String,
        deliveryHint: ConnectorDeliveryHint,
        payloadIdentity: ConnectorPayloadIdentity? = null,
    ): ConnectorEnvelope {
        val messageId = nextRequestId(source)
        return ConnectorEnvelope(
            messageId = messageId,
            correlationId = messageId,
            source = source,
            target = target,
            replyTo = "$source/replies",
            kind = ConnectorMessageKind.REQUEST,
            oneWay = false,
            timeoutMs = timeoutMs.toInt(),
            payload = payloadUtf8.toByteArray(),
            headers = mapOf(
                "method" to "POST",
                "operation" to operation,
            ),
            status = ConnectorBusinessStatus.OK,
            deliveryHint = deliveryHint,
        ).let { envelope ->
            payloadIdentity?.let(envelope::withPayloadIdentity) ?: envelope
        }
    }

    private fun buildOneWayEnvelope(
        source: String,
        target: String,
        payloadUtf8: String,
        deliveryHint: ConnectorDeliveryHint,
        payloadIdentity: ConnectorPayloadIdentity? = null,
    ): ConnectorEnvelope =
        ConnectorEnvelope(
            messageId = nextRequestId(source),
            source = source,
            target = target,
            kind = ConnectorMessageKind.REQUEST,
            oneWay = true,
            payload = payloadUtf8.toByteArray(),
            status = ConnectorBusinessStatus.OK,
            deliveryHint = deliveryHint,
        ).let { envelope ->
            payloadIdentity?.let(envelope::withPayloadIdentity) ?: envelope
        }

    private fun nextRequestId(source: String): String =
        "$source-${nextMessageId.getAndIncrement()}"

    companion object {
        const val DEFAULT_TERMINAL_EVENT_BUFFER_CAPACITY = 128
        const val DEFAULT_DEADLETTER_BUFFER_CAPACITY = 128
        private const val CORE_RESULT_RESPONSE = 1
        private const val CORE_RESULT_DEADLETTER = 2

        /** Builds a response envelope that targets the original request sender. */
        fun replyTo(
            request: ConnectorEnvelope,
            source: String,
            payloadUtf8: String,
        ): ConnectorEnvelope =
            replyEnvelope(
                request = request,
                source = source,
                payloadUtf8 = payloadUtf8,
            )

        /** Builds a typed response envelope that declares payload identity explicitly. */
        fun replyTo(
            request: ConnectorEnvelope,
            source: String,
            payloadUtf8: String,
            payloadIdentity: ConnectorPayloadIdentity,
        ): ConnectorEnvelope =
            replyEnvelope(
                request = request,
                source = source,
                payloadUtf8 = payloadUtf8,
                payloadIdentity = payloadIdentity.requireTyped("replyTo"),
            )

        /** Builds a typed response by reusing the request payload identity. */
        fun replyTypedTo(
            request: ConnectorEnvelope,
            source: String,
            payloadUtf8: String,
        ): ConnectorEnvelope =
            replyEnvelope(
                request = request.requireTypedPayloadIdentity("replyTypedTo request"),
                source = source,
                payloadUtf8 = payloadUtf8,
                payloadIdentity = request.payloadIdentity(),
            )

        /** Builds a plain-text typed response for first-run text handlers. */
        @JvmStatic
        @JvmOverloads
        fun replyTextTo(
            request: ConnectorEnvelope,
            source: String,
            payloadUtf8: String,
            messageType: String = "$source.reply",
            payloadSchemaVersion: Long = 1,
        ): ConnectorEnvelope =
            replyEnvelope(
                request = request,
                source = source,
                payloadUtf8 = payloadUtf8,
                payloadIdentity = ConnectorPayloadIdentity.text(messageType, payloadSchemaVersion),
            )

        private fun replyEnvelope(
            request: ConnectorEnvelope,
            source: String,
            payloadUtf8: String,
            payloadIdentity: ConnectorPayloadIdentity? = null,
        ): ConnectorEnvelope =
            ConnectorEnvelope(
                messageId = "${request.messageId}.reply.$source",
                correlationId = request.correlationId.ifBlank { request.messageId },
                source = source,
                target = request.source,
                kind = ConnectorMessageKind.RESPONSE,
                oneWay = false,
                timeoutMs = request.timeoutMs,
                payload = payloadUtf8.toByteArray(),
                status = ConnectorBusinessStatus.OK,
                deliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
            ).let { envelope ->
                payloadIdentity?.let(envelope::withPayloadIdentity) ?: envelope
            }

        /**
         * Builds the local route snapshot entry used by same-process handlers.
         *
         * Port `0` is the embedded default and does not allocate or imply a TCP listener.
         * A non-zero port is retained only as explicit route metadata.
         */
        @JvmStatic
        @JvmOverloads
        fun localRoute(target: String, port: Int = 0): RuntimeRouteSpec =
            RuntimeRouteSpec(
                target = target,
                endpoints = listOf(
                    RuntimeEndpointSpec(
                        host = "127.0.0.1",
                        port = port.toLong(),
                        flags = RuntimeEndpointFlags.LOCAL,
                    ),
                ),
            )

        /**
         * Builds one local route snapshot where every target carries the same diagnostic port.
         */
        @JvmStatic
        @JvmOverloads
        fun localRoutes(targets: List<String>, port: Int = 0): List<RuntimeRouteSpec> =
            targets.map { target -> localRoute(target, port) }

        /** Creates, starts, and wraps a runtime using the provided startup spec. */
        fun start(runtimeLibPath: String? = null, startSpec: RuntimeStartSpec): RuntimeClient {
            val handle = RuntimeHandle.open(runtimeLibPath, startSpec)
            try {
                handle.start()
                return RuntimeClient(handle)
            } catch (failure: Throwable) {
                handle.close()
                throw failure
            }
        }

        /** Convenience helper for a purely local single-process target setup. */
        fun startLocal(
            runtimeLibPath: String? = null,
            localTargets: List<String>,
            systemName: String = "connector-kotlin",
            nodeId: String = "node-kotlin",
        ): RuntimeClient {
            val routes = localRoutes(localTargets)
            return start(
                runtimeLibPath = runtimeLibPath,
                startSpec = RuntimeStartSpec(
                    systemName = systemName,
                    nodeId = nodeId,
                    routes = routes,
                ),
            )
        }
    }
}
