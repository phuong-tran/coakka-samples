package coakka.v2.connector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RuntimeNetworkConfigTest {
    @Test
    fun nativeNetworkOptionsMatchesThePublicCAbi() {
        assertEquals(48, NativeNetworkOptions().size())
    }

    @Test
    fun startSpecDefaultsToEmbeddedWithoutListenerMetadata() {
        val spec = RuntimeStartSpec(
            systemName = "network-default-test",
            nodeId = "network-default-node",
            routes = RuntimeClient.localRoutes(listOf("svc.echo")),
        )

        assertEquals(RuntimeNetworkMode.EMBEDDED, spec.network.mode)
        assertNull(spec.network.bindHost)
        assertEquals(0, spec.network.bindPort)
        assertEquals(0, spec.routes.single().endpoints.single().port)
    }

    @Test
    fun networkNodeRejectsWildcardAdvertiseHost() {
        assertFailsWith<IllegalArgumentException> {
            RuntimeNetworkConfig.networkNode(
                bindHost = "0.0.0.0",
                bindPort = 19301,
                advertiseHost = "0.0.0.0",
            )
        }
    }
}
