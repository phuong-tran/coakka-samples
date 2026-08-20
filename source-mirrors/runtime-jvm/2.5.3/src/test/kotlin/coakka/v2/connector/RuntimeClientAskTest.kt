package coakka.v2.connector

import coakka.v2.connector.protocol.ConnectorPayloadFormat
import coakka.v2.connector.protocol.ConnectorPayloadIdentity
import coakka.v2.connector.protocol.RequestTerminalEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class RuntimeClientAskTest {
    @Test
    fun connectorTextIdentityAndTypedRouteFlagsUseFriendlyNames() {
        val identity = ConnectorPayloadIdentity.text("demo.echo.request.v1")
        val route = RuntimeClient.localRoute("svc.echo")

        assertEquals("demo.echo.request.v1", identity.messageType)
        assertEquals(1, identity.payloadSchemaVersion)
        assertEquals(ConnectorPayloadFormat.TEXT, identity.payloadFormat)
        assertEquals(0, route.endpoints.single().port)
        assertEquals(RuntimeEndpointFlags.LOCAL, route.endpoints.single().flags)
        assertEquals(RuntimeRouteFlags.NONE, route.flags)
    }

    @Test
    fun localRoutesUseZeroPortMetadataByDefault() {
        val routes = RuntimeClient.localRoutes(listOf("svc.echo", "svc.audit"))
        val resolvedPorts = routes.map { it.endpoints.single().port }.toSet()

        assertEquals(1, resolvedPorts.size)
        assertEquals(0, resolvedPorts.single())
        assertTrue(routes.all { it.endpoints.single().host == "127.0.0.1" })
    }

    @Test
    fun askRoundTripWorksForLocalTarget() = runBlocking {
        val client = RuntimeClient.startLocal(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            localTargets = listOf("svc.echo"),
            systemName = "connector-kotlin-test",
            nodeId = "node-ask",
        )

        try {
            client.registerHandler("svc.echo") { request ->
                RuntimeClient.replyTo(
                    request = request,
                    source = "svc.echo",
                    payloadUtf8 = "reply:${request.payloadUtf8()}",
                )
            }

            val response = client.ask(
                source = "test-client",
                target = "svc.echo",
                payloadUtf8 = "hello",
            )

            assertEquals("reply:hello", response.payloadUtf8())
            assertEquals("test-client", response.target)
            assertTrue(client.snapshotStats().matchedResponses >= 1)
        } finally {
            client.shutdown()
        }
    }

    @Test
    fun askRoundTripWorksWithSeparateDeliveredRequestLane() = runBlocking {
        val client = RuntimeClient.startLocal(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            localTargets = listOf("svc.echo"),
            systemName = "connector-kotlin-test",
            nodeId = "node-ask-separate-lane",
            separateDeliveredRequestLane = true,
        )

        try {
            client.registerHandler("svc.echo") { request ->
                RuntimeClient.replyTo(
                    request = request,
                    source = "svc.echo",
                    payloadUtf8 = "reply:${request.payloadUtf8()}",
                )
            }

            val response = client.ask(
                source = "test-client",
                target = "svc.echo",
                payloadUtf8 = "hello",
            )

            assertEquals("reply:hello", response.payloadUtf8())
            assertEquals("test-client", response.target)
            assertTrue(client.snapshotStats().matchedResponses >= 1)
        } finally {
            client.shutdown()
        }
    }

    @Test
    fun typedAskRoundTripCarriesPayloadIdentity() = runBlocking {
        val client = RuntimeClient.startLocal(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            localTargets = listOf("svc.echo"),
            systemName = "connector-kotlin-test",
            nodeId = "node-typed-ask",
        )

        try {
            client.registerHandler("svc.echo") { request ->
                assertTrue(request.hasTypedPayloadIdentity())
                assertEquals("echo.request.v1", request.messageType)
                assertEquals(1, request.payloadSchemaVersion)
                assertEquals(ConnectorPayloadFormat.TEXT, request.payloadFormat)
                RuntimeClient.replyTypedTo(
                    request = request,
                    source = "svc.echo",
                    payloadUtf8 = "reply:${request.payloadUtf8()}",
                )
            }

            val response = client.ask(
                source = "test-client",
                target = "svc.echo",
                payloadUtf8 = "hello",
                payloadIdentity = ConnectorPayloadIdentity(
                    messageType = "echo.request.v1",
                    payloadSchemaVersion = 1,
                    payloadFormat = ConnectorPayloadFormat.TEXT,
                ),
            )

            assertEquals("reply:hello", response.payloadUtf8())
            assertEquals("echo.request.v1", response.messageType)
            assertEquals(1, response.payloadSchemaVersion)
            assertEquals(ConnectorPayloadFormat.TEXT, response.payloadFormat)
        } finally {
            client.shutdown()
        }
    }

    @Test
    fun submitRequestPublishesResponseTerminalEvent() = runBlocking {
        val client = RuntimeClient.startLocal(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            localTargets = listOf("svc.echo"),
            systemName = "connector-kotlin-test",
            nodeId = "node-submit-request",
        )

        try {
            client.registerHandler("svc.echo") { request ->
                RuntimeClient.replyTo(
                    request = request,
                    source = "svc.echo",
                    payloadUtf8 = "reply:${request.payloadUtf8()}",
                )
            }

            val subscribed = CompletableDeferred<Unit>()
            val terminalEventDeferred = async {
                client.terminalEvents()
                    .onStart { subscribed.complete(Unit) }
                    .first()
            }
            subscribed.await()
            val submitted = client.submitRequest(
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
                    assertEquals("test-client", terminalEvent.envelope.target)
                }

                is RequestTerminalEvent.Deadletter ->
                    error("expected response terminal event, got deadletter ${terminalEvent.deadletter.reason}")
            }

            val stats = client.snapshotStats()
            assertEquals(0, stats.pendingRequests)
            assertEquals(0, stats.lateResponses)
            assertEquals(0, stats.unhandledDeadletters)
        } finally {
            client.shutdown()
        }
    }

    @Test
    fun submitRequestTracksCustomCorrelationIdOnTerminalEvent() = runBlocking {
        val client = RuntimeClient.startLocal(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            localTargets = listOf("svc.echo"),
            systemName = "connector-kotlin-test",
            nodeId = "node-submit-custom-correlation",
        )

        try {
            client.registerHandler("svc.echo") { request ->
                RuntimeClient.replyTo(
                    request = request,
                    source = "svc.echo",
                    payloadUtf8 = "reply:${request.payloadUtf8()}",
                )
            }

            val subscribed = CompletableDeferred<Unit>()
            val terminalEventDeferred = async {
                client.terminalEvents()
                    .onStart { subscribed.complete(Unit) }
                    .first()
            }
            subscribed.await()
            val submitted = client.submitRequest(
                coakka.v2.connector.protocol.ConnectorEnvelope(
                    messageId = "custom-msg-1",
                    correlationId = "custom-corr-1",
                    source = "test-client",
                    target = "svc.echo",
                    replyTo = "test-client/replies",
                    kind = coakka.v2.connector.protocol.ConnectorMessageKind.REQUEST,
                    oneWay = false,
                    timeoutMs = 1_000,
                    payload = "hello".toByteArray(),
                ),
            )
            val terminalEvent = withTimeout(1_000) { terminalEventDeferred.await() }

            when (terminalEvent) {
                is RequestTerminalEvent.Response -> {
                    assertEquals("custom-msg-1", submitted.messageId)
                    assertEquals("custom-corr-1", submitted.correlationId)
                    assertEquals("custom-msg-1", terminalEvent.requestMessageId)
                    assertEquals("custom-corr-1", terminalEvent.correlationId)
                    assertEquals("reply:hello", terminalEvent.envelope.payloadUtf8())
                    assertEquals("custom-corr-1", terminalEvent.envelope.correlationId)
                }

                is RequestTerminalEvent.Deadletter ->
                    error("expected response terminal event, got deadletter ${terminalEvent.deadletter.reason}")
            }
        } finally {
            client.shutdown()
        }
    }

    @Test
    fun submitEnvelopeAllowsCustomRequestShape() = runBlocking {
        val client = RuntimeClient.startLocal(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            localTargets = listOf("svc.custom"),
            systemName = "connector-kotlin-test",
            nodeId = "node-submit",
        )

        try {
            val seen = CompletableDeferred<Pair<String?, String>>()
            client.registerHandler("svc.custom") { request ->
                seen.complete(request.headers["operation"] to request.payloadUtf8())
                null
            }

            val request = coakka.v2.connector.protocol.ConnectorEnvelope(
                messageId = "custom-1",
                correlationId = "",
                source = "test-client",
                target = "svc.custom",
                kind = coakka.v2.connector.protocol.ConnectorMessageKind.REQUEST,
                oneWay = true,
                payload = "payload".toByteArray(),
                headers = mapOf("operation" to "custom-op"),
            )

            client.submitEnvelope(request)
            val observed = withTimeout(1_000) { seen.await() }

            assertEquals("custom-op", observed.first)
            assertEquals("payload", observed.second)
        } finally {
            client.shutdown()
        }
    }

    @Test
    fun askTimesOutWhenHandlerDoesNotReply() = runBlocking {
        val client = RuntimeClient.startLocal(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            localTargets = listOf("svc.silent"),
            systemName = "connector-kotlin-test",
            nodeId = "node-timeout",
        )

        try {
            client.registerHandler("svc.silent") { _ -> null }

            assertFailsWith<TimeoutCancellationException> {
                client.ask(
                    source = "test-client",
                    target = "svc.silent",
                    payloadUtf8 = "hello",
                    timeoutMs = 100,
                )
            }

            val stats = client.snapshotStats()
            assertTrue(stats.deliveredRequests >= 1)
            assertEquals(0, stats.matchedResponses)
            assertEquals(0, stats.matchedDeadletters)
        } finally {
            client.shutdown()
        }
    }

    @Test
    fun submitTypedEnvelopeRejectsMissingPayloadIdentity() = runBlocking {
        val client = RuntimeClient.startLocal(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            localTargets = listOf("svc.custom"),
            systemName = "connector-kotlin-test",
            nodeId = "node-submit-typed-reject",
        )

        try {
            val error = assertFailsWith<IllegalArgumentException> {
                client.submitTypedEnvelope(
                    coakka.v2.connector.protocol.ConnectorEnvelope(
                        messageId = "custom-typed-1",
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
            client.shutdown()
        }
    }
}
