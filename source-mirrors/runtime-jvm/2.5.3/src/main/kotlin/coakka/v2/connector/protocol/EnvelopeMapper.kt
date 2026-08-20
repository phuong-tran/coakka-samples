package coakka.v2.connector.protocol

import coakka.v2.transport.BusinessStatus
import coakka.v2.transport.Deadletter
import coakka.v2.transport.DeliveryHint
import coakka.v2.transport.Envelope
import coakka.v2.transport.MessageKind
import coakka.v2.transport.PayloadFormat
import com.google.protobuf.ByteString

object EnvelopeMapper {
    fun toProto(envelope: ConnectorEnvelope): Envelope =
        Envelope.newBuilder()
            .setMessageId(envelope.messageId)
            .setCorrelationId(envelope.correlationId)
            .setSource(envelope.source)
            .setTarget(envelope.target)
            .setReplyTo(envelope.replyTo)
            .setKind(envelope.kind.toProto())
            .setOneWay(envelope.oneWay)
            .setTimeoutMs(envelope.timeoutMs)
            .setPayload(ByteString.copyFrom(envelope.payload))
            .putAllHeaders(envelope.headers)
            .setStatus(envelope.status.toProto())
            .setErrorCode(envelope.errorCode)
            .setErrorMessage(envelope.errorMessage)
            .setDeliveryHint(envelope.deliveryHint.toProto())
            .setMessageType(envelope.messageType)
            .setPayloadSchemaVersion(envelope.payloadSchemaVersion)
            .setPayloadFormat(envelope.payloadFormat.toProto())
            .build()

    fun fromProto(envelope: Envelope): ConnectorEnvelope =
        ConnectorEnvelope(
            messageId = envelope.messageId,
            correlationId = envelope.correlationId,
            source = envelope.source,
            target = envelope.target,
            replyTo = envelope.replyTo,
            kind = envelope.kind.toConnector(),
            oneWay = envelope.oneWay,
            timeoutMs = envelope.timeoutMs,
            payload = envelope.payload.toByteArray(),
            headers = envelope.headersMap,
            status = envelope.status.toConnector(),
            errorCode = envelope.errorCode,
            errorMessage = envelope.errorMessage,
            deliveryHint = envelope.deliveryHint.toConnector(),
            messageType = envelope.messageType,
            payloadSchemaVersion = envelope.payloadSchemaVersion,
            payloadFormat = envelope.payloadFormat.toConnector(),
        )

    fun fromProto(deadletter: Deadletter): ConnectorDeadletter =
        ConnectorDeadletter(
            originalEnvelope = fromProto(deadletter.originalEnvelope),
            reason = deadletter.reason.name,
            detail = deadletter.detail,
            activeGeneration = deadletter.activeGeneration,
            resolvedHost = deadletter.resolvedHost,
            resolvedPort = deadletter.resolvedPort,
        )

    private fun ConnectorMessageKind.toProto(): MessageKind =
        when (this) {
            ConnectorMessageKind.REQUEST -> MessageKind.MESSAGE_KIND_REQUEST
            ConnectorMessageKind.RESPONSE -> MessageKind.MESSAGE_KIND_RESPONSE
            ConnectorMessageKind.EVENT -> MessageKind.MESSAGE_KIND_EVENT
        }

    private fun MessageKind.toConnector(): ConnectorMessageKind =
        when (this) {
            MessageKind.MESSAGE_KIND_REQUEST -> ConnectorMessageKind.REQUEST
            MessageKind.MESSAGE_KIND_RESPONSE -> ConnectorMessageKind.RESPONSE
            MessageKind.MESSAGE_KIND_EVENT -> ConnectorMessageKind.EVENT
            else -> error("unsupported message kind=$this")
        }

    private fun ConnectorBusinessStatus.toProto(): BusinessStatus =
        when (this) {
            ConnectorBusinessStatus.OK -> BusinessStatus.BUSINESS_STATUS_OK
            ConnectorBusinessStatus.ERROR -> BusinessStatus.BUSINESS_STATUS_ERROR
        }

    private fun BusinessStatus.toConnector(): ConnectorBusinessStatus =
        when (this) {
            BusinessStatus.BUSINESS_STATUS_ERROR -> ConnectorBusinessStatus.ERROR
            else -> ConnectorBusinessStatus.OK
        }

    private fun ConnectorDeliveryHint.toProto(): DeliveryHint =
        when (this) {
            ConnectorDeliveryHint.ROUTER_DEFAULT -> DeliveryHint.DELIVERY_HINT_ROUTER_DEFAULT
            ConnectorDeliveryHint.PREFER_LOCAL -> DeliveryHint.DELIVERY_HINT_PREFER_LOCAL
            ConnectorDeliveryHint.REQUIRE_LOCAL -> DeliveryHint.DELIVERY_HINT_REQUIRE_LOCAL
            ConnectorDeliveryHint.REQUIRE_REMOTE -> DeliveryHint.DELIVERY_HINT_REQUIRE_REMOTE
        }

    private fun DeliveryHint.toConnector(): ConnectorDeliveryHint =
        when (this) {
            DeliveryHint.DELIVERY_HINT_PREFER_LOCAL -> ConnectorDeliveryHint.PREFER_LOCAL
            DeliveryHint.DELIVERY_HINT_REQUIRE_LOCAL -> ConnectorDeliveryHint.REQUIRE_LOCAL
            DeliveryHint.DELIVERY_HINT_REQUIRE_REMOTE -> ConnectorDeliveryHint.REQUIRE_REMOTE
            else -> ConnectorDeliveryHint.ROUTER_DEFAULT
        }

    @Suppress("DEPRECATION")
    private fun ConnectorPayloadFormat.toProto(): PayloadFormat =
        when (this) {
            ConnectorPayloadFormat.UNSPECIFIED -> PayloadFormat.PAYLOAD_FORMAT_UNSPECIFIED
            ConnectorPayloadFormat.JSON -> PayloadFormat.PAYLOAD_FORMAT_JSON
            ConnectorPayloadFormat.PROTOBUF -> PayloadFormat.PAYLOAD_FORMAT_PROTOBUF
            ConnectorPayloadFormat.THRIFT -> PayloadFormat.PAYLOAD_FORMAT_THRIFT
            ConnectorPayloadFormat.MSGPACK -> PayloadFormat.PAYLOAD_FORMAT_MSGPACK
            ConnectorPayloadFormat.TEXT -> PayloadFormat.PAYLOAD_FORMAT_PLAIN_TEXT
            ConnectorPayloadFormat.PLAIN_TEXT -> PayloadFormat.PAYLOAD_FORMAT_PLAIN_TEXT
            ConnectorPayloadFormat.BINARY -> PayloadFormat.PAYLOAD_FORMAT_BINARY
        }

    private fun PayloadFormat.toConnector(): ConnectorPayloadFormat =
        when (this) {
            PayloadFormat.PAYLOAD_FORMAT_JSON -> ConnectorPayloadFormat.JSON
            PayloadFormat.PAYLOAD_FORMAT_PROTOBUF -> ConnectorPayloadFormat.PROTOBUF
            PayloadFormat.PAYLOAD_FORMAT_THRIFT -> ConnectorPayloadFormat.THRIFT
            PayloadFormat.PAYLOAD_FORMAT_MSGPACK -> ConnectorPayloadFormat.MSGPACK
            PayloadFormat.PAYLOAD_FORMAT_PLAIN_TEXT -> ConnectorPayloadFormat.TEXT
            PayloadFormat.PAYLOAD_FORMAT_BINARY -> ConnectorPayloadFormat.BINARY
            else -> ConnectorPayloadFormat.UNSPECIFIED
        }
}
