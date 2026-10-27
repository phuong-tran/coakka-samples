package coakka.v2.connector

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RuntimeMonitorProjectionTest {
    private fun spec() = RuntimeStartSpec("monitor", "node", routes = emptyList(),
        monitor = RuntimeMonitorOptions(true))

    @Test
    fun noEventIsNullButClosedOwnerIsAnError() {
        val handle = RuntimeHandle.open(TestSupport.runtimeLibPath(), spec())
        try {
            assertNotNull(handle.monitor.awaitNextBlocking(0))
            assertNull(handle.monitor.awaitNextBlocking(0))
        } finally {
            handle.close()
        }
        assertFailsWith<IllegalStateException> { handle.monitor.awaitNextBlocking(0) }
    }

    @Test
    fun longWaitCannotWrapIntoAZeroOrNegativeInt() {
        runBlocking {
            RuntimeHandle.open(TestSupport.runtimeLibPath(), spec()).use {
                assertFailsWith<IllegalArgumentException> { it.monitor.awaitNext(0x1_0000_0000L) }
                assertFailsWith<IllegalArgumentException> { it.monitor.awaitNext(-1L) }
                assertNotNull(it.monitor.awaitNext(0))
            }
        }
    }
}
