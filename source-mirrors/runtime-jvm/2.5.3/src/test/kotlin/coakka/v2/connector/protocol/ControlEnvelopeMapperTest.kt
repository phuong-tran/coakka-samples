package coakka.v2.connector.protocol

import coakka.v2.connector.RuntimeEndpointFlags
import coakka.v2.connector.RuntimeEndpointSpec
import coakka.v2.connector.RuntimeOverloadMode
import coakka.v2.connector.RuntimeOverloadPolicySpec
import coakka.v2.connector.RuntimeRouteSpec
import coakka.v2.control.ConfigFormat
import coakka.v2.control.ControlKind
import coakka.v2.control.ControlPayloadType
import coakka.v2.control.OverloadMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ControlEnvelopeMapperTest {
    @Test
    fun snapshotEnvelopeCarriesExpectedMetadataAndRoutes() {
        val envelope = ControlEnvelopeMapper.buildSnapshotEnvelope(
            seq = 11,
            generation = 5,
            routes = listOf(
                RuntimeRouteSpec(
                    target = "svc.echo",
                    endpoints = listOf(
                        RuntimeEndpointSpec(
                            host = "127.0.0.1",
                            port = 9001,
                            weight = 3,
                            flags = RuntimeEndpointFlags.LOCAL,
                        ),
                    ),
                ),
            ),
            sourceConnector = "connector-kotlin-test",
            overloadPolicy = RuntimeOverloadPolicySpec(
                remoteOutboundMode = RuntimeOverloadMode.DROP_ONE_WAY_FIRST,
                remoteOutboundReplyReserveSlots = 1,
            ),
        )

        val payload = coakka.v2.control.RouteSnapshotPayload.parseFrom(envelope.payload)

        assertEquals(11, envelope.seq)
        assertEquals(5, envelope.generation)
        assertEquals(ControlKind.CONTROL_KIND_APPLY_SNAPSHOT, envelope.kind)
        assertEquals(ConfigFormat.CONFIG_FORMAT_PROTOBUF, envelope.payloadFormat)
        assertEquals(ControlPayloadType.CONTROL_PAYLOAD_TYPE_ROUTE_SNAPSHOT, envelope.payloadType)
        assertEquals(1, envelope.schemaVersion)
        assertEquals("connector-kotlin-test", envelope.metadataMap["source_connector"])
        assertEquals(5, payload.generation)
        assertEquals(OverloadMode.OVERLOAD_MODE_REJECT, payload.overloadPolicy.ingressMode)
        assertEquals(OverloadMode.OVERLOAD_MODE_REJECT, payload.overloadPolicy.localDeliveryMode)
        assertEquals(OverloadMode.OVERLOAD_MODE_DROP_ONE_WAY_FIRST, payload.overloadPolicy.remoteOutboundMode)
        assertEquals(1, payload.overloadPolicy.remoteOutboundReplyReserveSlots)
        assertEquals(1, payload.routesCount)
        assertEquals("svc.echo", payload.getRoutes(0).target)
        assertEquals("127.0.0.1", payload.getRoutes(0).getEndpoints(0).host)
        assertEquals(9001, payload.getRoutes(0).getEndpoints(0).port)
        assertEquals(3, payload.getRoutes(0).getEndpoints(0).weight)
        assertEquals(RuntimeEndpointFlags.LOCAL.value, payload.getRoutes(0).getEndpoints(0).flags)
    }

    @Test
    fun overloadPolicyRejectsUnsupportedIngressModeBeforeRuntime() {
        assertFailsWith<IllegalArgumentException> {
            RuntimeOverloadPolicySpec(ingressMode = RuntimeOverloadMode.DROP_ONE_WAY_FIRST)
        }
    }
}
