package coakka.v2.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class HostJniNetworkIntegrationTest {
    @Before
    fun requireHost() {
        assumeTrue(System.getProperty("coakka.android.hostJni") == "true")
    }

    private fun create() = NativeRuntimeBridge.nativeCreate("network", "network", 0, true, -1)

    private fun apply(handle: Long, intent: RuntimeNetworkConfig): Int =
        NativeRuntimeBridge.nativeApplyNetwork(
            handle, intent.mode.nativeValue, intent.bindHost, intent.bindPort ?: 0,
            intent.bindPort != null, intent.advertiseHost, intent.advertisePort ?: 0,
            intent.advertisePort != null,
        )

    private fun snapshot(handle: Long): RuntimeNetworkSnapshot {
        val numeric = LongArray(4)
        val text = arrayOfNulls<String>(2)
        requireNativeOk(NativeRuntimeBridge.nativeReadNetworkSnapshot(handle, numeric, text), "runtime_get_network_config")
        return numeric.toRuntimeNetworkSnapshot(text)
    }

    @Test
    fun coreNetworkFactsPreserveAbsenceExplicitModesAndCopiedHosts() {
        val handle = create()
        try {
            val absent = snapshot(handle)
            assertEquals(RuntimeNetworkMode.EMBEDDED, absent.mode)
            assertFalse(absent.explicitPolicy)
            assertNull(absent.bindHost)
            assertEquals(0, absent.bindPort)
            for (intent in listOf(RuntimeNetworkConfig.embedded(), RuntimeNetworkConfig.outboundOnly())) {
                assertEquals(0, apply(handle, intent))
                val accepted = snapshot(handle)
                assertEquals(intent.mode, accepted.mode)
                assertTrue(accepted.explicitPolicy)
                assertNull(accepted.bindHost)
                assertNull(accepted.advertiseHost)
            }
            val node = RuntimeNetworkConfig.networkNode("127.0.0.1", 32101, "node.example", 32102)
            assertEquals(0, apply(handle, node))
            val copied = snapshot(handle)
            assertEquals(RuntimeNetworkMode.NETWORK_NODE, copied.mode)
            assertEquals("127.0.0.1", copied.bindHost)
            assertEquals(32101, copied.bindPort)
            assertEquals("node.example", copied.advertiseHost)
            assertEquals(32102, copied.advertisePort)
            assertEquals(0, apply(handle, RuntimeNetworkConfig.embedded()))
            assertEquals("node.example", copied.advertiseHost)
        } finally {
            NativeRuntimeBridge.nativeDestroy(handle)
        }
    }

    @Test
    fun coreRejectsEverySuppliedInapplicableOrInvalidIntentAndOpenRetiresOwner() {
        val invalid = listOf(
            RuntimeNetworkConfig(RuntimeNetworkMode.EMBEDDED, bindHost = "127.0.0.1"),
            RuntimeNetworkConfig(RuntimeNetworkMode.EMBEDDED, bindPort = 0),
            RuntimeNetworkConfig(RuntimeNetworkMode.OUTBOUND_ONLY, advertiseHost = "node.example"),
            RuntimeNetworkConfig(RuntimeNetworkMode.OUTBOUND_ONLY, advertisePort = 0),
            RuntimeNetworkConfig(RuntimeNetworkMode.NETWORK_NODE),
            RuntimeNetworkConfig(RuntimeNetworkMode.NETWORK_NODE, "127.0.0.1", null, "node.example", 32102),
            RuntimeNetworkConfig(RuntimeNetworkMode.NETWORK_NODE, "127.0.0.1", 0, "node.example", 32102),
            RuntimeNetworkConfig(RuntimeNetworkMode.NETWORK_NODE, "127.0.0.1", 32101, "node.example", 0),
            RuntimeNetworkConfig(RuntimeNetworkMode.NETWORK_NODE, "invalid", 32101, "node.example", 32102),
            RuntimeNetworkConfig(RuntimeNetworkMode.LEGACY_ROUTE_DERIVED),
        ) + listOf("0.0.0.0", "::", "[::]", "::0", "").map {
            RuntimeNetworkConfig.networkNode("127.0.0.1", 32101, it, 32102)
        }
        val before = HostJniTestBridge.nativeRuntimeOwnerCount()
        for (intent in invalid) {
            val failure = assertThrows(CoAkkaNativeException::class.java) {
                CoAkkaAndroidRuntime.open(AndroidRuntimeConfig("refusal", "refusal", network = intent), emptyList())
            }
            assertEquals(-1, failure.status)
            assertTrue(failure.message!!.startsWith("runtime_apply_network_options "))
            assertEquals(before, HostJniTestBridge.nativeRuntimeOwnerCount())
        }
        val handle = create()
        try {
            assertEquals(-1, NativeRuntimeBridge.nativeApplyNetwork(handle, 4, null, 0, false, null, 0, false))
            // A nonzero value without a field bit must reach Core unchanged.
            assertEquals(-1, NativeRuntimeBridge.nativeApplyNetwork(handle, 1, null, 8, false, null, 0, false))
            assertThrows(IllegalArgumentException::class.java) {
                NativeRuntimeBridge.nativeApplyNetwork(handle, 1, null, -1, true, null, 0, false)
            }
        } finally {
            NativeRuntimeBridge.nativeDestroy(handle)
        }
    }

    @Test
    fun networkOutputFailureKeepsPendingVmCauseAndRetainsCallerOwner() {
        val handle = create()
        val before = HostJniTestBridge.nativeRuntimeOwnerCount()
        try {
            HostJniTestBridge.nativeFailRuntimeAt(6)
            assertThrows(OutOfMemoryError::class.java) { snapshot(handle) }
            assertEquals(before, HostJniTestBridge.nativeRuntimeOwnerCount())
            assertFalse(snapshot(handle).explicitPolicy)
            HostJniTestBridge.nativeFailRuntimeAt(7)
            val failure = assertThrows(CoAkkaNativeException::class.java) { snapshot(handle) }
            assertEquals(-1, failure.status)
            assertFalse(snapshot(handle).explicitPolicy)
            assertThrows(IllegalArgumentException::class.java) {
                NativeRuntimeBridge.nativeReadNetworkSnapshot(handle, LongArray(3), arrayOfNulls(2))
            }
        } finally {
            HostJniTestBridge.nativeFailRuntimeAt(0)
            NativeRuntimeBridge.nativeDestroy(handle)
        }
    }
}
