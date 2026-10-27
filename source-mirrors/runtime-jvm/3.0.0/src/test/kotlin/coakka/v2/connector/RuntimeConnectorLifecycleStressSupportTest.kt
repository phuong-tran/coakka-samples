package coakka.v2.connector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RuntimeConnectorLifecycleStressSupportTest {
    @Test
    fun boundedArgumentsAreParsedExplicitly() {
        val config = RuntimeConnectorLifecycleStressConfig.parse(
            arrayOf(
                "--warmup-cycles=4",
                "--cycles=250",
                "--requests-per-cycle=96",
                "--concurrency=12",
                "--request-timeout-ms=900",
                "--timeout-fault-ms=8",
                "--run-label=utm-p308",
            ),
        )

        assertEquals(4, config.warmupCycles)
        assertEquals(250, config.cycles)
        assertEquals(96, config.requestsPerCycle)
        assertEquals(12, config.concurrency)
        assertEquals(900, config.requestTimeoutMs)
        assertEquals(8, config.timeoutFaultMs)
        assertEquals("utm-p308", config.runLabel)
    }

    @Test
    fun invalidDuplicateAndUnknownArgumentsFailClosed() {
        assertFailsWith<IllegalArgumentException> {
            RuntimeConnectorLifecycleStressConfig.parse(arrayOf("--warmup-cycles=0"))
        }
        assertFailsWith<IllegalArgumentException> {
            RuntimeConnectorLifecycleStressConfig.parse(arrayOf("--cycles=1", "--cycles=2"))
        }
        assertFailsWith<IllegalArgumentException> {
            RuntimeConnectorLifecycleStressConfig.parse(arrayOf("--timeout-fault-ms=0"))
        }
        assertFailsWith<IllegalArgumentException> {
            RuntimeConnectorLifecycleStressConfig.parse(arrayOf("--run-label=not safe"))
        }
        assertFailsWith<IllegalArgumentException> {
            RuntimeConnectorLifecycleStressConfig.parse(arrayOf("--unknown=1"))
        }
    }
}
