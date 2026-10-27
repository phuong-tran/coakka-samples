package coakka.v2.connector

import coakka.v2.connector.internal.NativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Latest-state observability around the Core-owned monitor doorbell. */
data class RuntimeMonitorSnapshot(
    val signalCount: Long,
    val health: RuntimeHealthSnapshot,
    val stats: RuntimeStatsSnapshot,
)

class RuntimeMonitor internal constructor(private val handle: RuntimeHandle) {
    private val consumeLock = Any()

    fun isEnabled(): Boolean = handle.startupConfig().monitorEnabled

    fun snapshot(signalCount: Long = 0): RuntimeMonitorSnapshot =
        RuntimeMonitorSnapshot(signalCount, handle.health(), handle.stats())

    /** Null means Core reported no event before the bound, never closure/failure. */
    @JvmOverloads
    fun awaitNextBlocking(timeoutMs: Int = 1_000): RuntimeMonitorSnapshot? {
        require(timeoutMs >= 0) { "timeoutMs must be nonnegative" }
        check(isEnabled()) { "runtime monitor is not enabled" }
        return synchronized(consumeLock) {
            val record = NativeBridge.runtimeMonitorWait(handle.nativeId, timeoutMs)
            when (val status = record.value(0).toInt()) {
                CoakkaStatus.OK -> snapshot(record.value(1))
                CoakkaStatus.ERR_WOULD_BLOCK -> null
                else -> {
                    requireStatus(status, "monitor_consume")
                    null
                }
            }
        }
    }

    @JvmOverloads
    suspend fun awaitNext(timeoutMs: Long = 1_000): RuntimeMonitorSnapshot? {
        require(timeoutMs in 0..Int.MAX_VALUE.toLong()) { "timeoutMs exceeds the JNI wait representation" }
        return withContext(Dispatchers.IO) { awaitNextBlocking(timeoutMs.toInt()) }
    }
}
