package coakka.v2.connector

import com.sun.jna.Callback
import com.sun.jna.Pointer
import com.sun.jna.Structure

internal const val STREAM_LANE_DETAIL_BYTES = 160

/** Native publisher callback. All buffers are borrowed for the duration of [invoke]. */
fun interface NativeStreamSourceNext : Callback {
    fun invoke(context: Pointer?, destination: Pointer, capacity: Long, outFrame: NativeStreamFrame): Int
}

/** Native subscriber callback. All buffers are borrowed for the duration of [invoke]. */
fun interface NativeStreamConsumer : Callback {
    fun invoke(context: Pointer?, data: Pointer, frame: NativeStreamFrame): Int
}

@Structure.FieldOrder("struct_size", "sequence", "captured_mono_ns", "dropped_before", "flags", "size")
open class NativeStreamFrame : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var sequence: Long = 0
    @JvmField var captured_mono_ns: Long = 0
    @JvmField var dropped_before: Long = 0
    @JvmField var flags: Int = 0
    @JvmField var size: Long = 0
}

@Structure.FieldOrder(
    "struct_size", "mode", "reserved", "credential_generation", "credential_id",
    "ca_certificate_file", "identity_certificate_file", "private_key_file",
)
open class NativeStreamLaneSecurityConfig : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var mode: Int = 0
    @JvmField var reserved: Int = 0
    @JvmField var credential_generation: Long = 0
    @JvmField var credential_id: String? = null
    @JvmField var ca_certificate_file: String? = null
    @JvmField var identity_certificate_file: String? = null
    @JvmField var private_key_file: String? = null
}

@Structure.FieldOrder(
    "struct_size", "flags", "bind_host", "bind_port", "capacity", "max_frame_bytes",
    "max_window_bytes", "io_timeout_ms", "source_retry_ms", "progress_frames",
    "progress_interval_ms", "publisher_worker_count", "subscriber_worker_count", "security",
    "pressure_after_ms", "stalled_after_ms", "recovery_after_ms", "pressure_observation_ms",
)
open class NativeStreamLaneConfig : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var flags: Int = 0
    @JvmField var bind_host: String? = null
    @JvmField var bind_port: Short = 0
    @JvmField var capacity: Long = 0
    @JvmField var max_frame_bytes: Int = 0
    @JvmField var max_window_bytes: Int = 0
    @JvmField var io_timeout_ms: Int = 0
    @JvmField var source_retry_ms: Int = 0
    @JvmField var progress_frames: Int = 0
    @JvmField var progress_interval_ms: Int = 0
    @JvmField var publisher_worker_count: Int = 0
    @JvmField var subscriber_worker_count: Int = 0
    @JvmField var security: Pointer? = null
    @JvmField var pressure_after_ms: Int = 0
    @JvmField var stalled_after_ms: Int = 0
    @JvmField var recovery_after_ms: Int = 0
    @JvmField var pressure_observation_ms: Int = 0
}

@Structure.FieldOrder(
    "struct_size", "session_id", "authorization_token", "format_id", "max_frame_bytes",
    "source_next", "source_context",
)
open class NativeStreamPublishSpec : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var session_id: String? = null
    @JvmField var authorization_token: String? = null
    @JvmField var format_id: Long = 0
    @JvmField var max_frame_bytes: Int = 0
    @JvmField var source_next: NativeStreamSourceNext? = null
    @JvmField var source_context: Pointer? = null
}

@Structure.FieldOrder(
    "struct_size", "session_id", "authorization_token", "remote_host", "remote_port", "format_id",
    "max_frame_bytes", "initial_window_bytes", "timeout_ms", "consume", "consumer_context",
)
open class NativeStreamSubscribeSpec : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var session_id: String? = null
    @JvmField var authorization_token: String? = null
    @JvmField var remote_host: String? = null
    @JvmField var remote_port: Short = 0
    @JvmField var format_id: Long = 0
    @JvmField var max_frame_bytes: Int = 0
    @JvmField var initial_window_bytes: Int = 0
    @JvmField var timeout_ms: Int = 0
    @JvmField var consume: NativeStreamConsumer? = null
    @JvmField var consumer_context: Pointer? = null
}

@Structure.FieldOrder(
    "struct_size", "direction", "state", "result", "format_id", "frames", "bytes", "dropped_frames",
    "last_sequence", "negotiated_max_frame_bytes", "window_bytes", "cancel_requested", "update_sequence",
    "submitted_mono_ns", "started_mono_ns", "updated_mono_ns", "terminal_mono_ns", "detail",
)
open class NativeStreamSessionSnapshot : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var direction: Int = 0
    @JvmField var state: Int = 0
    @JvmField var result: Int = 0
    @JvmField var format_id: Long = 0
    @JvmField var frames: Long = 0
    @JvmField var bytes: Long = 0
    @JvmField var dropped_frames: Long = 0
    @JvmField var last_sequence: Long = 0
    @JvmField var negotiated_max_frame_bytes: Int = 0
    @JvmField var window_bytes: Int = 0
    @JvmField var cancel_requested: Int = 0
    @JvmField var update_sequence: Long = 0
    @JvmField var submitted_mono_ns: Long = 0
    @JvmField var started_mono_ns: Long = 0
    @JvmField var updated_mono_ns: Long = 0
    @JvmField var terminal_mono_ns: Long = 0
    @JvmField var detail: ByteArray = ByteArray(STREAM_LANE_DETAIL_BYTES)
}

@Structure.FieldOrder(
    "struct_size", "capacity", "queued_subscribers", "prepared_publishers", "active_publishers",
    "active_subscribers", "retained_records", "submitted_subscribers", "prepared_publisher_count",
    "ended_publishers", "ended_subscribers", "failed_publishers", "failed_subscribers", "canceled_sessions",
    "published_frames", "published_bytes", "consumed_frames", "consumed_bytes", "source_reported_drops",
)
open class NativeStreamLaneStats : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var capacity: Long = 0
    @JvmField var queued_subscribers: Long = 0
    @JvmField var prepared_publishers: Long = 0
    @JvmField var active_publishers: Long = 0
    @JvmField var active_subscribers: Long = 0
    @JvmField var retained_records: Long = 0
    @JvmField var submitted_subscribers: Long = 0
    @JvmField var prepared_publisher_count: Long = 0
    @JvmField var ended_publishers: Long = 0
    @JvmField var ended_subscribers: Long = 0
    @JvmField var failed_publishers: Long = 0
    @JvmField var failed_subscribers: Long = 0
    @JvmField var canceled_sessions: Long = 0
    @JvmField var published_frames: Long = 0
    @JvmField var published_bytes: Long = 0
    @JvmField var consumed_frames: Long = 0
    @JvmField var consumed_bytes: Long = 0
    @JvmField var source_reported_drops: Long = 0
}

@Structure.FieldOrder(
    "struct_size", "direction", "state", "reason_bits", "available_credit_bytes", "window_capacity_bytes",
    "update_sequence", "transition_count", "observed_mono_ns", "state_started_mono_ns",
    "pressure_started_mono_ns", "last_progress_mono_ns", "observed_delivery_bps", "current_operation_ns",
    "last_operation_ns", "total_pressured_ns", "max_pressured_ns",
)
open class NativeStreamPressureSnapshot : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var direction: Int = 0
    @JvmField var state: Int = 0
    @JvmField var reason_bits: Int = 0
    @JvmField var available_credit_bytes: Int = 0
    @JvmField var window_capacity_bytes: Int = 0
    @JvmField var update_sequence: Long = 0
    @JvmField var transition_count: Long = 0
    @JvmField var observed_mono_ns: Long = 0
    @JvmField var state_started_mono_ns: Long = 0
    @JvmField var pressure_started_mono_ns: Long = 0
    @JvmField var last_progress_mono_ns: Long = 0
    @JvmField var observed_delivery_bps: Long = 0
    @JvmField var current_operation_ns: Long = 0
    @JvmField var last_operation_ns: Long = 0
    @JvmField var total_pressured_ns: Long = 0
    @JvmField var max_pressured_ns: Long = 0
}
