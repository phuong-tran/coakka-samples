package benchmark

import coakka.http.Handler as CoAkkaHandler
import coakka.http.Header as CoAkkaHeader
import coakka.http.Headers as CoAkkaHeaders
import coakka.http.Responses
import coakka.http.ServiceBuilder
import coakka.http.CpuPolicy
import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.util.Callback
import org.eclipse.jetty.util.thread.QueuedThreadPool
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

private val BODY = "0123456789abcdef0123456789abcdef".toByteArray(StandardCharsets.US_ASCII)

private interface RunningServer {
    fun close()
}

fun main(arguments: Array<String>) {
    require(arguments.size == 2) { "usage: benchmark.MainKt <coakka|jetty> <port>" }
    val port = arguments[1].toInt()
    require(port in 1..65_535) { "port is invalid" }
    var server: RunningServer? = null
    val started = CountDownLatch(1)
    val stopped = CountDownLatch(1)
    val closing = AtomicBoolean()
    Runtime.getRuntime().addShutdownHook(
        Thread({
            if (closing.compareAndSet(false, true)) {
                try {
                    // Signals may arrive as soon as the socket accepts, before
                    // main finishes its cold observation. The latch publishes
                    // the owner safely and prevents shutdown from racing start.
                    started.await()
                    server?.let {
                        it.close()
                        println("benchmark-shutdown=pass")
                    }
                } finally {
                    stopped.countDown()
                }
            }
        }, "benchmark-shutdown"),
    )
    try {
        server = when (arguments[0]) {
            "coakka" -> startCoakka(port)
            "jetty" -> startJetty(port)
            else -> error("unsupported Kotlin/JVM lane: ${arguments[0]}")
        }
    } finally {
        started.countDown()
    }
    stopped.await()
}

private fun startCoakka(port: Int): RunningServer {
    val policy = when (System.getenv("COAKKA_BENCH_CPU_POLICY")) {
        "single" -> CpuPolicy.SINGLE
        "auto" -> CpuPolicy.AUTO
        else -> error("explicit benchmark CPU intent required")
    }
    val response = Responses.bytes(
        BODY,
        headers = CoAkkaHeaders(listOf(CoAkkaHeader("content-type", "application/octet-stream"))),
    )
    val service = ServiceBuilder()
        .listen("127.0.0.1", port)
        .cpu(policy)
        .get("/fixed", CoAkkaHandler { response })
        .start()
    // Startup-only projection of native observations, never a mirrored default
    // table or a per-request monitoring workload.
    try {
        val info = service.runtimeInfo()
        val cpu = checkNotNull(info.cpu)
        val limits = service.effectiveLimits()
        println("coakka-runtime-info={\"cpu\":{\"requestedPolicy\":\"${cpu.requestedPolicy}\"," +
            "\"placement\":\"${cpu.placement}\",\"selectedCpuCount\":${cpu.selectedCpuCount}," +
            "\"selectedCpuIds\":${cpu.selectedCpuIds}},\"execution\":{" +
            "\"observed\":${info.execution.observed},\"configuredEventLoops\":${info.execution.configuredEventLoops}," +
            "\"activeEventLoops\":${info.execution.activeEventLoops}}," +
            "\"requestNotificationBatchSize\":${info.requestNotificationBatchSize}," +
            "\"terminalNotificationBatchSize\":${info.terminalNotificationBatchSize}," +
            "\"ioUringEffective\":${info.ioUringEffective},\"limits\":{" +
            "\"headerTimeoutMillis\":${limits.headerTimeoutMillis},\"bodyTimeoutMillis\":${limits.bodyTimeoutMillis}," +
            "\"handlerTimeoutMillis\":${limits.appHostTimeoutMillis},\"idleTimeoutMillis\":${limits.idleTimeoutMillis}," +
            "\"keepAliveTimeoutMillis\":${limits.keepAliveTimeoutMillis}}}")
    } catch (failure: Throwable) {
        try { service.close() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
        throw failure
    }
    return object : RunningServer {
        override fun close() = service.close()
    }
}

private fun startJetty(port: Int): RunningServer {
    val threads = QueuedThreadPool(64, 8).apply { name = "jetty-benchmark" }
    val server = Server(threads)
    server.stopTimeout = 5_000
    // Keep one acceptor and scale selectors only with the declared CPU budget.
    val selectors = Runtime.getRuntime().availableProcessors().coerceIn(1, 2)
    val connector = ServerConnector(server, 1, selectors).apply {
        host = "127.0.0.1"
        this.port = port
        acceptQueueSize = 256
    }
    server.addConnector(connector)
    // This handler does no blocking work; declare that contract so Jetty can
    // execute its normal nonblocking path instead of forcing worker dispatch.
    server.handler = object : Handler.Abstract.NonBlocking() {
        override fun handle(request: Request, response: Response, callback: Callback): Boolean {
            if (request.httpURI.path != "/fixed" || request.method != "GET") {
                response.status = 404
                callback.succeeded()
                return true
            }
            response.status = 200
            response.headers.put(HttpHeader.CONTENT_TYPE, "application/octet-stream")
            response.headers.put(HttpHeader.CONTENT_LENGTH, BODY.size.toLong())
            response.write(true, ByteBuffer.wrap(BODY), callback)
            return true
        }
    }
    server.start()
    return object : RunningServer {
        override fun close() {
            server.stop()
            server.join()
        }
    }
}
