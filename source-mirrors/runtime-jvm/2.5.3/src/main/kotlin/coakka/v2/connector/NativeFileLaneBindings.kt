package coakka.v2.connector

import com.sun.jna.Pointer
import com.sun.jna.Structure

internal const val FILE_LANE_SHA256_BYTES = 32
internal const val FILE_LANE_DETAIL_BYTES = 160

@Structure.FieldOrder(
    "struct_size", "mode", "reserved", "credential_generation", "credential_id",
    "ca_certificate_file", "identity_certificate_file", "private_key_file",
)
open class NativeFileLaneSecurityConfig : Structure() {
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
    "struct_size", "flags", "bind_host", "bind_port", "queue_capacity", "max_file_size",
    "io_timeout_ms", "checkpoint_bytes", "progress_bytes", "progress_interval_ms",
    "sender_worker_count", "receiver_worker_count", "security",
)
open class NativeFileLaneConfig : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var flags: Int = 0
    @JvmField var bind_host: String? = null
    @JvmField var bind_port: Short = 0
    @JvmField var queue_capacity: Long = 0
    @JvmField var max_file_size: Long = 0
    @JvmField var io_timeout_ms: Int = 0
    @JvmField var checkpoint_bytes: Long = 0
    @JvmField var progress_bytes: Long = 0
    @JvmField var progress_interval_ms: Int = 0
    @JvmField var sender_worker_count: Int = 0
    @JvmField var receiver_worker_count: Int = 0
    @JvmField var security: Pointer? = null
}

@Structure.FieldOrder(
    "struct_size", "transfer_id", "authorization_token", "destination_path", "expected_size", "expected_sha256",
)
open class NativeFileReceiveSpec : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var transfer_id: String? = null
    @JvmField var authorization_token: String? = null
    @JvmField var destination_path: String? = null
    @JvmField var expected_size: Long = 0
    @JvmField var expected_sha256: ByteArray = ByteArray(FILE_LANE_SHA256_BYTES)
}

@Structure.FieldOrder(
    "struct_size", "transfer_id", "authorization_token", "remote_host", "remote_port", "source_path",
    "expected_size", "expected_sha256", "timeout_ms",
)
open class NativeFileSendSpec : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var transfer_id: String? = null
    @JvmField var authorization_token: String? = null
    @JvmField var remote_host: String? = null
    @JvmField var remote_port: Short = 0
    @JvmField var source_path: String? = null
    @JvmField var expected_size: Long = 0
    @JvmField var expected_sha256: ByteArray = ByteArray(FILE_LANE_SHA256_BYTES)
    @JvmField var timeout_ms: Int = 0
}

@Structure.FieldOrder(
    "struct_size", "direction", "state", "result", "expected_size", "transferred_bytes", "committed_offset",
    "progress_milli", "cancel_requested", "update_sequence", "submitted_mono_ns", "started_mono_ns",
    "updated_mono_ns", "terminal_mono_ns", "detail",
)
open class NativeFileTransferSnapshot : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var direction: Int = 0
    @JvmField var state: Int = 0
    @JvmField var result: Int = 0
    @JvmField var expected_size: Long = 0
    @JvmField var transferred_bytes: Long = 0
    @JvmField var committed_offset: Long = 0
    @JvmField var progress_milli: Int = 0
    @JvmField var cancel_requested: Int = 0
    @JvmField var update_sequence: Long = 0
    @JvmField var submitted_mono_ns: Long = 0
    @JvmField var started_mono_ns: Long = 0
    @JvmField var updated_mono_ns: Long = 0
    @JvmField var terminal_mono_ns: Long = 0
    @JvmField var detail: ByteArray = ByteArray(FILE_LANE_DETAIL_BYTES)
}

@Structure.FieldOrder(
    "struct_size", "queue_capacity", "queued_sends", "prepared_receives", "active_sends", "active_receives",
    "retained_records", "submitted_sends", "prepared_receive_count", "completed_sends", "completed_receives",
    "failed_sends", "failed_receives", "canceled_transfers", "completed_send_bytes", "completed_receive_bytes",
)
open class NativeFileLaneStats : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var queue_capacity: Long = 0
    @JvmField var queued_sends: Long = 0
    @JvmField var prepared_receives: Long = 0
    @JvmField var active_sends: Long = 0
    @JvmField var active_receives: Long = 0
    @JvmField var retained_records: Long = 0
    @JvmField var submitted_sends: Long = 0
    @JvmField var prepared_receive_count: Long = 0
    @JvmField var completed_sends: Long = 0
    @JvmField var completed_receives: Long = 0
    @JvmField var failed_sends: Long = 0
    @JvmField var failed_receives: Long = 0
    @JvmField var canceled_transfers: Long = 0
    @JvmField var completed_send_bytes: Long = 0
    @JvmField var completed_receive_bytes: Long = 0
}
