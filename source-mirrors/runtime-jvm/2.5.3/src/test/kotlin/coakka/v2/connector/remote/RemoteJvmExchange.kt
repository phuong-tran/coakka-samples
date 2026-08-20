package coakka.v2.connector.remote

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.max

private data class RemoteExchangeSummary(
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

private class ChildJvm(
    private val name: String,
    classpath: String,
    runtimeLibPath: String,
    localTarget: String,
    localPort: Int,
    peerTarget: String,
    peerPort: Int,
) {
    private val outputLines = LinkedBlockingQueue<String>()
    private val process: Process

    init {
        val javaBin = File(System.getProperty("java.home"), "bin/java").absolutePath
        process = ProcessBuilder(
            javaBin,
            "-Dcoakka.runtime.lib=$runtimeLibPath",
            "-cp",
            classpath,
            "coakka.v2.connector.remote.RemoteJvmNodeKt",
            name,
            localTarget,
            localPort.toString(),
            peerTarget,
            "127.0.0.1",
            peerPort.toString(),
        )
            .redirectErrorStream(true)
            .start()

        Thread {
            BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                lines.forEach { line ->
                    println("[$name] $line")
                    outputLines.offer(line)
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun waitFor(prefix: String, timeoutMs: Long): String {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (true) {
            val remainingNs = deadline - System.nanoTime()
            check(remainingNs > 0) { "timed out waiting for $prefix from $name" }
            val line = outputLines.poll(remainingNs, TimeUnit.NANOSECONDS)
            if (line != null && line.startsWith(prefix)) {
                return line
            }
            check(process.isAlive) { "$name exited early while waiting for $prefix" }
        }
    }

    fun sendLine(line: String) {
        process.outputStream.write((line + "\n").toByteArray())
        process.outputStream.flush()
    }

    fun stop(timeoutMs: Long = 5_000) {
        if (!process.isAlive) {
            return
        }
        sendLine("STOP")
        process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (process.isAlive) {
            process.destroyForcibly()
        }
        check(process.exitValue() == 0) { "$name exited with rc=${process.exitValue()}" }
    }
}

object RemoteJvmExchange {
    fun run(
        runtimeLibPath: String,
        requestsPerNode: Int = 100,
        concurrencyLevels: List<Int> = listOf(1, 4, 8, 16, 32),
    ) {
        val classpath = System.getProperty("java.class.path")
        val portA = reserveLoopbackPort()
        val portB = reserveLoopbackPort()
        val nodeA = ChildJvm(
            name = "node-a",
            classpath = classpath,
            runtimeLibPath = runtimeLibPath,
            localTarget = "svc.a",
            localPort = portA,
            peerTarget = "svc.b",
            peerPort = portB,
        )
        val nodeB = ChildJvm(
            name = "node-b",
            classpath = classpath,
            runtimeLibPath = runtimeLibPath,
            localTarget = "svc.b",
            localPort = portB,
            peerTarget = "svc.a",
            peerPort = portA,
        )

        try {
            nodeA.waitFor("READY ", 15_000)
            nodeB.waitFor("READY ", 15_000)

            for (concurrency in concurrencyLevels) {
                val startedAt = System.nanoTime()
                nodeA.sendLine("RUN $requestsPerNode $concurrency")
                nodeB.sendLine("RUN $requestsPerNode $concurrency")
                val doneA = parseSummary(nodeA.waitFor("DONE ", 30_000))
                val doneB = parseSummary(nodeB.waitFor("DONE ", 30_000))
                val elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0

                println(
                    "remote-jvm-exchange: requests_per_node=$requestsPerNode concurrency=$concurrency elapsed_seconds=${"%.3f".format(elapsedSeconds)}",
                )
                println(
                    "remote-jvm-exchange: node=${doneA.nodeName} throughput_ops_per_sec=${"%.1f".format(doneA.throughputOpsPerSec)} avg_us=${"%.1f".format(doneA.avgLatencyUs)} p50_us=${"%.1f".format(doneA.p50LatencyUs)} p95_us=${"%.1f".format(doneA.p95LatencyUs)} p99_us=${"%.1f".format(doneA.p99LatencyUs)}",
                )
                println(
                    "remote-jvm-exchange: node=${doneB.nodeName} throughput_ops_per_sec=${"%.1f".format(doneB.throughputOpsPerSec)} avg_us=${"%.1f".format(doneB.avgLatencyUs)} p50_us=${"%.1f".format(doneB.p50LatencyUs)} p95_us=${"%.1f".format(doneB.p95LatencyUs)} p99_us=${"%.1f".format(doneB.p99LatencyUs)}",
                )
            }
        } finally {
            nodeA.stop()
            nodeB.stop()
        }
    }

    private fun parseSummary(line: String): RemoteExchangeSummary {
        val values = mutableMapOf<String, String>()
        line.split(' ').forEach { part ->
            val separator = part.indexOf('=')
            if (separator > 0) {
                values[part.substring(0, separator)] = part.substring(separator + 1)
            }
        }
        return RemoteExchangeSummary(
            nodeName = values.getValue("node"),
            requests = values.getValue("requests").toInt(),
            concurrency = max(1, values.getValue("concurrency").toInt()),
            seconds = values.getValue("seconds").toDouble(),
            throughputOpsPerSec = values.getValue("throughput_ops_per_sec").toDouble(),
            avgLatencyUs = values.getValue("avg_us").toDouble(),
            p50LatencyUs = values.getValue("p50_us").toDouble(),
            p95LatencyUs = values.getValue("p95_us").toDouble(),
            p99LatencyUs = values.getValue("p99_us").toDouble(),
        )
    }

    private fun reserveLoopbackPort(): Int =
        ServerSocket(0).use { server ->
            server.reuseAddress = true
            server.localPort
        }
}

fun main(args: Array<String>) {
    val runtimeLibPath = System.getProperty("coakka.runtime.lib")
        ?: error("missing -Dcoakka.runtime.lib=/abs/path/to/libcoakka_runtime_v2.so")
    val requestsPerNode = args.getOrNull(0)?.toIntOrNull() ?: 100
    val concurrencyLevels = args.getOrNull(1)
        ?.split(',')
        ?.mapNotNull(String::toIntOrNull)
        ?.filter { it > 0 }
        ?.ifEmpty { null }
        ?: listOf(1, 4, 8, 16, 32)
    RemoteJvmExchange.run(runtimeLibPath, requestsPerNode, concurrencyLevels)
}
