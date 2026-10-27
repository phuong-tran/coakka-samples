package coakka.v2.connector

/**
 * Endpoint-level route flags understood by the runtime snapshot contract.
 *
 * Keep this type separate from [RuntimeRouteFlags] so connector code cannot
 * pass endpoint flags into route-level fields by accident.
 */
data class RuntimeEndpointFlags(val value: Int) {
    infix fun or(other: RuntimeEndpointFlags): RuntimeEndpointFlags =
        RuntimeEndpointFlags(value or other.value)

    companion object {
        /** No endpoint flags. */
        @JvmField
        val NONE = RuntimeEndpointFlags(0)

        /** Marks an endpoint as served by the current process. */
        @JvmField
        val LOCAL = RuntimeEndpointFlags(1)

        /** Keeps an endpoint in snapshot state but excludes it from new traffic. */
        @JvmField
        val UNAVAILABLE = RuntimeEndpointFlags(1 shl 1)

        /** Creates an endpoint flag wrapper for advanced ABI-adjacent code. */
        @JvmStatic
        fun of(value: Int): RuntimeEndpointFlags = RuntimeEndpointFlags(value)
    }
}

/**
 * Route-level intent. Core determines applicability to the selected strategy.
 */
data class RuntimeRouteFlags(val value: Int) {
    companion object {
        /** No route flags. */
        @JvmField
        val NONE = RuntimeRouteFlags(0)

        @JvmField
        val PREFER_LOCAL = RuntimeRouteFlags(1)

        /** Creates a route flag wrapper for advanced ABI-adjacent code. */
        @JvmStatic
        fun of(value: Int): RuntimeRouteFlags = RuntimeRouteFlags(value)
    }
}

/**
 * Declares one endpoint candidate inside a route snapshot pushed to the runtime.
 *
 * @property host Host or IP address published to the runtime.
 * @property port Service port published to the runtime.
 * @property weight Relative weight for weighted strategies.
 * @property flags Endpoint flags defined by the runtime ABI.
 */
data class RuntimeEndpointSpec(
    val host: String,
    val port: Long,
    val weight: Long = 0,
    val flags: RuntimeEndpointFlags = RuntimeEndpointFlags.NONE,
)

/**
 * Declares routing policy for one logical target.
 *
 * @property target Logical runtime target name.
 * @property endpoints Eligible endpoints for this target.
 * @property strategy Route resolution strategy from the runtime contract.
 * @property routeKeyHint Header key used by rendezvous hashing.
 * @property flags Route-level flags from the runtime contract.
 */
data class RuntimeRouteSpec(
    val target: String,
    val endpoints: List<RuntimeEndpointSpec>,
    val strategy: RuntimeRouteStrategy = RuntimeRouteStrategy.UNSPECIFIED,
    val routeKeyHint: String? = null,
    val flags: RuntimeRouteFlags = RuntimeRouteFlags.NONE,
)

/**
 * Runtime overload strategy exposed to app-host configuration.
 *
 * The connector declares the desired behavior; the native runtime owns enforcement and
 * diagnostics.
 */
data class RuntimeOverloadMode(val value: Int) {
    companion object {
        @JvmField val REJECT = RuntimeOverloadMode(0)
        @JvmField val DROP_EXPIRED_FIRST = RuntimeOverloadMode(1)
        @JvmField val DROP_ONE_WAY_FIRST = RuntimeOverloadMode(2)
        @JvmStatic fun of(value: Int): RuntimeOverloadMode = RuntimeOverloadMode(value)
    }
}

/** Route intent, not effective resolution. Zero asks Core to resolve its policy. */
data class RuntimeRouteStrategy(val value: Int) {
    companion object {
        @JvmField val UNSPECIFIED = RuntimeRouteStrategy(0)
        @JvmField val SINGLE_OWNER = RuntimeRouteStrategy(1)
        @JvmField val WEIGHTED_ROUND_ROBIN = RuntimeRouteStrategy(2)
        @JvmField val RENDEZVOUS_HASH = RuntimeRouteStrategy(3)
        @JvmStatic fun of(value: Int): RuntimeRouteStrategy = RuntimeRouteStrategy(value)
    }
}

/**
 * Declares how the runtime should behave when bounded queues are full.
 *
 * @property ingressMode Policy for app-host ingress into the runtime.
 * @property localDeliveryMode Policy for runtime delivery into local handlers.
 * @property remoteOutboundMode Policy for remote outbound transport queues.
 * @property remoteOutboundReplyReserveSlots Slots protected for reply-capable remote work when
 * using one-way shedding.
 */
data class RuntimeOverloadPolicySpec(
    val ingressMode: RuntimeOverloadMode = RuntimeOverloadMode.REJECT,
    val localDeliveryMode: RuntimeOverloadMode = RuntimeOverloadMode.REJECT,
    val remoteOutboundMode: RuntimeOverloadMode = RuntimeOverloadMode.REJECT,
    val remoteOutboundReplyReserveSlots: Long = 0,
)

/** Declares whether this runtime owns an inbound network listener. */
data class RuntimeNetworkMode(val value: Int) {
    companion object {
        @JvmField val LEGACY_ROUTE_DERIVED = RuntimeNetworkMode(0)
        @JvmField val EMBEDDED = RuntimeNetworkMode(1)
        @JvmField val OUTBOUND_ONLY = RuntimeNetworkMode(2)
        @JvmField val NETWORK_NODE = RuntimeNetworkMode(3)
        /** Unknown numeric intent/facts stay numeric; Core validates intent. */
        @JvmStatic fun of(value: Int): RuntimeNetworkMode = RuntimeNetworkMode(value)
    }
}

/** Nullable fields preserve absence; Core validates endpoints and owns listener policy. */
data class RuntimeNetworkConfig(
    val mode: RuntimeNetworkMode,
    val bindHost: String? = null,
    val bindPort: Int? = null,
    val advertiseHost: String? = null,
    val advertisePort: Int? = null,
) {
    internal fun fields(): Long = 1L or
        (if (bindHost != null) 2L else 0L) or
        (if (bindPort != null) 4L else 0L) or
        (if (advertiseHost != null) 8L else 0L) or
        (if (advertisePort != null) 16L else 0L)

    companion object {
        @JvmStatic
        fun embedded(): RuntimeNetworkConfig = RuntimeNetworkConfig(RuntimeNetworkMode.EMBEDDED)

        @JvmStatic
        fun outboundOnly(): RuntimeNetworkConfig =
            RuntimeNetworkConfig(RuntimeNetworkMode.OUTBOUND_ONLY)

        @JvmStatic
        fun networkNode(
            bindHost: String,
            bindPort: Int,
            advertiseHost: String,
            advertisePort: Int,
        ): RuntimeNetworkConfig = RuntimeNetworkConfig(
            mode = RuntimeNetworkMode.NETWORK_NODE,
            bindHost = bindHost,
            bindPort = bindPort,
            advertiseHost = advertiseHost,
            advertisePort = advertisePort,
        )
    }
}

/** Accepted Core facts; null hosts remain absent, not empty or reconstructed. */
data class RuntimeNetworkConfigSnapshot(
    val mode: RuntimeNetworkMode,
    val explicitPolicy: Boolean,
    val bindHost: String?,
    val bindPort: Int,
    val advertiseHost: String?,
    val advertisePort: Int,
)

/** Construction intent only. Omit the entire group to request Core defaults. */
data class RuntimeExecutionOptions(val cpuPolicy: Long)
data class RuntimeIoOptions(val useIoUring: Boolean)
data class RuntimeMonitorOptions(val enabled: Boolean)

/**
 * Collects boot-time settings required to create and seed a runtime instance.
 *
 * @property systemName Connector or host system identifier.
 * @property nodeId Logical node identifier inside the system.
 * @property network Optional listener participation intent. Null leaves selection to Core.
 * @property queueCapacity Requested ingress queue capacity. Zero omits connector policy and asks
 * Core to select and report its production default.
 * @property strictNoDrop Whether runtime should stay on strict no-drop behavior where supported.
 * @property generation Initial control generation applied before start.
 * @property overloadPolicy Optional policy attached to connector-owned route snapshots.
 * @property routes Initial route snapshot to load before start.
 * @property connectionStrategy Optional startup-only TCP connection strategy.
 * @property security Optional plaintext, TLS, or mTLS startup policy.
 * @property execution Optional CPU selection intent, resolved at Core.
 * @property io Optional receiver-readiness intent, probed only by Core.
 * @property monitor Optional collection permission, independent of reading a doorbell.
 */
data class RuntimeStartSpec @JvmOverloads constructor(
    val systemName: String,
    val nodeId: String,
    val queueCapacity: Int = 0,
    val strictNoDrop: Boolean = true,
    val generation: Long = 1,
    val overloadPolicy: RuntimeOverloadPolicySpec? = null,
    val routes: List<RuntimeRouteSpec>,
    val connectionStrategy: RuntimeTcpConnectionStrategySpec? = null,
    val security: RuntimeTcpSecuritySpec? = null,
    val network: RuntimeNetworkConfig? = null,
    val execution: RuntimeExecutionOptions? = null,
    val io: RuntimeIoOptions? = null,
    val monitor: RuntimeMonitorOptions? = null,
)

/** Core-owned construction defaults reported by [RuntimeStartupConfigSnapshot]. */
object CoakkaRuntimeStartupDefaultGroups {
    const val EXECUTION = 1L shl 0
    const val CONNECTION = 1L shl 1
    const val IO = 1L shl 2
    const val MONITOR = 1L shl 3
}

/** CPU placement intent accepted and selected by Core at construction. */
object CoakkaRuntimeStartupCpuPolicies {
    const val AUTO = 0
    const val SINGLE = 1
    const val UNKNOWN_CPU_ID = 0xffff_ffffL
}

/** Copy-safe Core facts for the accepted startup execution policy. */
data class RuntimeStartupExecutionSnapshot(
    val requestedPolicy: Int,
    val supported: Boolean,
    val availableCpuCount: Int,
    val selectedCpuIds: List<Long>,
    val startupApplied: Boolean,
)

/** Copy-safe Core facts for optional receiver readiness selected at construction. */
data class RuntimeStartupIoSnapshot(
    val requested: Boolean,
    val compiled: Boolean,
    val probed: Boolean,
    val supported: Boolean,
    val effective: Boolean,
    val probeError: Int,
)

/** Core's accepted construction facts; this never reconstructs state from connector input. */
data class RuntimeStartupConfigSnapshot(
    val defaultsRevision: Int,
    val defaultedGroups: Long,
    val strictNoDrop: Boolean,
    val queueCapacity: Int,
    val requestMaxFrameSize: Long,
    val runtimeState: Int,
    val execution: RuntimeStartupExecutionSnapshot,
    val connection: RuntimeTcpConnectionConfigSnapshot,
    val io: RuntimeStartupIoSnapshot,
    val monitorEnabled: Boolean,
) {
    fun wasDefaulted(group: Long): Boolean = (defaultedGroups and group) == group
}

/** Stable Core delivery-mode values; connectors never select these modes. */
object CoakkaRuntimeDeliveryModes {
    const val FRAMED = 1
    const val OWNED_QUEUE = 2
}

/** Core's accepted immutable delivery composition, copied without inference. */
data class RuntimeDeliveryConfigSnapshot(
    val compiled: Boolean,
    val requestMode: Int,
    val responseMode: Int,
    val remoteBatchMaxCalls: Int,
    val slotCapacity: Int,
    val byteCapacity: Long,
)
