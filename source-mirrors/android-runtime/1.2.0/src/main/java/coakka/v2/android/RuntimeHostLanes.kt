package coakka.v2.android

import android.os.ParcelFileDescriptor
import java.io.Closeable

internal class RuntimeHostLanes private constructor(
    private val cancellation: LaneCancellation,
    val request: WriteFrameLane,
    val response: ReadFrameLane,
    val deadletter: ReadFrameLane,
    val control: WriteFrameLane,
    val monitor: MonitorLane?,
    val deliveredRequest: ReadFrameLane?,
) : Closeable {
    fun cancelWaiters() {
        cancellation.cancel()
    }

    override fun close() {
        cancelWaiters()
        request.close()
        control.close()
        deliveredRequest?.close()
        response.close()
        deadletter.close()
        monitor?.close()
        cancellation.close()
    }

    companion object {
        const val ENABLE_MONITOR = 1
        const val SEPARATE_DELIVERED_REQUEST_LANE = 1 shl 1

        fun adopt(values: IntArray): RuntimeHostLanes {
            require(values.size == HOST_HANDLE_VALUE_COUNT) {
                "unexpected host handle count=${values.size}"
            }
            require(values[0] == HOST_HANDLE_LAYOUT_VERSION) {
                "unsupported host handle layout=${values[0]}"
            }

            val adopted = ArrayList<ParcelFileDescriptor>(values.size - 1)
            val cancellation = LaneCancellation.create()
            try {
                fun required(index: Int): ParcelFileDescriptor {
                    require(values[index] >= 0) { "missing required runtime fd at index=$index" }
                    return ParcelFileDescriptor.adoptFd(values[index]).also(adopted::add)
                }

                fun optional(index: Int): ParcelFileDescriptor? =
                    values[index].takeIf { it >= 0 }
                        ?.let(ParcelFileDescriptor::adoptFd)
                        ?.also(adopted::add)

                val request = WriteFrameLane(required(REQUEST_INDEX), cancellation)
                val response = ReadFrameLane(required(RESPONSE_INDEX), cancellation)
                val deadletter = ReadFrameLane(required(DEADLETTER_INDEX), cancellation)
                val control = WriteFrameLane(required(CONTROL_INDEX), cancellation)
                val monitor = optional(MONITOR_INDEX)?.let {
                    MonitorLane(it, cancellation)
                }
                val delivered = optional(DELIVERED_REQUEST_INDEX)?.let {
                    ReadFrameLane(it, cancellation)
                }
                adopted.clear()
                return RuntimeHostLanes(
                    cancellation = cancellation,
                    request = request,
                    response = response,
                    deadletter = deadletter,
                    control = control,
                    monitor = monitor,
                    deliveredRequest = delivered,
                )
            } catch (failure: Throwable) {
                cancellation.close()
                val adoptedRawFds = adopted.mapTo(HashSet()) { it.fd }
                adopted.forEach { pfd -> runCatching(pfd::close) }
                values.drop(1).forEach { rawFd ->
                    if (rawFd >= 0 && rawFd !in adoptedRawFds) {
                        runCatching { ParcelFileDescriptor.adoptFd(rawFd).close() }
                    }
                }
                throw failure
            }
        }

        private const val HOST_HANDLE_LAYOUT_VERSION = 1
        private const val HOST_HANDLE_VALUE_COUNT = 7
        private const val REQUEST_INDEX = 1
        private const val RESPONSE_INDEX = 2
        private const val DEADLETTER_INDEX = 3
        private const val CONTROL_INDEX = 4
        private const val MONITOR_INDEX = 5
        private const val DELIVERED_REQUEST_INDEX = 6
    }
}
