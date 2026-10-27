package coakka.v2.connector.protocol

import coakka.v2.connector.RuntimeEndpointFlags
import coakka.v2.connector.RuntimeEndpointSpec
import coakka.v2.connector.RuntimeOverloadMode
import coakka.v2.connector.RuntimeOverloadPolicySpec
import coakka.v2.connector.RuntimeRouteSpec
import coakka.v2.connector.RuntimeRouteStrategy
import coakka.v2.connector.RuntimeRouteFlags
import coakka.v2.connector.CoakkaStatus
import coakka.v2.connector.NativeLibraryResolver
import coakka.v2.connector.internal.NativeBridge
import coakka.v2.connector.RuntimeHandle
import coakka.v2.connector.RuntimeStartSpec
import coakka.v2.connector.TestSupport
import coakka.v2.control.ControlEnvelope
import coakka.v2.control.ConfigFormat
import coakka.v2.control.ControlKind
import coakka.v2.control.ControlPayloadType
import coakka.v2.control.OverloadMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlEnvelopeMapperTest {
    @Test
    fun snapshotEnvelopeCarriesExpectedMetadataAndRoutes() {
        val envelope = ControlEnvelope.parseFrom(ControlEnvelopeMapper.encodeSnapshot(
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
        ))

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
    fun coreRejectsUnsupportedIngressModeWithoutChangingGeneration() {
        val policy = RuntimeOverloadPolicySpec(ingressMode = RuntimeOverloadMode.DROP_ONE_WAY_FIRST)
        RuntimeHandle.open(TestSupport.runtimeLibPath(), RuntimeStartSpec("policy", "node", routes = emptyList())).use {
            val before = it.config()
            assertFailsWith<IllegalStateException> {
                it.controlClient.applySnapshot(2, emptyList(), "policy", policy)
            }
            assertEquals(before, it.config())
        }
    }

    @Test
    fun zeroStrategyAndWeightsStayUnspecifiedUntilCoreAdmission() {
        val route = RuntimeRouteSpec("local", listOf(
            RuntimeEndpointSpec("127.0.0.1", 0, flags = RuntimeEndpointFlags.LOCAL),
            RuntimeEndpointSpec("127.0.0.2", 0, flags = RuntimeEndpointFlags.LOCAL),
        ))
        val encoded = ControlEnvelope.parseFrom(ControlEnvelopeMapper.encodeSnapshot(2, 2, listOf(route), "test"))
        val payload = coakka.v2.control.RouteSnapshotPayload.parseFrom(encoded.payload)
        assertFalse(payload.hasOverloadPolicy())
        assertEquals(0, payload.getRoutes(0).strategyValue)
        assertEquals(listOf(0, 0), payload.getRoutes(0).endpointsList.map { it.weight })
        RuntimeHandle.open(TestSupport.runtimeLibPath(), RuntimeStartSpec("routes", "node", routes = emptyList())).use {
            it.controlClient.applySnapshot(2, listOf(route))
            val accepted = it.config()
            assertEquals(2L, accepted.appliedGeneration)
            assertEquals(1L, accepted.routeCount)
            assertFailsWith<IllegalStateException> {
                it.controlClient.applySnapshot(3, listOf(route.copy(strategy = RuntimeRouteStrategy.SINGLE_OWNER)))
            }
            assertEquals(accepted, it.config())
            it.controlClient.applySnapshot(3, emptyList())
            assertEquals(0L, it.config().routeCount)
            it.controlClient.applySnapshot(4, listOf(route))
            assertEquals(4L, it.config().appliedGeneration)
            assertEquals(1L, it.config().routeCount)
        }
    }

    @Test
    fun allIntegerBitsAndAbsentPolicySurviveEncoding() {
        val encoded = ControlEnvelope.parseFrom(ControlEnvelopeMapper.encodeSnapshot(-1L, Long.MIN_VALUE,
            listOf(RuntimeRouteSpec("target\u0000🙂", listOf(RuntimeEndpointSpec("host\u0000", 0xffff_ffffL,
                0xffff_ffffL, RuntimeEndpointFlags.of(-1))), RuntimeRouteStrategy.of(-123), "key\u0000",
                RuntimeRouteFlags.of(-1))), "source\u0000🙂",
            RuntimeOverloadPolicySpec(RuntimeOverloadMode.of(-234), RuntimeOverloadMode.of(-345),
                RuntimeOverloadMode.of(-456), 0xffff_ffffL)))
        assertEquals(-1L, encoded.seq)
        assertEquals(Long.MIN_VALUE, encoded.generation)
        assertEquals("source\u0000🙂", encoded.metadataMap["source_connector"])
        val payload = coakka.v2.control.RouteSnapshotPayload.parseFrom(encoded.payload)
        assertEquals(Long.MIN_VALUE, payload.generation)
        assertEquals(-234, payload.overloadPolicy.ingressModeValue)
        assertEquals(-345, payload.overloadPolicy.localDeliveryModeValue)
        assertEquals(-456, payload.overloadPolicy.remoteOutboundModeValue)
        assertEquals(-1, payload.overloadPolicy.remoteOutboundReplyReserveSlots)
        val route = payload.getRoutes(0)
        assertEquals(-123, route.strategyValue)
        assertEquals(-1, route.flags)
        assertEquals("key\u0000", route.routeKeyHint)
        assertEquals("target\u0000🙂", route.target)
        assertEquals(-1, route.getEndpoints(0).port)
        assertEquals(-1, route.getEndpoints(0).weight)
        assertEquals(-1, route.getEndpoints(0).flags)
        assertEquals("host\u0000", route.getEndpoints(0).host)
    }

    @Test
    fun omissionUsesCoreBootPolicyNotTheConnectorsLastInput() {
        RuntimeHandle.open(TestSupport.runtimeLibPath(), RuntimeStartSpec("policy", "node", routes = emptyList())).use {
            it.controlClient.applySnapshot(2, emptyList(), "test", RuntimeOverloadPolicySpec(
                remoteOutboundMode = RuntimeOverloadMode.DROP_ONE_WAY_FIRST, remoteOutboundReplyReserveSlots = 1))
            it.applyControlEnvelope(ControlEnvelopeMapper.encodeSnapshot(3, 3, emptyList(), "another caller",
                RuntimeOverloadPolicySpec(remoteOutboundMode = RuntimeOverloadMode.DROP_EXPIRED_FIRST)))
            assertEquals(RuntimeOverloadMode.DROP_EXPIRED_FIRST.value, it.config().effectiveRemoteOutboundOverloadMode)
            it.controlClient.applySnapshot(4, emptyList())
            val accepted = it.config()
            assertEquals(accepted.configuredRemoteOutboundOverloadMode, accepted.effectiveRemoteOutboundOverloadMode)
            assertEquals(accepted.configuredRemoteOutboundReplyReserveSlots, accepted.effectiveRemoteOutboundReplyReserveSlots)
            assertFailsWith<IllegalStateException> {
                it.controlClient.applySnapshot(5, emptyList(), "test",
                    RuntimeOverloadPolicySpec(remoteOutboundMode = RuntimeOverloadMode.of(-123)))
            }
            assertEquals(accepted, it.config())
            it.controlClient.applySnapshot(5, emptyList())
            assertEquals(accepted.effectiveRemoteOutboundOverloadMode, it.config().effectiveRemoteOutboundOverloadMode)
            assertEquals(5L, it.config().appliedGeneration)
        }
    }

    @Test
    fun malformedPrivateControlRecordsAndUnsignedWidthsFailClosed() {
        NativeLibraryResolver.load(TestSupport.runtimeLibPath())
        val cases = listOf(LongArray(7), LongArray(9),
            LongArray(8).also { it[2] = 2 }, LongArray(8).also { it[3] = 1 },
            LongArray(8).also { it[7] = -1 }, LongArray(8).also { it[7] = 32769 },
            LongArray(11).also { it[7] = 1; it[10] = Long.MAX_VALUE })
        for (numbers in cases) {
            val record = NativeBridge.encodeControl(numbers, arrayOf(byteArrayOf()))
            assertEquals(CoakkaStatus.ERR_INVALID_ARG.toLong(), record.value(0))
            assertEquals(1, record.values.size)
            assertTrue(record.bytes.isEmpty())
        }
        for (port in listOf(-1L, 0x1_0000_0000L)) {
            assertFailsWith<IllegalStateException> {
                ControlEnvelopeMapper.encodeSnapshot(1, 1,
                    listOf(RuntimeRouteSpec("target", listOf(RuntimeEndpointSpec("host", port)))), "test")
            }
        }
    }
}
