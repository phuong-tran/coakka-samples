package benchmark.frameworks

import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.netty.http.server.HttpServer
import reactor.netty.resources.LoopResources
import io.netty.channel.epoll.Epoll
import java.time.Duration

/** Spring WebFlux's real routing/codec path, not a bare transport comparison. */
fun main(args: Array<String>) {
    val cpus = cpuBudget()
    check(Epoll.isAvailable()) { "Expected native transport on this Pi" }
    val route = RouterFunctions.route().GET("/fixed") {
        ServerResponse.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(body.size.toLong()).bodyValue(body)
    }.build()
    val loops = LoopResources.create("benchmark-webflux", 1, cpus, true)
    val server = HttpServer.create().host("127.0.0.1").port(args.single().toInt())
        .runOn(loops, true).handle(ReactorHttpHandlerAdapter(RouterFunctions.toHttpHandler(route)))
        .bindNow(Duration.ofSeconds(30))
    awaitStop("webflux", "native-transport;selectors=1;workers=$cpus;functional-router") {
        server.disposeNow(Duration.ofSeconds(10))
        loops.disposeLater(Duration.ZERO, Duration.ofSeconds(5)).block(Duration.ofSeconds(10))
    }
}
