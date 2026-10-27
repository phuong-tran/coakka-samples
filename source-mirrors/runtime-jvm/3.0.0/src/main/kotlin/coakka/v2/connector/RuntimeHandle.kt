package coakka.v2.connector

import coakka.v2.connector.internal.NativeBridge

/** Owns one bridge-managed native runtime instance and its strict lifecycle. */
class RuntimeHandle private constructor(
    internal val nativeId: Long,
    internal val hostHandles: NativeHostHandles,
) : AutoCloseable {
    private val lifecycleLock = Any()
    private var started = false
    private var closed = false
    private var startupSecurityApplyResult: RuntimeTcpSecurityApplyResult? = null

    val controlClient: RuntimeControlClient = RuntimeControlClient(this)
    val monitor: RuntimeMonitor = RuntimeMonitor(this)

    fun runtimeInfo(): RuntimeInfoSnapshot = readRuntimeInfoLoaded()

    fun runtimeCapabilities(): RuntimeCapabilitiesSnapshot = readRuntimeCapabilitiesLoaded()

    fun startupSecurityResult(): RuntimeTcpSecurityApplyResult? = startupSecurityApplyResult

    /** Returns Core's accepted startup defaults and effective construction selection. */
    fun startupConfig(): RuntimeStartupConfigSnapshot = synchronized(lifecycleLock) {
        requireOpen()
        NativeBridge.runtimeStartupConfig(nativeId).startupConfigSnapshot()
    }

    fun networkConfig(): RuntimeNetworkConfigSnapshot = synchronized(lifecycleLock) {
        requireOpen()
        val record = NativeBridge.runtimeNetworkConfig(nativeId)
        requireStatus(record.value(0).toInt(), "runtime_get_network_config")
        check(record.bytes.size == 2) { "incomplete native network record" }
        RuntimeNetworkConfigSnapshot(
            mode = RuntimeNetworkMode.of(record.value(1).toInt()),
            explicitPolicy = record.value(2) != 0L,
            bindHost = record.bytes[0]?.toString(Charsets.UTF_8),
            bindPort = record.value(3).toInt(),
            advertiseHost = record.bytes[1]?.toString(Charsets.UTF_8),
            advertisePort = record.value(4).toInt(),
        )
    }

    /** Returns Core's accepted immutable native delivery composition. */
    fun deliveryConfig(): RuntimeDeliveryConfigSnapshot = synchronized(lifecycleLock) {
        requireOpen()
        NativeBridge.runtimeDeliveryConfig(nativeId).deliveryConfigSnapshot()
    }

    fun tcpConnectionConfig(): RuntimeTcpConnectionConfigSnapshot = synchronized(lifecycleLock) {
        requireOpen()
        val record = NativeBridge.runtimeConnectionConfig(nativeId)
        requireStatus(record.value(0).toInt(), "runtime_get_tcp_connection_config")
        record.connectionSnapshot()
    }

    fun applyTcpConnectionStrategy(spec: RuntimeTcpConnectionStrategySpec): RuntimeTcpConnectionApplyResult =
        synchronized(lifecycleLock) {
            requireOpen()
            NativeBridge.runtimeApplyConnection(nativeId, spec.toNativeValues()).connectionApplySnapshot()
        }

    fun tcpSecurityInfo(): RuntimeTcpSecurityInfoSnapshot = synchronized(lifecycleLock) {
        requireOpen()
        val record = NativeBridge.runtimeSecurityInfo(nativeId)
        requireStatus(record.value(0).toInt(), "runtime_get_tcp_security_info")
        record.securitySnapshot()
    }

    fun applyTcpSecurity(spec: RuntimeTcpSecuritySpec): RuntimeTcpSecurityApplyResult =
        synchronized(lifecycleLock) {
            requireOpen()
            NativeBridge.runtimeApplySecurity(
                nativeId,
                spec.toNativeValues(),
                spec.toNativeBytes(),
            ).securityApplySnapshot()
        }

    fun config(): RuntimeConfigSnapshot = synchronized(lifecycleLock) {
        requireOpen()
        NativeBridge.runtimeConfig(nativeId).configSnapshot()
    }

    fun start() = synchronized(lifecycleLock) {
        requireOpen()
        check(!started) { "runtime already started" }
        requireStatus(NativeBridge.runtimeStart(nativeId), "runtime_start")
        started = true
    }

    fun stop() = synchronized(lifecycleLock) {
        if (!started || closed) return@synchronized
        requireStatus(NativeBridge.runtimeStop(nativeId), "runtime_stop")
        started = false
    }

    fun health(): RuntimeHealthSnapshot = synchronized(lifecycleLock) {
        requireOpen()
        NativeBridge.runtimeHealth(nativeId).healthSnapshot()
    }

    fun stats(): RuntimeStatsSnapshot = synchronized(lifecycleLock) {
        requireOpen()
        NativeBridge.runtimeStats(nativeId).statsSnapshot()
    }

    internal fun submitEnvelope(bytes: ByteArray) {
        requireStatus(NativeBridge.runtimeSubmit(nativeId, bytes), "submit_envelope")
    }

    internal fun applyControlEnvelope(bytes: ByteArray) {
        requireStatus(NativeBridge.runtimeControl(nativeId, bytes), "apply_control_envelope")
    }

    internal fun openCoreAskClient() = synchronized(lifecycleLock) {
        requireOpen()
        check(started) { "runtime must be started before opening Core ask ownership" }
        requireStatus(NativeBridge.runtimeAskOpen(nativeId), "ask_client_create")
    }

    internal fun askEnvelope(bytes: ByteArray, timeoutMs: Int) =
        NativeBridge.runtimeAsk(nativeId, bytes, timeoutMs)

    internal fun askPayload(
        messageId: ByteArray,
        source: ByteArray,
        target: ByteArray,
        replyTo: ByteArray,
        payload: ByteArray,
        requestTimeoutMs: Int,
        waitTimeoutMs: Int,
        deliveryHint: Int,
    ) = NativeBridge.runtimeAskPayload(
        nativeId,
        messageId,
        source,
        target,
        replyTo,
        payload,
        requestTimeoutMs,
        waitTimeoutMs,
        deliveryHint,
    )

    internal fun replyPayload(
        requestFrame: ByteArray,
        source: ByteArray,
        payload: ByteArray,
    ) {
        requireStatus(
            NativeBridge.runtimeReplyPayload(nativeId, requestFrame, source, payload),
            "reply_payload",
        )
    }

    internal fun beginAskEnvelope(bytes: ByteArray): Long {
        val record = NativeBridge.runtimeAskBegin(nativeId, bytes)
        requireStatus(record.value(0).toInt(), "ask_client_begin")
        return record.value(1).also { check(it > 0) { "Core returned an invalid ask ticket id" } }
    }

    internal fun awaitAskEnvelope(ticketId: Long, timeoutMs: Int) =
        NativeBridge.runtimeAskAwait(nativeId, ticketId, timeoutMs)

    internal fun cancelAskEnvelope(ticketId: Long) =
        NativeBridge.runtimeAskCancel(nativeId, ticketId)

    override fun close() = synchronized(lifecycleLock) {
        if (closed) return@synchronized
        NativeBridge.runtimeClose(nativeId)
        started = false
        closed = true
        hostHandles.request_write_fd = -1
        hostHandles.response_read_fd = -1
        hostHandles.deadletter_read_fd = -1
        hostHandles.control_write_fd = -1
        hostHandles.monitor_read_fd = -1
        hostHandles.delivered_request_read_fd = -1
    }

    private fun requireOpen() {
        check(!closed) { "runtime handle already closed" }
    }

    companion object {
        fun readRuntimeInfo(runtimeLibPath: String? = null): RuntimeInfoSnapshot {
            NativeLibraryResolver.load(runtimeLibPath)
            return readRuntimeInfoLoaded()
        }

        @JvmStatic
        @JvmOverloads
        fun readRuntimeCapabilities(runtimeLibPath: String? = null): RuntimeCapabilitiesSnapshot {
            NativeLibraryResolver.load(runtimeLibPath)
            return readRuntimeCapabilitiesLoaded()
        }

        private fun readRuntimeInfoLoaded(): RuntimeInfoSnapshot = NativeBridge.runtimeInfo().infoSnapshot()

        private fun readRuntimeCapabilitiesLoaded(): RuntimeCapabilitiesSnapshot =
            NativeBridge.runtimeCapabilities().capabilitiesSnapshot()

        /**
         * Creates a native-startup instance, applies network/security intent,
         * exports bridge-owned host lanes, then seeds the initial Core snapshot.
         */
        @JvmStatic
        @JvmOverloads
        fun open(runtimeLibPath: String? = null, startSpec: RuntimeStartSpec): RuntimeHandle {
            NativeLibraryResolver.load(runtimeLibPath)
            val info = readRuntimeInfoLoaded()
            check(info.abiVersion == CoakkaRuntimeAbi.VERSION) { "unexpected ABI version" }

            val created = NativeBridge.runtimeCreate(
                startSpec.systemName.utf8(),
                startSpec.nodeId.utf8(),
                startSpec.queueCapacity,
                if (startSpec.strictNoDrop) 1 else 0,
                longArrayOf(
                    if (startSpec.execution != null) 1L else 0L,
                    startSpec.execution?.cpuPolicy ?: 0L,
                    if (startSpec.io != null) 1L else 0L,
                    if (startSpec.io?.useIoUring == true) 1L else 0L,
                    if (startSpec.monitor != null) 1L else 0L,
                    if (startSpec.monitor?.enabled == true) 1L else 0L,
                ),
                startSpec.connectionStrategy?.toNativeValues(),
            )
            requireStatus(created.value(0).toInt(), "runtime_create_native")
            val id = created.value(1)
            check(id != 0L) { "runtime bridge returned an invalid owner id" }

            var handle: RuntimeHandle? = null
            try {
                startSpec.network?.let { network ->
                    requireStatus(
                        NativeBridge.runtimeApplyNetwork(
                            id,
                            network.fields(),
                            network.mode.value,
                            network.bindHost?.utf8(),
                            network.bindPort ?: 0,
                            network.advertiseHost?.utf8(),
                            network.advertisePort ?: 0,
                        ),
                        "runtime_apply_network_options",
                    )
                }
                val startup = NativeBridge.runtimeStartupConfig(id).startupConfigSnapshot()
                val flags = if (startup.monitorEnabled) CoakkaHostHandlesFlags.ENABLE_MONITOR else 0
                val exported = NativeBridge.runtimeExportHandles(id, flags)
                requireStatus(exported.value(0).toInt(), "runtime_get_host_handles")
                val handles = NativeHostHandles(
                    request_write_fd = exported.value(1).toInt(),
                    response_read_fd = exported.value(2).toInt(),
                    deadletter_read_fd = exported.value(3).toInt(),
                    control_write_fd = exported.value(4).toInt(),
                    monitor_read_fd = exported.value(5).toInt(),
                    delivered_request_read_fd = exported.value(6).toInt(),
                )
                handle = RuntimeHandle(nativeId = id, hostHandles = handles)

                startSpec.security?.let { securitySpec ->
                    val result = handle.applyTcpSecurity(securitySpec)
                    handle.startupSecurityApplyResult = result
                    if (!result.applied()) throw RuntimeTcpSecurityApplyException(result)
                }
                handle.controlClient.applyStartSpec(startSpec)
                return handle
            } catch (failure: Throwable) {
                handle?.close() ?: NativeBridge.runtimeClose(id)
                throw failure
            }
        }

    }
}
