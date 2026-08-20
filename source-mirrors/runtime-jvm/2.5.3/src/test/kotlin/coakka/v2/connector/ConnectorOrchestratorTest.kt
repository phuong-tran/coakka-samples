package coakka.v2.connector

import coakka.v2.connector.protocol.ConnectorPayloadFormat
import coakka.v2.connector.protocol.ConnectorPayloadIdentity
import coakka.v2.connector.protocol.RequestTerminalEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class ConnectorOrchestratorTest {
    @Test
    fun coAkkaLocalFacadeHidesRouteAndPayloadIdentityForTextAsk() = runBlocking {
        val runtime = CoAkka.local(
            systemName = "connector-kotlin-practice",
            runtimeLibPath = TestSupport.runtimeLibPath(),
            nodeId = "node-local-facade",
        )

        try {
            runtime.handler("hello.en") { name ->
                "Hello $name"
            }

            assertEquals("Hello Nam", runtime.ask("hello.en", "Nam"))
        } finally {
            runtime.shutdown()
        }
    }

    @Test
    fun runtimeInfoExposesReadableMetadataThroughOrchestrator() = runBlocking {
        val orchestrator = ConnectorOrchestrator.start(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "connector-kotlin-test",
                nodeId = "node-orchestrator-info",
                routes = TestSupport.localRoutes("svc.echo"),
            ),
        )

        try {
            val runtimeInfo = orchestrator.runtimeInfo()

            assertEquals(CoakkaV2Library.ABI_VERSION, runtimeInfo.abiVersion)
            assertTrue(runtimeInfo.runtimeVersion.isNotBlank())
            assertTrue(runtimeInfo.southboundBackend.isNotBlank())
            assertTrue(runtimeInfo.allocatorBackend.isNotBlank())
            assertTrue(runtimeInfo.featureFlagsText.isNotBlank())
            assertTrue((runtimeInfo.featureFlags and CoakkaRuntimeFeatures.REQUEST_PIPE) != 0)
            assertTrue((runtimeInfo.featureFlags and CoakkaRuntimeFeatures.CONTROL_PIPE) != 0)
            assertTrue((runtimeInfo.featureFlags and CoakkaRuntimeFeatures.MONITOR) != 0)
            assertTrue((runtimeInfo.featureFlags and CoakkaRuntimeFeatures.DELIVERED_REQUEST_PIPE) != 0)
        } finally {
            orchestrator.kotlin.shutdown()
        }
    }

    @Test
    fun kotlinApiRoundTripWorksWithSeparateDeliveredRequestLane() = runBlocking {
        val orchestrator = ConnectorOrchestrator.start(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "connector-kotlin-test",
                nodeId = "node-orchestrator-separate-lane",
                separateDeliveredRequestLane = true,
                routes = TestSupport.localRoutes("svc.echo"),
            ),
        )

        try {
            assertTrue(orchestrator.monitor.isEnabled())
            assertEquals(1, orchestrator.runtimeConfig().routeCount)

            orchestrator.registerHandler("svc.echo") { request ->
                RuntimeClient.replyTo(
                    request = request,
                    source = "svc.echo",
                    payloadUtf8 = "reply:${request.payloadUtf8()}",
                )
            }

            val response = orchestrator.kotlin.ask(
                source = "test-client",
                target = "svc.echo",
                payloadUtf8 = "hello",
            )

            assertEquals("reply:hello", response.payloadUtf8())
            assertEquals("test-client", response.target)
            assertTrue(orchestrator.clientStats().matchedResponses >= 1)
            assertEquals(1, orchestrator.stats().routeCount)
        } finally {
            orchestrator.kotlin.shutdown()
        }
    }

    @Test
    fun javaApiRoundTripWorksForLocalTarget() = runBlocking {
        val orchestrator = ConnectorOrchestrator.start(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "connector-kotlin-test",
                nodeId = "node-orchestrator-java",
                routes = TestSupport.localRoutes("svc.echo"),
            ),
        )

        try {
            orchestrator.registerHandler("svc.echo") { request ->
                RuntimeClient.replyTo(
                    request = request,
                    source = "svc.echo",
                    payloadUtf8 = "reply:${request.payloadUtf8()}",
                )
            }

            val response = orchestrator.java.ask(
                "test-client",
                "svc.echo",
                "hello",
            ).get()

            assertEquals("reply:hello", response.payloadUtf8())
            assertEquals("test-client", response.target)
            assertTrue(orchestrator.clientStats().matchedResponses >= 1)
        } finally {
            orchestrator.kotlin.shutdown()
        }
    }

    @Test
    fun javaTextHelpersHideEnvelopeAndPayloadIdentityForHappyPath() = runBlocking {
        val orchestrator = ConnectorOrchestrator.start(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "connector-kotlin-test",
                nodeId = "node-orchestrator-java-text",
                routes = TestSupport.localRoutes("svc.echo.text"),
            ),
        )

        try {
            orchestrator.registerTextHandler("svc.echo.text") { payload ->
                "reply:$payload"
            }

            val response = orchestrator.java.askTextBlocking(
                "test-client",
                "svc.echo.text",
                "hello",
            )

            assertEquals("reply:hello", response)
            assertTrue(orchestrator.clientStats().matchedResponses >= 1)
        } finally {
            orchestrator.kotlin.shutdown()
        }
    }

    @Test
    fun javaApiTypedAskCarriesPayloadIdentity() = runBlocking {
        val orchestrator = ConnectorOrchestrator.start(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "connector-kotlin-test",
                nodeId = "node-orchestrator-java-typed",
                routes = TestSupport.localRoutes("svc.echo"),
            ),
        )

        try {
            orchestrator.registerHandler("svc.echo") { request ->
                RuntimeClient.replyTypedTo(
                    request = request,
                    source = "svc.echo",
                    payloadUtf8 = "reply:${request.payloadUtf8()}",
                )
            }

            val response = orchestrator.java.ask(
                "test-client",
                "svc.echo",
                "hello",
                ConnectorPayloadIdentity.text("echo.request.v1"),
            ).get()

            assertEquals("reply:hello", response.payloadUtf8())
            assertEquals("echo.request.v1", response.messageType)
            assertEquals(1, response.payloadSchemaVersion)
            assertEquals(ConnectorPayloadFormat.TEXT, response.payloadFormat)
        } finally {
            orchestrator.kotlin.shutdown()
        }
    }

    @Test
    fun javaApiSendOneWayDeliversWithoutResponseState() = runBlocking {
        val orchestrator = ConnectorOrchestrator.start(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "connector-kotlin-test",
                nodeId = "node-orchestrator-java-oneway",
                routes = TestSupport.localRoutes("svc.audit"),
            ),
        )

        try {
            val seen = CompletableDeferred<String>()
            orchestrator.registerHandler("svc.audit") { request ->
                seen.complete(request.payloadUtf8())
                null
            }

            orchestrator.java.sendOneWay(
                "test-client",
                "svc.audit",
                "fire-and-forget",
            ).get()

            assertEquals("fire-and-forget", withTimeout(1_000) { seen.await() })
            val clientStats = orchestrator.clientStats()
            assertTrue(clientStats.deliveredRequests >= 1)
            assertEquals(0, clientStats.matchedResponses)
        } finally {
            orchestrator.kotlin.shutdown()
        }
    }

    @Test
    fun kotlinSubmitRequestPublishesTerminalEvent() = runBlocking {
        val orchestrator = ConnectorOrchestrator.start(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "connector-kotlin-test",
                nodeId = "node-orchestrator-submit-request",
                routes = TestSupport.localRoutes("svc.echo"),
            ),
        )

        try {
            orchestrator.registerHandler("svc.echo") { request ->
                RuntimeClient.replyTo(
                    request = request,
                    source = "svc.echo",
                    payloadUtf8 = "reply:${request.payloadUtf8()}",
                )
            }

            val subscribed = CompletableDeferred<Unit>()
            val terminalEventDeferred = async {
                orchestrator.kotlin.terminalEvents()
                    .onStart { subscribed.complete(Unit) }
                    .first()
            }
            subscribed.await()
            val submitted = orchestrator.kotlin.submitRequest(
                source = "test-client",
                target = "svc.echo",
                payloadUtf8 = "hello",
            )
            val terminalEvent = withTimeout(1_000) { terminalEventDeferred.await() }

            when (terminalEvent) {
                is RequestTerminalEvent.Response -> {
                    assertEquals(submitted.messageId, terminalEvent.requestMessageId)
                    assertEquals(submitted.correlationId, terminalEvent.correlationId)
                    assertEquals("reply:hello", terminalEvent.envelope.payloadUtf8())
                }

                is RequestTerminalEvent.Deadletter ->
                    error("expected response terminal event, got deadletter ${terminalEvent.deadletter.reason}")
            }
        } finally {
            orchestrator.kotlin.shutdown()
        }
    }

    @Test
    fun javaApiSubmitEnvelopeAllowsCustomOneWayRequestShape() = runBlocking {
        val orchestrator = ConnectorOrchestrator.start(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "connector-kotlin-test",
                nodeId = "node-orchestrator-java-submit",
                routes = TestSupport.localRoutes("svc.custom"),
            ),
        )

        try {
            val seen = CompletableDeferred<Pair<String?, String>>()
            orchestrator.registerHandler("svc.custom") { request ->
                seen.complete(request.headers["operation"] to request.payloadUtf8())
                null
            }

            orchestrator.java.submitEnvelope(
                coakka.v2.connector.protocol.ConnectorEnvelope(
                    messageId = "custom-1",
                    correlationId = "",
                    source = "test-client",
                    target = "svc.custom",
                    kind = coakka.v2.connector.protocol.ConnectorMessageKind.REQUEST,
                    oneWay = true,
                    payload = "payload".toByteArray(),
                    headers = mapOf("operation" to "custom-op"),
                ),
            ).get()

            val observed = withTimeout(1_000) { seen.await() }
            assertEquals("custom-op", observed.first)
            assertEquals("payload", observed.second)
        } finally {
            orchestrator.kotlin.shutdown()
        }
    }

    @Test
    fun kotlinTypedSubmitRejectsMissingPayloadIdentity() = runBlocking {
        val orchestrator = ConnectorOrchestrator.start(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "connector-kotlin-test",
                nodeId = "node-orchestrator-submit-typed-reject",
                routes = TestSupport.localRoutes("svc.custom"),
            ),
        )

        try {
            val error = assertFailsWith<IllegalArgumentException> {
                orchestrator.kotlin.submitTypedEnvelope(
                    coakka.v2.connector.protocol.ConnectorEnvelope(
                        messageId = "typed-submit-1",
                        source = "test-client",
                        target = "svc.custom",
                        kind = coakka.v2.connector.protocol.ConnectorMessageKind.REQUEST,
                        oneWay = true,
                        payload = "payload".toByteArray(),
                    ),
                )
            }

            assertEquals("submitTypedEnvelope requires messageType", error.message)
        } finally {
            orchestrator.kotlin.shutdown()
        }
    }

    @Test
    fun applySnapshotUpdatesMonitorAndRuntimeViews() = runBlocking {
        val orchestrator = ConnectorOrchestrator.start(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "connector-kotlin-test",
                nodeId = "node-orchestrator-monitor",
                generation = 5,
                routes = TestSupport.localRoutes("svc.echo"),
            ),
        )

        try {
            while (orchestrator.monitor.awaitNextBlocking(10) != null) {
                // Drain startup wakeups so the assertion below targets the new apply.
            }

            orchestrator.applySnapshot(
                generation = 6,
                routes = TestSupport.localRoutes("svc.echo", "svc.audit"),
                sourceConnector = "connector-kotlin-test",
            )

            val update = assertNotNull(orchestrator.monitor.awaitNextBlocking(1_000))
            assertTrue(update.signalCount > 0)
            assertEquals(6, update.health.appliedGeneration)
            assertEquals(6, update.stats.appliedGeneration)
            assertEquals(2, update.stats.routeCount)
            assertEquals(6, orchestrator.health().appliedGeneration)
            assertEquals(2, orchestrator.runtimeConfig().routeCount)
            assertEquals(2, orchestrator.stats().routeCount)
        } finally {
            orchestrator.kotlin.shutdown()
        }
    }

    @Test
    fun startAllowsMultipleActiveOrchestrators() = runBlocking {
        val first = ConnectorOrchestrator.start(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "connector-kotlin-test",
                nodeId = "node-orchestrator-first",
                routes = TestSupport.localRoutes("svc.echo"),
            ),
        )
        val second = ConnectorOrchestrator.start(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "connector-kotlin-test",
                nodeId = "node-orchestrator-second",
                routes = TestSupport.localRoutes("svc.echo"),
            ),
        )

        try {
            assertEquals("node-orchestrator-first", first.runtimeConfig().nodeId)
            assertEquals("node-orchestrator-second", second.runtimeConfig().nodeId)
        } finally {
            second.kotlin.shutdown()
            first.kotlin.shutdown()
        }
    }
}
