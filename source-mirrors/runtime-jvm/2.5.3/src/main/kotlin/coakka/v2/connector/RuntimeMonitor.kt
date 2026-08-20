package coakka.v2.connector

import com.sun.jna.ptr.LongByReference
import coakka.v2.connector.requireStatus
import coakka.v2.connector.statusName
import coakka.v2.connector.waitReadable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Latest-state observability wrapper around the runtime monitor doorbell.
 *
 * The monitor fd is not a history stream. A wakeup only means "refresh your view now".
 */
data class RuntimeMonitorSnapshot(
    val signalCount: Long,
    val health: RuntimeHealthSnapshot,
    val stats: RuntimeStatsSnapshot,
)

class RuntimeMonitor internal constructor(
    private val handle: RuntimeHandle,
) {
    private val consumeLock = Any()
    private val isWindowsHost = System.getProperty("os.name").lowercase().contains("win")

    fun isEnabled(): Boolean = handle.hostHandles.monitor_read_fd >= 0

    fun snapshot(signalCount: Long = 0): RuntimeMonitorSnapshot =
        RuntimeMonitorSnapshot(
            signalCount = signalCount,
            health = handle.health(),
            stats = handle.stats(),
        )

    @JvmOverloads
    fun awaitNextBlocking(timeoutMs: Int = 1_000): RuntimeMonitorSnapshot? {
        check(isEnabled()) { "runtime monitor is not enabled" }
        synchronized(consumeLock) {
            val monitorFd = handle.hostHandles.monitor_read_fd
            if (isWindowsHost) {
                val deadlineNanos = System.nanoTime() + (timeoutMs.toLong() * 1_000_000L)
                while (true) {
                    val signalCount = LongByReference()
                    when (val rc = handle.lib.coakka_v2_monitor_consume(monitorFd, signalCount)) {
                        CoakkaStatus.OK -> return snapshot(signalCount = signalCount.value)
                        CoakkaStatus.ERR_WOULD_BLOCK -> {
                            val remainingMs =
                                ((deadlineNanos - System.nanoTime()) / 1_000_000L).coerceAtLeast(0L)
                            if (remainingMs == 0L) {
                                return null
                            }
                            Thread.sleep(minOf(5L, remainingMs))
                        }
                        CoakkaStatus.ERR_CLOSED -> return null
                        else -> error("monitor_consume failed rc=$rc (${statusName(rc)})")
                    }
                }
            }
            if (!waitReadable(monitorFd, timeoutMs)) {
                return null
            }
            val signalCount = LongByReference()
            requireStatus(handle.lib.coakka_v2_monitor_consume(monitorFd, signalCount), "monitor_consume")
            return snapshot(signalCount = signalCount.value)
        }
    }

    @JvmOverloads
    suspend fun awaitNext(timeoutMs: Long = 1_000): RuntimeMonitorSnapshot? =
        withContext(Dispatchers.IO) {
            awaitNextBlocking(timeoutMs.toInt())
        }
}
