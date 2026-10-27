package coakka.v2.connector

import coakka.v2.connector.internal.NativeBridge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeStartupProjectionTest {
    private fun spec() = RuntimeStartSpec("startup-projection", "node", routes = emptyList())

    @Test
    fun omittedGroupsAreCoreDefaultsNotExplicitConnectorValues() {
        RuntimeHandle.open(TestSupport.runtimeLibPath(), spec()).use {
            val actual = it.startupConfig()
            assertEquals(15L, actual.defaultedGroups)
            assertEquals(CoakkaRuntimeStartupCpuPolicies.AUTO, actual.execution.requestedPolicy)
            assertEquals(RuntimeTcpConnectionMode.MULTIPLEXING, actual.connection.mode)
            assertFalse(actual.io.requested)
            assertFalse(actual.io.probed)
            assertFalse(actual.monitorEnabled)
            assertFalse(it.monitor.isEnabled())
            assertEquals(-1, it.hostHandles.monitor_read_fd)
        }
    }

    @Test
    fun explicitZeroAndFalseRetainPresence() {
        RuntimeHandle.open(TestSupport.runtimeLibPath(), spec().copy(
            execution = RuntimeExecutionOptions(0),
            io = RuntimeIoOptions(false),
            monitor = RuntimeMonitorOptions(false),
            connectionStrategy = RuntimeTcpConnectionStrategySpec(RuntimeTcpConnectionMode.MULTIPLEXING),
        )).use {
            val actual = it.startupConfig()
            assertEquals(0L, actual.defaultedGroups)
            assertEquals(CoakkaRuntimeStartupCpuPolicies.AUTO, actual.execution.requestedPolicy)
            assertEquals(RuntimeTcpConnectionMode.MULTIPLEXING, actual.connection.mode)
            assertFalse(actual.io.requested)
            assertFalse(actual.io.probed)
            assertFalse(actual.monitorEnabled)
        }
    }

    @Test
    fun explicitMonitorCollectionExportsItsDoorbell() {
        RuntimeHandle.open(TestSupport.runtimeLibPath(), spec().copy(monitor = RuntimeMonitorOptions(true))).use {
            assertTrue(it.startupConfig().monitorEnabled)
            assertTrue(it.monitor.isEnabled())
            assertTrue(it.hostHandles.monitor_read_fd >= 0)
            assertFalse(it.startupConfig().wasDefaulted(CoakkaRuntimeStartupDefaultGroups.MONITOR))
        }
    }

    @Test
    fun coreRefusalDoesNotPublishAnOwnerAndNextCreationWorks() {
        val error = assertFailsWith<IllegalStateException> {
            RuntimeHandle.open(TestSupport.runtimeLibPath(), spec().copy(execution = RuntimeExecutionOptions(999))).close()
        }
        assertTrue(error.message.orEmpty().contains("runtime_create_native failed rc=-1"))
        RuntimeHandle.open(TestSupport.runtimeLibPath(), spec()).use {
            assertEquals(15L, it.startupConfig().defaultedGroups)
        }
    }

    @Test
    fun privateProjectionRejectsMalformedExtentsBeforeCopy() {
        NativeLibraryResolver.load(TestSupport.runtimeLibPath())
        for (values in listOf(LongArray(0), LongArray(5), LongArray(7), longArrayOf(2, 0, 0, 0, 0, 0))) {
            val result = NativeBridge.runtimeCreate("s".utf8(), "n".utf8(), 0, 1, values, null)
            assertEquals(CoakkaStatus.ERR_INVALID_ARG.toLong(), result.value(0))
            assertEquals(1, result.values.size)
        }
        val result = NativeBridge.runtimeCreate("s".utf8(), "n".utf8(), 0, 1, LongArray(6), LongArray(4))
        assertEquals(CoakkaStatus.ERR_INVALID_ARG.toLong(), result.value(0))
        assertEquals(1, result.values.size)
    }
}
