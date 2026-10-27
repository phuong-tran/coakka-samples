package coakka.v2.connector

import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

internal data class RuntimeConnectorLifecycleStressConfig(
    val warmupCycles: Int = 3,
    val cycles: Int = 100,
    val requestsPerCycle: Int = 64,
    val concurrency: Int = 8,
    val requestTimeoutMs: Long = 1_000,
    val timeoutFaultMs: Long = 10,
    val runLabel: String = "manual",
) {
    companion object {
        fun parse(args: Array<String>): RuntimeConnectorLifecycleStressConfig {
            val fields = linkedMapOf<String, String>()
            args.forEach { argument ->
                require(argument.startsWith("--") && '=' in argument) {
                    "stress arguments must use --name=value"
                }
                val (name, value) = argument.substring(2).split('=', limit = 2)
                require(name in setOf(
                    "warmup-cycles",
                    "cycles",
                    "requests-per-cycle",
                    "concurrency",
                    "request-timeout-ms",
                    "timeout-fault-ms",
                    "run-label",
                )) { "unknown stress argument --$name" }
                require(fields.put(name, value) == null) { "duplicate stress argument --$name" }
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
            return RuntimeConnectorLifecycleStressConfig(
                warmupCycles = boundedInt("warmup-cycles", 3, 1..100),
                cycles = boundedInt("cycles", 100, 1..10_000),
                requestsPerCycle = boundedInt("requests-per-cycle", 64, 1..10_000),
                concurrency = boundedInt("concurrency", 8, 1..1_024),
                requestTimeoutMs = boundedLong("request-timeout-ms", 1_000, 1L..60_000L),
                timeoutFaultMs = boundedLong("timeout-fault-ms", 10, 1L..60_000L),
                runLabel = label,
            )
        }
    }
}

private data class RuntimeConnectorProcessSnapshot(
    val openFileDescriptors: Long,
    val javaThreads: Int,
    val processThreads: Long,
    val residentBytes: Long,
    val heapBytes: Long,
)

private class RuntimeConnectorProcessMeter {
    private val threadBean = ManagementFactory.getThreadMXBean()
    private val unixBean =
        ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.UnixOperatingSystemMXBean
    private val procTask = Paths.get("/proc/self/task")
    private val procStatus = Paths.get("/proc/self/status")

    fun snapshot(): RuntimeConnectorProcessSnapshot = RuntimeConnectorProcessSnapshot(
        openFileDescriptors = unixBean?.openFileDescriptorCount ?: -1,
        javaThreads = threadBean.threadCount,
        processThreads = countDirectoryEntries(procTask),
        residentBytes = readResidentBytes(procStatus),
        heapBytes = ManagementFactory.getMemoryMXBean().heapMemoryUsage.used,
    )

    private fun countDirectoryEntries(path: Path): Long {
        if (!Files.isDirectory(path)) return -1
        return Files.list(path).use { entries -> entries.count() }
    }

    private fun readResidentBytes(path: Path): Long {
        if (!Files.isRegularFile(path)) return -1
        val line = Files.readAllLines(path).firstOrNull { it.startsWith("VmRSS:") } ?: return -1
        val kibibytes = line.removePrefix("VmRSS:").trim().substringBefore(' ').toLongOrNull() ?: return -1
        return kibibytes * 1_024L
    }
}

private data class RuntimeConnectorStressTotals(
    val replies: Long = 0,
    val deadletters: Long = 0,
    val timeouts: Long = 0,
    val rejectedStarts: Long = 0,
) {
    operator fun plus(other: RuntimeConnectorStressTotals) = RuntimeConnectorStressTotals(
        replies = replies + other.replies,
        deadletters = deadletters + other.deadletters,
        timeouts = timeouts + other.timeouts,
        rejectedStarts = rejectedStarts + other.rejectedStarts,
    )
}

private suspend fun runLifecycleStressCycle(
    config: RuntimeConnectorLifecycleStressConfig,
    cycle: Int,
): RuntimeConnectorStressTotals {
    val invalid = runCatching {
        RuntimeHandle.open(
            startSpec = RuntimeStartSpec(
                systemName = "runtime-jni-stress-invalid",
                nodeId = "runtime-jni-stress-invalid-$cycle",
                routes = listOf(
                    RuntimeRouteSpec(
                        target = "svc.remote",
                        endpoints = listOf(RuntimeEndpointSpec("127.0.0.1", 19_399)),
                    ),
                ),
            ),
        )
    }
    invalid.getOrNull()?.close()
    check(invalid.isFailure) { "embedded remote route unexpectedly passed at cycle=$cycle" }

    val echoTarget = "svc.stress.echo"
    val silentTarget = "svc.stress.silent"
    val payload = "cycle-$cycle"
    val payloadBytes = payload.toByteArray()
    val client = RuntimeClient.startLocal(
        localTargets = listOf(echoTarget, silentTarget),
        systemName = "runtime-jni-stress",
        nodeId = "runtime-jni-stress-$cycle",
    )
    try {
        val startup = client.startupConfig()
        check(startup.wasDefaulted(CoakkaRuntimeStartupDefaultGroups.EXECUTION))
        check(startup.wasDefaulted(CoakkaRuntimeStartupDefaultGroups.CONNECTION))
        check(startup.connection.mode == RuntimeTcpConnectionMode.MULTIPLEXING)

        client.registerPayloadHandler(echoTarget) { request -> request }
        client.registerPayloadHandler(silentTarget) { null }

        val spans = RuntimeConnectorBenchmarkMath.workerSpans(
            total = config.requestsPerCycle,
            workers = config.concurrency,
        )
        coroutineScope {
            spans.mapIndexed { worker, span ->
                async(Dispatchers.Default) {
                    for (request in span) {
                        val reply = client.askRaw(
                            source = "stress.$worker",
                            target = echoTarget,
                            payloadUtf8 = payload,
                            timeoutMs = config.requestTimeoutMs,
                            operation = "lifecycle-stress-$request",
                        )
                        check(reply.payload.contentEquals(payloadBytes)) {
                            "reply payload mismatch at cycle=$cycle request=$request"
                        }
                    }
                }
            }.awaitAll()
        }

        val deadletter = runCatching {
            client.askRaw(
                source = "stress.fault",
                target = "svc.stress.missing",
                payloadUtf8 = payload,
                timeoutMs = config.requestTimeoutMs,
                operation = "missing-route",
            )
        }.exceptionOrNull()
        check(deadletter is DeadletterException) {
            "missing route did not produce Core deadletter at cycle=$cycle: $deadletter"
        }

        val timeout = runCatching {
            client.askRaw(
                source = "stress.fault",
                target = silentTarget,
                payloadUtf8 = payload,
                timeoutMs = config.timeoutFaultMs,
                operation = "silent-timeout",
            )
        }.exceptionOrNull()
        check(timeout is RuntimeAskTimeoutException) {
            "silent handler did not produce Core timeout at cycle=$cycle: $timeout"
        }

        val stats = client.snapshotStats()
        check(stats.pendingRequests == 0) { "pending request leaked at cycle=$cycle" }
        check(stats.matchedResponses == config.requestsPerCycle.toLong()) {
            "matched response mismatch at cycle=$cycle"
        }
        check(stats.matchedDeadletters == 1L) { "deadletter mismatch at cycle=$cycle" }
        check(stats.unhandledDeadletters == 0L) { "unhandled deadletter at cycle=$cycle" }
        return RuntimeConnectorStressTotals(
            replies = config.requestsPerCycle.toLong(),
            deadletters = 1,
            timeouts = 1,
            rejectedStarts = 1,
        )
    } finally {
        client.shutdown()
    }
}

private fun requireReturnedToBaseline(
    baseline: RuntimeConnectorProcessSnapshot,
    current: RuntimeConnectorProcessSnapshot,
    cycle: Int,
    concurrency: Int,
) {
    if (baseline.openFileDescriptors >= 0 && current.openFileDescriptors >= 0) {
        check(current.openFileDescriptors == baseline.openFileDescriptors) {
            "fd count did not return to baseline at cycle=$cycle: " +
                "baseline=${baseline.openFileDescriptors} current=${current.openFileDescriptors}"
        }
    }
    // Dispatchers.Default executes request coroutines while Dispatchers.IO owns
    // the blocking Core wait. Both process-global pools grow lazily and retain
    // workers across Runtime lifecycles, so exact equality is not a lifecycle
    // invariant. The load shape supplies the bound; per-cycle native/JNI leaks
    // still fail once they exceed the two dispatcher lanes.
    val maximumThreadGrowth = 2L * concurrency
    if (baseline.processThreads >= 0 && current.processThreads >= 0) {
        check(current.processThreads <= baseline.processThreads + maximumThreadGrowth) {
            "process thread count exceeded the load-owned bound at cycle=$cycle: " +
                "baseline=${baseline.processThreads} current=${current.processThreads} " +
                "maximum_growth=$maximumThreadGrowth"
        }
    }
    check(current.javaThreads.toLong() <= baseline.javaThreads + maximumThreadGrowth) {
        "Java thread count exceeded the load-owned bound at cycle=$cycle: " +
            "baseline=${baseline.javaThreads} current=${current.javaThreads} " +
            "maximum_growth=$maximumThreadGrowth"
    }
}

private fun formatStressSeconds(value: Double): String = String.format(Locale.ROOT, "%.3f", value)

fun main(args: Array<String>) = runBlocking {
    val config = RuntimeConnectorLifecycleStressConfig.parse(args)
    val meter = RuntimeConnectorProcessMeter()
    repeat(config.warmupCycles) { cycle -> runLifecycleStressCycle(config, -(cycle + 1)) }
    val baseline = meter.snapshot()
    var totals = RuntimeConnectorStressTotals()
    var maxOpenFileDescriptors = baseline.openFileDescriptors
    var maxJavaThreads = baseline.javaThreads
    var maxProcessThreads = baseline.processThreads
    var maxResidentBytes = baseline.residentBytes
    var maxHeapBytes = baseline.heapBytes
    val startedAt = System.nanoTime()

    repeat(config.cycles) { cycleIndex ->
        val cycle = cycleIndex + 1
        totals += runLifecycleStressCycle(config, cycle)
        val current = meter.snapshot()
        requireReturnedToBaseline(baseline, current, cycle, config.concurrency)
        maxOpenFileDescriptors = max(maxOpenFileDescriptors, current.openFileDescriptors)
        maxJavaThreads = max(maxJavaThreads, current.javaThreads)
        maxProcessThreads = max(maxProcessThreads, current.processThreads)
        maxResidentBytes = max(maxResidentBytes, current.residentBytes)
        maxHeapBytes = max(maxHeapBytes, current.heapBytes)
    }

    val ended = meter.snapshot()
    requireReturnedToBaseline(baseline, ended, config.cycles, config.concurrency)
    maxOpenFileDescriptors = max(maxOpenFileDescriptors, ended.openFileDescriptors)
    maxJavaThreads = max(maxJavaThreads, ended.javaThreads)
    maxProcessThreads = max(maxProcessThreads, ended.processThreads)
    maxResidentBytes = max(maxResidentBytes, ended.residentBytes)
    maxHeapBytes = max(maxHeapBytes, ended.heapBytes)
    val elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0
    println(
        buildString {
            append("runtime-jni-lifecycle-stress-result")
            append(" run_label=").append(config.runLabel)
            append(" warmup_cycles=").append(config.warmupCycles)
            append(" cycles=").append(config.cycles)
            append(" requests_per_cycle=").append(config.requestsPerCycle)
            append(" concurrency=").append(config.concurrency)
            append(" replies=").append(totals.replies)
            append(" deadletters=").append(totals.deadletters)
            append(" timeouts=").append(totals.timeouts)
            append(" rejected_starts=").append(totals.rejectedStarts)
            append(" elapsed_seconds=").append(formatStressSeconds(elapsedSeconds))
            append(" open_fds_baseline=").append(baseline.openFileDescriptors)
            append(" open_fds_end=").append(ended.openFileDescriptors)
            append(" open_fds_max_after_close=").append(maxOpenFileDescriptors)
            append(" java_threads_baseline=").append(baseline.javaThreads)
            append(" java_threads_end=").append(ended.javaThreads)
            append(" java_threads_max_after_close=").append(maxJavaThreads)
            append(" process_threads_baseline=").append(baseline.processThreads)
            append(" process_threads_end=").append(ended.processThreads)
            append(" process_threads_max_after_close=").append(maxProcessThreads)
            append(" rss_baseline_bytes=").append(baseline.residentBytes)
            append(" rss_end_bytes=").append(ended.residentBytes)
            append(" rss_max_after_close_bytes=").append(maxResidentBytes)
            append(" heap_baseline_bytes=").append(baseline.heapBytes)
            append(" heap_end_bytes=").append(ended.heapBytes)
            append(" heap_max_after_close_bytes=").append(maxHeapBytes)
            append(" connection_mode=auto")
            append(" effective_connection_mode=multiplexing")
            append(" java_version=").append(System.getProperty("java.version").replace(' ', '_'))
            append(" os_arch=").append(System.getProperty("os.arch"))
        },
    )
}
