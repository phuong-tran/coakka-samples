package coakka.v2.connector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RuntimeConnectorBenchmarkSupportTest {
    @Test
    fun boundedArgumentsAreParsedWithoutAmbientDefaults() {
        val config = RuntimeConnectorBenchmarkConfig.parse(
            arrayOf(
                "--requests=101",
                "--warmup-requests=11",
                "--concurrency=8",
                "--request-payload-bytes=63",
                "--response-payload-bytes=20",
                "--queue-capacity=256",
                "--timeout-ms=4000",
                "--transport=tcp",
                "--server-port=19372",
                "--connection-mode=bounded-pool",
                "--run-label=pi-c8-r1",
            ),
        )

        assertEquals(101, config.requests)
        assertEquals(11, config.warmupRequests)
        assertEquals(8, config.concurrency)
        assertEquals(63, config.requestPayloadBytes)
        assertEquals(20, config.responsePayloadBytes)
        assertEquals(256, config.queueCapacity)
        assertEquals(4000, config.timeoutMs)
        assertEquals("tcp", config.transport)
        assertEquals(19372, config.serverPort)
        assertEquals("bounded-pool", config.connectionMode)
        assertEquals("pi-c8-r1", config.runLabel)
    }

    @Test
    fun invalidOrDuplicateArgumentsFailClosed() {
        assertFailsWith<IllegalArgumentException> {
            RuntimeConnectorBenchmarkConfig.parse(arrayOf("--concurrency=0"))
        }
        assertFailsWith<IllegalArgumentException> {
            RuntimeConnectorBenchmarkConfig.parse(arrayOf("--requests=1", "--requests=2"))
        }
        assertFailsWith<IllegalArgumentException> {
            RuntimeConnectorBenchmarkConfig.parse(arrayOf("--unknown=1"))
        }
        assertFailsWith<IllegalArgumentException> {
            RuntimeConnectorBenchmarkConfig.parse(arrayOf("--run-label=not safe"))
        }
        assertFailsWith<IllegalArgumentException> {
            RuntimeConnectorBenchmarkConfig.parse(arrayOf("--transport=udp"))
        }
        assertFailsWith<IllegalArgumentException> {
            RuntimeConnectorBenchmarkConfig.parse(arrayOf("--connection-mode=guessed"))
        }
    }

    @Test
    fun workerSpansCoverEveryRequestOnceWithBoundedSkew() {
        val spans = RuntimeConnectorBenchmarkMath.workerSpans(total = 101, workers = 8)
        val indices = spans.flatMap { it.toList() }

        assertEquals((0 until 101).toList(), indices)
        assertTrue(spans.maxOf { it.count() } - spans.minOf { it.count() } <= 1)
    }

    @Test
    fun percentileUsesTheNativeComparatorNearestRank() {
        val sorted = longArrayOf(10, 20, 30, 40, 50)

        assertEquals(30, RuntimeConnectorBenchmarkMath.percentileNanos(sorted, 0.50))
        assertEquals(50, RuntimeConnectorBenchmarkMath.percentileNanos(sorted, 0.95))
        assertEquals(50, RuntimeConnectorBenchmarkMath.percentileNanos(sorted, 0.99))
    }
}
