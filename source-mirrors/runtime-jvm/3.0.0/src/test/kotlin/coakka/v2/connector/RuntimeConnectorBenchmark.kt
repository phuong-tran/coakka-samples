package coakka.v2.connector

import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import java.util.Locale
import coakka.v2.connector.protocol.ConnectorDeliveryHint
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

internal data class RuntimeConnectorBenchmarkConfig(
    val requests: Int = 100_000,
    val warmupRequests: Int = 2_000,
    val concurrency: Int = 8,
    val requestPayloadBytes: Int = 63,
    val responsePayloadBytes: Int = 20,
    val queueCapacity: Int = 256,
    val timeoutMs: Long = 5_000,
    val transport: String = "tcp",
    val serverPort: Int = 19_371,
    val connectionMode: String = "auto",
    val runLabel: String = "manual",
) {
    companion object {
        fun parse(args: Array<String>): RuntimeConnectorBenchmarkConfig {
            val fields = linkedMapOf<String, String>()
            args.forEach { argument ->
                require(argument.startsWith("--") && '=' in argument) {
                    "benchmark arguments must use --name=value"
                }
                val (name, value) = argument.substring(2).split('=', limit = 2)
                require(name in setOf(
                    "requests",
                    "warmup-requests",
                    "concurrency",
                    "request-payload-bytes",
                    "response-payload-bytes",
                    "queue-capacity",
                    "timeout-ms",
                    "transport",
                    "server-port",
                    "connection-mode",
                    "run-label",
                )) { "unknown benchmark argument --$name" }
                require(fields.put(name, value) == null) { "duplicate benchmark argument --$name" }
            }

            fun boundedInt(name: String, default: Int, range: IntRange): Int {
                val parsed = fields[name]?.toIntOrNull() ?: if (name in fields) {
                    error("--$name must be an integer")
                } else {
                    default
                }
                require(parsed in range) { "--$name must be in ${range.first}..${range.last}" }
                return parsed
            }

            fun boundedLong(name: String, default: Long, range: LongRange): Long {
                val parsed = fields[name]?.toLongOrNull() ?: if (name in fields) {
                    error("--$name must be an integer")
                } else {
                    default
                }
                require(parsed in range) { "--$name must be in ${range.first}..${range.last}" }
                return parsed
            }

            val label = fields["run-label"] ?: "manual"
            require(label.matches(Regex("[A-Za-z0-9._-]{1,64}"))) {
                "--run-label must contain 1..64 safe identifier characters"
            }
            val transport = fields["transport"] ?: "tcp"
            require(transport == "local" || transport == "tcp") {
                "--transport must be local or tcp"
            }
            val connectionMode = fields["connection-mode"] ?: "auto"
            require(connectionMode in setOf(
                "auto",
                "per-exchange",
                "bounded-pool",
                "persistent-single-flight",
                "multiplexing",
            )) { "unsupported --connection-mode=$connectionMode" }
            return RuntimeConnectorBenchmarkConfig(
                requests = boundedInt("requests", 100_000, 1..2_000_000),
                warmupRequests = boundedInt("warmup-requests", 2_000, 0..1_000_000),
                concurrency = boundedInt("concurrency", 8, 1..1_024),
                requestPayloadBytes = boundedInt("request-payload-bytes", 63, 1..1_048_576),
                responsePayloadBytes = boundedInt("response-payload-bytes", 20, 1..1_048_576),
                queueCapacity = boundedInt("queue-capacity", 256, 1..1_048_576),
                timeoutMs = boundedLong("timeout-ms", 5_000, 1L..60_000L),
                transport = transport,
                serverPort = boundedInt("server-port", 19_371, 1..65_535),
                connectionMode = connectionMode,
                runLabel = label,
            )
        }
    }
}

internal object RuntimeConnectorBenchmarkMath {
    fun workerSpans(total: Int, workers: Int): List<IntRange> {
        require(total >= 0 && workers > 0)
        val activeWorkers = min(workers, total.coerceAtLeast(1))
        val base = total / activeWorkers
        val remainder = total % activeWorkers
        var offset = 0
        return List(activeWorkers) { worker ->
            val count = base + if (worker < remainder) 1 else 0
            val start = offset
            offset += count
            start until offset
        }
    }

    fun percentileNanos(sorted: LongArray, percentile: Double): Long {
        require(sorted.isNotEmpty() && percentile in 0.0..1.0)
        val index = (ceil(sorted.size * percentile).toInt() - 1).coerceAtLeast(0)
        return sorted[index]
    }
}

private data class WindowResult(
    val elapsedNanos: Long,
    val latenciesNanos: LongArray,
)

private data class GcSnapshot(val collections: Long, val milliseconds: Long)

private class AllocationMeter private constructor(
    private val bean: com.sun.management.ThreadMXBean,
) {
    fun snapshot(): Long = bean.getThreadAllocatedBytes(bean.allThreadIds)
        .sumOf { it.coerceAtLeast(0) }

    companion object {
        fun create(): AllocationMeter? {
            val bean = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
                ?: return null
            if (!bean.isThreadAllocatedMemorySupported) {
                return null
            }
            if (!bean.isThreadAllocatedMemoryEnabled) {
                bean.isThreadAllocatedMemoryEnabled = true
            }
            return AllocationMeter(bean)
        }
    }
}

private fun gcSnapshot(): GcSnapshot =
    ManagementFactory.getGarbageCollectorMXBeans().fold(GcSnapshot(0, 0)) { total, bean ->
        GcSnapshot(
            collections = total.collections + bean.collectionCount.coerceAtLeast(0),
            milliseconds = total.milliseconds + bean.collectionTime.coerceAtLeast(0),
        )
    }

private fun heapPeakBytes(): Long =
    ManagementFactory.getMemoryPoolMXBeans()
        .filter { it.type == MemoryType.HEAP }
        .sumOf { it.peakUsage.used.coerceAtLeast(0) }

private suspend fun runWindow(
    client: RuntimeClient?,
    askClient: RuntimeAskClient?,
    requests: Int,
    concurrency: Int,
    requestPayloadUtf8: String,
    requestPayloadBytes: ByteArray,
    responsePayloadBytes: ByteArray,
    timeoutMs: Long,
    deliveryHint: ConnectorDeliveryHint,
    recordLatencies: Boolean,
): WindowResult = coroutineScope {
    require((client == null) != (askClient == null)) { "exactly one benchmark client is required" }
    if (requests == 0) {
        return@coroutineScope WindowResult(0, LongArray(0))
    }
    val latencies = if (recordLatencies) LongArray(requests) else LongArray(0)
    val spans = RuntimeConnectorBenchmarkMath.workerSpans(requests, concurrency)
    val startedAt = System.nanoTime()
    spans.mapIndexed { worker, span ->
        async(if (askClient == null) Dispatchers.Default else Dispatchers.IO) {
            for (index in span) {
                val requestStartedAt = if (recordLatencies) System.nanoTime() else 0L
                val actualPayload = if (askClient != null) {
                    askClient.askPayloadBlocking(
                        source = "bench.$worker",
                        target = "svc.benchmark.echo",
                        payload = requestPayloadBytes,
                        timeoutMs = timeoutMs.toInt(),
                        deliveryHint = deliveryHint,
                    )
                } else {
                    checkNotNull(client).askRaw(
                        source = "bench.$worker",
                        target = "svc.benchmark.echo",
                        payloadUtf8 = requestPayloadUtf8,
                        timeoutMs = timeoutMs,
                        operation = "benchmark",
                        deliveryHint = deliveryHint,
                    ).payload
                }
                check(actualPayload.contentEquals(responsePayloadBytes)) {
                    "response payload mismatch at request $index"
                }
                if (recordLatencies) {
                    latencies[index] = System.nanoTime() - requestStartedAt
                }
            }
        }
    }.awaitAll()
    WindowResult(System.nanoTime() - startedAt, latencies)
}

private fun format(value: Double): String = String.format(Locale.ROOT, "%.3f", value)

private fun RuntimeConnectorBenchmarkConfig.connectionStrategy(): RuntimeTcpConnectionStrategySpec? =
    if (connectionMode == "auto") null else RuntimeTcpConnectionStrategySpec(
        mode = when (connectionMode) {
            "per-exchange" -> RuntimeTcpConnectionMode.PER_EXCHANGE
            "bounded-pool" -> RuntimeTcpConnectionMode.BOUNDED_POOL
            "persistent-single-flight" -> RuntimeTcpConnectionMode.PERSISTENT_SINGLE_FLIGHT
            "multiplexing" -> RuntimeTcpConnectionMode.MULTIPLEXING
            else -> error("validated connection mode became invalid")
        },
    )

fun main(args: Array<String>) = runBlocking {
    val config = RuntimeConnectorBenchmarkConfig.parse(args)
    val requestPayload = "x".repeat(config.requestPayloadBytes)
    val requestPayloadBytes = requestPayload.toByteArray(Charsets.UTF_8)
    val responsePayload = "y".repeat(config.responsePayloadBytes)
    val responsePayloadBytes = responsePayload.toByteArray(Charsets.UTF_8)
    val threadBean = ManagementFactory.getThreadMXBean()
    val allocationMeter = AllocationMeter.create()
    val unixOperatingSystemBean =
        ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.UnixOperatingSystemMXBean
    val memoryPools = ManagementFactory.getMemoryPoolMXBeans().filter { it.type == MemoryType.HEAP }
    val connectionStrategy = config.connectionStrategy()
    val server = RuntimeClient.start(
        startSpec = RuntimeStartSpec(
            systemName = "runtime-jni-benchmark-server",
            nodeId = "runtime-jni-benchmark-server-${config.runLabel}",
            queueCapacity = config.queueCapacity,
            strictNoDrop = true,
            routes = RuntimeClient.localRoutes(listOf("svc.benchmark.echo"), config.serverPort),
            connectionStrategy = connectionStrategy,
            network = if (config.transport == "tcp") {
                RuntimeNetworkConfig.networkNode(
                    bindHost = "127.0.0.1",
                    bindPort = config.serverPort,
                    advertiseHost = "127.0.0.1",
                    advertisePort = config.serverPort,
                )
            } else {
                RuntimeNetworkConfig.embedded()
            },
        ),
    )
    var client: RuntimeClient? = null
    var askClient: RuntimeAskClient? = null
    try {
        if (config.transport == "tcp") {
            askClient = RuntimeAskClient.start(
                startSpec = RuntimeStartSpec(
                    systemName = "runtime-jni-benchmark-client",
                    nodeId = "runtime-jni-benchmark-client-${config.runLabel}",
                    queueCapacity = config.queueCapacity,
                    strictNoDrop = true,
                    routes = listOf(
                        RuntimeRouteSpec(
                            target = "svc.benchmark.echo",
                            endpoints = listOf(
                                RuntimeEndpointSpec(
                                    host = "127.0.0.1",
                                    port = config.serverPort.toLong(),
                                    flags = RuntimeEndpointFlags.NONE,
                                ),
                            ),
                        ),
                    ),
                    connectionStrategy = connectionStrategy,
                    network = RuntimeNetworkConfig.outboundOnly(),
                ),
            )
        } else {
            client = server
        }
        server.registerPayloadHandler("svc.benchmark.echo") { requestPayload ->
            check(requestPayload.contentEquals(requestPayloadBytes)) { "handler payload mismatch" }
            responsePayloadBytes
        }
        val serverStartup = server.startupConfig()
        val serverDelivery = server.deliveryConfig()
        val clientStartup = askClient?.startupConfig() ?: serverStartup
        val clientDelivery = askClient?.deliveryConfig() ?: serverDelivery
        if (config.connectionMode == "auto") {
            check(serverStartup.wasDefaulted(CoakkaRuntimeStartupDefaultGroups.EXECUTION))
            check(serverStartup.wasDefaulted(CoakkaRuntimeStartupDefaultGroups.CONNECTION))
            check(clientStartup.wasDefaulted(CoakkaRuntimeStartupDefaultGroups.EXECUTION))
            check(clientStartup.wasDefaulted(CoakkaRuntimeStartupDefaultGroups.CONNECTION))
        }
        val expectedConnectionMode = connectionStrategy?.mode ?: RuntimeTcpConnectionMode.MULTIPLEXING
        check(serverStartup.connection.mode == expectedConnectionMode)
        check(clientStartup.connection.mode == expectedConnectionMode)
        listOf(serverDelivery, clientDelivery).forEach { delivery ->
            check(delivery.compiled)
            check(delivery.requestMode == CoakkaRuntimeDeliveryModes.OWNED_QUEUE)
            check(delivery.responseMode == CoakkaRuntimeDeliveryModes.OWNED_QUEUE)
            check(delivery.remoteBatchMaxCalls == 8)
        }

        runWindow(
            client = client,
            askClient = askClient,
            requests = config.warmupRequests,
            concurrency = config.concurrency,
            requestPayloadUtf8 = requestPayload,
            requestPayloadBytes = requestPayloadBytes,
            responsePayloadBytes = responsePayloadBytes,
            timeoutMs = config.timeoutMs,
            deliveryHint = if (config.transport == "tcp") {
                ConnectorDeliveryHint.REQUIRE_REMOTE
            } else {
                ConnectorDeliveryHint.ROUTER_DEFAULT
            },
            recordLatencies = false,
        )
        val clientStatsBefore = client?.snapshotStats()
        val askStatsBefore = askClient?.snapshotStats()
        val serverStatsBefore = server.snapshotStats()
        memoryPools.forEach { it.resetPeakUsage() }
        threadBean.resetPeakThreadCount()
        val gcBefore = gcSnapshot()
        val allocatedBefore = allocationMeter?.snapshot()
        val openFileDescriptorsBefore = unixOperatingSystemBean?.openFileDescriptorCount ?: -1
        val heapBefore = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used

        val result = runWindow(
            client = client,
            askClient = askClient,
            requests = config.requests,
            concurrency = config.concurrency,
            requestPayloadUtf8 = requestPayload,
            requestPayloadBytes = requestPayloadBytes,
            responsePayloadBytes = responsePayloadBytes,
            timeoutMs = config.timeoutMs,
            deliveryHint = if (config.transport == "tcp") {
                ConnectorDeliveryHint.REQUIRE_REMOTE
            } else {
                ConnectorDeliveryHint.ROUTER_DEFAULT
            },
            recordLatencies = true,
        )

        val heapAfter = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
        val heapPeak = heapPeakBytes()
        val gcAfter = gcSnapshot()
        val allocatedAfter = allocationMeter?.snapshot()
        val openFileDescriptorsAfter = unixOperatingSystemBean?.openFileDescriptorCount ?: -1
        val clientStatsAfter = client?.snapshotStats()
        val askStatsAfter = askClient?.snapshotStats()
        val serverStatsAfter = server.snapshotStats()
        check((clientStatsAfter?.pendingRequests ?: askStatsAfter?.pendingRequests) == 0) {
            "pending requests remain after measured window"
        }
        check(serverStatsAfter.deliveredRequests - serverStatsBefore.deliveredRequests == config.requests.toLong()) {
            "delivered request count mismatch"
        }
        val matchedBefore = clientStatsBefore?.matchedResponses ?: checkNotNull(askStatsBefore).matchedResponses
        val matchedAfter = clientStatsAfter?.matchedResponses ?: checkNotNull(askStatsAfter).matchedResponses
        check(matchedAfter - matchedBefore == config.requests.toLong()) {
            "matched response count mismatch"
        }
        val deadlettersBefore = clientStatsBefore?.matchedDeadletters ?: checkNotNull(askStatsBefore).matchedDeadletters
        val deadlettersAfter = clientStatsAfter?.matchedDeadletters ?: checkNotNull(askStatsAfter).matchedDeadletters
        check(deadlettersAfter == deadlettersBefore) {
            "measured window produced a deadletter"
        }
        if (clientStatsAfter != null && clientStatsBefore != null) {
            check(clientStatsAfter.lateResponses == clientStatsBefore.lateResponses) {
                "measured window produced a late response"
            }
            check(clientStatsAfter.unhandledDeadletters == clientStatsBefore.unhandledDeadletters) {
                "measured window produced an unhandled deadletter"
            }
        } else {
            checkNotNull(askStatsAfter)
            checkNotNull(askStatsBefore)
            check(askStatsAfter.timedOutRequests == askStatsBefore.timedOutRequests) {
                "measured window produced an ask timeout"
            }
        }

        val sorted = result.latenciesNanos.copyOf().apply { sort() }
        val seconds = result.elapsedNanos / 1_000_000_000.0
        val throughput = config.requests / seconds
        val averageNanos = result.latenciesNanos.average()
        println(
            buildString {
                append("runtime-jni-benchmark-result")
                append(" run_label=").append(config.runLabel)
                append(" transport=").append(config.transport)
                append(" connection_mode=").append(config.connectionMode)
                append(" server_port=").append(config.serverPort)
                append(" requests=").append(config.requests)
                append(" warmup_requests=").append(config.warmupRequests)
                append(" concurrency=").append(config.concurrency)
                append(" request_payload_bytes=").append(config.requestPayloadBytes)
                append(" response_payload_bytes=").append(config.responsePayloadBytes)
                append(" queue_capacity=").append(config.queueCapacity)
                append(" server_cpu_policy=").append(serverStartup.execution.requestedPolicy)
                append(" server_selected_cpus=").append(serverStartup.execution.selectedCpuIds.joinToString(","))
                append(" client_cpu_policy=").append(clientStartup.execution.requestedPolicy)
                append(" client_selected_cpus=").append(clientStartup.execution.selectedCpuIds.joinToString(","))
                append(" server_connection_mode=").append(serverStartup.connection.mode.value)
                append(" client_connection_mode=").append(clientStartup.connection.mode.value)
                append(" delivery_request_mode=").append(clientDelivery.requestMode)
                append(" delivery_response_mode=").append(clientDelivery.responseMode)
                append(" delivery_remote_batch_max_calls=").append(clientDelivery.remoteBatchMaxCalls)
                append(" delivery_slot_capacity=").append(clientDelivery.slotCapacity)
                append(" delivery_byte_capacity=").append(clientDelivery.byteCapacity)
                append(" elapsed_seconds=").append(format(seconds))
                append(" throughput_req_per_sec=").append(format(throughput))
                append(" avg_us=").append(format(averageNanos / 1_000.0))
                append(" p50_us=").append(format(RuntimeConnectorBenchmarkMath.percentileNanos(sorted, 0.50) / 1_000.0))
                append(" p95_us=").append(format(RuntimeConnectorBenchmarkMath.percentileNanos(sorted, 0.95) / 1_000.0))
                append(" p99_us=").append(format(RuntimeConnectorBenchmarkMath.percentileNanos(sorted, 0.99) / 1_000.0))
                append(" max_us=").append(format(sorted.last() / 1_000.0))
                append(" heap_before_bytes=").append(heapBefore)
                append(" heap_after_bytes=").append(heapAfter)
                append(" heap_peak_bytes=").append(heapPeak)
                append(" gc_collections=").append(gcAfter.collections - gcBefore.collections)
                append(" gc_milliseconds=").append(gcAfter.milliseconds - gcBefore.milliseconds)
                append(" thread_allocated_bytes_delta=").append(
                    if (allocatedBefore != null && allocatedAfter != null) {
                        (allocatedAfter - allocatedBefore).coerceAtLeast(0)
                    } else {
                        -1
                    },
                )
                append(" thread_count_end=").append(threadBean.threadCount)
                append(" thread_count_peak=").append(threadBean.peakThreadCount)
                append(" open_fds_before=").append(openFileDescriptorsBefore)
                append(" open_fds_after=").append(openFileDescriptorsAfter)
                append(" delivered_delta=").append(serverStatsAfter.deliveredRequests - serverStatsBefore.deliveredRequests)
                append(" matched_delta=").append(matchedAfter - matchedBefore)
                append(" java_version=").append(System.getProperty("java.version").replace(' ', '_'))
                append(" vm_name=").append(System.getProperty("java.vm.name").replace(' ', '_'))
                append(" os_arch=").append(System.getProperty("os.arch"))
            },
        )
    } finally {
        val shutdownStartedAt = System.nanoTime()
        try {
            askClient?.close()
            val runningClient = client
            if (runningClient != null && runningClient !== server) {
                runningClient.shutdown()
            }
        } finally {
            server.shutdown()
        }
        val shutdownNanos = System.nanoTime() - shutdownStartedAt
        println("runtime-jni-benchmark-shutdown shutdown_us=${format(shutdownNanos / 1_000.0)}")
    }
}
