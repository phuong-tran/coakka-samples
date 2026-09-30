package benchmark

import coakka.http.Handler as CoAkkaHandler
import coakka.http.Header as CoAkkaHeader
import coakka.http.Headers as CoAkkaHeaders
import coakka.http.Responses
import coakka.http.ServiceBuilder
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpUtil
import io.netty.handler.codec.http.HttpVersion
import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.util.Callback
import org.eclipse.jetty.util.thread.QueuedThreadPool
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val BODY = "0123456789abcdef0123456789abcdef".toByteArray(StandardCharsets.US_ASCII)

private interface RunningServer {
    fun close()
}

fun main(arguments: Array<String>) {
    require(arguments.size == 2) { "usage: benchmark.MainKt <coakka|netty|jetty> <port>" }
    val port = arguments[1].toInt()
    require(port in 1..65_535) { "port is invalid" }
    val server = when (arguments[0]) {
        "coakka" -> startCoakka(port)
        "netty" -> startNetty(port)
        "jetty" -> startJetty(port)
        else -> error("unsupported Kotlin/JVM lane: ${arguments[0]}")
    }
    val stopped = CountDownLatch(1)
    val closing = AtomicBoolean()
    Runtime.getRuntime().addShutdownHook(
        Thread({
            if (closing.compareAndSet(false, true)) {
                try {
                    server.close()
                } finally {
                    stopped.countDown()
                }
            }
        }, "benchmark-shutdown"),
    )
    stopped.await()
}

private fun startCoakka(port: Int): RunningServer {
    val response = Responses.bytes(
        BODY,
        headers = CoAkkaHeaders(listOf(CoAkkaHeader("content-type", "application/octet-stream"))),
    )
    val service = ServiceBuilder()
        .listen("127.0.0.1", port)
        .concurrency(3)
        .get("/fixed", CoAkkaHandler { response })
        .start()
    return object : RunningServer {
        override fun close() = service.close()
    }
}

private fun startNetty(port: Int): RunningServer {
    val boss: EventLoopGroup = NioEventLoopGroup(1)
    val workers: EventLoopGroup = NioEventLoopGroup(3)
    val listener: Channel = try {
        ServerBootstrap()
            .group(boss, workers)
            .channel(NioServerSocketChannel::class.java)
            .option(ChannelOption.SO_BACKLOG, 256)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(channel: SocketChannel) {
                    channel.pipeline()
                        .addLast(HttpServerCodec())
                        .addLast(HttpObjectAggregator(1 shl 20))
                        .addLast(object : SimpleChannelInboundHandler<FullHttpRequest>() {
                            override fun channelRead0(context: ChannelHandlerContext, request: FullHttpRequest) {
                                val response = if (request.method().name() == "GET" && request.uri() == "/fixed") {
                                    DefaultFullHttpResponse(
                                        HttpVersion.HTTP_1_1,
                                        HttpResponseStatus.OK,
                                        Unpooled.wrappedBuffer(BODY),
                                    )
                                } else {
                                    DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND)
                                }
                                response.headers()[HttpHeaderNames.CONTENT_TYPE] = "application/octet-stream"
                                HttpUtil.setContentLength(response, response.content().readableBytes().toLong())
                                HttpUtil.setKeepAlive(response, HttpUtil.isKeepAlive(request))
                                context.writeAndFlush(response)
                            }
                        })
                }
            })
            .bind("127.0.0.1", port)
            .sync()
            .channel()
    } catch (error: Throwable) {
        workers.shutdownGracefully().syncUninterruptibly()
        boss.shutdownGracefully().syncUninterruptibly()
        throw error
    }
    return object : RunningServer {
        override fun close() {
            listener.close().syncUninterruptibly()
            workers.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly()
            boss.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly()
        }
    }
}

private fun startJetty(port: Int): RunningServer {
    val threads = QueuedThreadPool(8, 8).apply { name = "jetty-benchmark" }
    val server = Server(threads)
    val connector = ServerConnector(server, 1, 1).apply {
        host = "127.0.0.1"
        this.port = port
        acceptQueueSize = 256
    }
    server.addConnector(connector)
    server.handler = object : Handler.Abstract() {
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
