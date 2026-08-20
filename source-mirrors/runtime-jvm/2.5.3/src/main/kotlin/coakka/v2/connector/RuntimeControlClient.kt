package coakka.v2.connector

import coakka.v2.connector.protocol.ControlEnvelopeMapper
import java.util.concurrent.atomic.AtomicLong

/**
 * Builds and submits connector-owned control snapshots to the runtime.
 *
 * The connector stays responsible for generation monotonicity and route compilation.
 */
class RuntimeControlClient internal constructor(
    private val handle: RuntimeHandle,
    initialSeq: Long = 1,
) {
    private val nextSeq = AtomicLong(initialSeq)
    private var snapshotOverloadPolicy: RuntimeOverloadPolicySpec? = null

    /**
     * Applies a full route snapshot through the protobuf `ControlEnvelope` lane.
     *
     * The configured overload policy is retained for later full snapshots.
     *
     * @param generation Monotonic control generation for this rollout.
     * @param routes Full route snapshot that should become active.
     * @param sourceConnector Diagnostic connector identifier attached to metadata.
     */
    fun applySnapshot(generation: Long, routes: List<RuntimeRouteSpec>, sourceConnector: String = "connector-kotlin") {
        applySnapshot(generation, routes, sourceConnector, snapshotOverloadPolicy)
    }

    fun applySnapshot(
        generation: Long,
        routes: List<RuntimeRouteSpec>,
        sourceConnector: String,
        overloadPolicy: RuntimeOverloadPolicySpec?,
    ) {
        val envelope = ControlEnvelopeMapper.buildSnapshotEnvelope(
            seq = nextSeq.getAndIncrement(),
            generation = generation,
            routes = routes,
            sourceConnector = sourceConnector,
            overloadPolicy = overloadPolicy,
        )
        handle.applyControlEnvelope(envelope.toByteArray())
        snapshotOverloadPolicy = overloadPolicy
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
