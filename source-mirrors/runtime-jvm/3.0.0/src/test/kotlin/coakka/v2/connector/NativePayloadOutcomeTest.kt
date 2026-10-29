package coakka.v2.connector

import coakka.v2.connector.internal.NativeBridge
import coakka.v2.connector.internal.NativePayloadResult
import coakka.v2.connector.protocol.ConnectorDeliveryHint
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

/** Private representation seam; matching/lifecycle remain the actual Core path. */
class NativePayloadOutcomeTest {
    @Test
    fun copiedSuccessArmSurvivesLaterCallsAndNativeClose() = runBlocking {
        val port = TestSupport.availableLoopbackPort()
        val server = RuntimeClient.start(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "outcome-server", nodeId = "server",
                routes = RuntimeClient.localRoutes(listOf("echo"), port),
                network = RuntimeNetworkConfig.networkNode("127.0.0.1", port, "127.0.0.1", port),
            ),
        )
        val client = RuntimeHandle.open(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "outcome-client", nodeId = "client",
                routes = listOf(RuntimeRouteSpec("echo", listOf(RuntimeEndpointSpec("127.0.0.1", port.toLong())))),
                network = RuntimeNetworkConfig.outboundOnly(),
            ),
        )
        val held = mutableListOf<Pair<ByteArray, ByteArray>>()
        try {
            server.registerPayloadHandler("echo") { it }
            client.start()
            client.openCoreAskClient()
            for ((index, size) in listOf(0, 20, 96 * 1024).withIndex()) {
                val input = ByteArray(size) { (it % 127).toByte() }
                val expected = input.copyOf()
                val result = client.askPayload(
                    "message-$index".toByteArray(), "caller".toByteArray(),
                    "echo".toByteArray(), "reply".toByteArray(), input, 2000, 2000,
                    ConnectorDeliveryHint.REQUIRE_REMOTE.value,
                )
                val bytes = assertIs<ByteArray>(result)
                assertNotSame(input, bytes)
                input.fill(0)
                assertContentEquals(expected, bytes)
                held += expected to bytes
            }
        } finally {
            client.close()
            server.shutdown()
        }
        System.gc()
        held.forEach { (expected, bytes) -> assertContentEquals(expected, bytes) }
    }

    @Test
    fun nativeFailureAndDeadletterKeepStructuredFacts() {
        NativeLibraryResolver.load(TestSupport.runtimeLibPath())
        val closed = assertIs<NativePayloadResult>(NativeBridge.runtimeAskPayload(
            0L, byteArrayOf(1), byteArrayOf(1), byteArrayOf(1), byteArrayOf(1),
            byteArrayOf(1), 1000, 1000, ConnectorDeliveryHint.ROUTER_DEFAULT.value,
        ))
        assertEquals(CoakkaStatus.ERR_CLOSED, closed.status)
        assertEquals(0, closed.kind)
        assertNull(closed.payload)
        val client = RuntimeHandle.open(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(systemName = "outcome-missing", nodeId = "client", routes = emptyList()),
        )
        val retained: ByteArray
        try {
            client.start()
            client.openCoreAskClient()
            val deadletter = assertIs<NativePayloadResult>(client.askPayload(
                "missing-id".toByteArray(), "caller".toByteArray(), "missing".toByteArray(),
                "reply".toByteArray(), byteArrayOf(1), 1000, 1000,
                ConnectorDeliveryHint.ROUTER_DEFAULT.value,
            ))
            assertEquals(CoakkaStatus.OK, deadletter.status)
            assertEquals(2, deadletter.kind)
            retained = assertNotNull(deadletter.payload)
            assertNotNull(coakka.v2.connector.protocol.EnvelopeMapper.decodeDeadletter(retained).originalEnvelope)
        } finally {
            client.close()
        }
        assertNotNull(coakka.v2.connector.protocol.EnvelopeMapper.decodeDeadletter(retained).originalEnvelope)
    }
}
