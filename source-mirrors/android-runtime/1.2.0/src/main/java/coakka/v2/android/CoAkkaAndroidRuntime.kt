package coakka.v2.android

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns one native runtime and every Android-side descriptor exported by it.
 *
 * Frame reads are blocking and belong on dedicated I/O workers. Call [close]
 * exactly once from the owning service or application lifecycle.
 */
class CoAkkaAndroidRuntime private constructor(
    private var nativeHandle: Long,
    private val lanes: RuntimeHostLanes,
) : Closeable {
    private val closing = AtomicBoolean(false)
    private val lifecycleLock = Any()

    fun submitEnvelope(envelope: ByteArray): Boolean =
        !closing.get() && lanes.request.write(envelope)

    fun applyControlEnvelope(envelope: ByteArray): Boolean =
        !closing.get() && lanes.control.write(envelope)

    /** Blocks until one locally delivered request is available or the lane closes. */
    fun readDeliveredRequest(): ByteArray? = lanes.deliveredRequest?.read()

    /** Blocks until one response is available or the lane closes. */
    fun readResponse(): ByteArray? = lanes.response.read()

    /** Blocks until one deadletter is available or the lane closes. */
    fun readDeadletter(): ByteArray? = lanes.deadletter.read()

    /** Drains the monitor doorbell; the returned count may represent coalesced changes. */
    fun consumeMonitorDoorbell(): Long = synchronized(lifecycleLock) {
        check(!closing.get()) { "runtime is closing" }
        lanes.monitor?.consume() ?: 0L
    }

    /** Blocks until the monitor doorbell is ready or returns null during close. */
    fun waitForMonitorDoorbell(): Long? =
        if (closing.get()) null else lanes.monitor?.awaitAndConsume()

    /** Pulls the current health snapshot after a monitor wakeup or on demand. */
    fun readHealth(): AndroidRuntimeHealth = synchronized(lifecycleLock) {
        check(!closing.get()) { "runtime is closing" }
        val handle = nativeHandle
        check(handle != 0L) { "runtime is closed" }
        val values = checkNotNull(NativeRuntimeBridge.nativeReadHealth(handle)) {
            "runtime health read failed"
        }
        require(values.size == HEALTH_VALUE_COUNT) {
            "unexpected runtime health field count=${values.size}"
        }
        AndroidRuntimeHealth(
            state = RuntimeState.fromNative(values[0].toInt()),
            flags = values[1],
            appliedGeneration = values[2],
        )
    }

    override fun close() {
        if (!closing.compareAndSet(false, true)) {
            return
        }
        lanes.cancelWaiters()
        synchronized(lifecycleLock) {
            val handle = nativeHandle
            if (handle == 0L) {
                return
            }
            val stopStatus = NativeRuntimeBridge.nativeStop(handle)
            lanes.close()
            NativeRuntimeBridge.nativeDestroy(handle)
            nativeHandle = 0L
            requireNativeOk(stopStatus, "runtime_stop")
        }
    }

    companion object {
        const val ABI_VERSION = 1
        private const val HEALTH_VALUE_COUNT = 3

        /** Reads immutable build identity without opening a runtime instance. */
        @JvmStatic
        fun runtimeInfo(): AndroidRuntimeInfo {
            val numeric = LongArray(3)
            val text = arrayOfNulls<String>(4)
            requireNativeOk(
                NativeRuntimeBridge.nativeReadRuntimeInfo(numeric, text),
                "runtime_get_info",
            )
            return AndroidRuntimeInfo(
                abiVersion = numeric[0].toInt(),
                featureFlags = numeric[1].toInt(),
                remoteWireProfileVersion = numeric[2].toInt(),
                runtimeVersion = checkNotNull(text[0]),
                gitCommit = checkNotNull(text[1]),
                southboundBackend = checkNotNull(text[2]),
                buildId = checkNotNull(text[3]),
            )
        }

        @JvmStatic
        @JvmOverloads
        fun open(
            config: AndroidRuntimeConfig,
            routes: List<AndroidRuntimeRoute>,
            generation: Long = 1L,
        ): CoAkkaAndroidRuntime {
            check(NativeRuntimeBridge.nativeAbiVersion() == ABI_VERSION) {
                "unsupported native CoAkka ABI"
            }
            val handle = NativeRuntimeBridge.nativeCreate(
                systemName = config.systemName,
                nodeId = config.nodeId,
                queueCapacity = config.queueCapacity,
                strictNoDrop = config.strictNoDrop,
            )
            check(handle != 0L) { "native runtime creation failed" }

            var lanes: RuntimeHostLanes? = null
            try {
                requireNativeOk(
                    NativeRuntimeBridge.nativeApplyNetwork(
                        handle = handle,
                        mode = config.network.mode.nativeValue,
                        bindHost = config.network.bindHost,
                        bindPort = config.network.bindPort,
                        advertiseHost = config.network.advertiseHost,
                        advertisePort = config.network.advertisePort,
                    ),
                    "runtime_apply_network_options",
                )
                val hostHandles = checkNotNull(
                    NativeRuntimeBridge.nativeOpenHostHandles(
                        handle,
                        RuntimeHostLanes.ENABLE_MONITOR or
                            RuntimeHostLanes.SEPARATE_DELIVERED_REQUEST_LANE,
                    ),
                ) { "runtime host fd export failed" }
                lanes = RuntimeHostLanes.adopt(hostHandles)

                val initialControl = RuntimeControlEncoder.encodeSnapshot(
                    generation = generation,
                    routes = routes,
                )
                requireNativeOk(
                    NativeRuntimeBridge.nativeApplyInitialControl(handle, initialControl),
                    "runtime_apply_initial_control",
                )
                requireNativeOk(NativeRuntimeBridge.nativeStart(handle), "runtime_start")
                return CoAkkaAndroidRuntime(handle, lanes)
            } catch (failure: Throwable) {
                lanes?.close()
                NativeRuntimeBridge.nativeDestroy(handle)
                throw failure
            }
        }
    }
}
