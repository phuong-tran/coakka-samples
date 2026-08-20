package coakka.v2.connector

import coakka.v2.connector.protocol.RequestTerminalEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class RuntimeClientDeadletterTest {
    @Test
    fun askFailsWithDeadletterWhenRouteIsMissing() = runBlocking {
        val client = RuntimeClient.startLocal(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            localTargets = listOf("svc.echo"),
            systemName = "connector-kotlin-test",
            nodeId = "node-deadletter",
        )

        try {
            val error = assertFailsWith<DeadletterException> {
                client.ask(
                    source = "test-client",
                    target = "svc.unknown",
                    payloadUtf8 = "hello",
                )
            }

            assertEquals("DEADLETTER_REASON_ROUTE_MISS", error.deadletter.reason)
            assertEquals("svc.unknown", error.deadletter.originalEnvelope.target)
            assertTrue(client.snapshotStats().matchedDeadletters >= 1)
        } finally {
            client.shutdown()
        }
    }

    @Test
    fun deadletterFlowObservesAskDeadletterWhenRouteIsMissing() = runBlocking {
        val client = RuntimeClient.startLocal(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            localTargets = listOf("svc.echo"),
            systemName = "connector-kotlin-test",
            nodeId = "node-deadletter-flow",
        )

        try {
            val subscribed = CompletableDeferred<Unit>()
            val observedDeferred = async {
                client.deadletters()
                    .onStart { subscribed.complete(Unit) }
                    .first()
            }
            subscribed.await()

            assertFailsWith<DeadletterException> {
                client.ask(
                    source = "test-client",
                    target = "svc.unknown",
                    payloadUtf8 = "hello",
                )
            }

            val observed = withTimeout(1_000) { observedDeferred.await() }
            assertEquals("DEADLETTER_REASON_ROUTE_MISS", observed.deadletter.reason)
            assertEquals("svc.unknown", observed.deadletter.originalEnvelope.target)
            assertTrue(observed.matchedPendingRequest)
            assertEquals(0, client.snapshotStats().deadletterObservationDropCount)
        } finally {
            client.shutdown()
        }
    }

    @Test
    fun askFailsWithDeadletterWhenRouteIsMissingWithSeparateDeliveredRequestLane() = runBlocking {
        val client = RuntimeClient.startLocal(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            localTargets = listOf("svc.echo"),
            systemName = "connector-kotlin-test",
            nodeId = "node-deadletter-separate-lane",
            separateDeliveredRequestLane = true,
        )

        try {
            val error = assertFailsWith<DeadletterException> {
                client.ask(
                    source = "test-client",
                    target = "svc.unknown",
                    payloadUtf8 = "hello",
                )
            }

            assertEquals("DEADLETTER_REASON_ROUTE_MISS", error.deadletter.reason)
            assertEquals("svc.unknown", error.deadletter.originalEnvelope.target)
            assertTrue(client.snapshotStats().matchedDeadletters >= 1)
        } finally {
            client.shutdown()
        }
    }

    @Test
    fun submitRequestPublishesDeadletterTerminalEventWhenRouteIsMissing() = runBlocking {
        val client = RuntimeClient.startLocal(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            localTargets = listOf("svc.echo"),
            systemName = "connector-kotlin-test",
            nodeId = "node-deadletter-terminal-event",
        )

        try {
            val subscribed = CompletableDeferred<Unit>()
            val terminalEventDeferred = async {
                client.terminalEvents()
                    .onStart { subscribed.complete(Unit) }
                    .first()
            }
            subscribed.await()
            val submitted = client.submitRequest(
                source = "test-client",
                target = "svc.unknown",
                payloadUtf8 = "hello",
            )
            val terminalEvent = withTimeout(1_000) { terminalEventDeferred.await() }

            when (terminalEvent) {
                is RequestTerminalEvent.Deadletter -> {
                    assertEquals(submitted.messageId, terminalEvent.requestMessageId)
                    assertEquals(submitted.correlationId, terminalEvent.correlationId)
                    assertEquals("DEADLETTER_REASON_ROUTE_MISS", terminalEvent.deadletter.reason)
                    assertEquals("svc.unknown", terminalEvent.deadletter.originalEnvelope.target)
                }

                is RequestTerminalEvent.Response ->
                    error("expected deadletter terminal event, got response ${terminalEvent.envelope.messageId}")
            }

            val stats = client.snapshotStats()
            assertEquals(0, stats.pendingRequests)
            assertEquals(0, stats.unhandledDeadletters)
        } finally {
            client.shutdown()
        }
    }

    @Test
    fun duplicateHandlerRegistrationFailsFast() = runBlocking {
        val client = RuntimeClient.startLocal(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            localTargets = listOf("svc.echo"),
            systemName = "connector-kotlin-test",
            nodeId = "node-handler",
        )

        try {
            client.registerHandler("svc.echo") { null }
            assertFailsWith<IllegalStateException> {
                client.registerHandler("svc.echo") { null }
            }
        } finally {
            client.shutdown()
        }
    }
}
