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
import coakka.v2.transport.Deadletter
import coakka.v2.transport.Envelope
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

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

/**
 * Host-facing request client layered on top of the native runtime.
 *
 * It owns reader loops for main-channel responses and deadletters, a pending request map,
 * and a handler registry for locally delivered requests.
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

    private val submitMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nextMessageId = AtomicLong(1)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<ConnectorEnvelope>>()
    private val trackedRequestsByMessageId = ConcurrentHashMap<String, TrackedRequest>()
    private val trackedRequestMessageIdsByCorrelationId = ConcurrentHashMap<String, String>()
    private val requestHandlers = ConcurrentHashMap<String, suspend (ConnectorEnvelope) -> ConnectorEnvelope?>()
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
    private val deliveredRequestFd = handle.hostHandles.delivered_request_read_fd
        .takeIf { it >= 0 }
        ?: handle.hostHandles.response_read_fd
    private val deliveredRequestReader = NativeFrameReader(handle.lib, deliveredRequestFd)
    private val separateDeliveredRequestLaneEnabled =
        deliveredRequestFd != handle.hostHandles.response_read_fd
    private val responseReader = handle.hostHandles.response_read_fd
        .takeIf { separateDeliveredRequestLaneEnabled }
        ?.let { NativeFrameReader(handle.lib, it) }
    private val deadletterReader = NativeFrameReader(handle.lib, handle.hostHandles.deadletter_read_fd)
    private val requestJob: Job
    private val responseJob: Job
    private val deadletterJob: Job
    private var closed = false

    init {
        requestJob = scope.launch {
            while (isActive) {
                val frame = deliveredRequestReader.readFrameOrNull(100) ?: continue
                val envelope = EnvelopeMapper.fromProto(Envelope.parseFrom(frame))
                if (separateDeliveredRequestLaneEnabled) {
                    check(envelope.kind == ConnectorMessageKind.REQUEST) {
                        "unexpected envelope kind=${envelope.kind} on delivered-request lane"
                    }
                    dispatchRequest(envelope)
                } else {
                    when (envelope.kind) {
                        ConnectorMessageKind.REQUEST -> dispatchRequest(envelope)
                        ConnectorMessageKind.RESPONSE -> dispatchResponse(envelope)
                        else -> error("unexpected envelope kind=${envelope.kind}")
                    }
                }
            }
        }

        responseJob = scope.launch {
            if (!separateDeliveredRequestLaneEnabled) {
                return@launch
            }
            while (isActive) {
                val frame = responseReader!!.readFrameOrNull(100) ?: continue
                val envelope = EnvelopeMapper.fromProto(Envelope.parseFrom(frame))
                when (envelope.kind) {
                    ConnectorMessageKind.RESPONSE -> dispatchResponse(envelope)
                    ConnectorMessageKind.REQUEST ->
                        error("unexpected request on response-only lane")
                    else -> error("unexpected envelope kind=${envelope.kind}")
                }
            }
        }

        deadletterJob = scope.launch {
            while (isActive) {
                val frame = deadletterReader.readFrameOrNull(100) ?: continue
                dispatchDeadletter(Deadletter.parseFrom(frame))
            }
        }
    }

    /** Registers a local handler for requests delivered to [target]. */
    fun registerHandler(target: String, handler: suspend (ConnectorEnvelope) -> ConnectorEnvelope?) {
        check(requestHandlers.putIfAbsent(target, handler) == null) {
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
     * Returns a hot per-subscriber flow of every deadletter drained by this connector.
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
        val trackedRequest = trackRequest(envelope)
        try {
            submitRawEnvelope(trackedRequest)
            return SubmittedRequest(
                messageId = trackedRequest.messageId,
                correlationId = trackedRequest.correlationId,
            )
        } catch (t: Throwable) {
            forgetTrackedRequest(trackedRequest.messageId)
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
        val trackedRequest = trackRequest(request)
        val waiter = CompletableDeferred<ConnectorEnvelope>()
        check(pending.putIfAbsent(trackedRequest.messageId, waiter) == null) {
            "duplicate pending message_id=${trackedRequest.messageId}"
        }

        try {
            submitRawEnvelope(trackedRequest)
            return withTimeout(timeoutMs) {
                waiter.await()
            }
        } finally {
            pending.remove(trackedRequest.messageId, waiter)
            forgetTrackedRequest(trackedRequest.messageId)
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
        submitMutex.withLock {
            handle.submitEnvelope(EnvelopeMapper.toProto(envelope).toByteArray())
        }
    }

    /** Submits an envelope through the typed path after enforcing payload identity. */
    suspend fun submitTypedEnvelope(envelope: ConnectorEnvelope) {
        submitRawEnvelope(envelope.requireTypedPayloadIdentity("submitTypedEnvelope"))
    }

    /** Returns connector-local bookkeeping stats, not the full native runtime stats set. */
    fun snapshotStats(): RuntimeClientStats =
        RuntimeClientStats(
            pendingRequests = pending.size,
            deliveredRequests = deliveredRequests.get(),
            matchedResponses = matchedResponses.get(),
            matchedDeadletters = matchedDeadletters.get(),
            lateResponses = lateResponses.get(),
            unhandledDeadletters = unhandledDeadletters.get(),
            terminalEventDropCount = terminalEventDropCount.get(),
            deadletterObservationDropCount = deadletterObservationDropCount.get(),
        )

    suspend fun shutdown() {
        if (closed) {
            return
        }
        closed = true
        requestJob.cancelAndJoin()
        responseJob.cancelAndJoin()
        deadletterJob.cancelAndJoin()
        deliveredRequestReader.close()
        responseReader?.close()
        deadletterReader.close()
        scope.coroutineContext[Job]?.cancelAndJoin()
        handle.close()
    }

    override fun close() {
        error("use suspend shutdown() to close RuntimeClient")
    }

    private fun dispatchRequest(request: ConnectorEnvelope) {
        deliveredRequests.incrementAndGet()
        val handler = requestHandlers[request.target] ?: return
        scope.launch {
            val reply = handler(request)
            if (!request.oneWay && reply != null) {
                submitEnvelope(reply)
            }
        }
    }

    private fun dispatchResponse(response: ConnectorEnvelope) {
        val trackedRequest = forgetTrackedRequestByCorrelationId(response.correlationId)
        if (trackedRequest != null) {
            publishTerminalEvent(
                RequestTerminalEvent.Response(
                    requestMessageId = trackedRequest.requestMessageId,
                    correlationId = trackedRequest.correlationId,
                    envelope = response,
                ),
            )
            val waiter = pending.remove(trackedRequest.requestMessageId)
            if (waiter != null) {
                matchedResponses.incrementAndGet()
                waiter.complete(response)
            }
            return
        }
        lateResponses.incrementAndGet()
    }

    private fun dispatchDeadletter(deadletter: Deadletter) {
        val mapped = EnvelopeMapper.fromProto(deadletter)
        val trackedRequest = forgetTrackedRequest(mapped.originalEnvelope.messageId)
        publishObservedDeadletter(mapped, trackedRequest)
        if (trackedRequest != null) {
            publishTerminalEvent(
                RequestTerminalEvent.Deadletter(
                    requestMessageId = trackedRequest.requestMessageId,
                    correlationId = trackedRequest.correlationId,
                    deadletter = mapped,
                ),
            )
            val waiter = pending.remove(trackedRequest.requestMessageId)
            if (waiter != null) {
                matchedDeadletters.incrementAndGet()
                waiter.completeExceptionally(DeadletterException(mapped))
            }
            return
        }
        unhandledDeadletters.incrementAndGet()
    }

    private fun trackRequest(envelope: ConnectorEnvelope): ConnectorEnvelope {
        val normalizedEnvelope = normalizeTrackedRequest(envelope)
        val trackedRequest = TrackedRequest(
            requestMessageId = normalizedEnvelope.messageId,
            correlationId = normalizedEnvelope.correlationId,
        )
        check(trackedRequestsByMessageId.putIfAbsent(trackedRequest.requestMessageId, trackedRequest) == null) {
            "duplicate tracked request message_id=${trackedRequest.requestMessageId}"
        }
        check(
            trackedRequestMessageIdsByCorrelationId.putIfAbsent(
                trackedRequest.correlationId,
                trackedRequest.requestMessageId,
            ) == null,
        ) {
            trackedRequestsByMessageId.remove(trackedRequest.requestMessageId, trackedRequest)
            "duplicate tracked request correlation_id=${trackedRequest.correlationId}"
        }
        return normalizedEnvelope
    }

    private fun forgetTrackedRequest(requestMessageId: String): TrackedRequest? {
        val trackedRequest = trackedRequestsByMessageId.remove(requestMessageId) ?: return null
        trackedRequestMessageIdsByCorrelationId.remove(
            trackedRequest.correlationId,
            trackedRequest.requestMessageId,
        )
        return trackedRequest
    }

    private fun forgetTrackedRequestByCorrelationId(correlationId: String): TrackedRequest? {
        val requestMessageId = trackedRequestMessageIdsByCorrelationId.remove(correlationId) ?: return null
        return trackedRequestsByMessageId.remove(requestMessageId)
    }

    private fun normalizeTrackedRequest(envelope: ConnectorEnvelope): ConnectorEnvelope {
        require(envelope.kind == ConnectorMessageKind.REQUEST) {
            "submitRequest requires REQUEST kind"
        }
        require(!envelope.oneWay) {
            "submitRequest requires oneWay=false"
        }
        require(envelope.messageId.isNotBlank()) {
            "submitRequest requires messageId"
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
            requestMessageId = trackedRequest?.requestMessageId ?: deadletter.originalEnvelope.messageId.takeIf { it.isNotBlank() },
            correlationId = trackedRequest?.correlationId ?: deadletter.originalEnvelope.correlationId.takeIf { it.isNotBlank() },
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
            payloadSchemaVersion: Int = 1,
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
                        port = port,
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
            handle.start()
            return RuntimeClient(handle)
        }

        /** Convenience helper for a purely local single-process target setup. */
        fun startLocal(
            runtimeLibPath: String? = null,
            localTargets: List<String>,
            systemName: String = "connector-kotlin",
            nodeId: String = "node-kotlin",
            separateDeliveredRequestLane: Boolean = true,
        ): RuntimeClient {
            val routes = localRoutes(localTargets)
            return start(
                runtimeLibPath = runtimeLibPath,
                startSpec = RuntimeStartSpec(
                    systemName = systemName,
                    nodeId = nodeId,
                    separateDeliveredRequestLane = separateDeliveredRequestLane,
                    routes = routes,
                ),
            )
        }
    }
}
