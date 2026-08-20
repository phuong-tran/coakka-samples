package coakka.v2.connector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class RuntimeClientOneWayTest {
    @Test
    fun sendOneWayDeliversRequestWithoutResponse() = runBlocking {
        val client = RuntimeClient.startLocal(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            localTargets = listOf("svc.oneway"),
            systemName = "connector-kotlin-test",
            nodeId = "node-oneway",
        )

        try {
            val seen = CompletableDeferred<String>()
            client.registerHandler("svc.oneway") { request ->
                seen.complete(request.payloadUtf8())
                null
            }

            client.sendOneWay(
                source = "test-client",
                target = "svc.oneway",
                payloadUtf8 = "fire-and-forget",
            )

            assertEquals("fire-and-forget", withTimeout(1_000) { seen.await() })
            val stats = client.snapshotStats()
            assertTrue(stats.deliveredRequests >= 1)
            assertEquals(0, stats.matchedResponses)
        } finally {
            client.shutdown()
        }
    }

    @Test
    fun sendOneWayDeliversRequestWithoutResponseWithSeparateDeliveredRequestLane() = runBlocking {
        val client = RuntimeClient.startLocal(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            localTargets = listOf("svc.oneway"),
            systemName = "connector-kotlin-test",
            nodeId = "node-oneway-separate-lane",
            separateDeliveredRequestLane = true,
        )

        try {
            val seen = CompletableDeferred<String>()
            client.registerHandler("svc.oneway") { request ->
                seen.complete(request.payloadUtf8())
                null
            }

            client.sendOneWay(
                source = "test-client",
                target = "svc.oneway",
                payloadUtf8 = "fire-and-forget",
            )

            assertEquals("fire-and-forget", withTimeout(1_000) { seen.await() })
            val stats = client.snapshotStats()
            assertTrue(stats.deliveredRequests >= 1)
            assertEquals(0, stats.matchedResponses)
        } finally {
            client.shutdown()
        }
    }
}
