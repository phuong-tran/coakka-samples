@file:JvmName("MainKt")

package sample

import coakka.http.FileAuthority
import coakka.http.BodyPolicy
import coakka.http.Handler
import coakka.http.Header
import coakka.http.Headers
import coakka.http.IoBackend
import coakka.http.Limits
import coakka.http.MonitorCategory
import coakka.http.MonitorCollection
import coakka.http.MonitorOptions
import coakka.http.OutboundEndpoint
import coakka.http.OutboundRequest
import coakka.http.OutboundStrategy
import coakka.http.OutboundTarget
import coakka.http.Request
import coakka.http.Response
import coakka.http.ResponseStreamProducer
import coakka.http.Responses
import coakka.http.RouteRebind
import coakka.http.RoutePublication
import coakka.http.RouteControlCode
import coakka.http.RuntimeRoute
import coakka.http.ServerSentEvent
import coakka.http.ServerSentEventsProducer
import coakka.http.Service
import coakka.http.ServiceBuilder
import coakka.http.ServiceWebSocketHandler
import coakka.http.StaticMount
import coakka.http.WebSocketEventKind
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch

private const val OUTBOUND_TARGET = "sample.upstream"

/** Deliberate application choices accepted by the runnable sample. */
private data class Options(
    val smoke: Boolean,
    val assets: File,
    val securityFixtures: File?,
    val ioUring: Boolean,
)

/** Parse only the options needed to run this sample on any supported host. */
private fun parseOptions(arguments: Array<String>): Options {
    val assetIndex = arguments.indexOf("--assets")
    require(assetIndex < 0 || assetIndex + 1 < arguments.size) { "--assets requires a directory" }
    val assets = File(if (assetIndex >= 0) arguments[assetIndex + 1] else "../assets")
        .toPath().toAbsolutePath().normalize().toFile()
    require(assets.isDirectory) { "asset root is not a directory: $assets" }
    val securityIndex = arguments.indexOf("--security-smoke")
    val securityFixtures = if (securityIndex >= 0) {
        require(securityIndex + 1 < arguments.size) { "--security-smoke requires a directory" }
        File(arguments[securityIndex + 1]).toPath().toAbsolutePath().normalize().toFile()
    } else {
        null
    }
    return Options(
        arguments.contains("--smoke"),
        assets,
        securityFixtures,
        arguments.contains("--io-uring"),
    )
}

/** Return two chunks and one final trailer under the bounded writer contract. */
private fun streamingResponse(): Response = Responses.stream(
    ResponseStreamProducer { writer ->
        writer.write("stream-".toByteArray())
        writer.write("ready".toByteArray())
        Headers.of(Header("x-stream-end", "done"))
    },
    headers = Headers.of(Header("content-type", "text/plain")),
)

/** Return one typed event and complete the finite SSE response. */
private fun serverSentEvents(): Response = Responses.serverSentEvents(
    ServerSentEventsProducer { writer ->
        writer.write(
            ServerSentEvent(
                "ready".toByteArray(),
                eventType = "state",
                id = "1",
                retryMillis = 1_500,
            ),
        )
    },
)

/** Echo WebSocket text while the service owns callback ordering and lifetime. */
private fun webSocket(): Response = Responses.webSocket(
    ServiceWebSocketHandler { session, event ->
        when (event.kind) {
            WebSocketEventKind.OPEN -> session.sendText("welcome")
            WebSocketEventKind.TEXT -> session.sendText(String(event.bytes(), StandardCharsets.UTF_8))
            else -> Unit
        }
    },
    "coakka.sample",
)

/** Read copied path, ordered query, and indexed header projections. */
private fun helloResponse(request: Request): Response {
    val name = String(request.pathParameters.first().encodedValue(), StandardCharsets.UTF_8)
    val firstQuery = request.queryParameters.firstOrNull()
    val title = if (
        firstQuery != null &&
        String(firstQuery.encodedKey(), StandardCharsets.US_ASCII) == "title"
    ) {
        firstQuery.encodedValue()?.let { String(it, StandardCharsets.UTF_8) } ?: "hello"
    } else {
        "hello"
    }
    return Responses.text("$title $name from ${request.headers["x-sample-caller"].orEmpty()}")
}

/** Declare all resources before listener publication and start the service. */
private fun createService(assets: File, upstreamPort: Int, ioUring: Boolean): Service {
    val endpoint = OutboundEndpoint(
        nodeId = "loopback",
        connectHost = "127.0.0.1",
        connectPort = upstreamPort,
        httpAuthority = "127.0.0.1:$upstreamPort",
    )
    val builder = ServiceBuilder()
        .concurrency(2)
        .limits(Limits(maxHandlerBindings = 16))
        .monitor(
            MonitorOptions(
                collection = MonitorCollection.AGGREGATES_AND_EVENTS,
                eventCapacity = 32,
                maxEventsPerRead = 8,
                categories = MonitorCategory.LIFECYCLE.bit or MonitorCategory.EXCHANGE.bit,
                signalReserved = true,
            ),
        )
        .staticMount(
            StaticMount(
                "/app",
                assets.path,
                indexFile = "index.html",
                spaFallbackFile = "index.html",
            ),
        )
        .fileAuthority(FileAuthority(82, assets.path, 2, 1L shl 20))
        .outboundTarget(
            OutboundTarget(
                OUTBOUND_TARGET,
                1,
                OutboundStrategy.SINGLE_OWNER,
                listOf(endpoint),
            ),
        )
        .get("/hello/{name}", Handler(::helloResponse))
        .post("/echo", Handler { request -> Responses.bytes(request.bytes(), status = 201) })
        .postStream("/upload", Handler { request -> Responses.bytes(request.body.readBytes()) })
        .get("/stream", Handler { streamingResponse() })
        .get("/events", Handler { serverSentEvents() })
        .webSocket("/socket", Handler { webSocket() })
        .get("/download", Handler {
            Responses.file(
                82,
                "/sample.txt",
                headers = Headers.of(Header("content-type", "text/plain")),
            )
        })
        .get("/version", Handler { Responses.text("v1") })
    if (ioUring) builder.ioBackend(IoBackend.IO_URING)
    return builder.start()
}

/** Activate a prepared callback without replacing the structural route map. */
private fun activateReplacement(service: Service) {
    service.prepareHandler(9, Handler { Responses.text("v2", status = 201) })
    val outcome = service.rebindHandler(RouteRebind(1, 1, 8, 1, 9))
    check(outcome.code == RouteControlCode.APPLIED && outcome.changed) {
        "handler replacement was rejected: ${outcome.code}"
    }
}

/** Replace a complete structural generation on an isolated service. */
private fun demonstrateRoutePublication() {
    val service = ServiceBuilder()
        .limits(Limits(maxHandlerBindings = 3))
        .get("/old", Handler { Responses.text("old-generation") })
        .start()
    try {
        service.prepareHandler(2, Handler { Responses.text("new-generation", status = 201) })
        val publication = RoutePublication(
            activationId = 1,
            expectedRouteGeneration = 1,
            expectedBindingChangeSequence = 1,
            routes = listOf(
                RuntimeRoute(
                    id = 2,
                    method = "GET",
                    path = "/published",
                    handlerBindingId = 2,
                    bodyPolicy = BodyPolicy(
                        enabled = true,
                        acceptAbsent = true,
                        acceptOther = true,
                        maxBodyBytes = 1_048_576,
                    ),
                ),
            ),
        )
        val outcome = service.publishRoutes(publication)
        check(outcome.code == RouteControlCode.APPLIED && outcome.changed) {
            "route generation was rejected: ${outcome.code}"
        }
        check(request(service.port, "/published") == (201 to "new-generation"))
    } finally {
        service.close()
    }
}

/** Submit and consume one request on the runtime-owned outbound lane. */
private fun demonstrateOutbound(service: Service) {
    val call = service.submitOutbound(
        OutboundRequest(OUTBOUND_TARGET, "GET", "/source", timeoutMillis = 3_000),
    )
    val terminal = checkNotNull(service.takeOutbound(5_000)) { "outbound request timed out" }
    check(terminal.call == call && terminal.responseStatus == 200)
    check(String(terminal.responseBody(), StandardCharsets.UTF_8) == "outbound-ready")
}

/** Issue one loopback request for the self-contained smoke command. */
private fun request(
    port: Int,
    path: String,
    body: String? = null,
): Pair<Int, String> {
    val connection = URI("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 5_000
    connection.readTimeout = 5_000
    if (path.startsWith("/app/")) connection.setRequestProperty("accept", "text/html")
    if (path.startsWith("/hello/")) connection.setRequestProperty("x-sample-caller", "smoke")
    if (body != null) {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("content-type", "text/plain")
        connection.outputStream.use { it.write(body.toByteArray()) }
    }
    val status = connection.responseCode
    val stream = if (status >= 400) connection.errorStream else connection.inputStream
    return status to stream.use { String(it.readBytes(), StandardCharsets.UTF_8) }
}

/** Validate representative routes over the actual listener. */
private fun smoke(service: Service) {
    val cases = listOf(
        Triple("/hello/reader?title=hello", 200, "hello reader from smoke"),
        Triple("/stream", 200, "stream-ready"),
        Triple("/events", 200, "data: ready"),
        Triple("/download", 200, "confined application file"),
        Triple("/app/client/route", 200, "CoAkka HTTP Runtime"),
        Triple("/version", 201, "v2"),
    )
    check(request(service.port, "/echo", "payload") == (201 to "payload"))
    val uploaded = request(service.port, "/upload", "streamed")
    check(uploaded == (200 to "streamed")) { "/upload: status=${uploaded.first} body=${uploaded.second}" }
    for ((path, status, expected) in cases) {
        val observed = request(service.port, path)
        check(observed.first == status && expected in observed.second) {
            "$path: status=${observed.first} body=${observed.second}"
        }
    }
    println("kotlin-smoke=pass")
}

/** Own startup, signal wait, and reverse-order graceful shutdown. */
fun main(arguments: Array<String>) {
    val selected = parseOptions(arguments)
    selected.securityFixtures?.let {
        runSecuritySmoke(it)
        return
    }
    demonstrateRoutePublication()
    val upstream = ServiceBuilder()
        .get("/source", Handler { Responses.text("outbound-ready") })
        .start()
    var service: Service? = null
    try {
        service = createService(selected.assets, upstream.port, selected.ioUring)
        activateReplacement(service)
        demonstrateOutbound(service)
        val health = service.probeLiveness(1_000)
        val page = service.readMonitorEvents(maximumEvents = 8)
        val runtimeInfo = service.runtimeInfo()
        println(
            "ready=${health.ready} monitor-latest=${page.latestSequence} retained=${page.events.size} " +
                "io-uring-requested=${runtimeInfo.ioUringRequested} io-uring-effective=${runtimeInfo.ioUringEffective}",
        )
        println("kotlin-sample=http://127.0.0.1:${service.port}")
        if (selected.smoke) {
            smoke(service)
        } else {
            val stopped = CountDownLatch(1)
            Runtime.getRuntime().addShutdownHook(Thread { stopped.countDown() })
            stopped.await()
        }
    } finally {
        service?.close()
        upstream.close()
    }
}
