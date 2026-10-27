package coakka.v2.connector.protocol

import coakka.v2.connector.NativeLibraryResolver
import coakka.v2.connector.internal.NativeBridge
import coakka.v2.connector.internal.NativeRecord
import coakka.v2.connector.requireStatus

/**
 * Core value codec. No wire grammar, schema classes or outcome inference lives here.
 * Calls are synchronous. Callers must not mutate input arrays/maps until return;
 * encoded/decoded arrays are independent copies, never retained native views.
 */
object EnvelopeMapper {
    @JvmStatic
    fun encode(envelope: ConnectorEnvelope): ByteArray {
        NativeLibraryResolver.load()
        require(envelope.headers.size <= MAX_VALUE_PROPERTIES) { "message property staging bound exceeded" }
        require(envelope.payload.size <= 64 * 1024 * 1024) { "payload staging bound exceeded" }
        val bytes = ArrayList<ByteArray>(9 + envelope.headers.size * 2)
        val staging = ValueTextStaging()
        bytes.add(staging.text(envelope.messageId))
        bytes.add(staging.text(envelope.correlationId))
        bytes.add(staging.text(envelope.source))
        bytes.add(staging.text(envelope.target))
        bytes.add(staging.text(envelope.replyTo))
        bytes.add(envelope.payload)
        bytes.add(staging.text(envelope.messageType))
        bytes.add(staging.text(envelope.errorCode))
        bytes.add(staging.text(envelope.errorMessage))
        envelope.headers.forEach { (key, value) ->
            bytes.add(staging.text(key))
            bytes.add(staging.text(value))
        }
        val result = NativeBridge.encodeMessage(longArrayOf(
            envelope.kind.value.toLong(), if (envelope.oneWay) 1 else 0,
            envelope.timeoutMs.toLong(), envelope.deliveryHint.value.toLong(),
            envelope.payloadSchemaVersion, envelope.payloadFormat.value.toLong(),
            envelope.status.value.toLong(), envelope.headers.size.toLong(),
        ), bytes.toTypedArray())
        requireStatus(result.value(0).toInt(), "client_encode_message_value")
        return result.payload(0)
    }

    @JvmStatic
    fun decode(bytes: ByteArray): ConnectorEnvelope {
        NativeLibraryResolver.load()
        val record = NativeBridge.decodeMessage(bytes)
        requireStatus(record.value(0).toInt(), "client_decode_message_value")
        return record.message(1, 0)
    }

    @JvmStatic
    fun decodeDeadletter(bytes: ByteArray): ConnectorDeadletter {
        NativeLibraryResolver.load()
        val record = NativeBridge.decodeDeadletter(bytes)
        requireStatus(record.value(0).toInt(), "client_decode_deadletter_value")
        val count = record.propertyCount(11)
        val original = when (record.value(12)) {
            0L -> {
                check(record.values.size == 13 && record.bytes.size == 2 + count * 2) { "invalid native deadletter extent" }
                null
            }
            1L -> record.message(13, 2 + count * 2)
            else -> error("invalid native original-message presence")
        }
        val failure = when (record.value(5)) {
            0L -> null
            1L -> ConnectorNativeFailure(record.value(6), record.value(7), record.value(8), record.value(9), record.value(10))
            else -> error("invalid native failure presence")
        }
        return ConnectorDeadletter(
            originalEnvelope = original,
            reason = ConnectorDeadletterReason.of(record.value(1).toInt()),
            detail = record.valueText(0),
            activeGeneration = record.value(2),
            resolvedHost = record.valueText(1),
            resolvedPort = record.value(3),
            endpointAttemptCount = record.value(4),
            failure = failure,
            failureDetails = record.properties(2, count),
        )
    }

    private fun NativeRecord.message(numericOffset: Int, byteOffset: Int): ConnectorEnvelope {
        val count = propertyCount(numericOffset + 7)
        check(values.size == numericOffset + 8 && bytes.size == byteOffset + 9 + count * 2) {
            "invalid native message extent"
        }
        val oneWay = value(numericOffset + 1)
        check(oneWay == 0L || oneWay == 1L) { "invalid native message boolean" }
        return ConnectorEnvelope(
            messageId = valueText(byteOffset), correlationId = valueText(byteOffset + 1),
            source = valueText(byteOffset + 2), target = valueText(byteOffset + 3),
            replyTo = valueText(byteOffset + 4), payload = payload(byteOffset + 5),
            messageType = valueText(byteOffset + 6), errorCode = valueText(byteOffset + 7),
            errorMessage = valueText(byteOffset + 8), headers = properties(byteOffset + 9, count),
            kind = ConnectorMessageKind.of(value(numericOffset).toInt()), oneWay = oneWay != 0L,
            timeoutMs = value(numericOffset + 2).toInt(),
            deliveryHint = ConnectorDeliveryHint.of(value(numericOffset + 3).toInt()),
            payloadSchemaVersion = value(numericOffset + 4),
            payloadFormat = ConnectorPayloadFormat.of(value(numericOffset + 5).toInt()),
            status = ConnectorBusinessStatus.of(value(numericOffset + 6).toInt()),
        )
    }

    private fun NativeRecord.propertyCount(index: Int): Int {
        val count = value(index)
        check(count in 0..MAX_VALUE_PROPERTIES.toLong()) { "invalid native property count" }
        return count.toInt()
    }

    private fun NativeRecord.properties(offset: Int, count: Int): Map<String, String> {
        if (count == 0) return emptyMap()
        val result = LinkedHashMap<String, String>(count)
        repeat(count) { index -> result[valueText(offset + index * 2)] = valueText(offset + index * 2 + 1) }
        return result
    }

    private fun NativeRecord.valueText(index: Int): String = payload(index).toString(Charsets.UTF_8)
}

// JVM-owned staging ceilings, not effective runtime settings. JNI independently
// checks against the exact SDK before copying or allocating native tables.
internal const val MAX_VALUE_PROPERTIES = 32768

/** Bound aggregate UTF-8 staging before allocation; never replace malformed UTF-16. */
internal class ValueTextStaging {
    private var remaining = 1024 * 1024

    fun text(value: String): ByteArray {
        require(value.length <= remaining) { "text staging bound exceeded" }
        var index = 0
        var size = 0
        while (index < value.length) {
            val char = value[index++]
            size += when {
                Character.isHighSurrogate(char) -> {
                    require(index < value.length && Character.isLowSurrogate(value[index])) { "text contains an unpaired surrogate" }
                    index++
                    4
                }
                Character.isLowSurrogate(char) -> throw IllegalArgumentException("text contains an unpaired surrogate")
                char.code < 0x80 -> 1
                char.code < 0x800 -> 2
                else -> 3
            }
            require(size <= remaining) { "text staging bound exceeded" }
        }
        remaining -= size
        return value.toByteArray(Charsets.UTF_8)
    }
}
