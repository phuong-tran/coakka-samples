package coakka.v2.connector.simulated

import coakka.v2.connector.DeadletterException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class SimulatedRuntimeClientTest {
    @Test
    fun askWorksAcrossTwoSimulatedNodes() = runBlocking {
        val bus = SimulatedRuntimeBus()
        val nodeA = SimulatedRuntimeClient(bus, "node-a")
        val nodeB = SimulatedRuntimeClient(bus, "node-b")

        try {
            nodeB.registerHandler("svc.b") { request ->
                SimulatedRuntimeClient.replyTo(request, source = "svc.b", payloadUtf8 = "reply:${request.payloadUtf8()}")
            }

            val response = nodeA.ask(
                source = "svc.a.worker",
                target = "svc.b",
                payloadUtf8 = "hello",
            )

            assertEquals("reply:hello", response.payloadUtf8())
            assertEquals("svc.a.worker", response.target)
            assertTrue(nodeA.snapshotStats().matchedResponses >= 1)
        } finally {
            nodeA.shutdown()
            nodeB.shutdown()
        }
    }

    @Test
    fun sendOneWayDeliversWithoutPendingReplyState() = runBlocking {
        val bus = SimulatedRuntimeBus()
        val producer = SimulatedRuntimeClient(bus, "producer")
        val consumer = SimulatedRuntimeClient(bus, "consumer")

        try {
            val seen = CompletableDeferred<String>()
            consumer.registerHandler("svc.audit") { request ->
                seen.complete(request.payloadUtf8())
                null
            }

            producer.sendOneWay(source = "producer", target = "svc.audit", payloadUtf8 = "fire")

            assertEquals("fire", withTimeout(1_000) { seen.await() })
            assertEquals(0, producer.snapshotStats().pendingRequests)
            assertEquals(0, producer.snapshotStats().matchedResponses)
        } finally {
            producer.shutdown()
            consumer.shutdown()
        }
    }

    @Test
    fun routeMissProducesDeadletterWithoutNativeRuntime() = runBlocking {
        val bus = SimulatedRuntimeBus()
        val client = SimulatedRuntimeClient(bus, "solo")

        try {
            val error = assertFailsWith<DeadletterException> {
                client.ask(source = "solo", target = "svc.missing", payloadUtf8 = "hello")
            }

            assertEquals("SIMULATED_ROUTE_MISS", error.deadletter.reason)
            assertEquals("svc.missing", error.deadletter.originalEnvelope.target)
            assertTrue(client.snapshotStats().matchedDeadletters >= 1)
        } finally {
            client.shutdown()
        }
    }

    @Test
    fun timeoutAndLateReplyAreObservable() = runBlocking {
        val bus = SimulatedRuntimeBus()
        val nodeA = SimulatedRuntimeClient(bus, "node-a")
        val nodeB = SimulatedRuntimeClient(bus, "node-b")

        try {
            nodeB.registerHandler("svc.slow") { request ->
                kotlinx.coroutines.delay(150)
                SimulatedRuntimeClient.replyTo(request, source = "svc.slow", payloadUtf8 = "late")
            }

            assertFailsWith<TimeoutCancellationException> {
                nodeA.ask(
                    source = "caller",
                    target = "svc.slow",
                    payloadUtf8 = "hello",
                    timeoutMs = 50,
                )
            }

            withTimeout(1_000) {
                while (nodeA.snapshotStats().lateResponses == 0L) {
                    kotlinx.coroutines.delay(10)
                }
            }
            assertTrue(nodeA.snapshotStats().lateResponses >= 1)
        } finally {
            nodeA.shutdown()
            nodeB.shutdown()
        }
    }

    @Test
    fun concurrentAskBurstWorksOnSimulatedBus() = runBlocking {
        val bus = SimulatedRuntimeBus()
        val nodeA = SimulatedRuntimeClient(bus, "node-a")
        val nodeB = SimulatedRuntimeClient(bus, "node-b")

        try {
            nodeB.registerHandler("svc.echo") { request ->
                SimulatedRuntimeClient.replyTo(request, source = "svc.echo", payloadUtf8 = request.payloadUtf8())
            }

            val results = (0 until 32).map { idx ->
                async {
                    nodeA.ask(
                        source = "worker-$idx",
                        target = "svc.echo",
                        payloadUtf8 = "payload-$idx",
                        timeoutMs = 1_000,
                    ).payloadUtf8()
                }
            }.awaitAll()

            assertEquals(32, results.size)
            assertEquals("payload-0", results.first())
            assertEquals("payload-31", results.last())
            assertTrue(nodeA.snapshotStats().matchedResponses >= 32)
        } finally {
            nodeA.shutdown()
            nodeB.shutdown()
        }
    }
}
