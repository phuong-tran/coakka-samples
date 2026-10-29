package coakka.v2.android

import coakka.v2.control.ConfigFormat
import coakka.v2.control.ControlEnvelope
import coakka.v2.control.ControlKind
import coakka.v2.control.ControlPayloadType
import coakka.v2.control.Endpoint
import coakka.v2.control.Route
import coakka.v2.control.RouteSnapshotPayload

object RuntimeControlEncoder {
    @JvmStatic
    fun encodeSnapshot(
        generation: Long,
        routes: List<AndroidRuntimeRoute>,
        sequence: Long = generation,
        sourceConnector: String = "coakka-runtime-android",
    ): ByteArray {
        require(generation > 0) { "generation must be positive" }
        require(routes.map(AndroidRuntimeRoute::target).distinct().size == routes.size) {
            "route targets must be unique"
        }

        val payload = RouteSnapshotPayload.newBuilder()
            .setGeneration(generation)
            .apply {
                routes.forEach { route ->
                    addRoutes(
                        Route.newBuilder()
                            .setTarget(route.target)
                            .setStrategyValue(route.strategy)
                            .setFlags(route.flags)
                            .apply {
                                route.routeKeyHint?.let(::setRouteKeyHint)
                                route.endpoints.forEach { endpoint ->
                                    addEndpoints(
                                        Endpoint.newBuilder()
                                            .setHost(endpoint.host)
                                            .setPort(endpoint.port)
                                            .setWeight(endpoint.weight)
                                            .setFlags(endpoint.flags)
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
            .setSeq(sequence)
            .setGeneration(generation)
            .setKind(ControlKind.CONTROL_KIND_APPLY_SNAPSHOT)
            .setPayloadFormat(ConfigFormat.CONFIG_FORMAT_PROTOBUF)
            .setPayloadType(ControlPayloadType.CONTROL_PAYLOAD_TYPE_ROUTE_SNAPSHOT)
            .setSchemaVersion(1)
            .setPayload(payload.toByteString())
            .putMetadata("source_connector", sourceConnector)
            .build()
            .toByteArray()
    }
}
