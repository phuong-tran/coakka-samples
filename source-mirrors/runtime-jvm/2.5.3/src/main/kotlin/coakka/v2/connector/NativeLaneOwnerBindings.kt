package coakka.v2.connector

import com.sun.jna.Structure

internal const val LANE_OWNER_INSTANCE_ID_BYTES = 128
internal const val LANE_ADVERTISED_HOST_BYTES = 256
internal const val FILE_LANE_TRANSFER_ID_BYTES = 65
internal const val FILE_LANE_TOKEN_BYTES = 129
internal const val STREAM_LANE_SESSION_ID_BYTES = 65
internal const val STREAM_LANE_TOKEN_BYTES = 129

@Structure.FieldOrder("struct_size", "owner_instance_id", "advertised_host")
open class NativeLaneOwnerConfig : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var owner_instance_id: String? = null
    @JvmField var advertised_host: String? = null
}

@Structure.FieldOrder("struct_size", "port", "reserved", "owner_instance_id", "advertised_host")
open class NativeLaneOwnerEndpoint : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var port: Short = 0
    @JvmField var reserved: Short = 0
    @JvmField var owner_instance_id: ByteArray = ByteArray(LANE_OWNER_INSTANCE_ID_BYTES)
    @JvmField var advertised_host: ByteArray = ByteArray(LANE_ADVERTISED_HOST_BYTES)
}

@Structure.FieldOrder("struct_size", "lane", "owner")
open class NativeFileLaneOwnedConfig : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var lane: NativeFileLaneConfig = NativeFileLaneConfig()
    @JvmField var owner: NativeLaneOwnerConfig = NativeLaneOwnerConfig()
}

@Structure.FieldOrder(
    "struct_size", "owner", "transfer_id", "authorization_token", "expected_size", "expected_sha256",
)
open class NativeFileReceiveGrant : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var owner: NativeLaneOwnerEndpoint = NativeLaneOwnerEndpoint()
    @JvmField var transfer_id: ByteArray = ByteArray(FILE_LANE_TRANSFER_ID_BYTES)
    @JvmField var authorization_token: ByteArray = ByteArray(FILE_LANE_TOKEN_BYTES)
    @JvmField var expected_size: Long = 0
    @JvmField var expected_sha256: ByteArray = ByteArray(FILE_LANE_SHA256_BYTES)
}

@Structure.FieldOrder("struct_size", "lane", "owner")
open class NativeStreamLaneOwnedConfig : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var lane: NativeStreamLaneConfig = NativeStreamLaneConfig()
    @JvmField var owner: NativeLaneOwnerConfig = NativeLaneOwnerConfig()
}

@Structure.FieldOrder(
    "struct_size", "owner", "session_id", "authorization_token", "format_id", "max_frame_bytes", "reserved",
)
open class NativeStreamPublishGrant : Structure() {
    @JvmField var struct_size: Long = 0
    @JvmField var owner: NativeLaneOwnerEndpoint = NativeLaneOwnerEndpoint()
    @JvmField var session_id: ByteArray = ByteArray(STREAM_LANE_SESSION_ID_BYTES)
    @JvmField var authorization_token: ByteArray = ByteArray(STREAM_LANE_TOKEN_BYTES)
    @JvmField var format_id: Long = 0
    @JvmField var max_frame_bytes: Int = 0
    @JvmField var reserved: Int = 0
}

internal fun nativeFixedText(value: ByteArray): String {
    val end = value.indexOf(0.toByte()).let { if (it < 0) value.size else it }
    return value.copyOf(end).toString(Charsets.UTF_8)
}
