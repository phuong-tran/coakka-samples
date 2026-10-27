package coakka.v2.connector.protocol

import coakka.v2.connector.NativeLibraryResolver
import coakka.v2.connector.RuntimeOverloadPolicySpec
import coakka.v2.connector.RuntimeRouteSpec
import coakka.v2.connector.internal.NativeBridge
import coakka.v2.connector.requireStatus

/**
 * Language-value staging only. Core owns the complete control encoding.
 * Do not mutate input collections during this synchronous call. Long sequence
 * and generation values retain all 64 bits of Core's unsigned identifiers.
 */
object ControlEnvelopeMapper {
    @JvmStatic
    @JvmOverloads
    fun encodeSnapshot(
        seq: Long,
        generation: Long,
        routes: List<RuntimeRouteSpec>,
        sourceConnector: String,
        overloadPolicy: RuntimeOverloadPolicySpec? = null,
    ): ByteArray {
        NativeLibraryResolver.load()
        require(routes.size <= MAX_VALUE_PROPERTIES) { "route staging bound exceeded" }
        var endpointCount = 0L
        routes.forEach {
            endpointCount += it.endpoints.size.toLong()
            require(endpointCount <= MAX_VALUE_PROPERTIES) { "endpoint staging bound exceeded" }
        }
        val values = LongArray(8 + routes.size * 3 + endpointCount.toInt() * 3)
        val bytes = ArrayList<ByteArray>(1 + routes.size * 2 + endpointCount.toInt())
        val staging = ValueTextStaging()
        values[0] = seq
        values[1] = generation
        values[2] = if (overloadPolicy != null) 1 else 0
        values[3] = overloadPolicy?.ingressMode?.value?.toLong() ?: 0
        values[4] = overloadPolicy?.localDeliveryMode?.value?.toLong() ?: 0
        values[5] = overloadPolicy?.remoteOutboundMode?.value?.toLong() ?: 0
        values[6] = overloadPolicy?.remoteOutboundReplyReserveSlots ?: 0
        values[7] = routes.size.toLong()
        bytes.add(staging.text(sourceConnector))
        var cursor = 8
        routes.forEach { route ->
            bytes.add(staging.text(route.target))
            bytes.add(staging.text(route.routeKeyHint.orEmpty()))
            values[cursor++] = route.strategy.value.toLong()
            values[cursor++] = route.flags.value.toLong() and 0xffff_ffffL
            values[cursor++] = route.endpoints.size.toLong()
            route.endpoints.forEach { endpoint ->
                bytes.add(staging.text(endpoint.host))
                values[cursor++] = endpoint.port
                values[cursor++] = endpoint.weight
                values[cursor++] = endpoint.flags.value.toLong() and 0xffff_ffffL
            }
        }
        check(cursor == values.size) { "control inputs changed during encoding" }
        val result = NativeBridge.encodeControl(values, bytes.toTypedArray())
        requireStatus(result.value(0).toInt(), "client_encode_control_value")
        return result.payload(0)
    }
}
