package coakka.v2.connector.simulated

import coakka.v2.connector.DeadletterException
import coakka.v2.connector.RuntimeClientStats
import coakka.v2.connector.protocol.ConnectorBusinessStatus
import coakka.v2.connector.protocol.ConnectorDeadletter
import coakka.v2.connector.protocol.ConnectorDeliveryHint
import coakka.v2.connector.protocol.ConnectorEnvelope
import coakka.v2.connector.protocol.ConnectorMessageKind
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

internal class SimulatedRuntimeBus {
    private val routeOwners = ConcurrentHashMap<String, SimulatedRuntimeClient>()
    private val pendingOwners = ConcurrentHashMap<String, SimulatedRuntimeClient>()

    fun registerTarget(target: String, client: SimulatedRuntimeClient) {
        check(routeOwners.putIfAbsent(target, client) == null) {
            "target already registered: $target"
        }
    }

    suspend fun submit(from: SimulatedRuntimeClient, envelope: ConnectorEnvelope) {
        when (envelope.kind) {
            ConnectorMessageKind.RESPONSE -> {
                val owner = pendingOwners.remove(envelope.correlationId)
                if (owner != null) {
                    owner.deliverMain(envelope)
                }
            }

            ConnectorMessageKind.REQUEST, ConnectorMessageKind.EVENT -> {
                if (!envelope.oneWay && envelope.kind == ConnectorMessageKind.REQUEST) {
                    pendingOwners[envelope.messageId] = from
                }
                val destination = routeOwners[envelope.target]
                if (destination != null) {
                    destination.deliverMain(envelope)
                } else {
                    if (!envelope.oneWay && envelope.kind == ConnectorMessageKind.REQUEST) {
                        pendingOwners.remove(envelope.messageId)
                    }
                    from.deliverDeadletter(
                        ConnectorDeadletter(
                            originalEnvelope = envelope,
                            reason = "SIMULATED_ROUTE_MISS",
                            detail = "no handler registered for target=${envelope.target}",
                            activeGeneration = 1,
                            resolvedHost = "",
                            resolvedPort = 0,
                        ),
                    )
                }
            }
        }
    }
}

internal class SimulatedRuntimeClient(
    private val bus: SimulatedRuntimeBus,
    private val clientName: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainChannel = Channel<ConnectorEnvelope>(Channel.UNLIMITED)
    private val deadletterChannel = Channel<ConnectorDeadletter>(Channel.UNLIMITED)
    private val submitMutex = Mutex()
    private val nextMessageId = AtomicLong(1)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<ConnectorEnvelope>>()
    private val requestHandlers = ConcurrentHashMap<String, suspend (ConnectorEnvelope) -> ConnectorEnvelope?>()
    private val deliveredRequests = AtomicLong(0)
    private val matchedResponses = AtomicLong(0)
    private val matchedDeadletters = AtomicLong(0)
    private val lateResponses = AtomicLong(0)
    private val unhandledDeadletters = AtomicLong(0)
    private val responseJob: Job
    private val deadletterJob: Job

    init {
        responseJob = scope.launch {
            while (isActive) {
                val envelope = mainChannel.receive()
                when (envelope.kind) {
                    ConnectorMessageKind.REQUEST -> dispatchRequest(envelope)
                    ConnectorMessageKind.RESPONSE -> dispatchResponse(envelope)
                    ConnectorMessageKind.EVENT -> Unit
                }
            }
        }
        deadletterJob = scope.launch {
            while (isActive) {
                dispatchDeadletter(deadletterChannel.receive())
            }
        }
    }

    fun registerHandler(target: String, handler: suspend (ConnectorEnvelope) -> ConnectorEnvelope?) {
        bus.registerTarget(target, this)
        check(requestHandlers.putIfAbsent(target, handler) == null) {
            "handler already registered for target=$target"
        }
    }

    suspend fun ask(
        source: String,
        target: String,
        payloadUtf8: String,
        timeoutMs: Long = 1_000,
        operation: String = "ask",
        deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
    ): ConnectorEnvelope {
        val messageId = nextRequestId()
        val request = ConnectorEnvelope(
            messageId = messageId,
            correlationId = messageId,
            source = source,
            target = target,
            replyTo = "$source/replies",
            kind = ConnectorMessageKind.REQUEST,
            oneWay = false,
            timeoutMs = timeoutMs.toInt(),
            payload = payloadUtf8.toByteArray(),
            headers = mapOf("operation" to operation, "client" to clientName),
            status = ConnectorBusinessStatus.OK,
            deliveryHint = deliveryHint,
        )
        val waiter = CompletableDeferred<ConnectorEnvelope>()
        check(pending.putIfAbsent(messageId, waiter) == null) {
            "duplicate pending message_id=$messageId"
        }

        try {
            submitEnvelope(request)
            return withTimeout(timeoutMs) { waiter.await() }
        } finally {
            pending.remove(messageId, waiter)
        }
    }

    suspend fun sendOneWay(source: String, target: String, payloadUtf8: String) {
        submitEnvelope(
            ConnectorEnvelope(
                messageId = nextRequestId(),
                source = source,
                target = target,
                kind = ConnectorMessageKind.REQUEST,
                oneWay = true,
                payload = payloadUtf8.toByteArray(),
            ),
        )
    }

    suspend fun submitEnvelope(envelope: ConnectorEnvelope) {
        submitMutex.withLock {
            bus.submit(this, envelope)
        }
    }

    suspend fun shutdown() {
        responseJob.cancelAndJoin()
        deadletterJob.cancelAndJoin()
        mainChannel.close()
        deadletterChannel.close()
        scope.coroutineContext[Job]?.cancelAndJoin()
    }

    fun snapshotStats(): RuntimeClientStats =
        RuntimeClientStats(
            pendingRequests = pending.size,
            deliveredRequests = deliveredRequests.get(),
            matchedResponses = matchedResponses.get(),
            matchedDeadletters = matchedDeadletters.get(),
            lateResponses = lateResponses.get(),
            unhandledDeadletters = unhandledDeadletters.get(),
            terminalEventDropCount = 0,
        )

    suspend fun deliverMain(envelope: ConnectorEnvelope) {
        mainChannel.send(envelope)
    }

    suspend fun deliverDeadletter(deadletter: ConnectorDeadletter) {
        deadletterChannel.send(deadletter)
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
        val waiter = pending.remove(response.correlationId)
        if (waiter != null) {
            matchedResponses.incrementAndGet()
            waiter.complete(response)
        } else {
            lateResponses.incrementAndGet()
        }
    }

    private fun dispatchDeadletter(deadletter: ConnectorDeadletter) {
        val waiter = pending.remove(deadletter.originalEnvelope.messageId)
        if (waiter != null) {
            matchedDeadletters.incrementAndGet()
            waiter.completeExceptionally(DeadletterException(deadletter))
        } else {
            unhandledDeadletters.incrementAndGet()
        }
    }

    private fun nextRequestId(): String = "$clientName-${nextMessageId.getAndIncrement()}"

    companion object {
        fun replyTo(request: ConnectorEnvelope, source: String, payloadUtf8: String): ConnectorEnvelope =
            ConnectorEnvelope(
                messageId = "${request.messageId}.reply.$source",
                correlationId = request.messageId,
                source = source,
                target = request.source,
                kind = ConnectorMessageKind.RESPONSE,
                oneWay = false,
                timeoutMs = request.timeoutMs,
                payload = payloadUtf8.toByteArray(),
            )
    }
}
