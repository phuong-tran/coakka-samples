package coakka.v2.android

/** App-owned copies of a delivered request's dispatch values. */
data class AndroidRuntimeRequest(
    val target: String,
    val payload: ByteArray,
    val oneWay: Boolean,
)

/**
 * Builds and reads opaque runtime messages through Core's public contract.
 * Input arrays must remain read-only until the synchronous call returns.
 * Returned byte arrays are mutable copies owned by the application. No native
 * view survives a call. Retain a delivered frame until [reply] returns; the
 * application controls whether and how its handler responds.
 */
object AndroidRuntimeMessages {
    @JvmStatic
    @JvmOverloads
    fun request(
        messageId: String,
        source: String,
        target: String,
        replyTo: String,
        payload: ByteArray,
        timeoutMillis: Int,
        oneWay: Boolean = false,
    ): ByteArray {
        require(timeoutMillis >= 0) { "timeoutMillis must be non-negative" }
        val status = IntArray(1)
        val frame = NativeRuntimeBridge.nativeBuildMessageRequest(
            text(messageId), text(source), text(target), text(replyTo), payload,
            timeoutMillis, oneWay, status,
        )
        requireNativeOk(status[0], "message_request")
        return checkNotNull(frame) { "runtime message builder returned no frame" }
    }

    @JvmStatic
    fun reply(deliveredRequest: ByteArray, source: String, payload: ByteArray): ByteArray {
        val status = IntArray(1)
        val frame = NativeRuntimeBridge.nativeBuildMessageReply(deliveredRequest, text(source), payload, status)
        requireNativeOk(status[0], "message_reply")
        return checkNotNull(frame) { "runtime reply builder returned no frame" }
    }

    @JvmStatic
    fun decodeRequest(envelope: ByteArray): AndroidRuntimeRequest {
        val facts = IntArray(2)
        val values = NativeRuntimeBridge.nativeReadMessageRequest(envelope, facts)
        requireNativeOk(facts[0], "message_request_values")
        check(values != null && values.size == 2 && facts[1] in 0..1) { "invalid runtime request projection" }
        return AndroidRuntimeRequest(
            values[0].decodeToString(throwOnInvalidSequence = true), values[1], facts[1] == 1,
        )
    }

    @JvmStatic
    fun responsePayload(envelope: ByteArray): ByteArray {
        val status = IntArray(1)
        val payload = NativeRuntimeBridge.nativeMessageResponsePayload(envelope, status)
        requireNativeOk(status[0], "message_response_payload")
        return checkNotNull(payload) { "runtime response projection returned no payload" }
    }

    private fun text(value: String): ByteArray {
        val encoded = value.encodeToByteArray(throwOnInvalidSequence = true)
        require(encoded.none { it == 0.toByte() }) { "message text cannot contain NUL" }
        if (encoded.size == Int.MAX_VALUE) throw OutOfMemoryError("message text exceeds host-array capacity")
        return encoded.copyOf(encoded.size + 1)
    }
}
