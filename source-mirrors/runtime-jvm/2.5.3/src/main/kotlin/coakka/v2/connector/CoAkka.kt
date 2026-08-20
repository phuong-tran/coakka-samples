package coakka.v2.connector

import kotlinx.coroutines.runBlocking

/**
 * Beginner-facing connector entrypoint.
 *
 * Most application code should start here and only drop to [ConnectorOrchestrator],
 * [RuntimeStartSpec], or raw envelopes when it needs explicit control-plane
 * snapshots or custom transport fields.
 */
object CoAkka {
    /**
     * Creates a same-process local text runtime facade.
     *
     * [runtimeLibPath] may be omitted when the connector jar carries an embedded
     * native runtime or `-Dcoakka.runtime.lib=...` is configured.
     */
    @JvmStatic
    @JvmOverloads
    fun local(
        systemName: String,
        runtimeLibPath: String? = null,
        nodeId: String = "node-$systemName",
    ): LocalRuntime =
        LocalRuntime(
            runtimeLibPath = runtimeLibPath,
            systemName = systemName,
            nodeId = nodeId,
        )
}

/**
 * Level 1 local text API for request/reply practice and same-process tests.
 *
 * This facade hides route snapshots, endpoint host/port placeholders, and
 * payload identity for plain-text handlers. It still uses the production
 * [ConnectorOrchestrator] and native runtime underneath.
 */
class LocalRuntime internal constructor(
    private val runtimeLibPath: String?,
    private val systemName: String,
    private val nodeId: String,
) : AutoCloseable {
    private val localRoutePort = 0
    private val localTargets = LinkedHashSet<String>()
    private var generation = 1L
    private var orchestrator: ConnectorOrchestrator? = null
    private var closed = false

    /**
     * Registers a plain-text local handler for [target].
     *
     * Adding a handler after startup applies a newer route snapshot generation.
     */
    @Synchronized
    fun handler(target: String, handler: suspend (String) -> String): LocalRuntime {
        check(!closed) { "local runtime is closed" }
        check(localTargets.add(target)) { "handler already registered for target=$target" }

        val requestHandler: suspend (coakka.v2.connector.protocol.ConnectorEnvelope) -> coakka.v2.connector.protocol.ConnectorEnvelope? = { request ->
            RuntimeClient.replyTextTo(
                request = request,
                source = target,
                payloadUtf8 = handler(request.payloadUtf8()),
                messageType = "$target.reply",
            )
        }

        val current = orchestrator
        if (current == null) {
            val created = ConnectorOrchestrator.start(
                runtimeLibPath = runtimeLibPath,
                startSpec = RuntimeStartSpec(
                    systemName = systemName,
                    nodeId = nodeId,
                    generation = generation,
                    routes = localRoutesLocked(),
                ),
            )
            created.registerHandler(target, requestHandler)
            orchestrator = created
            return this
        }

        current.registerHandler(target, requestHandler)
        generation += 1
        current.applySnapshot(
            generation = generation,
            routes = localRoutesLocked(),
            sourceConnector = systemName,
        )
        return this
    }

    /** Sends one plain-text request and returns the plain-text reply body. */
    suspend fun ask(
        target: String,
        payloadUtf8: String,
        timeoutMs: Long = 1_000,
    ): String {
        val current = synchronized(this) {
            check(!closed) { "local runtime is closed" }
            check(localTargets.contains(target)) { "no local handler registered for target=$target" }
            checkNotNull(orchestrator) { "local runtime has not started; register a handler first" }
        }
        return current.kotlin.askText(
            source = "$systemName/local",
            target = target,
            payloadUtf8 = payloadUtf8,
            timeoutMs = timeoutMs,
            operation = "local_text_ask",
        )
    }

    /** Suspends until the underlying orchestrator has shut down. */
    suspend fun shutdown() {
        val toClose = synchronized(this) {
            if (closed) {
                null
            } else {
                closed = true
                val current = orchestrator
                orchestrator = null
                current
            }
        }
        toClose?.kotlin?.shutdown()
    }

    override fun close() {
        runBlocking {
            shutdown()
        }
    }

    private fun localRoutesLocked(): List<RuntimeRouteSpec> =
        RuntimeClient.localRoutes(localTargets.toList(), localRoutePort)
}
