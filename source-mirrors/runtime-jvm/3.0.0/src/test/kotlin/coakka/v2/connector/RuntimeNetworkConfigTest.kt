package coakka.v2.connector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeNetworkConfigTest {
    @Test
    fun nativeNetworkOptionsMatchesThePublicCAbi() {
        assertEquals(1, CoakkaRuntimeAbi.VERSION)
    }

    @Test
    fun absentIntentIsNotReconstructedInTheConnector() {
        val spec = RuntimeStartSpec(
            systemName = "network-default-test",
            nodeId = "network-default-node",
            routes = RuntimeClient.localRoutes(listOf("svc.echo")),
        )

        assertNull(spec.network)
        assertEquals(0, spec.routes.single().endpoints.single().port)
        RuntimeHandle.open(TestSupport.runtimeLibPath(), spec).use { handle ->
            val actual = handle.networkConfig()
            assertEquals(RuntimeNetworkMode.EMBEDDED, actual.mode)
            assertFalse(actual.explicitPolicy)
            assertNull(actual.bindHost)
            assertNull(actual.advertiseHost)
            assertEquals(0, actual.bindPort)
            assertEquals(0, actual.advertisePort)
        }
    }

    @Test
    fun coreRejectsWildcardAdvertiseHostWithoutConnectorPolicy() {
        val intent = RuntimeNetworkConfig.networkNode("0.0.0.0", 19301, "0.0.0.0", 19301)
        val error = assertFailsWith<IllegalStateException> {
            open(intent).close()
        }
        assertTrue(error.message.orEmpty().contains("runtime_apply_network_options failed rc=-1"))
        open(RuntimeNetworkConfig.embedded()).use {
            assertTrue(it.networkConfig().explicitPolicy)
            assertEquals(RuntimeNetworkMode.EMBEDDED, it.networkConfig().mode)
        }
    }

    @Test
    fun zeroPortsStayExplicitAndCoreOwnsTheirRefusal() {
        val zero = RuntimeNetworkConfig.networkNode("127.0.0.1", 0, "127.0.0.1", 0)
        assertEquals(31L, zero.fields())
        assertEquals(0, zero.bindPort)
        assertEquals(0, zero.advertisePort)
        val error = assertFailsWith<IllegalStateException> { open(zero).close() }
        assertTrue(error.message.orEmpty().contains("runtime_apply_network_options failed rc=-1"))
        open(RuntimeNetworkConfig.networkNode("127.0.0.1", 19301, "127.0.0.1", 19302)).use {
            val actual = it.networkConfig()
            assertEquals(RuntimeNetworkMode.NETWORK_NODE, actual.mode)
            assertTrue(actual.explicitPolicy)
            assertEquals("127.0.0.1", actual.bindHost)
            assertEquals("127.0.0.1", actual.advertiseHost)
            assertEquals(19301, actual.bindPort)
            assertEquals(19302, actual.advertisePort)
        }
    }

    @Test
    fun missingPortsAndUnknownModesReachCoreWithoutSubstitution() {
        for (intent in listOf(
            RuntimeNetworkConfig(RuntimeNetworkMode.NETWORK_NODE, bindHost = "127.0.0.1", advertiseHost = "127.0.0.1"),
            RuntimeNetworkConfig(RuntimeNetworkMode.of(-1)),
        )) {
            val error = assertFailsWith<IllegalStateException> { open(intent).close() }
            assertTrue(error.message.orEmpty().contains("runtime_apply_network_options failed rc=-1"))
        }
    }

    @Test
    fun networkInspectionAfterCloseDoesNotFabricateFacts() {
        val handle = open(RuntimeNetworkConfig.embedded())
        handle.close()
        assertFailsWith<IllegalStateException> { handle.networkConfig() }
    }

    private fun open(network: RuntimeNetworkConfig) = RuntimeHandle.open(
        TestSupport.runtimeLibPath(),
        RuntimeStartSpec("network-intent", "node", routes = emptyList(), network = network),
    )
}
