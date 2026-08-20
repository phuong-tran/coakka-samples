package coakka.v2.connector

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.ptr.ShortByReference
import com.sun.jna.win32.StdCallLibrary

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
    const val SOUTHBOUND_PROBE_ONLY = 1 shl 4
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
    const val CAF_BACKEND = 1 shl 5
    const val JEMALLOC = 1 shl 6
    const val DELIVERED_REQUEST_PIPE = 1 shl 7
    const val FILE_LANE = 1 shl 23
    const val STREAM_LANE = 1 shl 24
    const val LANE_OWNER_GRANTS = 1 shl 25
}

@Structure.FieldOrder("system_name", "node_id", "strict_no_drop", "queue_capacity")
open class RuntimeConfig : Structure() {
    @JvmField var system_name: String? = null
    @JvmField var node_id: String? = null
    @JvmField var strict_no_drop: Int = 1
    @JvmField var queue_capacity: Int = 128
}

@Structure.FieldOrder(
    "struct_size",
    "fields",
    "mode",
    "reserved",
    "bind_host",
    "advertise_host",
    "bind_port",
    "advertise_port",
    "reserved2",
)
open class NativeNetworkOptions : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var fields: Long = 0
    @JvmField var mode: Int = 0
    @JvmField var reserved: Int = 0
    @JvmField var bind_host: String? = null
    @JvmField var advertise_host: String? = null
    @JvmField var bind_port: Short = 0
    @JvmField var advertise_port: Short = 0
    @JvmField var reserved2: Int = 0
}

@Structure.FieldOrder(
    "struct_size",
    "abi_version",
    "feature_flags",
    "runtime_version",
    "git_commit",
    "southbound_backend",
    "allocator_backend",
    "docs_hint",
)
open class RuntimeInfo : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var abi_version: Int = 0
    @JvmField var feature_flags: Int = 0
    @JvmField var runtime_version: String? = null
    @JvmField var git_commit: String? = null
    @JvmField var southbound_backend: String? = null
    @JvmField var allocator_backend: String? = null
    @JvmField var docs_hint: String? = null
}

@Structure.FieldOrder(
    "struct_size",
    "system_name",
    "node_id",
    "strict_no_drop",
    "queue_capacity",
    "request_max_frame_size",
    "local_dispatch_batch_limit",
    "runtime_state",
    "snapshot_present",
    "applied_generation",
    "route_count",
    "southbound_bind_host",
    "southbound_bind_port",
    "configured_ingress_overload_mode",
    "configured_local_delivery_overload_mode",
    "configured_remote_outbound_overload_mode",
    "configured_remote_outbound_reply_reserve_slots",
    "effective_ingress_overload_mode",
    "effective_local_delivery_overload_mode",
    "effective_remote_outbound_overload_mode",
    "effective_remote_outbound_reply_reserve_slots",
)
open class RuntimeConfigView : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var system_name: String? = null
    @JvmField var node_id: String? = null
    @JvmField var strict_no_drop: Int = 0
    @JvmField var queue_capacity: Int = 0
    @JvmField var request_max_frame_size: Long = 0
    @JvmField var local_dispatch_batch_limit: Long = 0
    @JvmField var runtime_state: Int = 0
    @JvmField var snapshot_present: Int = 0
    @JvmField var applied_generation: Long = 0
    @JvmField var route_count: Long = 0
    @JvmField var southbound_bind_host: String? = null
    @JvmField var southbound_bind_port: Short = 0
    @JvmField var configured_ingress_overload_mode: Int = 0
    @JvmField var configured_local_delivery_overload_mode: Int = 0
    @JvmField var configured_remote_outbound_overload_mode: Int = 0
    @JvmField var configured_remote_outbound_reply_reserve_slots: Long = 0
    @JvmField var effective_ingress_overload_mode: Int = 0
    @JvmField var effective_local_delivery_overload_mode: Int = 0
    @JvmField var effective_remote_outbound_overload_mode: Int = 0
    @JvmField var effective_remote_outbound_reply_reserve_slots: Long = 0
}

@Structure.FieldOrder(
    "struct_size",
    "flags",
    "request_write_fd",
    "response_read_fd",
    "deadletter_read_fd",
    "control_write_fd",
    "monitor_read_fd",
    "delivered_request_read_fd",
)
open class HostHandles : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var flags: Int = 0
    @JvmField var request_write_fd: Int = -1
    @JvmField var response_read_fd: Int = -1
    @JvmField var deadletter_read_fd: Int = -1
    @JvmField var control_write_fd: Int = -1
    @JvmField var monitor_read_fd: Int = -1
    @JvmField var delivered_request_read_fd: Int = -1
}

@Structure.FieldOrder("struct_size", "runtime_state", "flags", "applied_generation")
open class RuntimeHealth : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var runtime_state: Int = 0
    @JvmField var flags: Int = 0
    @JvmField var applied_generation: Long = 0
}

@Structure.FieldOrder(
    "struct_size",
    "applied_generation",
    "route_count",
    "runtime_state",
    "ingress_queue_capacity",
    "ingress_queue_depth",
    "ingress_queue_high_watermark",
    "queue_rejected_count",
    "route_miss_count",
    "deadletter_count",
    "delivery_failed_count",
    "caf_send_failed_count",
    "southbound_submit_attempt_count",
    "southbound_probe_connect_success_count",
    "southbound_probe_connect_failure_count",
    "request_max_frame_size",
    "local_dispatch_batch_limit",
    "delivered_request_outbound_queue_capacity",
    "delivered_request_outbound_queue_depth",
    "delivered_request_outbound_queue_high_watermark",
    "delivered_request_outbound_enqueue_block_count",
    "response_outbound_queue_capacity",
    "response_outbound_queue_depth",
    "response_outbound_queue_high_watermark",
    "response_outbound_enqueue_block_count",
    "deadletter_outbound_queue_capacity",
    "deadletter_outbound_queue_depth",
    "deadletter_outbound_queue_high_watermark",
    "deadletter_outbound_enqueue_block_count",
    "remote_reply_timeout_count",
    "late_remote_reply_drop_count",
    "remote_outbound_queue_capacity",
    "remote_outbound_queue_depth",
    "remote_outbound_queue_high_watermark",
    "remote_outbound_queue_rejected_count",
    "remote_outbound_expired_drop_count",
    "endpoint_unavailable_count",
    "remote_response_forwarded_count",
    "remote_deadletter_forwarded_count",
    "drained_route_count",
    "control_rejected_count",
    "control_invalid_count",
    "control_stale_generation_count",
    "control_bad_state_count",
    "control_io_count",
    "remote_outbound_reply_reserve_slots",
    "remote_outbound_reply_reservation_reject_count",
    "ingress_overload_mode",
    "local_delivery_overload_mode",
    "remote_outbound_overload_mode",
    "monitor_event_emitted_count",
    "monitor_event_dropped_count",
    "monitor_event_emitted_lifetime_count",
    "monitor_event_dropped_lifetime_count",
    "local_work_queue_capacity",
    "local_work_queue_depth",
    "local_work_queue_high_watermark",
    "delivered_request_outbound_direct_write_count",
    "response_outbound_direct_write_count",
    "deadletter_outbound_direct_write_count",
    "remote_outbound_one_way_drop_count",
)
open class RuntimeStats : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var applied_generation: Long = 0
    @JvmField var route_count: Long = 0
    @JvmField var runtime_state: Int = 0
    @JvmField var ingress_queue_capacity: Long = 0
    @JvmField var ingress_queue_depth: Long = 0
    @JvmField var ingress_queue_high_watermark: Long = 0
    @JvmField var queue_rejected_count: Long = 0
    @JvmField var route_miss_count: Long = 0
    @JvmField var deadletter_count: Long = 0
    @JvmField var delivery_failed_count: Long = 0
    @JvmField var caf_send_failed_count: Long = 0
    @JvmField var southbound_submit_attempt_count: Long = 0
    @JvmField var southbound_probe_connect_success_count: Long = 0
    @JvmField var southbound_probe_connect_failure_count: Long = 0
    @JvmField var request_max_frame_size: Long = 0
    @JvmField var local_dispatch_batch_limit: Long = 0
    @JvmField var delivered_request_outbound_queue_capacity: Long = 0
    @JvmField var delivered_request_outbound_queue_depth: Long = 0
    @JvmField var delivered_request_outbound_queue_high_watermark: Long = 0
    @JvmField var delivered_request_outbound_enqueue_block_count: Long = 0
    @JvmField var response_outbound_queue_capacity: Long = 0
    @JvmField var response_outbound_queue_depth: Long = 0
    @JvmField var response_outbound_queue_high_watermark: Long = 0
    @JvmField var response_outbound_enqueue_block_count: Long = 0
    @JvmField var deadletter_outbound_queue_capacity: Long = 0
    @JvmField var deadletter_outbound_queue_depth: Long = 0
    @JvmField var deadletter_outbound_queue_high_watermark: Long = 0
    @JvmField var deadletter_outbound_enqueue_block_count: Long = 0
    @JvmField var remote_reply_timeout_count: Long = 0
    @JvmField var late_remote_reply_drop_count: Long = 0
    @JvmField var remote_outbound_queue_capacity: Long = 0
    @JvmField var remote_outbound_queue_depth: Long = 0
    @JvmField var remote_outbound_queue_high_watermark: Long = 0
    @JvmField var remote_outbound_queue_rejected_count: Long = 0
    @JvmField var remote_outbound_expired_drop_count: Long = 0
    @JvmField var endpoint_unavailable_count: Long = 0
    @JvmField var remote_response_forwarded_count: Long = 0
    @JvmField var remote_deadletter_forwarded_count: Long = 0
    @JvmField var drained_route_count: Long = 0
    @JvmField var control_rejected_count: Long = 0
    @JvmField var control_invalid_count: Long = 0
    @JvmField var control_stale_generation_count: Long = 0
    @JvmField var control_bad_state_count: Long = 0
    @JvmField var control_io_count: Long = 0
    @JvmField var remote_outbound_reply_reserve_slots: Long = 0
    @JvmField var remote_outbound_reply_reservation_reject_count: Long = 0
    @JvmField var ingress_overload_mode: Int = 0
    @JvmField var local_delivery_overload_mode: Int = 0
    @JvmField var remote_outbound_overload_mode: Int = 0
    @JvmField var monitor_event_emitted_count: Long = 0
    @JvmField var monitor_event_dropped_count: Long = 0
    @JvmField var monitor_event_emitted_lifetime_count: Long = 0
    @JvmField var monitor_event_dropped_lifetime_count: Long = 0
    @JvmField var local_work_queue_capacity: Long = 0
    @JvmField var local_work_queue_depth: Long = 0
    @JvmField var local_work_queue_high_watermark: Long = 0
    @JvmField var delivered_request_outbound_direct_write_count: Long = 0
    @JvmField var response_outbound_direct_write_count: Long = 0
    @JvmField var deadletter_outbound_direct_write_count: Long = 0
    @JvmField var remote_outbound_one_way_drop_count: Long = 0
}

@Structure.FieldOrder("fd", "events", "revents")
open class PollFd : Structure() {
    @JvmField var fd: Int = -1
    @JvmField var events: Short = 0
    @JvmField var revents: Short = 0
}

interface CoakkaV2Library : Library {
    fun coakka_v2_runtime_get_abi_version(): Int
    fun coakka_v2_runtime_get_info(outInfo: RuntimeInfo): Int
    fun coakka_v2_runtime_get_config(rt: Pointer?, outConfig: RuntimeConfigView): Int
    fun coakka_v2_runtime_create(cfg: RuntimeConfig): Pointer?
    fun coakka_v2_runtime_destroy(rt: Pointer?)
    fun coakka_v2_runtime_apply_network_options(rt: Pointer?, options: NativeNetworkOptions): Int
    fun coakka_v2_runtime_get_host_handles(rt: Pointer?, outHandles: HostHandles): Int
    fun coakka_v2_runtime_start(rt: Pointer?): Int
    fun coakka_v2_runtime_stop(rt: Pointer?): Int
    fun coakka_v2_runtime_get_health(rt: Pointer?, outHealth: RuntimeHealth): Int
    fun coakka_v2_runtime_get_stats(rt: Pointer?, outStats: RuntimeStats): Int
    fun coakka_v2_runtime_get_capabilities(outCapabilities: NativeRuntimeCapabilities): Int
    fun coakka_v2_runtime_apply_tcp_connection_options_ex(
        rt: Pointer?,
        options: NativeTcpConnectionOptions,
        outResult: NativeTcpConnectionApplyResult,
    ): Int
    fun coakka_v2_runtime_get_tcp_connection_config(
        rt: Pointer?,
        outConfig: NativeTcpConnectionConfig,
    ): Int
    fun coakka_v2_runtime_apply_tcp_security_options_ex(
        rt: Pointer?,
        options: NativeTcpSecurityOptions,
        outResult: NativeTcpSecurityApplyResult,
    ): Int
    fun coakka_v2_runtime_get_tcp_security_info(
        rt: Pointer?,
        outInfo: NativeTcpSecurityInfo,
    ): Int
    fun coakka_v2_file_lane_create_ex(config: NativeFileLaneConfig, outLane: PointerByReference): Int
    fun coakka_v2_file_lane_create_owned_ex(config: NativeFileLaneOwnedConfig, outLane: PointerByReference): Int
    fun coakka_v2_file_lane_destroy(lane: Pointer?)
    fun coakka_v2_file_lane_start(lane: Pointer?): Int
    fun coakka_v2_file_lane_stop(lane: Pointer?): Int
    fun coakka_v2_file_lane_get_bound_port(lane: Pointer?, outPort: ShortByReference): Int
    fun coakka_v2_file_lane_prepare_receive(lane: Pointer?, spec: NativeFileReceiveSpec): Int
    fun coakka_v2_file_lane_prepare_receive_grant(
        lane: Pointer?,
        spec: NativeFileReceiveSpec,
        outGrant: NativeFileReceiveGrant,
    ): Int
    fun coakka_v2_file_lane_submit_send(lane: Pointer?, spec: NativeFileSendSpec): Int
    fun coakka_v2_file_lane_get_transfer(
        lane: Pointer?,
        transferId: String,
        direction: Int,
        outSnapshot: NativeFileTransferSnapshot,
    ): Int
    fun coakka_v2_file_lane_wait_transfer(
        lane: Pointer?,
        transferId: String,
        direction: Int,
        afterUpdateSequence: Long,
        timeoutMs: Int,
        outSnapshot: NativeFileTransferSnapshot,
    ): Int
    fun coakka_v2_file_lane_cancel_transfer(lane: Pointer?, transferId: String, direction: Int): Int
    fun coakka_v2_file_lane_forget_transfer(lane: Pointer?, transferId: String, direction: Int): Int
    fun coakka_v2_file_lane_get_stats(lane: Pointer?, outStats: NativeFileLaneStats): Int
    fun coakka_v2_file_sha256_path(path: String, outSha256: ByteArray, outSize: LongByReference): Int
    fun coakka_v2_stream_lane_create_ex(config: NativeStreamLaneConfig, outLane: PointerByReference): Int
    fun coakka_v2_stream_lane_create_owned_ex(config: NativeStreamLaneOwnedConfig, outLane: PointerByReference): Int
    fun coakka_v2_stream_lane_destroy(lane: Pointer?)
    fun coakka_v2_stream_lane_start(lane: Pointer?): Int
    fun coakka_v2_stream_lane_stop(lane: Pointer?): Int
    fun coakka_v2_stream_lane_get_bound_port(lane: Pointer?, outPort: ShortByReference): Int
    fun coakka_v2_stream_lane_prepare_publish(lane: Pointer?, spec: NativeStreamPublishSpec): Int
    fun coakka_v2_stream_lane_prepare_publish_grant(
        lane: Pointer?,
        spec: NativeStreamPublishSpec,
        outGrant: NativeStreamPublishGrant,
    ): Int
    fun coakka_v2_stream_lane_subscribe(lane: Pointer?, spec: NativeStreamSubscribeSpec): Int
    fun coakka_v2_stream_lane_get_session(
        lane: Pointer?,
        sessionId: String,
        direction: Int,
        outSnapshot: NativeStreamSessionSnapshot,
    ): Int
    fun coakka_v2_stream_lane_wait_session(
        lane: Pointer?,
        sessionId: String,
        direction: Int,
        afterUpdateSequence: Long,
        timeoutMs: Int,
        outSnapshot: NativeStreamSessionSnapshot,
    ): Int
    fun coakka_v2_stream_lane_get_pressure(
        lane: Pointer?,
        sessionId: String,
        direction: Int,
        outSnapshot: NativeStreamPressureSnapshot,
    ): Int
    fun coakka_v2_stream_lane_wait_pressure(
        lane: Pointer?,
        sessionId: String,
        direction: Int,
        afterUpdateSequence: Long,
        timeoutMs: Int,
        outSnapshot: NativeStreamPressureSnapshot,
    ): Int
    fun coakka_v2_stream_lane_cancel_session(lane: Pointer?, sessionId: String, direction: Int): Int
    fun coakka_v2_stream_lane_forget_session(lane: Pointer?, sessionId: String, direction: Int): Int
    fun coakka_v2_stream_lane_get_stats(lane: Pointer?, outStats: NativeStreamLaneStats): Int
    fun coakka_v2_runtime_submit_envelope(rt: Pointer?, buf: ByteArray, len: Long): Int
    fun coakka_v2_runtime_apply_control_envelope(rt: Pointer?, buf: ByteArray, len: Long): Int
    fun coakka_v2_frame_reader_create(fd: Int, maxFrameSize: Long): Pointer?
    fun coakka_v2_frame_reader_destroy(reader: Pointer?)
    fun coakka_v2_frame_read_try(reader: Pointer?, outBuf: PointerByReference, outLen: LongByReference): Int
    fun coakka_v2_frame_release(buf: Pointer?)
    fun coakka_v2_monitor_consume(fd: Int, outSignalCount: LongByReference): Int
    fun coakka_v2_status_name(status: Int): String?
    fun coakka_v2_runtime_state_name(state: Int): String?
    fun coakka_v2_transport_apply_reason_name(reason: Int): String?
    fun coakka_v2_overload_mode_name(mode: Int): String?
    fun coakka_v2_format_runtime_feature_flags(flags: Int, buf: ByteArray, bufLen: Long): Long
    fun coakka_v2_format_health_flags(flags: Int, buf: ByteArray, bufLen: Long): Long

    companion object {
        const val ABI_VERSION = 1

        fun load(path: String): CoakkaV2Library =
            Native.load(path, CoakkaV2Library::class.java)
    }
}

interface PosixLibC : Library {
    fun read(fd: Int, buf: ByteArray, count: Long): Long
    fun close(fd: Int): Int
    fun poll(fds: Pointer, nfds: Int, timeout: Int): Int

    companion object {
        val INSTANCE: PosixLibC = Native.load("c", PosixLibC::class.java)
    }
}

interface WindowsMsvcrt : Library {
    fun _read(fd: Int, buf: ByteArray, count: Int): Int
    fun _close(fd: Int): Int
    fun _get_osfhandle(fd: Int): Long

    companion object {
        val INSTANCE: WindowsMsvcrt = Native.load("msvcrt", WindowsMsvcrt::class.java)
    }
}

interface WindowsKernel32 : StdCallLibrary {
    fun WaitForSingleObject(handle: Pointer, millis: Int): Int

    companion object {
        val INSTANCE: WindowsKernel32 = Native.load("kernel32", WindowsKernel32::class.java)
    }
}
