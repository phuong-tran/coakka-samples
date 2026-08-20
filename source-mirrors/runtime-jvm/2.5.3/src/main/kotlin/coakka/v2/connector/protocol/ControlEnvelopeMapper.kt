package coakka.v2.connector.protocol

import coakka.v2.connector.RuntimeOverloadPolicySpec
import coakka.v2.connector.RuntimeRouteSpec
import coakka.v2.control.ConfigFormat
import coakka.v2.control.ControlEnvelope
import coakka.v2.control.ControlKind
import coakka.v2.control.ControlPayloadType
import coakka.v2.control.Endpoint
import coakka.v2.control.OverloadPolicy
import coakka.v2.control.Route
import coakka.v2.control.RouteSnapshotPayload

object ControlEnvelopeMapper {
    fun buildSnapshotEnvelope(
        seq: Long,
        generation: Long,
        routes: List<RuntimeRouteSpec>,
        sourceConnector: String,
        overloadPolicy: RuntimeOverloadPolicySpec? = null,
    ): ControlEnvelope {
        val payload = RouteSnapshotPayload.newBuilder()
            .setGeneration(generation)
            .apply {
                overloadPolicy?.let {
                    setOverloadPolicy(
                        OverloadPolicy.newBuilder()
                            .setIngressMode(it.ingressMode.proto)
                            .setLocalDeliveryMode(it.localDeliveryMode.proto)
                            .setRemoteOutboundMode(it.remoteOutboundMode.proto)
                            .setRemoteOutboundReplyReserveSlots(it.remoteOutboundReplyReserveSlots)
                            .build(),
                    )
                }
                routes.forEach { route ->
                    addRoutes(
                        Route.newBuilder()
                            .setTarget(route.target)
                            .setStrategy(route.strategy)
                            .setFlags(route.flags.value)
                            .apply {
                                route.routeKeyHint?.let(::setRouteKeyHint)
                                route.endpoints.forEach { endpoint ->
                                    addEndpoints(
                                        Endpoint.newBuilder()
                                            .setHost(endpoint.host)
                                            .setPort(endpoint.port)
                                            .setWeight(endpoint.weight)
                                            .setFlags(endpoint.flags.value)
                                            .build(),
                                    )
                                }
                            }
                            .build(),
                    )
                }
            }
            .build()

        return ControlEnvelope.newBuilder()
            .setSeq(seq)
            .setGeneration(generation)
            .setKind(ControlKind.CONTROL_KIND_APPLY_SNAPSHOT)
            .setPayloadFormat(ConfigFormat.CONFIG_FORMAT_PROTOBUF)
            .setPayloadType(ControlPayloadType.CONTROL_PAYLOAD_TYPE_ROUTE_SNAPSHOT)
            .setSchemaVersion(1)
            .setPayload(payload.toByteString())
            .putMetadata("source_connector", sourceConnector)
            .build()
    }
}
