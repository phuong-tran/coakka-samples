package coakka.v2.connector

object CoakkaStatus {
    const val OK = 0
    const val ERR_INVALID_ARG = -1
    const val ERR_NOMEM = -2
    const val ERR_BAD_STATE = -3
    const val ERR_STALE_GENERATION = -4
    const val ERR_IO = -5
    const val ERR_WOULD_BLOCK = -6
    const val ERR_CLOSED = -7
    const val ERR_FEATURE_UNAVAILABLE = -8
    const val ERR_FEATURE_NOT_ENTITLED = -9
}

object CoakkaHealthFlags {
    const val PROCESS_ALIVE = 1 shl 0
    const val RUNTIME_STARTED = 1 shl 1
    const val CONTROL_SNAPSHOT_PRESENT = 1 shl 2
    const val DATAPLANE_READY = 1 shl 3
    const val TRANSPORT_PROBE_ONLY = 1 shl 4
    const val REMOTE_OUTBOUND_SATURATED = 1 shl 5
    const val DRAINED_ROUTE_PRESENT = 1 shl 6
}

object CoakkaHostHandlesFlags {
    const val ENABLE_MONITOR = 1 shl 0
    const val SEPARATE_DELIVERED_REQUEST_LANE = 1 shl 1
}

object CoakkaRuntimeFeatures {
    const val REQUEST_PIPE = 1 shl 0
    const val CONTROL_PIPE = 1 shl 1
    const val MONITOR = 1 shl 2
    const val NATIVE_SUBMIT = 1 shl 3
    const val CONTROL_JSON = 1 shl 4
    const val REMOTE_TRANSPORT = 1 shl 5
    const val CUSTOM_ALLOCATOR = 1 shl 6
    const val DELIVERED_REQUEST_PIPE = 1 shl 7
    const val FILE_LANE = 1 shl 23
    const val STREAM_LANE = 1 shl 24
    const val LANE_OWNER_GRANTS = 1 shl 25
}


/** Public native contract version; not a second implementation or transport. */
object CoakkaRuntimeAbi {
    const val VERSION = 1
}

/** Copy-owned descriptor facts. Only the native bridge owns descriptor I/O/close. */
internal data class NativeHostHandles(
    var request_write_fd: Int = -1,
    var response_read_fd: Int = -1,
    var deadletter_read_fd: Int = -1,
    var control_write_fd: Int = -1,
    var monitor_read_fd: Int = -1,
    var delivered_request_read_fd: Int = -1,
)
