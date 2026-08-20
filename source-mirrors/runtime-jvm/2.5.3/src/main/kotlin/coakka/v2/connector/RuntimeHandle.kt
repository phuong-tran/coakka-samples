package coakka.v2.connector

import com.sun.jna.Pointer

/**
 * Owns one native `coakka v2` runtime instance and its strict lifecycle.
 *
 * A handle is responsible for loading the runtime, exporting host fds,
 * applying the initial control snapshot, and coordinating `start` / `stop`.
 */
class RuntimeHandle private constructor(
    internal val lib: CoakkaV2Library,
    private val runtime: Pointer,
    internal val hostHandles: HostHandles,
) : AutoCloseable {
    private val lifecycleLock = Any()
    private val isWindowsHost = System.getProperty("os.name").lowercase().contains("win")
    private var started = false
    private var closed = false
    private var startupConnectionApplyResult: RuntimeTcpConnectionApplyResult? = null
    private var startupSecurityApplyResult: RuntimeTcpSecurityApplyResult? = null

    val controlClient: RuntimeControlClient = RuntimeControlClient(this)
    val monitor: RuntimeMonitor = RuntimeMonitor(this)

    /** Returns static runtime metadata for the loaded runtime library bound to this handle. */
    fun runtimeInfo(): RuntimeInfoSnapshot = readRuntimeInfo(lib)

    /** Returns compiled, entitled, and effective capabilities for the loaded runtime binary. */
    fun runtimeCapabilities(): RuntimeCapabilitiesSnapshot = readRuntimeCapabilities(lib)

    /** Returns the result of the startup connection apply, or null when no policy was requested. */
    fun startupConnectionResult(): RuntimeTcpConnectionApplyResult? = startupConnectionApplyResult

    /** Returns the result of the startup security apply, or null when no policy was requested. */
    fun startupSecurityResult(): RuntimeTcpSecurityApplyResult? = startupSecurityApplyResult

    /** Returns the immutable effective TCP connection strategy for this runtime instance. */
    fun tcpConnectionConfig(): RuntimeTcpConnectionConfigSnapshot {
        synchronized(lifecycleLock) {
            check(!closed) { "runtime handle already closed" }
            val config = NativeTcpConnectionConfig().apply {
                struct_size = size().toLong()
                write()
            }
            requireStatus(
                lib.coakka_v2_runtime_get_tcp_connection_config(runtime, config),
                "runtime_get_tcp_connection_config",
            )
            config.read()
            return config.toSnapshot()
        }
    }

    /**
     * Atomically selects a connection strategy while the runtime is CREATED.
     * STARTED and STOPPED instances return a structured rejection without changing active state.
     */
    fun applyTcpConnectionStrategy(
        spec: RuntimeTcpConnectionStrategySpec,
    ): RuntimeTcpConnectionApplyResult {
        synchronized(lifecycleLock) {
            check(!closed) { "runtime handle already closed" }
            val options = spec.toNative()
            val nativeResult = NativeTcpConnectionApplyResult().apply {
                struct_size = size().toLong()
                write()
            }
            val rc = lib.coakka_v2_runtime_apply_tcp_connection_options_ex(
                runtime,
                options,
                nativeResult,
            )
            nativeResult.read()
            return nativeResult.toSnapshot(lib, rc)
        }
    }

    /** Returns copy-safe, non-secret TLS state for this runtime instance. */
    fun tcpSecurityInfo(): RuntimeTcpSecurityInfoSnapshot {
        synchronized(lifecycleLock) {
            check(!closed) { "runtime handle already closed" }
            val info = NativeTcpSecurityInfo().apply {
                struct_size = size().toLong()
                write()
            }
            requireStatus(
                lib.coakka_v2_runtime_get_tcp_security_info(runtime, info),
                "runtime_get_tcp_security_info",
            )
            info.read()
            return info.toSnapshot()
        }
    }

    /**
     * Atomically applies TLS configuration or reloads a later generation of the active mode.
     * A rejection returns the unchanged active generation and never exposes credential paths.
     */
    fun applyTcpSecurity(spec: RuntimeTcpSecuritySpec): RuntimeTcpSecurityApplyResult {
        synchronized(lifecycleLock) {
            check(!closed) { "runtime handle already closed" }
            val options = spec.toNative()
            val nativeResult = NativeTcpSecurityApplyResult().apply {
                struct_size = size().toLong()
                write()
            }
            val rc = lib.coakka_v2_runtime_apply_tcp_security_options_ex(
                runtime,
                options,
                nativeResult,
            )
            nativeResult.read()
            return nativeResult.toSnapshot(lib, rc)
        }
    }

    /** Returns the latest runtime-owned effective config for this runtime instance. */
    fun config(): RuntimeConfigSnapshot {
        val config = RuntimeConfigView().apply {
            struct_size = size().toLong()
            write()
        }
        requireStatus(lib.coakka_v2_runtime_get_config(runtime, config), "runtime_get_config")
        config.read()
        return RuntimeConfigSnapshot(
            systemName = safeText(config.system_name),
            nodeId = safeText(config.node_id),
            strictNoDrop = config.strict_no_drop != 0,
            queueCapacity = config.queue_capacity,
            requestMaxFrameSize = config.request_max_frame_size,
            localDispatchBatchLimit = config.local_dispatch_batch_limit,
            runtimeState = config.runtime_state,
            runtimeStateName = runtimeStateName(lib, config.runtime_state),
            snapshotPresent = config.snapshot_present != 0,
            appliedGeneration = config.applied_generation,
            routeCount = config.route_count,
            southboundBindHost = config.southbound_bind_host,
            southboundBindPort = config.southbound_bind_port.toInt() and 0xffff,
            configuredIngressOverloadMode = config.configured_ingress_overload_mode,
            configuredIngressOverloadModeName = overloadModeName(lib, config.configured_ingress_overload_mode),
            configuredLocalDeliveryOverloadMode = config.configured_local_delivery_overload_mode,
            configuredLocalDeliveryOverloadModeName = overloadModeName(lib, config.configured_local_delivery_overload_mode),
            configuredRemoteOutboundOverloadMode = config.configured_remote_outbound_overload_mode,
            configuredRemoteOutboundOverloadModeName = overloadModeName(lib, config.configured_remote_outbound_overload_mode),
            configuredRemoteOutboundReplyReserveSlots = config.configured_remote_outbound_reply_reserve_slots,
            effectiveIngressOverloadMode = config.effective_ingress_overload_mode,
            effectiveIngressOverloadModeName = overloadModeName(lib, config.effective_ingress_overload_mode),
            effectiveLocalDeliveryOverloadMode = config.effective_local_delivery_overload_mode,
            effectiveLocalDeliveryOverloadModeName = overloadModeName(lib, config.effective_local_delivery_overload_mode),
            effectiveRemoteOutboundOverloadMode = config.effective_remote_outbound_overload_mode,
            effectiveRemoteOutboundOverloadModeName = overloadModeName(lib, config.effective_remote_outbound_overload_mode),
            effectiveRemoteOutboundReplyReserveSlots = config.effective_remote_outbound_reply_reserve_slots,
        )
    }

    /** Starts the native runtime after host handles and initial control have been prepared. */
    fun start() {
        synchronized(lifecycleLock) {
            check(!closed) { "runtime handle already closed" }
            check(!started) { "runtime already started" }
            requireStatus(lib.coakka_v2_runtime_start(runtime), "runtime_start")
            started = true
        }
    }

    /** Stops the native runtime. A stopped instance is not restarted in place. */
    fun stop() {
        synchronized(lifecycleLock) {
            if (!started || closed) {
                return
            }
            requireStatus(lib.coakka_v2_runtime_stop(runtime), "runtime_stop")
            started = false
        }
    }

    /** Returns the latest coarse health view exported by the native runtime. */
    fun health(): RuntimeHealthSnapshot {
        val health = RuntimeHealth().apply {
            struct_size = size().toLong()
            write()
        }
        requireStatus(lib.coakka_v2_runtime_get_health(runtime, health), "runtime_get_health")
        health.read()
        return RuntimeHealthSnapshot(
            runtimeState = health.runtime_state,
            runtimeStateName = runtimeStateName(lib, health.runtime_state),
            flags = health.flags,
            flagsText = healthFlagsText(lib, health.flags),
            appliedGeneration = health.applied_generation,
        )
    }

    /** Returns a reduced stats snapshot that is useful for connector-side diagnostics. */
    fun stats(): RuntimeStatsSnapshot {
        val stats = RuntimeStats().apply {
            struct_size = size().toLong()
            write()
        }
        requireStatus(lib.coakka_v2_runtime_get_stats(runtime, stats), "runtime_get_stats")
        stats.read()
        return RuntimeStatsSnapshot(
            appliedGeneration = stats.applied_generation,
            routeCount = stats.route_count,
            runtimeState = stats.runtime_state,
            runtimeStateName = runtimeStateName(lib, stats.runtime_state),
            queueRejectedCount = stats.queue_rejected_count,
            routeMissCount = stats.route_miss_count,
            deadletterCount = stats.deadletter_count,
            deliveryFailedCount = stats.delivery_failed_count,
            controlRejectedCount = stats.control_rejected_count,
            localWorkQueueCapacity = stats.local_work_queue_capacity,
            localWorkQueueDepth = stats.local_work_queue_depth,
            localWorkQueueHighWatermark = stats.local_work_queue_high_watermark,
            deliveredRequestOutboundQueueCapacity = stats.delivered_request_outbound_queue_capacity,
            deliveredRequestOutboundQueueHighWatermark =
                stats.delivered_request_outbound_queue_high_watermark,
            deliveredRequestOutboundEnqueueBlockCount =
                stats.delivered_request_outbound_enqueue_block_count,
            deliveredRequestOutboundDirectWriteCount =
                stats.delivered_request_outbound_direct_write_count,
            responseOutboundQueueCapacity = stats.response_outbound_queue_capacity,
            responseOutboundQueueHighWatermark = stats.response_outbound_queue_high_watermark,
            responseOutboundEnqueueBlockCount = stats.response_outbound_enqueue_block_count,
            responseOutboundDirectWriteCount = stats.response_outbound_direct_write_count,
            deadletterOutboundQueueCapacity = stats.deadletter_outbound_queue_capacity,
            deadletterOutboundQueueHighWatermark = stats.deadletter_outbound_queue_high_watermark,
            deadletterOutboundEnqueueBlockCount = stats.deadletter_outbound_enqueue_block_count,
            deadletterOutboundDirectWriteCount = stats.deadletter_outbound_direct_write_count,
            remoteOutboundQueueCapacity = stats.remote_outbound_queue_capacity,
            remoteOutboundQueueDepth = stats.remote_outbound_queue_depth,
            remoteOutboundQueueHighWatermark = stats.remote_outbound_queue_high_watermark,
            remoteOutboundQueueRejectedCount = stats.remote_outbound_queue_rejected_count,
            remoteOutboundExpiredDropCount = stats.remote_outbound_expired_drop_count,
            remoteOutboundReplyReserveSlots = stats.remote_outbound_reply_reserve_slots,
            remoteOutboundReplyReservationRejectCount = stats.remote_outbound_reply_reservation_reject_count,
            ingressOverloadMode = stats.ingress_overload_mode,
            ingressOverloadModeName = overloadModeName(lib, stats.ingress_overload_mode),
            localDeliveryOverloadMode = stats.local_delivery_overload_mode,
            localDeliveryOverloadModeName = overloadModeName(lib, stats.local_delivery_overload_mode),
            remoteOutboundOverloadMode = stats.remote_outbound_overload_mode,
            remoteOutboundOverloadModeName = overloadModeName(lib, stats.remote_outbound_overload_mode),
            monitorEventEmittedCount = stats.monitor_event_emitted_count,
            monitorEventDroppedCount = stats.monitor_event_dropped_count,
            monitorEventEmittedLifetimeCount = stats.monitor_event_emitted_lifetime_count,
            monitorEventDroppedLifetimeCount = stats.monitor_event_dropped_lifetime_count,
            remoteOutboundOneWayDropCount = stats.remote_outbound_one_way_drop_count,
        )
    }

    internal fun submitEnvelope(bytes: ByteArray) {
        requireStatus(
            lib.coakka_v2_runtime_submit_envelope(runtime, bytes, bytes.size.toLong()),
            "submit_envelope",
        )
    }

    internal fun applyControlEnvelope(bytes: ByteArray) {
        requireStatus(
            lib.coakka_v2_runtime_apply_control_envelope(runtime, bytes, bytes.size.toLong()),
            "apply_control_envelope",
        )
    }

    override fun close() {
        synchronized(lifecycleLock) {
            if (closed) {
                return
            }
            if (started) {
                requireStatus(lib.coakka_v2_runtime_stop(runtime), "runtime_stop")
                started = false
            }
            if (isWindowsHost) {
                // Windows runtime handles are currently process-lifetime owned from the JVM side.
                // `runtime_destroy` is deferred because the current JNA lane proves `stop()`
                // safely but still tears down the guest process during explicit destroy.
                hostHandles.request_write_fd = -1
                hostHandles.response_read_fd = -1
                hostHandles.deadletter_read_fd = -1
                hostHandles.control_write_fd = -1
                hostHandles.monitor_read_fd = -1
                hostHandles.delivered_request_read_fd = -1
                closed = true
                return
            }
            lib.coakka_v2_runtime_destroy(runtime)
            closeIfOpen(hostHandles.request_write_fd)
            closeIfOpen(hostHandles.response_read_fd)
            closeIfOpen(hostHandles.deadletter_read_fd)
            closeIfOpen(hostHandles.control_write_fd)
            closeIfOpen(hostHandles.monitor_read_fd)
            closeIfOpen(hostHandles.delivered_request_read_fd)
            hostHandles.request_write_fd = -1
            hostHandles.response_read_fd = -1
            hostHandles.deadletter_read_fd = -1
            hostHandles.control_write_fd = -1
            hostHandles.monitor_read_fd = -1
            hostHandles.delivered_request_read_fd = -1
            closed = true
        }
    }

    companion object {
        /** Reads global runtime metadata without creating a runtime instance. */
        fun readRuntimeInfo(runtimeLibPath: String? = null): RuntimeInfoSnapshot {
            val lib = CoakkaV2Library.load(NativeLibraryResolver.resolve(runtimeLibPath))
            return readRuntimeInfo(lib)
        }

        /** Reads runtime capability truth without creating a runtime instance. */
        @JvmStatic
        @JvmOverloads
        fun readRuntimeCapabilities(runtimeLibPath: String? = null): RuntimeCapabilitiesSnapshot {
            val lib = CoakkaV2Library.load(NativeLibraryResolver.resolve(runtimeLibPath))
            return readRuntimeCapabilities(lib)
        }

        private fun readRuntimeCapabilities(lib: CoakkaV2Library): RuntimeCapabilitiesSnapshot {
            val capabilities = NativeRuntimeCapabilities().apply {
                struct_size = size().toLong()
                write()
            }
            requireStatus(
                lib.coakka_v2_runtime_get_capabilities(capabilities),
                "runtime_get_capabilities",
            )
            capabilities.read()
            return capabilities.toSnapshot()
        }

        private fun readRuntimeInfo(lib: CoakkaV2Library): RuntimeInfoSnapshot {
            val info = RuntimeInfo().apply {
                struct_size = size().toLong()
                write()
            }
            requireStatus(lib.coakka_v2_runtime_get_info(info), "runtime_get_info")
            info.read()
            return RuntimeInfoSnapshot(
                abiVersion = info.abi_version,
                featureFlags = info.feature_flags,
                runtimeVersion = safeText(info.runtime_version),
                gitCommit = safeText(info.git_commit),
                southboundBackend = safeText(info.southbound_backend),
                allocatorBackend = safeText(info.allocator_backend),
                docsHint = safeText(info.docs_hint),
                featureFlagsText = runtimeFeatureFlagsText(lib, info.feature_flags),
            )
        }

        /**
         * Creates a new runtime handle, validates ABI compatibility, exports host handles,
         * and applies the initial route snapshot from [startSpec].
         */
        fun open(runtimeLibPath: String? = null, startSpec: RuntimeStartSpec): RuntimeHandle {
            val lib = CoakkaV2Library.load(NativeLibraryResolver.resolve(runtimeLibPath))
            check(lib.coakka_v2_runtime_get_abi_version() == CoakkaV2Library.ABI_VERSION) {
                "unexpected ABI version"
            }

            val cfg = RuntimeConfig().apply {
                system_name = startSpec.systemName
                node_id = startSpec.nodeId
                strict_no_drop = if (startSpec.strictNoDrop) 1 else 0
                queue_capacity = startSpec.queueCapacity
                write()
            }

            val runtime = lib.coakka_v2_runtime_create(cfg)
                ?: error("coakka_v2_runtime_create returned null")
            val handles = HostHandles().apply {
                struct_size = size().toLong()
                flags = CoakkaHostHandlesFlags.ENABLE_MONITOR
                request_write_fd = -1
                response_read_fd = -1
                deadletter_read_fd = -1
                control_write_fd = -1
                monitor_read_fd = -1
                delivered_request_read_fd = -1
                if (startSpec.separateDeliveredRequestLane) {
                    flags = flags or CoakkaHostHandlesFlags.SEPARATE_DELIVERED_REQUEST_LANE
                }
                write()
            }

            try {
                val handle = RuntimeHandle(lib = lib, runtime = runtime, hostHandles = handles)
                val network = startSpec.network
                val networkOptions = NativeNetworkOptions().apply {
                    struct_size = size().toLong()
                    fields = if (network.mode == RuntimeNetworkMode.NETWORK_NODE) 0x1fL else 0x1L
                    mode = network.mode.nativeValue
                    bind_host = network.bindHost
                    advertise_host = network.advertiseHost
                    bind_port = network.bindPort.toShort()
                    advertise_port = network.advertisePort.toShort()
                    write()
                }
                requireStatus(
                    lib.coakka_v2_runtime_apply_network_options(runtime, networkOptions),
                    "runtime_apply_network_options",
                )
                startSpec.connectionStrategy?.let { connectionSpec ->
                    val result = handle.applyTcpConnectionStrategy(connectionSpec)
                    handle.startupConnectionApplyResult = result
                    if (!result.applied()) {
                        throw RuntimeTcpConnectionApplyException(result)
                    }
                }
                startSpec.security?.let { securitySpec ->
                    val result = handle.applyTcpSecurity(securitySpec)
                    handle.startupSecurityApplyResult = result
                    if (!result.applied()) {
                        throw RuntimeTcpSecurityApplyException(result)
                    }
                }
                requireStatus(lib.coakka_v2_runtime_get_host_handles(runtime, handles), "get_host_handles")
                handles.read()
                handle.controlClient.applyStartSpec(startSpec)
                return handle
            } catch (t: Throwable) {
                lib.coakka_v2_runtime_destroy(runtime)
                if (!handleIsWindowsHost()) {
                    closeIfOpen(handles.request_write_fd)
                    closeIfOpen(handles.response_read_fd)
                    closeIfOpen(handles.deadletter_read_fd)
                    closeIfOpen(handles.control_write_fd)
                    closeIfOpen(handles.monitor_read_fd)
                    closeIfOpen(handles.delivered_request_read_fd)
                }
                handles.request_write_fd = -1
                handles.response_read_fd = -1
                handles.deadletter_read_fd = -1
                handles.control_write_fd = -1
                handles.monitor_read_fd = -1
                handles.delivered_request_read_fd = -1
                throw t
            }
        }

        private fun handleIsWindowsHost(): Boolean =
            System.getProperty("os.name").lowercase().contains("win")
    }
}
