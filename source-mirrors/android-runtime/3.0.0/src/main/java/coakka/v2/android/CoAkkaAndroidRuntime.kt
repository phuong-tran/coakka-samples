package coakka.v2.android

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock

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
    private val nativeCallLock = ReentrantLock()
    private val nativeCallsDrained = nativeCallLock.newCondition()
    private var activeNativeCalls = 0

    /** Submits a handler reply or one-way envelope; use [ask] for request/reply. */
    fun submitEnvelope(envelope: ByteArray): Boolean =
        !closing.get() && lanes.request.write(envelope)

    fun applyControlEnvelope(envelope: ByteArray): Boolean =
        !closing.get() && lanes.control.write(envelope)

    /** Blocks until one locally delivered request is available or close begins. */
    fun readDeliveredRequest(): ByteArray? {
        val handle = enterBlockingNativeCall() ?: return null
        try {
            val status = IntArray(1)
            val envelope = NativeRuntimeBridge.nativeReadDeliveredRequest(handle, status)
            return when (status[0]) {
                NativeStatus.OK -> checkNotNull(envelope) {
                    "runtime request reader returned no envelope"
                }
                NativeStatus.CLOSED -> null
                else -> throw CoAkkaNativeException("runtime_request_reader_read", status[0])
            }
        } finally {
            leaveBlockingNativeCall()
        }
    }

    /**
     * Submits one reply-capable request and returns Core's matched terminal.
     * The timeout is one monotonic deadline; close interrupts it within the
     * connector's bounded native wait slice.
     */
    fun ask(envelope: ByteArray, timeoutMillis: Int): AndroidRuntimeAskResult {
        require(timeoutMillis >= 0) { "timeoutMillis must be non-negative" }
        val handle = checkNotNull(enterBlockingNativeCall()) { "runtime is closing" }
        try {
            val result = IntArray(2)
            val terminal = NativeRuntimeBridge.nativeAsk(
                handle,
                envelope,
                timeoutMillis,
                result,
            )
            requireNativeOk(result[0], "runtime_ask")
            return AndroidRuntimeAskResult(
                kind = AndroidRuntimeAskResultKind.fromNative(result[1]),
                envelope = checkNotNull(terminal) { "native ask returned no terminal envelope" },
            )
        } finally {
            leaveBlockingNativeCall()
        }
    }

    /** Drains monitor changes; returns zero when collection is disabled. */
    fun consumeMonitorDoorbell(): Long = synchronized(lifecycleLock) {
        check(!closing.get()) { "runtime is closing" }
        lanes.monitor?.consume() ?: 0L
    }

    /** Waits for a monitor change; returns null when disabled or closing. */
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

    /** Reads one owned copy of Core's accepted immutable startup facts. */
    fun startupConfig(): AndroidRuntimeStartupConfig = synchronized(lifecycleLock) {
        check(!closing.get()) { "runtime is closing" }
        val handle = nativeHandle
        check(handle != 0L) { "runtime is closed" }
        checkNotNull(NativeRuntimeBridge.nativeReadStartupConfig(handle)) {
            "runtime startup configuration read failed"
        }.toAndroidRuntimeStartupConfig()
    }

    /** Reads Core's accepted network facts; inputs are never reconstructed as state. */
    fun networkSnapshot(): RuntimeNetworkSnapshot = synchronized(lifecycleLock) {
        check(!closing.get()) { "runtime is closing" }
        val numeric = LongArray(4)
        val text = arrayOfNulls<String>(2)
        requireNativeOk(
            NativeRuntimeBridge.nativeReadNetworkSnapshot(nativeHandle, numeric, text),
            "runtime_get_network_config",
        )
        numeric.toRuntimeNetworkSnapshot(text)
    }

    override fun close() {
        if (!closing.compareAndSet(false, true)) {
            return
        }
        val handle = nativeCallLock.run {
            lock()
            try {
                nativeHandle
            } finally {
                unlock()
            }
        }
        if (handle != 0L) {
            NativeRuntimeBridge.nativeSignalClose(handle)
        }
        lanes.cancelWaiters()
        nativeCallLock.lock()
        try {
            while (activeNativeCalls != 0) {
                nativeCallsDrained.awaitUninterruptibly()
            }
        } finally {
            nativeCallLock.unlock()
        }
        synchronized(lifecycleLock) {
            val ownedHandle = nativeHandle
            if (ownedHandle == 0L) {
                return
            }
            val stopStatus = NativeRuntimeBridge.nativeStop(ownedHandle)
            lanes.close()
            NativeRuntimeBridge.nativeDestroy(ownedHandle)
            nativeHandle = 0L
            requireNativeOk(stopStatus, "runtime_stop")
        }
    }

    private fun enterBlockingNativeCall(): Long? {
        nativeCallLock.lock()
        return try {
            if (closing.get() || nativeHandle == 0L) {
                null
            } else {
                activeNativeCalls += 1
                nativeHandle
            }
        } finally {
            nativeCallLock.unlock()
        }
    }

    private fun leaveBlockingNativeCall() {
        nativeCallLock.lock()
        try {
            activeNativeCalls -= 1
            check(activeNativeCalls >= 0) { "native call admission underflow" }
            if (activeNativeCalls == 0) {
                nativeCallsDrained.signalAll()
            }
        } finally {
            nativeCallLock.unlock()
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
                monitorIntent = config.monitor.toNativeIntent(),
            )
            check(handle != 0L) { "native runtime creation failed" }

            var lanes: RuntimeHostLanes? = null
            try {
                config.network?.let { intent ->
                    requireNativeOk(
                        NativeRuntimeBridge.nativeApplyNetwork(
                            handle, intent.mode.nativeValue, intent.bindHost,
                            intent.bindPort ?: 0, intent.bindPort != null, intent.advertiseHost,
                            intent.advertisePort ?: 0, intent.advertisePort != null,
                        ),
                        "runtime_apply_network_options",
                    )
                }
                val accepted = checkNotNull(NativeRuntimeBridge.nativeReadStartupConfig(handle)) {
                    "runtime startup configuration read failed"
                }.toAndroidRuntimeStartupConfig()
                // Export only what Core accepted. Input preferences are not native state.
                val hostFlags = if (accepted.monitorEnabled) RuntimeHostLanes.ENABLE_MONITOR else 0
                val hostHandles = checkNotNull(
                    NativeRuntimeBridge.nativeOpenHostHandles(
                        handle,
                        hostFlags,
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
