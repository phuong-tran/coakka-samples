package coakka.v2.connector.demo.orders

import coakka.v2.connector.protocol.ConnectorPayloadFormat
import coakka.v2.connector.protocol.ConnectorPayloadIdentity

data class CreateOrderRequest(
    val customerName: String,
    val itemName: String,
    val quantity: Int,
    val benchmarkBlob: String? = null,
)

data class OrderAcceptedResponse(
    val orderId: String,
    val status: String,
    val handledBy: String,
    val createdAt: String,
)

object OrderPayloads {
    val CREATE_ORDER_REQUEST_V1 = ConnectorPayloadIdentity(
        messageType = "demo.order.create.request.v1",
        payloadSchemaVersion = 1,
        payloadFormat = ConnectorPayloadFormat.JSON,
    )

    val ORDER_ACCEPTED_RESPONSE_V1 = ConnectorPayloadIdentity(
        messageType = "demo.order.accepted.response.v1",
        payloadSchemaVersion = 1,
        payloadFormat = ConnectorPayloadFormat.JSON,
    )
}
