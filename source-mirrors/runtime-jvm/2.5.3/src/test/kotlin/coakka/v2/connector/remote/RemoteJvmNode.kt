package coakka.v2.connector.remote

import coakka.v2.connector.RuntimeClient
import coakka.v2.connector.RuntimeEndpointFlags
import coakka.v2.connector.RuntimeEndpointSpec
import coakka.v2.connector.RuntimeNetworkConfig
import coakka.v2.connector.RuntimeRouteSpec
import coakka.v2.connector.RuntimeStartSpec
import coakka.v2.connector.protocol.ConnectorDeliveryHint
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.math.max

data class RemoteNodeConfig(
    val nodeName: String,
    val localTarget: String,
    val localPort: Int,
    val peerTarget: String,
    val peerHost: String,
    val peerPort: Int,
)

data class RemoteNodeRunResult(
    val nodeName: String,
    val requests: Int,
    val concurrency: Int,
    val seconds: Double,
    val throughputOpsPerSec: Double,
    val avgLatencyUs: Double,
    val p50LatencyUs: Double,
    val p95LatencyUs: Double,
    val p99LatencyUs: Double,
)

object RemoteJvmNode {
    fun run(runtimeLibPath: String, config: RemoteNodeConfig) = runBlocking {
        val client = RuntimeClient.start(
            runtimeLibPath = runtimeLibPath,
            startSpec = RuntimeStartSpec(
                systemName = "remote-jvm-${config.nodeName}",
                nodeId = "node-${config.nodeName}",
                separateDeliveredRequestLane = true,
                network = RuntimeNetworkConfig.networkNode(
                    bindHost = "127.0.0.1",
                    bindPort = config.localPort,
                    advertiseHost = "127.0.0.1",
                ),
                routes = listOf(
                    RuntimeRouteSpec(
                        target = config.localTarget,
                        endpoints = listOf(
                            RuntimeEndpointSpec(
                                host = "127.0.0.1",
                                port = config.localPort,
                                flags = RuntimeEndpointFlags.LOCAL,
                            ),
                        ),
                    ),
                    RuntimeRouteSpec(
                        target = config.peerTarget,
                        endpoints = listOf(
                            RuntimeEndpointSpec(
                                host = config.peerHost,
                                port = config.peerPort,
                                flags = RuntimeEndpointFlags.NONE,
                            ),
                        ),
                    ),
                ),
            ),
        )

        try {
            client.registerHandler(config.localTarget) { request ->
                RuntimeClient.replyTo(
                    request = request,
                    source = config.localTarget,
                    payloadUtf8 = "reply-from-${config.nodeName}:${request.messageId}",
                )
            }

            println("READY node=${config.nodeName} localTarget=${config.localTarget} localPort=${config.localPort}")

            val reader = BufferedReader(InputStreamReader(System.`in`))
            while (true) {
                val line = reader.readLine() ?: break
                val trimmed = line.trim()
                if (trimmed.isEmpty()) {
                    continue
                }
                if (trimmed == "STOP") {
                    println("STOPPED node=${config.nodeName}")
                    break
                }
                if (trimmed.startsWith("RUN ")) {
                    val parts = trimmed.split(' ')
                    check(parts.size >= 3) { "invalid RUN command: $trimmed" }
                    val requests = parts[1].toInt()
                    val concurrency = parts[2].toInt()
                    val result = runAskBurst(client, config, requests, concurrency)
                    println(
                        "DONE node=${result.nodeName} requests=${result.requests} concurrency=${result.concurrency} seconds=${"%.3f".format(result.seconds)} " +
                            "throughput_ops_per_sec=${"%.1f".format(result.throughputOpsPerSec)} avg_us=${"%.1f".format(result.avgLatencyUs)} " +
                            "p50_us=${"%.1f".format(result.p50LatencyUs)} p95_us=${"%.1f".format(result.p95LatencyUs)} p99_us=${"%.1f".format(result.p99LatencyUs)}",
                    )
                    continue
                }
                error("unknown command for ${config.nodeName}: $trimmed")
            }
        } finally {
            client.shutdown()
        }
    }

    private suspend fun runAskBurst(
        client: RuntimeClient,
        config: RemoteNodeConfig,
        requests: Int,
        concurrency: Int,
    ): RemoteNodeRunResult = coroutineScope {
        val latencies = LongArray(requests)
        val workers = max(1, concurrency)
        val perWorker = requests / workers
        val remainder = requests % workers
        var base = 0
        val startedAt = System.nanoTime()

        val jobs = (0 until workers).map { workerId ->
            val span = perWorker + if (workerId < remainder) 1 else 0
            val startIndex = base
            base += span

            async {
                repeat(span) { offset ->
                    val index = startIndex + offset
                    val singleStart = System.nanoTime()
                    val response = client.ask(
                        source = "${config.localTarget}.worker.$workerId",
                        target = config.peerTarget,
                        payloadUtf8 = "remote-${config.nodeName}-$index",
                        timeoutMs = 5_000,
                        operation = "remote_jvm_exchange",
                        deliveryHint = ConnectorDeliveryHint.REQUIRE_REMOTE,
                    )
                    check(response.source == config.peerTarget) {
                        "unexpected reply source=${response.source} for node=${config.nodeName}"
                    }
                    latencies[index] = System.nanoTime() - singleStart
                }
            }
        }
        jobs.awaitAll()

        val elapsedNs = System.nanoTime() - startedAt
        val sorted = latencies.copyOf().apply { sort() }
        val totalLatencyNs = latencies.fold(0L, Long::plus)

        fun percentile(p: Double): Double {
            val idx = ((sorted.size - 1) * p).toInt()
            return sorted[idx] / 1_000.0
        }

        val seconds = elapsedNs / 1_000_000_000.0
        RemoteNodeRunResult(
            nodeName = config.nodeName,
            requests = requests,
            concurrency = workers,
            seconds = seconds,
            throughputOpsPerSec = requests / seconds,
            avgLatencyUs = (totalLatencyNs / requests.toDouble()) / 1_000,
            p50LatencyUs = percentile(0.50),
            p95LatencyUs = percentile(0.95),
            p99LatencyUs = percentile(0.99),
        )
    }
}

fun main(args: Array<String>) {
    require(args.size == 6) {
        "usage: <nodeName> <localTarget> <localPort> <peerTarget> <peerHost> <peerPort>"
    }
    val runtimeLibPath = System.getProperty("coakka.runtime.lib")
        ?: error("missing -Dcoakka.runtime.lib=/abs/path/to/libcoakka_runtime_v2.so")
    val config = RemoteNodeConfig(
        nodeName = args[0],
        localTarget = args[1],
        localPort = args[2].toInt(),
        peerTarget = args[3],
        peerHost = args[4],
        peerPort = args[5].toInt(),
    )
    RemoteJvmNode.run(runtimeLibPath, config)
}
