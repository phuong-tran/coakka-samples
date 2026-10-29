package coakka.v2.android

enum class RuntimeNetworkMode(internal val nativeValue: Int) {
    LEGACY_ROUTE_DERIVED(0),
    EMBEDDED(1),
    OUTBOUND_ONLY(2),
    NETWORK_NODE(3);

    companion object {
        internal fun fromNative(value: Int): RuntimeNetworkMode =
            entries.firstOrNull { it.nativeValue == value }
                ?: error("unknown native network mode=$value")
    }
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

/** Stable Core connection-mode values; these map ABI values, not defaults. */
enum class RuntimeTcpConnectionMode(internal val nativeValue: Int) {
    PER_EXCHANGE(0),
    BOUNDED_POOL(1),
    PERSISTENT_SINGLE_FLIGHT(2),
    MULTIPLEXED(3);

    companion object {
        internal fun fromNative(value: Int): RuntimeTcpConnectionMode =
            entries.firstOrNull { it.nativeValue == value }
                ?: error("unknown native TCP connection mode=$value")
    }
}

/** Core-owned construction-default provenance bits. */
object AndroidRuntimeStartupDefaultGroups {
    const val EXECUTION = 1L shl 0
    const val CONNECTION = 1L shl 1
    const val IO = 1L shl 2
    const val MONITOR = 1L shl 3
}

/** Stable Core CPU-policy values; these map ABI values, not defaults. */
object AndroidRuntimeStartupCpuPolicies {
    const val AUTO = 0
    const val SINGLE = 1
}

data class AndroidRuntimeStartupExecution(
    val requestedPolicy: Int,
    val supported: Boolean,
    val availableCpuCount: Int,
    val selectedCpuIds: List<Long>,
    val startupApplied: Boolean,
)

data class AndroidRuntimeTcpConnectionConfig(
    val defaultsRevision: Int,
    val mode: RuntimeTcpConnectionMode,
    val applicableFields: Long,
    val explicitlyConfiguredFields: Long,
    val defaultedFields: Long,
    val configurableFields: Long,
    val maxConnections: Int,
    val maxRequestsPerConnection: Long,
    val idleTimeoutMillis: Long,
)

data class AndroidRuntimeStartupIo(
    val requested: Boolean,
    val compiled: Boolean,
    val probed: Boolean,
    val supported: Boolean,
    val effective: Boolean,
    val probeError: Int,
)

/** Copy-safe Core facts for the accepted immutable startup configuration. */
data class AndroidRuntimeStartupConfig(
    val defaultsRevision: Int,
    val defaultedGroups: Long,
    val strictNoDrop: Boolean,
    val queueCapacity: Int,
    val requestMaxFrameSize: Long,
    val runtimeState: RuntimeState,
    val execution: AndroidRuntimeStartupExecution,
    val connection: AndroidRuntimeTcpConnectionConfig,
    val io: AndroidRuntimeStartupIo,
    val monitorEnabled: Boolean,
) {
    fun wasDefaulted(group: Long): Boolean = (defaultedGroups and group) == group
}

internal const val RUNTIME_STARTUP_VALUE_COUNT = 29

internal fun LongArray.toAndroidRuntimeStartupConfig(): AndroidRuntimeStartupConfig {
    require(size == RUNTIME_STARTUP_VALUE_COUNT) {
        "unexpected runtime startup field count=$size"
    }
    val selectedCpuCount = this[9].toInt()
    require(selectedCpuCount in 0..2) {
        "invalid selected CPU count=$selectedCpuCount"
    }
    return AndroidRuntimeStartupConfig(
        defaultsRevision = this[0].toInt(),
        defaultedGroups = this[1],
        strictNoDrop = this[2] != 0L,
        queueCapacity = this[3].toInt(),
        requestMaxFrameSize = this[4],
        runtimeState = RuntimeState.fromNative(this[5].toInt()),
        execution = AndroidRuntimeStartupExecution(
            requestedPolicy = this[6].toInt(),
            supported = this[7] != 0L,
            availableCpuCount = this[8].toInt(),
            selectedCpuIds = (0 until selectedCpuCount).map { this[10 + it] },
            startupApplied = this[12] != 0L,
        ),
        connection = AndroidRuntimeTcpConnectionConfig(
            defaultsRevision = this[13].toInt(),
            mode = RuntimeTcpConnectionMode.fromNative(this[14].toInt()),
            applicableFields = this[15],
            explicitlyConfiguredFields = this[16],
            defaultedFields = this[17],
            configurableFields = this[18],
            maxConnections = this[19].toInt(),
            maxRequestsPerConnection = this[20],
            idleTimeoutMillis = this[21],
        ),
        io = AndroidRuntimeStartupIo(
            requested = this[22] != 0L,
            compiled = this[23] != 0L,
            probed = this[24] != 0L,
            supported = this[25] != 0L,
            effective = this[26] != 0L,
            probeError = this[27].toInt(),
        ),
        monitorEnabled = this[28] != 0L,
    )
}

data class AndroidRuntimeHealth(
    val state: RuntimeState,
    val flags: Long,
    val appliedGeneration: Long,
)

enum class AndroidRuntimeAskResultKind(internal val nativeValue: Int) {
    RESPONSE(1),
    DEADLETTER(2);

    companion object {
        internal fun fromNative(value: Int): AndroidRuntimeAskResultKind =
            entries.firstOrNull { it.nativeValue == value }
                ?: error("unknown native ask result kind=$value")
    }
}

/** One Core-matched terminal envelope copied out of its native owner. */
data class AndroidRuntimeAskResult(
    val kind: AndroidRuntimeAskResultKind,
    val envelope: ByteArray,
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

/** Optional network intent; Core validates mode, endpoints and field applicability. */
data class RuntimeNetworkConfig @JvmOverloads constructor(
    val mode: RuntimeNetworkMode,
    val bindHost: String? = null,
    val bindPort: Int? = null,
    val advertiseHost: String? = null,
    val advertisePort: Int? = null,
) {
    init {
        require(bindPort == null || bindPort in 0..65535) { "bindPort must fit uint16" }
        require(advertisePort == null || advertisePort in 0..65535) { "advertisePort must fit uint16" }
    }

    companion object {
        @JvmStatic
        fun embedded(): RuntimeNetworkConfig = RuntimeNetworkConfig(RuntimeNetworkMode.EMBEDDED)

        @JvmStatic
        fun outboundOnly(): RuntimeNetworkConfig = RuntimeNetworkConfig(RuntimeNetworkMode.OUTBOUND_ONLY)

        @JvmStatic
        fun networkNode(
            bindHost: String,
            bindPort: Int,
            advertiseHost: String,
            advertisePort: Int,
        ): RuntimeNetworkConfig = RuntimeNetworkConfig(
            RuntimeNetworkMode.NETWORK_NODE, bindHost, bindPort, advertiseHost, advertisePort,
        )
    }
}

/** One copy-safe Core-issued network snapshot, including explicit-intent provenance. */
data class RuntimeNetworkSnapshot(
    val mode: RuntimeNetworkMode,
    val explicitPolicy: Boolean,
    val bindHost: String?,
    val bindPort: Int,
    val advertiseHost: String?,
    val advertisePort: Int,
)

internal fun LongArray.toRuntimeNetworkSnapshot(text: Array<String?>): RuntimeNetworkSnapshot {
    require(size == 4 && text.size == 2) { "unexpected native network snapshot extent" }
    require(this[0] in 0..Int.MAX_VALUE.toLong()) { "native network mode is outside integer range" }
    require(this[1] in 0L..1L) { "unknown native network explicit-policy fact" }
    require(this[2] in 0L..65535L && this[3] in 0L..65535L) { "native port is outside uint16 range" }
    return RuntimeNetworkSnapshot(
        RuntimeNetworkMode.fromNative(this[0].toInt()), this[1] == 1L,
        text[0], this[2].toInt(), text[1], this[3].toInt(),
    )
}

/** Optional startup collection intent; absence delegates the choice to Core. */
data class AndroidRuntimeMonitorOptions(val enabled: Boolean)

internal fun AndroidRuntimeMonitorOptions?.toNativeIntent(): Int = when (this?.enabled) {
    null -> -1
    false -> 0
    true -> 1
}

data class AndroidRuntimeConfig @JvmOverloads constructor(
    val systemName: String,
    val nodeId: String,
    val network: RuntimeNetworkConfig? = null,
    val queueCapacity: Int = 0,
    val strictNoDrop: Boolean = true,
    val monitor: AndroidRuntimeMonitorOptions? = null,
) {
    init {
        require(systemName.isNotBlank()) { "systemName must not be blank" }
        require(nodeId.isNotBlank()) { "nodeId must not be blank" }
        require(queueCapacity >= 0) { "queueCapacity must be non-negative" }
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
