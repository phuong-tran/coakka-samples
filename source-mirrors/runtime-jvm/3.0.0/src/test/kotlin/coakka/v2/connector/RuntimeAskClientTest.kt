package coakka.v2.connector

import coakka.v2.connector.protocol.ConnectorDeliveryHint
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

class RuntimeAskClientTest {
    @Test
    fun uncachedLongNamesStillReachCore() {
        val client = RuntimeAskClient.start(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(systemName = "uncached-names", nodeId = "uncached-names", routes = emptyList()),
        )
        try {
            repeat(2) {
                // Exceeds the host's cache threshold, not a Core input limit.
                assertFailsWith<DeadletterException> {
                    client.askPayloadBlocking("source-" + "é".repeat(80), "missing-" + "x".repeat(160), byteArrayOf(1), 1_000)
                }
            }
            assertEquals(RuntimeAskClientStats(0, 0, 2, 0), client.snapshotStats())
        } finally {
            client.close()
        }
    }

    @Test
    fun coreOwnedAskRoundTripsAndLeavesNoPendingState() = runBlocking {
        val port = availablePort()
        val server = RuntimeClient.start(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "core-ask-test-server",
                nodeId = "core-ask-test-server",
                routes = RuntimeClient.localRoutes(listOf("svc.echo"), port),
                network = RuntimeNetworkConfig.networkNode(
                    bindHost = "127.0.0.1",
                    bindPort = port,
                    advertiseHost = "127.0.0.1",
                    advertisePort = port,
                ),
            ),
        )
        var client: RuntimeAskClient? = null
        try {
            server.registerHandler("svc.echo") { request ->
                RuntimeClient.replyTo(request, "svc.echo", "reply:${request.payloadUtf8()}")
            }
            val runningClient = RuntimeAskClient.start(
                runtimeLibPath = TestSupport.runtimeLibPath(),
                startSpec = RuntimeStartSpec(
                    systemName = "core-ask-test-client",
                    nodeId = "core-ask-test-client",
                    routes = listOf(remoteRoute("svc.echo", port)),
                    network = RuntimeNetworkConfig.outboundOnly(),
                ),
            )
            client = runningClient

            val response = runningClient.askBlocking(
                source = "test-client",
                target = "svc.echo",
                payloadUtf8 = "hello",
                timeoutMs = 2_000,
                deliveryHint = ConnectorDeliveryHint.REQUIRE_REMOTE,
            )

            assertEquals("reply:hello", response.payloadUtf8())
            assertEquals(RuntimeAskClientStats(0, 1, 0, 0), runningClient.snapshotStats())
        } finally {
            client?.close()
            server.shutdown()
        }
    }

    @Test
    fun coreRawHelpersOwnBytesFirstRequestAndPayloadProjection() = runBlocking {
        val port = availablePort()
        val server = RuntimeClient.start(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "core-payload-test-server",
                nodeId = "core-payload-test-server",
                routes = RuntimeClient.localRoutes(listOf("svc.payload"), port),
                network = RuntimeNetworkConfig.networkNode(
                    bindHost = "127.0.0.1",
                    bindPort = port,
                    advertiseHost = "127.0.0.1",
                    advertisePort = port,
                ),
            ),
        )
        var client: RuntimeAskClient? = null
        try {
            server.registerHandler("svc.payload") { request ->
                RuntimeClient.replyTo(
                    request,
                    "svc.payload",
                    if (request.payload.isEmpty()) "" else "payload:${request.payloadUtf8()}",
                )
            }
            val runningClient = RuntimeAskClient.start(
                runtimeLibPath = TestSupport.runtimeLibPath(),
                startSpec = RuntimeStartSpec(
                    systemName = "core-payload-test-client",
                    nodeId = "core-payload-test-client",
                    routes = listOf(remoteRoute("svc.payload", port)),
                    network = RuntimeNetworkConfig.outboundOnly(),
                ),
            )
            client = runningClient

            val payload = runningClient.askPayloadBlocking(
                source = "payload-client",
                target = "svc.payload",
                payload = "hello".toByteArray(),
                timeoutMs = 2_000,
                deliveryHint = ConnectorDeliveryHint.REQUIRE_REMOTE,
            )

            assertEquals("payload:hello", payload.toString(Charsets.UTF_8))
            assertEquals(
                0,
                runningClient.askPayloadBlocking(
                    source = "payload-client",
                    target = "svc.payload",
                    payload = byteArrayOf(),
                    timeoutMs = 2_000,
                    deliveryHint = ConnectorDeliveryHint.REQUIRE_REMOTE,
                ).size,
            )
            assertEquals(RuntimeAskClientStats(0, 2, 0, 0), runningClient.snapshotStats())
        } finally {
            client?.close()
            server.shutdown()
        }
    }

    @Test
    fun coreOwnedAskProjectsDeadletterAndBoundedTimeout() = runBlocking {
        val missing = RuntimeAskClient.start(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "core-ask-deadletter-test",
                nodeId = "core-ask-deadletter-test",
                routes = emptyList(),
            ),
        )
        try {
            assertFailsWith<DeadletterException> {
                missing.askBlocking("test-client", "svc.missing", "hello", 1_000)
            }
            assertFailsWith<DeadletterException> {
                missing.askPayloadBlocking(
                    "test-client",
                    "svc.missing",
                    "hello".toByteArray(),
                    1_000,
                )
            }
            assertEquals(RuntimeAskClientStats(0, 0, 2, 0), missing.snapshotStats())
        } finally {
            missing.close()
        }

        val port = availablePort()
        val silentServer = RuntimeClient.start(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "core-ask-timeout-server",
                nodeId = "core-ask-timeout-server",
                routes = RuntimeClient.localRoutes(listOf("svc.silent"), port),
                network = RuntimeNetworkConfig.networkNode(
                    bindHost = "127.0.0.1",
                    bindPort = port,
                    advertiseHost = "127.0.0.1",
                    advertisePort = port,
                ),
            ),
        )
        var timeoutClient: RuntimeAskClient? = null
        try {
            silentServer.registerHandler("svc.silent") { null }
            val runningClient = RuntimeAskClient.start(
                runtimeLibPath = TestSupport.runtimeLibPath(),
                startSpec = RuntimeStartSpec(
                    systemName = "core-ask-timeout-client",
                    nodeId = "core-ask-timeout-client",
                    routes = listOf(remoteRoute("svc.silent", port)),
                    network = RuntimeNetworkConfig.outboundOnly(),
                ),
            )
            timeoutClient = runningClient
            assertFailsWith<RuntimeAskTimeoutException> {
                runningClient.askBlocking(
                    "test-client",
                    "svc.silent",
                    "hello",
                    requestTimeoutMs = 1_000,
                    waitTimeoutMs = 30,
                    deliveryHint = ConnectorDeliveryHint.REQUIRE_REMOTE,
                )
            }
            assertEquals(RuntimeAskClientStats(0, 0, 0, 1), runningClient.snapshotStats())
        } finally {
            timeoutClient?.close()
            silentServer.shutdown()
        }
    }

    private fun remoteRoute(target: String, port: Int): RuntimeRouteSpec =
        RuntimeRouteSpec(
            target = target,
            endpoints = listOf(RuntimeEndpointSpec("127.0.0.1", port.toLong())),
        )

    private fun availablePort(): Int = ServerSocket(0).use { it.localPort }
}
