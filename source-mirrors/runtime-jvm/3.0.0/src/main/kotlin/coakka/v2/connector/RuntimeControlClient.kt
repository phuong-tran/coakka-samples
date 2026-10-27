package coakka.v2.connector

import coakka.v2.connector.protocol.ControlEnvelopeMapper
import java.util.concurrent.atomic.AtomicLong

/**
 * Submits application route intent through Core's encoder and admission API.
 *
 * Core owns generation admission, route compilation and effective policy.
 */
class RuntimeControlClient internal constructor(
    private val handle: RuntimeHandle,
    initialSeq: Long = 1,
) {
    private val nextSeq = AtomicLong(initialSeq)

    /**
     * Applies a complete route replacement through Core.
     *
     * Omitted policy stays absent; Core uses its configured boot-time policy.
     * A snapshot override must be supplied again when desired for a new generation.
     *
     * @param generation Monotonic control generation for this rollout.
     * @param routes Full route snapshot that should become active.
     * @param sourceConnector Diagnostic connector identifier attached to metadata.
     */
    fun applySnapshot(generation: Long, routes: List<RuntimeRouteSpec>, sourceConnector: String = "connector-kotlin") {
        applySnapshot(generation, routes, sourceConnector, null)
    }

    fun applySnapshot(
        generation: Long,
        routes: List<RuntimeRouteSpec>,
        sourceConnector: String,
        overloadPolicy: RuntimeOverloadPolicySpec?,
    ) {
        val envelope = ControlEnvelopeMapper.encodeSnapshot(
            seq = nextSeq.getAndIncrement(),
            generation = generation,
            routes = routes,
            sourceConnector = sourceConnector,
            overloadPolicy = overloadPolicy,
        )
        handle.applyControlEnvelope(envelope)
    }

    internal fun applyStartSpec(startSpec: RuntimeStartSpec) {
        applySnapshot(
            generation = startSpec.generation,
            routes = startSpec.routes,
            sourceConnector = startSpec.systemName,
            overloadPolicy = startSpec.overloadPolicy,
        )
    }
}
