package coakka.v2.connector.protocol

import coakka.v2.connector.CoakkaStatus
import coakka.v2.connector.NativeLibraryResolver
import coakka.v2.connector.TestSupport
import coakka.v2.connector.internal.NativeBridge
import coakka.v2.transport.Deadletter
import coakka.v2.transport.Envelope
import com.google.protobuf.ByteString
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Private independent grammar oracle; neither these schemas nor their runtime ship. */
class CoreValueProjectionTest {
    private fun value() = ConnectorEnvelope(
        messageId = "id\u0000tiếng Việt🙂", correlationId = "correlation\u0000", source = "source",
        target = "target", replyTo = "reply", kind = ConnectorMessageKind.of(-123), oneWay = true,
        timeoutMs = Int.MIN_VALUE, payload = byteArrayOf(0, -1, 7),
        headers = mapOf("key\u0000" to "value🙂"), status = ConnectorBusinessStatus.of(-234),
        errorCode = "error\u0000code", errorMessage = "error message", deliveryHint = ConnectorDeliveryHint.of(-345),
        messageType = "application\u0000type", payloadSchemaVersion = 0xffff_ffffL,
        payloadFormat = ConnectorPayloadFormat.of(-456),
    )

    @Test
    fun allValuesAndUnknownNumbersRoundTripThroughIndependentOracle() {
        val input = value()
        val bytes = EnvelopeMapper.encode(input)
        val oracle = Envelope.parseFrom(bytes)
        assertEquals(input.messageId, oracle.messageId)
        assertEquals(input.kind.value, oracle.kindValue)
        assertEquals(input.status.value, oracle.statusValue)
        assertEquals(input.deliveryHint.value, oracle.deliveryHintValue)
        assertEquals(input.payloadFormat.value, oracle.payloadFormatValue)
        assertEquals(-1, oracle.payloadSchemaVersion)
        val actual = EnvelopeMapper.decode(oracle.toByteArray())
        assertContentEquals(input.payload, actual.payload)
        assertEquals(input.copy(payload = actual.payload), actual)
    }

    @Test
    fun emptyValuesDoNotBecomeSuccessOrDefaultRouting() {
        val actual = EnvelopeMapper.decode(byteArrayOf())
        assertEquals(ConnectorMessageKind.UNSPECIFIED, actual.kind)
        assertEquals(ConnectorBusinessStatus.UNSPECIFIED, actual.status)
        assertEquals(ConnectorDeliveryHint.UNSPECIFIED, actual.deliveryHint)
        assertEquals(ConnectorPayloadFormat.UNSPECIFIED, actual.payloadFormat)
        assertEquals(0L, actual.payloadSchemaVersion)
        assertEquals(0, EnvelopeMapper.encode(actual).size)
    }

    @Test
    fun absentDeadletterGroupsRemainAbsent() {
        val actual = EnvelopeMapper.decodeDeadletter(byteArrayOf())
        assertNull(actual.originalEnvelope)
        assertNull(actual.failure)
        assertEquals(ConnectorDeadletterReason.UNSPECIFIED, actual.reason)
        assertTrue(actual.failureDetails.isEmpty())
    }

    @Test
    fun failureDimensionsAndUnknownDetailsComeOnlyFromCore() {
        val details = mapOf("schema" to "transport-failure/1", "domain" to "CONNECTIVITY",
            "phase" to "CONNECT", "certainty" to "NOT_DELIVERED", "scope" to "ENDPOINT",
            "code" to "CONNECT_FAILED", "future" to "preserved")
        val terminal = Deadletter.newBuilder().setReasonValue(-123).setActiveGeneration(-1L)
            .setResolvedPort(-1).setEndpointAttemptCount(-1).setResolvedHost("host\u0000")
            .setDetail("raw\u0000detail").putAllTransportFailureMetadata(details)
            .setOriginalEnvelope(Envelope.parseFrom(EnvelopeMapper.encode(value()))).build()
        val actual = EnvelopeMapper.decodeDeadletter(terminal.toByteArray())
        assertEquals(ConnectorDeadletterReason.of(-123), actual.reason)
        assertEquals(-1L, actual.activeGeneration)
        assertEquals(0xffff_ffffL, actual.resolvedPort)
        assertEquals(0xffff_ffffL, actual.endpointAttemptCount)
        assertEquals("raw\u0000detail", actual.detail)
        assertEquals("host\u0000", actual.resolvedHost)
        assertEquals(ConnectorNativeFailure(1, 3, 1, 1, 2), actual.failure)
        assertEquals(details, actual.failureDetails)
        assertEquals(value().messageId, assertNotNull(actual.originalEnvelope).messageId)
        val unknown = terminal.toBuilder().putTransportFailureMetadata("code", "FUTURE_CODE").build()
        val decoded = EnvelopeMapper.decodeDeadletter(unknown.toByteArray())
        assertEquals(0L, assertNotNull(decoded.failure).code)
        assertEquals("FUTURE_CODE", decoded.failureDetails["code"])
        val invalid = terminal.toBuilder().putTransportFailureMetadata("schema", "transport-failure/2").build()
        val error = assertFailsWith<IllegalStateException> { EnvelopeMapper.decodeDeadletter(invalid.toByteArray()) }
        assertTrue(error.message.orEmpty().contains("failed rc=${CoakkaStatus.ERR_IO}"))
    }

    @Test
    fun sourceBytesAndDecodedLeaseHaveIndependentLifetimes() {
        val source = value().copy(payload = ByteArray(96 * 1024) { (it % 251).toByte() })
        val expected = source.payload.copyOf()
        val encoded = EnvelopeMapper.encode(source)
        source.payload.fill(0)
        val decoded = EnvelopeMapper.decode(encoded)
        encoded.fill(0)
        repeat(20) { EnvelopeMapper.decode(EnvelopeMapper.encode(value())) }
        assertContentEquals(expected, decoded.payload)
    }

    @Test
    fun utf16AndUnsignedWidthCannotBeSilentlyTruncated() {
        assertFailsWith<IllegalArgumentException> { EnvelopeMapper.encode(value().copy(source = "\uD800")) }
        assertFailsWith<IllegalArgumentException> { EnvelopeMapper.encode(value().copy(source = "\uDC00")) }
        assertFailsWith<IllegalStateException> { EnvelopeMapper.encode(value().copy(payloadSchemaVersion = 0x1_0000_0000L)) }
        assertFailsWith<IllegalStateException> { EnvelopeMapper.encode(value().copy(payloadSchemaVersion = -1)) }
        assertFailsWith<IllegalArgumentException> { EnvelopeMapper.encode(value().copy(source = "x".repeat(1024 * 1024 + 1))) }
    }

    @Test
    fun privateShapesAndInvalidUtf8FailWithoutPartialRecords() {
        NativeLibraryResolver.load(TestSupport.runtimeLibPath())
        val fields = Array(9) { byteArrayOf() }
        val cases = listOf(
            LongArray(7) to fields,
            LongArray(9) to fields,
            LongArray(8).also { it[7] = 32769 } to fields,
            LongArray(8).also { it[1] = 2 } to fields,
            LongArray(8) to Array(8) { byteArrayOf() },
            LongArray(8) to fields.copyOf().also { it[0] = byteArrayOf(-1) },
        )
        for ((numbers, bytes) in cases) {
            val result = NativeBridge.encodeMessage(numbers, bytes)
            assertEquals(CoakkaStatus.ERR_INVALID_ARG.toLong(), result.value(0))
            assertEquals(1, result.values.size)
            assertTrue(result.bytes.isEmpty())
        }
        assertFailsWith<IllegalStateException> { EnvelopeMapper.decode(byteArrayOf(-1)) }
    }

    @Test
    fun unknownWireFieldsAreNotAdvertisedAsRetainedValues() {
        val oracle = Envelope.newBuilder().setPayload(ByteString.copyFromUtf8("opaque")).build()
        val extra = byteArrayOf(0xA0.toByte(), 0x06, 0x01) // test-only unknown field100
        val decoded = EnvelopeMapper.decode(oracle.toByteArray() + extra)
        assertEquals(oracle, Envelope.parseFrom(EnvelopeMapper.encode(decoded)))
    }

    @Test
    fun fourIndependentCallersDoNotShareValueLeases() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            val futures = executor.invokeAll((0 until 4).map { caller -> Callable {
                repeat(250) { turn ->
                    val input = value().copy(messageId = "$caller:$turn")
                    val output = EnvelopeMapper.decode(EnvelopeMapper.encode(input))
                    assertEquals(input.copy(payload = output.payload), output)
                }
            } }, 30, TimeUnit.SECONDS)
            futures.forEach { it.get() }
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}
