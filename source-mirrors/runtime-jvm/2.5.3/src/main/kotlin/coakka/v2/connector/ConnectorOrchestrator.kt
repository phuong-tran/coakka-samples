package coakka.v2.connector

import coakka.v2.connector.protocol.ConnectorDeliveryHint
import coakka.v2.connector.protocol.ConnectorEnvelope
import coakka.v2.connector.protocol.ConnectorPayloadIdentity
import coakka.v2.connector.protocol.ObservedDeadletter
import coakka.v2.connector.protocol.RequestTerminalEvent
import coakka.v2.connector.protocol.SubmittedRequest
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Java 8-friendly callback for runtime deadletter observations. */
fun interface DeadletterListener {
    fun onDeadletter(deadletter: ObservedDeadletter)
}

/** Java/Kotlin-friendly callback for local plain-text handlers. */
fun interface TextHandler {
    fun handle(payloadUtf8: String): String
}

/** Handle returned by Java deadletter subscriptions. */
interface DeadletterSubscription : AutoCloseable {
    override fun close()
}

/**
 * Host-facing lifecycle owner for one connector runtime inside the current JVM process.
 *
 * Application code should prefer this interface over manually wiring lower-level runtime pieces.
 * A process may hold more than one active orchestrator when a host needs multiple runtime handles.
 *
 * Transport-facing request APIs are split into:
 * - [kotlin] for Kotlin callers that want `suspend` functions
 * - [java] for Java callers that want `CompletableFuture`
 */
interface ConnectorOrchestrator : AutoCloseable {
    /** Kotlin-facing request API for coroutine-first callers. */
    val kotlin: Kotlin

    /** Java-facing request API for `CompletableFuture` callers. */
    val java: Java

    /** Control lane bound to the same runtime instance owned by this orchestrator. */
    val control: RuntimeControlClient

    /** Latest-state observability lane bound to the same runtime instance owned by this orchestrator. */
    val monitor: RuntimeMonitor

    /** Returns the latest coarse health snapshot exported by the native runtime. */
    fun health(): RuntimeHealthSnapshot

    /** Returns the latest reduced runtime stats snapshot exported by the native runtime. */
    fun stats(): RuntimeStatsSnapshot

    /** Returns static runtime/library metadata for the loaded runtime. */
    fun runtimeInfo(): RuntimeInfoSnapshot

    /** Returns the latest runtime-owned effective config for the active runtime instance. */
    fun runtimeConfig(): RuntimeConfigSnapshot

    /** Returns compiled, entitled, and effective transport capability truth. */
    fun runtimeCapabilities(): RuntimeCapabilitiesSnapshot

    /** Returns the effective TCP connection strategy owned by the runtime. */
    fun tcpConnectionConfig(): RuntimeTcpConnectionConfigSnapshot

    /** Returns copy-safe, non-secret active TLS identity metadata. */
    fun tcpSecurityInfo(): RuntimeTcpSecurityInfoSnapshot

    /** Returns the startup connection result, or null when startup used runtime defaults. */
    fun startupConnectionResult(): RuntimeTcpConnectionApplyResult?

    /** Returns the startup security result, or null when startup used runtime defaults. */
    fun startupSecurityResult(): RuntimeTcpSecurityApplyResult?

    /**
     * Applies a connection strategy while the runtime is CREATED.
     * A started orchestrator returns a structured BAD_STATE result and preserves active state.
     */
    fun applyTcpConnectionStrategy(
        spec: RuntimeTcpConnectionStrategySpec,
    ): RuntimeTcpConnectionApplyResult

    /**
     * Applies TLS configuration or reloads a later credential generation atomically.
     * Rejection preserves the active generation and returns only non-secret metadata.
     */
    fun applyTcpSecurity(spec: RuntimeTcpSecuritySpec): RuntimeTcpSecurityApplyResult

    /** Registers a local request handler on the underlying runtime client. */
    fun registerHandler(
        target: String,
        handler: suspend (ConnectorEnvelope) -> ConnectorEnvelope?,
    )

    /**
     * Registers a local plain-text handler and builds a typed text reply.
     *
     * This is the Level 1 handler API for first-run apps. Use [registerHandler]
     * when a handler needs full envelope access.
     */
    fun registerTextHandler(target: String, handler: TextHandler) {
        registerHandler(target) { request ->
            RuntimeClient.replyTextTo(
                request = request,
                source = target,
                payloadUtf8 = handler.handle(request.payloadUtf8()),
                messageType = "$target.reply",
            )
        }
    }

    /** Returns connector-local request bookkeeping counters from the owned client. */
    fun clientStats(): RuntimeClientStats

    /** Applies a full route snapshot through the orchestrator-owned control lane. */
    fun applySnapshot(
        generation: Long,
        routes: List<RuntimeRouteSpec>,
        sourceConnector: String = "connector-kotlin",
    )

    /** Applies a route snapshot and carries an explicit overload policy for this generation. */
    fun applySnapshot(
        generation: Long,
        routes: List<RuntimeRouteSpec>,
        sourceConnector: String,
        overloadPolicy: RuntimeOverloadPolicySpec?,
    )

    override fun close() {
        error("use suspend shutdown() to close ConnectorOrchestrator")
    }

    /** Kotlin-facing request and shutdown API. */
    interface Kotlin {
        /**
         * Sends a reply-capable request and waits inline for its terminal outcome.
         *
         * This is the suspend-and-wait host API shape over the same runtime request/reply contract that
         * [submitRequest] can expose as submit-first, consume-later.
         */
        suspend fun ask(
            source: String,
            target: String,
            payloadUtf8: String,
            timeoutMs: Long = 1_000,
            operation: String = "ask",
            deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
        ): ConnectorEnvelope

        /** Typed variant of [ask] for callers that want inline wait plus declared payload identity. */
        suspend fun ask(
            source: String,
            target: String,
            payloadUtf8: String,
            payloadIdentity: ConnectorPayloadIdentity,
            timeoutMs: Long = 1_000,
            operation: String = "ask",
            deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
        ): ConnectorEnvelope

        /**
         * Sends a plain-text typed request and returns the plain-text reply body.
         *
         * This is the Level 1 request API for Kotlin callers.
         */
        suspend fun askText(
            source: String,
            target: String,
            payloadUtf8: String,
            timeoutMs: Long = 1_000,
            operation: String = "ask",
            deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
        ): String =
            ask(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                payloadIdentity = ConnectorPayloadIdentity.text("$target.request"),
                timeoutMs = timeoutMs,
                operation = operation,
                deliveryHint = deliveryHint,
            ).payloadUtf8()

        /** Submits a reply-capable request and returns its tracking identity without waiting for the terminal outcome. */
        suspend fun submitRequest(
            source: String,
            target: String,
            payloadUtf8: String,
            timeoutMs: Long = 1_000,
            operation: String = "ask",
            deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
        ): SubmittedRequest

        /** Typed variant of [submitRequest] for callers that want submit-first plus declared payload identity. */
        suspend fun submitRequest(
            source: String,
            target: String,
            payloadUtf8: String,
            payloadIdentity: ConnectorPayloadIdentity,
            timeoutMs: Long = 1_000,
            operation: String = "ask",
            deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
        ): SubmittedRequest

        /** Explicit raw alias for callers that intentionally stay below the typed payload contract. */
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

        /** Submits a pre-built reply-capable request while keeping it on the terminal-event lane. */
        suspend fun submitRequest(envelope: ConnectorEnvelope): SubmittedRequest

        /** Returns a per-subscriber flow of terminal request outcomes from the orchestrator-owned client. */
        fun terminalEvents(
            bufferCapacity: Int = RuntimeClient.DEFAULT_TERMINAL_EVENT_BUFFER_CAPACITY,
        ): Flow<RequestTerminalEvent>

        /** Returns a per-subscriber flow of every deadletter drained by the connector. */
        fun deadletters(
            bufferCapacity: Int = RuntimeClient.DEFAULT_DEADLETTER_BUFFER_CAPACITY,
        ): Flow<ObservedDeadletter>

        /** Explicit raw alias for callers that intentionally stay below the typed payload contract. */
        suspend fun askRaw(
            source: String,
            target: String,
            payloadUtf8: String,
            timeoutMs: Long = 1_000,
            operation: String = "ask",
            deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
        ): ConnectorEnvelope =
            ask(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                timeoutMs = timeoutMs,
                operation = operation,
                deliveryHint = deliveryHint,
            )

        /** Sends a fire-and-forget request through the orchestrator-owned client. */
        suspend fun sendOneWay(
            source: String,
            target: String,
            payloadUtf8: String,
            deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
        )

        /** Sends a typed fire-and-forget request through the orchestrator-owned client. */
        suspend fun sendOneWay(
            source: String,
            target: String,
            payloadUtf8: String,
            payloadIdentity: ConnectorPayloadIdentity,
            deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
        )

        /** Explicit raw alias for callers that intentionally skip typed payload identity. */
        suspend fun sendOneWayRaw(
            source: String,
            target: String,
            payloadUtf8: String,
            deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
        ) {
            sendOneWay(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                deliveryHint = deliveryHint,
            )
        }

        /** Submits a pre-built envelope for advanced use cases that need full message control. */
        suspend fun submitEnvelope(envelope: ConnectorEnvelope)

        /** Submits a typed envelope after enforcing payload identity. */
        suspend fun submitTypedEnvelope(envelope: ConnectorEnvelope)

        /** Explicit raw alias for pre-built envelopes that intentionally skip typed validation. */
        suspend fun submitRawEnvelope(envelope: ConnectorEnvelope) {
            submitEnvelope(envelope)
        }

        /** Suspends until the underlying client and runtime are fully shut down. */
        suspend fun shutdown()
    }

    /** Java-facing request and shutdown API. */
    interface Java {
        /**
         * Java-friendly request API that completes with the reply envelope or fails exceptionally.
         */
        fun ask(
            source: String,
            target: String,
            payloadUtf8: String,
            timeoutMs: Long = 1_000,
            operation: String = "ask",
            deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
        ): CompletableFuture<ConnectorEnvelope>

        /** Java-friendly typed request API that enforces payload identity. */
        fun ask(
            source: String,
            target: String,
            payloadUtf8: String,
            payloadIdentity: ConnectorPayloadIdentity,
            timeoutMs: Long = 1_000,
            operation: String = "ask",
            deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
        ): CompletableFuture<ConnectorEnvelope>

        /** Java-friendly plain-text request API. */
        fun askText(
            source: String,
            target: String,
            payloadUtf8: String,
            timeoutMs: Long = 1_000,
            operation: String = "ask",
            deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
        ): CompletableFuture<String> =
            ask(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                payloadIdentity = ConnectorPayloadIdentity.text("$target.request"),
                timeoutMs = timeoutMs,
                operation = operation,
                deliveryHint = deliveryHint,
            ).thenApply { response -> response.payloadUtf8() }

        /** Java-friendly plain-text request overload with default timeout, operation, and delivery hint. */
        fun askText(
            source: String,
            target: String,
            payloadUtf8: String,
        ): CompletableFuture<String> =
            askText(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                timeoutMs = 1_000,
                operation = "ask",
                deliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
            )

        /** Blocking plain-text request helper for simple Java hosts. */
        fun askTextBlocking(
            source: String,
            target: String,
            payloadUtf8: String,
            timeoutMs: Long = 1_000,
            operation: String = "ask",
            deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
        ): String =
            askText(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                timeoutMs = timeoutMs,
                operation = operation,
                deliveryHint = deliveryHint,
            ).get()

        /** Blocking plain-text request overload with default timeout, operation, and delivery hint. */
        fun askTextBlocking(
            source: String,
            target: String,
            payloadUtf8: String,
        ): String =
            askTextBlocking(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                timeoutMs = 1_000,
                operation = "ask",
                deliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
            )

        /** Java-friendly overload with default timeout, operation, and delivery hint. */
        fun ask(
            source: String,
            target: String,
            payloadUtf8: String,
        ): CompletableFuture<ConnectorEnvelope> =
            ask(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                timeoutMs = 1_000,
                operation = "ask",
                deliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
            )

        /** Java-friendly overload with explicit timeout and default operation plus delivery hint. */
        fun ask(
            source: String,
            target: String,
            payloadUtf8: String,
            timeoutMs: Long,
        ): CompletableFuture<ConnectorEnvelope> =
            ask(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                timeoutMs = timeoutMs,
                operation = "ask",
                deliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
            )

        /** Java-friendly overload with explicit timeout and operation but default delivery hint. */
        fun ask(
            source: String,
            target: String,
            payloadUtf8: String,
            timeoutMs: Long,
            operation: String,
        ): CompletableFuture<ConnectorEnvelope> =
            ask(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                timeoutMs = timeoutMs,
                operation = operation,
                deliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
            )

        /** Java-friendly typed overload with default timeout, operation, and delivery hint. */
        fun ask(
            source: String,
            target: String,
            payloadUtf8: String,
            payloadIdentity: ConnectorPayloadIdentity,
        ): CompletableFuture<ConnectorEnvelope> =
            ask(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                payloadIdentity = payloadIdentity,
                timeoutMs = 1_000,
                operation = "ask",
                deliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
            )

        /** Java-friendly typed overload with explicit timeout and default operation plus delivery hint. */
        fun ask(
            source: String,
            target: String,
            payloadUtf8: String,
            payloadIdentity: ConnectorPayloadIdentity,
            timeoutMs: Long,
        ): CompletableFuture<ConnectorEnvelope> =
            ask(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                payloadIdentity = payloadIdentity,
                timeoutMs = timeoutMs,
                operation = "ask",
                deliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
            )

        /** Java-friendly explicit raw alias for callers that intentionally skip typed validation. */
        fun askRaw(
            source: String,
            target: String,
            payloadUtf8: String,
            timeoutMs: Long = 1_000,
            operation: String = "ask",
            deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
        ): CompletableFuture<ConnectorEnvelope> =
            ask(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                timeoutMs = timeoutMs,
                operation = operation,
                deliveryHint = deliveryHint,
            )

        /** Java-friendly one-way send API that completes when the request has been submitted to the runtime. */
        fun sendOneWay(
            source: String,
            target: String,
            payloadUtf8: String,
            deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
        ): CompletableFuture<Void?>

        /** Java-friendly typed one-way send API that enforces payload identity. */
        fun sendOneWay(
            source: String,
            target: String,
            payloadUtf8: String,
            payloadIdentity: ConnectorPayloadIdentity,
            deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
        ): CompletableFuture<Void?>

        /** Java-friendly overload with default delivery hint. */
        fun sendOneWay(
            source: String,
            target: String,
            payloadUtf8: String,
        ): CompletableFuture<Void?> =
            sendOneWay(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                deliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
            )

        /** Java-friendly typed overload with default delivery hint. */
        fun sendOneWay(
            source: String,
            target: String,
            payloadUtf8: String,
            payloadIdentity: ConnectorPayloadIdentity,
        ): CompletableFuture<Void?> =
            sendOneWay(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                payloadIdentity = payloadIdentity,
                deliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
            )

        /** Java-friendly explicit raw alias for callers that intentionally skip typed validation. */
        fun sendOneWayRaw(
            source: String,
            target: String,
            payloadUtf8: String,
            deliveryHint: ConnectorDeliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
        ): CompletableFuture<Void?> =
            sendOneWay(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                deliveryHint = deliveryHint,
            )

        /** Java-friendly advanced submission API for callers that need full control over message shape. */
        fun submitEnvelope(envelope: ConnectorEnvelope): CompletableFuture<Void?>

        /** Java-friendly typed submission API that enforces payload identity. */
        fun submitTypedEnvelope(envelope: ConnectorEnvelope): CompletableFuture<Void?>

        /** Java-friendly explicit raw alias for advanced custom envelopes. */
        fun submitRawEnvelope(envelope: ConnectorEnvelope): CompletableFuture<Void?> =
            submitEnvelope(envelope)

        /** Java-friendly shutdown API that completes once the connector runtime is fully closed. */
        fun shutdown(): CompletableFuture<Void?>

        /**
         * Java-friendly deadletter observation API.
         *
         * The listener sees deadletters that also fail `ask(...)` plus unhandled
         * deadletters. The returned subscription must be closed by the application.
         */
        fun subscribeDeadletters(
            listener: DeadletterListener,
            bufferCapacity: Int = RuntimeClient.DEFAULT_DEADLETTER_BUFFER_CAPACITY,
        ): DeadletterSubscription

        /** Java-friendly overload using the default deadletter buffer capacity. */
        fun subscribeDeadletters(listener: DeadletterListener): DeadletterSubscription =
            subscribeDeadletters(
                listener = listener,
                bufferCapacity = RuntimeClient.DEFAULT_DEADLETTER_BUFFER_CAPACITY,
            )
    }

    companion object {
        /**
         * Opens the native runtime, applies the initial startup snapshot, starts it,
         * and returns an orchestrator for this JVM process.
         */
        @JvmStatic
        @JvmOverloads
        fun start(runtimeLibPath: String? = null, startSpec: RuntimeStartSpec): ConnectorOrchestrator =
            ConnectorOrchestratorFactory.start(runtimeLibPath, startSpec)
    }
}

private class ConnectorOrchestratorImpl(
    private val handle: RuntimeHandle,
    private val client: RuntimeClient,
) : ConnectorOrchestrator {
    private val asyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val kotlinApi = KotlinApi()
    private val javaApi = JavaApi()

    override val kotlin: ConnectorOrchestrator.Kotlin
        get() = kotlinApi

    override val java: ConnectorOrchestrator.Java
        get() = javaApi

    override val control: RuntimeControlClient
        get() = handle.controlClient

    override val monitor: RuntimeMonitor
        get() = handle.monitor

    override fun health(): RuntimeHealthSnapshot = handle.health()

    override fun stats(): RuntimeStatsSnapshot = handle.stats()

    override fun runtimeInfo(): RuntimeInfoSnapshot = handle.runtimeInfo()

    override fun runtimeConfig(): RuntimeConfigSnapshot = handle.config()

    override fun runtimeCapabilities(): RuntimeCapabilitiesSnapshot = handle.runtimeCapabilities()

    override fun tcpConnectionConfig(): RuntimeTcpConnectionConfigSnapshot =
        handle.tcpConnectionConfig()

    override fun tcpSecurityInfo(): RuntimeTcpSecurityInfoSnapshot = handle.tcpSecurityInfo()

    override fun startupConnectionResult(): RuntimeTcpConnectionApplyResult? =
        handle.startupConnectionResult()

    override fun startupSecurityResult(): RuntimeTcpSecurityApplyResult? =
        handle.startupSecurityResult()

    override fun applyTcpConnectionStrategy(
        spec: RuntimeTcpConnectionStrategySpec,
    ): RuntimeTcpConnectionApplyResult = handle.applyTcpConnectionStrategy(spec)

    override fun applyTcpSecurity(spec: RuntimeTcpSecuritySpec): RuntimeTcpSecurityApplyResult =
        handle.applyTcpSecurity(spec)

    override fun registerHandler(
        target: String,
        handler: suspend (ConnectorEnvelope) -> ConnectorEnvelope?,
    ) {
        client.registerHandler(target, handler)
    }

    override fun clientStats(): RuntimeClientStats = client.snapshotStats()

    override fun applySnapshot(
        generation: Long,
        routes: List<RuntimeRouteSpec>,
        sourceConnector: String,
    ) {
        control.applySnapshot(generation, routes, sourceConnector)
    }

    override fun applySnapshot(
        generation: Long,
        routes: List<RuntimeRouteSpec>,
        sourceConnector: String,
        overloadPolicy: RuntimeOverloadPolicySpec?,
    ) {
        control.applySnapshot(generation, routes, sourceConnector, overloadPolicy)
    }

    private inner class KotlinApi : ConnectorOrchestrator.Kotlin {
        override suspend fun ask(
            source: String,
            target: String,
            payloadUtf8: String,
            timeoutMs: Long,
            operation: String,
            deliveryHint: ConnectorDeliveryHint,
        ): ConnectorEnvelope =
            client.ask(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                timeoutMs = timeoutMs,
                operation = operation,
                deliveryHint = deliveryHint,
            )

        override suspend fun ask(
            source: String,
            target: String,
            payloadUtf8: String,
            payloadIdentity: ConnectorPayloadIdentity,
            timeoutMs: Long,
            operation: String,
            deliveryHint: ConnectorDeliveryHint,
        ): ConnectorEnvelope =
            client.ask(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                payloadIdentity = payloadIdentity,
                timeoutMs = timeoutMs,
                operation = operation,
                deliveryHint = deliveryHint,
            )

        override suspend fun submitRequest(
            source: String,
            target: String,
            payloadUtf8: String,
            timeoutMs: Long,
            operation: String,
            deliveryHint: ConnectorDeliveryHint,
        ): SubmittedRequest =
            client.submitRequest(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                timeoutMs = timeoutMs,
                operation = operation,
                deliveryHint = deliveryHint,
            )

        override suspend fun submitRequest(
            source: String,
            target: String,
            payloadUtf8: String,
            payloadIdentity: ConnectorPayloadIdentity,
            timeoutMs: Long,
            operation: String,
            deliveryHint: ConnectorDeliveryHint,
        ): SubmittedRequest =
            client.submitRequest(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                payloadIdentity = payloadIdentity,
                timeoutMs = timeoutMs,
                operation = operation,
                deliveryHint = deliveryHint,
            )

        override suspend fun submitRequest(envelope: ConnectorEnvelope): SubmittedRequest =
            client.submitRequest(envelope)

        override fun terminalEvents(bufferCapacity: Int): Flow<RequestTerminalEvent> =
            client.terminalEvents(bufferCapacity)

        override fun deadletters(bufferCapacity: Int): Flow<ObservedDeadletter> =
            client.deadletters(bufferCapacity)

        override suspend fun sendOneWay(
            source: String,
            target: String,
            payloadUtf8: String,
            deliveryHint: ConnectorDeliveryHint,
        ) {
            client.sendOneWay(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                deliveryHint = deliveryHint,
            )
        }

        override suspend fun sendOneWay(
            source: String,
            target: String,
            payloadUtf8: String,
            payloadIdentity: ConnectorPayloadIdentity,
            deliveryHint: ConnectorDeliveryHint,
        ) {
            client.sendOneWay(
                source = source,
                target = target,
                payloadUtf8 = payloadUtf8,
                payloadIdentity = payloadIdentity,
                deliveryHint = deliveryHint,
            )
        }

        override suspend fun submitEnvelope(envelope: ConnectorEnvelope) {
            client.submitEnvelope(envelope)
        }

        override suspend fun submitTypedEnvelope(envelope: ConnectorEnvelope) {
            client.submitTypedEnvelope(envelope)
        }

        override suspend fun shutdown() {
            try {
                client.shutdown()
            } finally {
                asyncScope.cancel()
                ConnectorOrchestratorFactory.clear(this@ConnectorOrchestratorImpl)
            }
        }
    }

    private inner class JavaApi : ConnectorOrchestrator.Java {
        override fun ask(
            source: String,
            target: String,
            payloadUtf8: String,
            timeoutMs: Long,
            operation: String,
            deliveryHint: ConnectorDeliveryHint,
        ): CompletableFuture<ConnectorEnvelope> =
            asyncScope.future {
                kotlinApi.ask(
                    source = source,
                    target = target,
                    payloadUtf8 = payloadUtf8,
                    timeoutMs = timeoutMs,
                    operation = operation,
                    deliveryHint = deliveryHint,
                )
            }

        override fun ask(
            source: String,
            target: String,
            payloadUtf8: String,
            payloadIdentity: ConnectorPayloadIdentity,
            timeoutMs: Long,
            operation: String,
            deliveryHint: ConnectorDeliveryHint,
        ): CompletableFuture<ConnectorEnvelope> =
            asyncScope.future {
                kotlinApi.ask(
                    source = source,
                    target = target,
                    payloadUtf8 = payloadUtf8,
                    payloadIdentity = payloadIdentity,
                    timeoutMs = timeoutMs,
                    operation = operation,
                    deliveryHint = deliveryHint,
                )
            }

        override fun sendOneWay(
            source: String,
            target: String,
            payloadUtf8: String,
            deliveryHint: ConnectorDeliveryHint,
        ): CompletableFuture<Void?> =
            asyncScope.future {
                kotlinApi.sendOneWay(
                    source = source,
                    target = target,
                    payloadUtf8 = payloadUtf8,
                    deliveryHint = deliveryHint,
                )
                null
            }

        override fun sendOneWay(
            source: String,
            target: String,
            payloadUtf8: String,
            payloadIdentity: ConnectorPayloadIdentity,
            deliveryHint: ConnectorDeliveryHint,
        ): CompletableFuture<Void?> =
            asyncScope.future {
                kotlinApi.sendOneWay(
                    source = source,
                    target = target,
                    payloadUtf8 = payloadUtf8,
                    payloadIdentity = payloadIdentity,
                    deliveryHint = deliveryHint,
                )
                null
            }

        override fun submitEnvelope(envelope: ConnectorEnvelope): CompletableFuture<Void?> =
            asyncScope.future {
                kotlinApi.submitEnvelope(envelope)
                null
            }

        override fun submitTypedEnvelope(envelope: ConnectorEnvelope): CompletableFuture<Void?> =
            asyncScope.future {
                kotlinApi.submitTypedEnvelope(envelope)
                null
            }

        override fun shutdown(): CompletableFuture<Void?> =
            CompletableFuture.supplyAsync {
                runBlocking {
                    kotlinApi.shutdown()
                }
                null
            }

        override fun subscribeDeadletters(
            listener: DeadletterListener,
            bufferCapacity: Int,
        ): DeadletterSubscription {
            val subscribed = CompletableDeferred<Unit>()
            val job = asyncScope.launch {
                client.deadletters(bufferCapacity)
                    .onStart { subscribed.complete(Unit) }
                    .collect { observed ->
                        listener.onDeadletter(observed)
                    }
            }
            runBlocking { subscribed.await() }
            return object : DeadletterSubscription {
                override fun close() {
                    job.cancel()
                }
            }
        }
    }
}

private object ConnectorOrchestratorFactory {
    private val lock = Any()

    fun start(runtimeLibPath: String? = null, startSpec: RuntimeStartSpec): ConnectorOrchestrator {
        synchronized(lock) {
            val handle = RuntimeHandle.open(runtimeLibPath, startSpec)
            handle.start()
            return ConnectorOrchestratorImpl(
                handle = handle,
                client = RuntimeClient(handle),
            )
        }
    }

    fun clear(orchestrator: ConnectorOrchestratorImpl) {
        synchronized(lock) {
            // Factory lock remains the lifecycle serialization point for future shared state.
        }
    }
}
