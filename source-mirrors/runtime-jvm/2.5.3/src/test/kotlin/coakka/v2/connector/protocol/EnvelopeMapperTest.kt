package coakka.v2.connector.protocol

import coakka.v2.transport.Deadletter
import coakka.v2.transport.DeadletterReason
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EnvelopeMapperTest {
    @Test
    fun connectorEnvelopeRoundTripsThroughProtoMapping() {
        val original = ConnectorEnvelope(
            messageId = "msg-1",
            correlationId = "corr-1",
            source = "caller",
            target = "svc.echo",
            replyTo = "caller/replies",
            kind = ConnectorMessageKind.REQUEST,
            oneWay = false,
            timeoutMs = 321,
            payload = byteArrayOf(1, 2, 3),
            headers = mapOf("method" to "POST", "operation" to "echo"),
            status = ConnectorBusinessStatus.ERROR,
            errorCode = "BUSINESS_ERROR",
            errorMessage = "something happened",
            deliveryHint = ConnectorDeliveryHint.REQUIRE_LOCAL,
            messageType = "echo.request.v1",
            payloadSchemaVersion = 1,
            payloadFormat = ConnectorPayloadFormat.JSON,
        )

        val remapped = EnvelopeMapper.fromProto(EnvelopeMapper.toProto(original))

        assertEquals(original.messageId, remapped.messageId)
        assertEquals(original.correlationId, remapped.correlationId)
        assertEquals(original.source, remapped.source)
        assertEquals(original.target, remapped.target)
        assertEquals(original.replyTo, remapped.replyTo)
        assertEquals(original.kind, remapped.kind)
        assertEquals(original.oneWay, remapped.oneWay)
        assertEquals(original.timeoutMs, remapped.timeoutMs)
        assertContentEquals(original.payload, remapped.payload)
        assertEquals(original.headers, remapped.headers)
        assertEquals(original.status, remapped.status)
        assertEquals(original.errorCode, remapped.errorCode)
        assertEquals(original.errorMessage, remapped.errorMessage)
        assertEquals(original.deliveryHint, remapped.deliveryHint)
        assertEquals(original.messageType, remapped.messageType)
        assertEquals(original.payloadSchemaVersion, remapped.payloadSchemaVersion)
        assertEquals(original.payloadFormat, remapped.payloadFormat)
        assertEquals(original.payloadIdentity(), remapped.payloadIdentity())
    }

    @Test
    fun deadletterMapsIntoConnectorView() {
        val original = ConnectorEnvelope(
            messageId = "msg-dead",
            source = "caller",
            target = "svc.missing",
            kind = ConnectorMessageKind.REQUEST,
            oneWay = false,
            payload = "hello".toByteArray(),
            messageType = "echo.request.v1",
            payloadSchemaVersion = 1,
            payloadFormat = ConnectorPayloadFormat.TEXT,
        )

        val protoDeadletter = Deadletter.newBuilder()
            .setOriginalEnvelope(EnvelopeMapper.toProto(original))
            .setReason(DeadletterReason.DEADLETTER_REASON_ROUTE_MISS)
            .setDetail("route not found")
            .setActiveGeneration(9)
            .setResolvedHost("127.0.0.1")
            .setResolvedPort(9001)
            .build()

        val mapped = EnvelopeMapper.fromProto(protoDeadletter)

        assertEquals("msg-dead", mapped.originalEnvelope.messageId)
        assertEquals("DEADLETTER_REASON_ROUTE_MISS", mapped.reason)
        assertEquals("route not found", mapped.detail)
        assertEquals(9, mapped.activeGeneration)
        assertEquals("127.0.0.1", mapped.resolvedHost)
        assertEquals(9001, mapped.resolvedPort)
        assertEquals("echo.request.v1", mapped.originalEnvelope.messageType)
        assertEquals(1, mapped.originalEnvelope.payloadSchemaVersion)
        assertEquals(ConnectorPayloadFormat.TEXT, mapped.originalEnvelope.payloadFormat)
    }

    @Test
    fun payloadIdentityHelperReflectsEnvelopeFields() {
        val envelope = ConnectorEnvelope(
            messageId = "msg-identity",
            source = "caller",
            target = "svc.echo",
            kind = ConnectorMessageKind.EVENT,
            oneWay = true,
            messageType = "audit.user_logged_in.v1",
            payloadSchemaVersion = 3,
            payloadFormat = ConnectorPayloadFormat.PROTOBUF,
        )

        assertEquals(
            ConnectorPayloadIdentity(
                messageType = "audit.user_logged_in.v1",
                payloadSchemaVersion = 3,
                payloadFormat = ConnectorPayloadFormat.PROTOBUF,
            ),
            envelope.payloadIdentity(),
        )
    }

    @Test
    fun typedPayloadIdentityValidationRejectsWeakIdentity() {
        val error = assertFailsWith<IllegalArgumentException> {
            ConnectorPayloadIdentity(
                messageType = "",
                payloadSchemaVersion = 0,
                payloadFormat = ConnectorPayloadFormat.UNSPECIFIED,
            ).requireTyped("typed ask")
        }

        assertEquals("typed ask requires messageType", error.message)
    }

    @Test
    fun withPayloadIdentityAttachesTypedIdentityToEnvelope() {
        val envelope = ConnectorEnvelope(
            messageId = "msg-raw",
            source = "caller",
            target = "svc.echo",
            kind = ConnectorMessageKind.REQUEST,
            oneWay = false,
        ).withPayloadIdentity(
            ConnectorPayloadIdentity(
                messageType = "echo.request.v1",
                payloadSchemaVersion = 1,
                payloadFormat = ConnectorPayloadFormat.TEXT,
            ),
        )

        assertEquals(true, envelope.hasTypedPayloadIdentity())
        assertEquals("echo.request.v1", envelope.messageType)
        assertEquals(1, envelope.payloadSchemaVersion)
        assertEquals(ConnectorPayloadFormat.TEXT, envelope.payloadFormat)
    }
}
