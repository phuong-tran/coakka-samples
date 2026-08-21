package coakka.v2.android

enum class RuntimeNetworkMode(internal val nativeValue: Int) {
    EMBEDDED(1),
    OUTBOUND_ONLY(2),
    NETWORK_NODE(3),
}

enum class RuntimeState(internal val nativeValue: Int) {
    CREATED(0),
    STARTED(1),
    STOPPED(2);

    companion object {
        internal fun fromNative(value: Int): RuntimeState =
            entries.firstOrNull { it.nativeValue == value }
                ?: error("unknown native runtime state=$value")
    }
}

data class AndroidRuntimeHealth(
    val state: RuntimeState,
    val flags: Long,
    val appliedGeneration: Long,
)

object AndroidRuntimeFeatures {
    const val FILE_LANE = 1 shl 23
    const val STREAM_LANE = 1 shl 24
    const val LANE_OWNER_GRANTS = 1 shl 25
}

/** Immutable identity and feature truth read from the bundled native Core. */
data class AndroidRuntimeInfo(
    val abiVersion: Int,
    val featureFlags: Int,
    val remoteWireProfileVersion: Int,
    val runtimeVersion: String,
    val gitCommit: String,
    val southboundBackend: String,
    val buildId: String,
) {
    fun supports(feature: Int): Boolean = featureFlags and feature == feature
}

/** Immutable startup network policy; listener ownership is not inferred from routes. */
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
            require(bindHost == null && bindPort == 0) {
                "$mode does not accept a listener"
            }
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

data class AndroidRuntimeConfig(
    val systemName: String,
    val nodeId: String,
    val network: RuntimeNetworkConfig = RuntimeNetworkConfig.embedded(),
    val queueCapacity: Int = 128,
    val strictNoDrop: Boolean = true,
) {
    init {
        require(systemName.isNotBlank()) { "systemName must not be blank" }
        require(nodeId.isNotBlank()) { "nodeId must not be blank" }
        require(queueCapacity > 0) { "queueCapacity must be positive" }
    }
}

object RuntimeEndpointFlags {
    const val NONE = 0
    const val LOCAL = 1
    const val UNAVAILABLE = 1 shl 1
}

object RuntimeRouteFlags {
    const val NONE = 0
    const val PREFER_LOCAL = 1
}

data class AndroidRuntimeEndpoint(
    val host: String,
    val port: Int,
    val weight: Int = 1,
    val flags: Int = RuntimeEndpointFlags.NONE,
) {
    init {
        require(host.isNotBlank()) { "endpoint host must not be blank" }
        require(port in 0..65535) { "endpoint port must be in 0..65535" }
        require(port > 0 || flags and RuntimeEndpointFlags.LOCAL != 0) {
            "only LOCAL endpoints may use port=0"
        }
        require(weight > 0) { "endpoint weight must be positive" }
    }
}

data class AndroidRuntimeRoute(
    val target: String,
    val endpoints: List<AndroidRuntimeEndpoint>,
    val strategy: Int = 1,
    val routeKeyHint: String? = null,
    val flags: Int = RuntimeRouteFlags.NONE,
) {
    init {
        require(target.isNotBlank()) { "route target must not be blank" }
        require(endpoints.isNotEmpty()) { "route must contain an endpoint" }
    }

    companion object {
        @JvmStatic
        fun local(target: String, nodeId: String): AndroidRuntimeRoute = AndroidRuntimeRoute(
            target = target,
            endpoints = listOf(
                AndroidRuntimeEndpoint(
                    host = nodeId,
                    port = 0,
                    flags = RuntimeEndpointFlags.LOCAL,
                ),
            ),
        )
    }
}
