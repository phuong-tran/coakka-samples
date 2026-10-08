package benchmark.frameworks

import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerOptions
import io.vertx.ext.web.Router
import java.util.concurrent.TimeUnit

/** One Web Router verticle per assigned CPU, sharing the framework listener. */
fun main(args: Array<String>) {
    val cpus = cpuBudget()
    val port = args.single().toInt()
    val vertx = Vertx.vertx(VertxOptions().setEventLoopPoolSize(cpus).setPreferNativeTransport(true))
    check(vertx.isNativeTransportEnabled) { "Expected native transport on this Pi" }
    vertx.deployVerticle({ object : AbstractVerticle() {
        override fun start(start: Promise<Void>) {
            val fixed = Buffer.buffer(body)
            val router = Router.router(vertx)
            router.get("/fixed").handler { context ->
                context.response().putHeader("content-type", "application/octet-stream")
                    .putHeader("content-length", body.size.toString()).end(fixed)
            }
            vertx.createHttpServer(HttpServerOptions().setTcpNoDelay(true).setAcceptBacklog(256))
                .requestHandler(router).listen(port, "127.0.0.1").onComplete { result ->
                    if (result.succeeded()) start.complete() else start.fail(result.cause())
                }
        }
    } }, DeploymentOptions().setInstances(cpus)).toCompletionStage().toCompletableFuture()
        .get(30, TimeUnit.SECONDS)
    awaitStop("vertx", "native-transport;event-loops=$cpus;web-router-verticles=$cpus") {
        vertx.close().toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS)
    }
}
