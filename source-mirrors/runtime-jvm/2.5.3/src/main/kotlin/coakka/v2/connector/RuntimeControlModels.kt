package coakka.v2.connector

import coakka.v2.control.RouteResolutionStrategy
import coakka.v2.control.OverloadMode

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
 * Route-level flags reserved for route policy extension.
 *
 * The current public snapshot contract does not define non-zero route flags yet.
 */
data class RuntimeRouteFlags(val value: Int) {
    companion object {
        /** No route flags. */
        @JvmField
        val NONE = RuntimeRouteFlags(0)

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
    val port: Int,
    val weight: Int = 1,
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
    val strategy: RouteResolutionStrategy = RouteResolutionStrategy.ROUTE_RESOLUTION_STRATEGY_SINGLE_OWNER,
    val routeKeyHint: String? = null,
    val flags: RuntimeRouteFlags = RuntimeRouteFlags.NONE,
)

/**
 * Runtime overload strategy exposed to app-host configuration.
 *
 * The connector declares the desired behavior; the native runtime owns enforcement and
 * diagnostics.
 */
enum class RuntimeOverloadMode(internal val proto: OverloadMode) {
    REJECT(OverloadMode.OVERLOAD_MODE_REJECT),
    DROP_EXPIRED_FIRST(OverloadMode.OVERLOAD_MODE_DROP_EXPIRED_FIRST),
    DROP_ONE_WAY_FIRST(OverloadMode.OVERLOAD_MODE_DROP_ONE_WAY_FIRST),
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
    val remoteOutboundReplyReserveSlots: Int = 0,
) {
    init {
        require(ingressMode == RuntimeOverloadMode.REJECT) {
            "ingressMode currently supports only REJECT"
        }
        require(localDeliveryMode == RuntimeOverloadMode.REJECT) {
            "localDeliveryMode currently supports only REJECT"
        }
        require(remoteOutboundReplyReserveSlots >= 0) {
            "remoteOutboundReplyReserveSlots must be >= 0"
        }
    }
}

/** Declares whether this runtime owns an inbound network listener. */
enum class RuntimeNetworkMode(internal val nativeValue: Int) {
    EMBEDDED(1),
    OUTBOUND_ONLY(2),
    NETWORK_NODE(3),
}

/** Immutable startup network policy; listener ownership is never inferred from routes. */
data class RuntimeNetworkConfig(
    val mode: RuntimeNetworkMode,
    val bindHost: String? = null,
    val bindPort: Int = 0,
    val advertiseHost: String? = null,
    val advertisePort: Int = 0,
) {
    init {
        if (mode == RuntimeNetworkMode.NETWORK_NODE) {
            require(!bindHost.isNullOrBlank()) { "NETWORK_NODE requires bindHost" }
            require(bindPort in 1..65535) { "NETWORK_NODE requires bindPort in 1..65535" }
            require(!advertiseHost.isNullOrBlank()) { "NETWORK_NODE requires advertiseHost" }
            require(advertiseHost !in wildcardHosts) { "advertiseHost must not be wildcard" }
            require(advertisePort in 1..65535) {
                "NETWORK_NODE requires advertisePort in 1..65535"
            }
        } else {
            require(bindHost == null && bindPort == 0) { "$mode does not accept a listener" }
            require(advertiseHost == null && advertisePort == 0) {
                "$mode does not advertise a listener"
            }
        }
    }

    companion object {
        private val wildcardHosts = setOf("0.0.0.0", "::", "[::]", "::0")

        @JvmStatic
        fun embedded(): RuntimeNetworkConfig = RuntimeNetworkConfig(RuntimeNetworkMode.EMBEDDED)

        @JvmStatic
        fun outboundOnly(): RuntimeNetworkConfig =
            RuntimeNetworkConfig(RuntimeNetworkMode.OUTBOUND_ONLY)

        @JvmStatic
        @JvmOverloads
        fun networkNode(
            bindHost: String,
            bindPort: Int,
            advertiseHost: String,
            advertisePort: Int = bindPort,
        ): RuntimeNetworkConfig = RuntimeNetworkConfig(
            mode = RuntimeNetworkMode.NETWORK_NODE,
            bindHost = bindHost,
            bindPort = bindPort,
            advertiseHost = advertiseHost,
            advertisePort = advertisePort,
        )
    }
}

/**
 * Collects boot-time settings required to create and seed a runtime instance.
 *
 * @property systemName Connector or host system identifier.
 * @property nodeId Logical node identifier inside the system.
 * @property network Explicit listener participation policy. Defaults to an embedded runtime with
 * no TCP listener.
 * @property queueCapacity Ingress queue capacity configured at runtime create time.
 * @property strictNoDrop Whether runtime should stay on strict no-drop behavior where supported.
 * @property separateDeliveredRequestLane Whether the connector should opt into the additive
 * runtime host lane that exports locally delivered `REQUEST` traffic on its own fd.
 * Defaults to `true` for request/reply hosts; advanced mostly one-way hosts may disable it.
 * @property generation Initial control generation applied before start.
 * @property overloadPolicy Optional policy attached to connector-owned route snapshots.
 * @property routes Initial route snapshot to load before start.
 * @property connectionStrategy Optional startup-only TCP connection strategy.
 * @property security Optional plaintext, TLS, or mTLS startup policy.
 */
data class RuntimeStartSpec @JvmOverloads constructor(
    val systemName: String,
    val nodeId: String,
    val queueCapacity: Int = 128,
    val strictNoDrop: Boolean = true,
    val separateDeliveredRequestLane: Boolean = true,
    val generation: Long = 1,
    val overloadPolicy: RuntimeOverloadPolicySpec? = null,
    val routes: List<RuntimeRouteSpec>,
    val connectionStrategy: RuntimeTcpConnectionStrategySpec? = null,
    val security: RuntimeTcpSecuritySpec? = null,
    val network: RuntimeNetworkConfig = RuntimeNetworkConfig.embedded(),
)
