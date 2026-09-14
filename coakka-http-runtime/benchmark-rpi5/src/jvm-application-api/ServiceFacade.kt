package coakka.http.server

import coakka.http.connector.HostInlineHttpService
import coakka.http.connector.HostInlineHttpServiceBuilder
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

class Request internal constructor(
    private val request: FullHttpRequest,
    private val encodedPathParameters: Map<String, String>,
) {
    val method: String
        get() = request.method().name()

    val target: String
        get() = request.uri()

    val pathParameters: Map<String, String>
        get() = encodedPathParameters

    fun bytes(): ByteArray {
        val content = request.content()
        val result = ByteArray(content.readableBytes())
        content.getBytes(content.readerIndex(), result)
        return result
    }
}

class Response internal constructor(internal val native: FullHttpResponse)

object Responses {
    @JvmStatic
    fun bytes(body: ByteArray): Response = bytes(200, body)

    @JvmStatic
    @JvmOverloads
    fun bytes(status: Int, body: ByteArray, contentType: String? = null): Response {
        require(status in 200..599) { "response status code is invalid" }
        val response = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.valueOf(status),
            Unpooled.wrappedBuffer(body.copyOf()),
        )
        if (contentType != null) {
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType)
        }
        return Response(response)
    }

    @JvmStatic
    fun text(value: String): Response = bytes(
        200,
        value.toByteArray(StandardCharsets.UTF_8),
        "text/plain; charset=utf-8",
    )
}

fun interface Handler {
    fun handle(request: Request): Response
}

class ServiceBuilder {
    private val delegate = HostInlineHttpServiceBuilder()

    fun listen(host: String, port: Int): ServiceBuilder = apply {
        delegate.listen(host, port)
    }

    @JvmOverloads
    fun concurrency(eventLoops: Int, maxActiveHandlers: Int = 256): ServiceBuilder = apply {
        delegate.concurrency(eventLoops, maxActiveHandlers)
    }

    @JvmOverloads
    fun bounds(
        maxRequestBody: Int = 1_048_576,
        maxRequestTarget: Int = 8_192,
        maxHeaderBytes: Int = 1_048_576,
        maxResponseBytes: Int = 1_048_576,
    ): ServiceBuilder = apply {
        delegate.bounds(
            maxRequestBody,
            maxRequestTarget,
            maxHeaderBytes,
            maxResponseBytes,
        )
    }

    fun route(method: String, pattern: String, handler: Handler): ServiceBuilder = apply {
        delegate.route(method, pattern) { request, captures ->
            handler.handle(Request(request, captures)).native
        }
    }

    fun get(pattern: String, handler: Handler): ServiceBuilder = route("GET", pattern, handler)
    fun post(pattern: String, handler: Handler): ServiceBuilder = route("POST", pattern, handler)
    fun put(pattern: String, handler: Handler): ServiceBuilder = route("PUT", pattern, handler)
    fun patch(pattern: String, handler: Handler): ServiceBuilder = route("PATCH", pattern, handler)
    fun delete(pattern: String, handler: Handler): ServiceBuilder = route("DELETE", pattern, handler)

    fun start(): Service {
        val core = System.getenv("COAKKA_HTTP_APPLICATION_CORE")
            ?: error("coakka-http-runtime-core is unavailable")
        return Service(delegate.build(Paths.get(core).toAbsolutePath()).start())
    }
}

class Service internal constructor(
    private val delegate: HostInlineHttpService,
) : AutoCloseable {
    val port: Int
        get() = delegate.boundPort

    fun snapshot(): Snapshot {
        val value = delegate.snapshot()
        return Snapshot(
            value.running,
            value.dispatched,
            value.rejected,
            value.failed,
            value.pending,
            value.maxActiveHandlers,
        )
    }

    fun pollError(): Throwable? = delegate.pollError()

    override fun close() = delegate.close(5, TimeUnit.SECONDS)
}

data class Snapshot(
    val running: Boolean,
    val dispatched: Long,
    val rejected: Long,
    val failed: Long,
    val pending: Int,
    val maxActiveHandlers: Int,
)
