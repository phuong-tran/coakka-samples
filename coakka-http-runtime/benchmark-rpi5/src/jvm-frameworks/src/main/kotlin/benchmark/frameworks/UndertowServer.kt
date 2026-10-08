package benchmark.frameworks

import io.undertow.Undertow
import io.undertow.server.handlers.GracefulShutdownHandler
import io.undertow.util.Headers
import io.undertow.util.Methods
import java.nio.ByteBuffer

/** Real nonblocking Undertow routing; no blocking servlet dispatch is added. */
fun main(args: Array<String>) {
    val cpus = cpuBudget()
    val handler = GracefulShutdownHandler { exchange ->
        if (exchange.requestMethod != Methods.GET || exchange.requestPath != "/fixed") {
            exchange.statusCode = 404
            exchange.endExchange()
        } else {
            exchange.responseHeaders.put(Headers.CONTENT_TYPE, "application/octet-stream")
            exchange.responseContentLength = body.size.toLong()
            exchange.responseSender.send(ByteBuffer.wrap(body))
        }
    }
    val server = Undertow.builder().addHttpListener(args.single().toInt(), "127.0.0.1")
        .setIoThreads(cpus).setWorkerThreads(cpus * 8).setHandler(handler).build()
    server.start()
    awaitStop("undertow", "io=$cpus;workers=${cpus * 8};nonblocking-handler") {
        handler.shutdown()
        check(handler.awaitShutdown(5_000)) { "Undertow drain timeout" }
        server.stop()
    }
}
