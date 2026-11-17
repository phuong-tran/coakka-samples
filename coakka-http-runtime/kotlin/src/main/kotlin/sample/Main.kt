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
import coakka.http.Compression
import coakka.http.CompressionMode
import coakka.http.OutboundReason
import coakka.http.TransportTimeouts
import coakka.http.CpuPolicy
import coakka.http.NotificationProfile
import java.io.ByteArrayOutputStream
import java.io.InputStream
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
    val compression: Boolean,
    val tuning: Boolean,
    val singleCpu: Boolean,
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
        arguments.contains("--compression"),
        arguments.contains("--tuning"),
        arguments.contains("--single-cpu"),
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
                "ready\nsecond line".toByteArray(),
                eventType = "state",
                id = "1",
                retryMillis = 1_000,
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
            WebSocketEventKind.BINARY -> session.sendBinary(event.bytes())
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
private fun createService(selected: Options, upstreamPort: Int): Service {
    val assets = selected.assets
    val endpoint = OutboundEndpoint(
        nodeId = "loopback",
        connectHost = "127.0.0.1",
        connectPort = upstreamPort,
        httpAuthority = "127.0.0.1:$upstreamPort",
    )
    val builder = ServiceBuilder()
        .concurrency(2)
        .cpu(if (selected.singleCpu) CpuPolicy.SINGLE else CpuPolicy.AUTO)
        .limits(Limits(maxHandlerBindings = 16, maxRequestBodyBytes = 65_536,
            maxResponseBodyBytes = 65_536,
            requestNotificationProfile = if (selected.tuning) NotificationProfile.SMALL else NotificationProfile.AUTO,
            terminalNotificationProfile = if (selected.tuning) NotificationProfile.MEDIUM else NotificationProfile.AUTO,
            transportTimeouts = TransportTimeouts(headerTimeoutMillis = 5_000, bodyTimeoutMillis = 5_000)))
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
        .postStream("/upload", Handler { request ->
            // The example buffers only within its explicit 64KiB ceiling;
            // real applications can consume each bounded chunk incrementally.
            println("kotlin-upload-reading")
            val bytes = try { readBounded(request.body, 65_536) } catch (failure: java.io.IOException) {
                println("kotlin-upload-read-failed")
                throw failure
            }
            val observed = request.trailers["x-upload-check"]
            Responses.bytes(bytes, headers = if (observed == null) Headers()
                else Headers.of(Header("x-upload-observed", observed)))
        })
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
        .get("/compressed", Handler { Responses.text("compression-ready ".repeat(128)) })
    if (selected.ioUring) builder.ioBackend(IoBackend.IO_URING)
    if (selected.compression) builder.compression(Compression(CompressionMode.GZIP, minimumBodyBytes = 1))
    return builder.start()
}

/** Activate a prepared callback without replacing the structural route map. */
private fun activateReplacement(service: Service) {
    val snapshot = service.routes
    // This bounded control-plane selection is not request dispatch. Reserve
    // a distinct application binding identity, not a newly added route's ID.
    val route = snapshot.routes.single { it.routeId == 8L }
    service.prepareHandler(100, Handler { Responses.text("v2", status = 201) })
    val outcome = service.rebindHandler(RouteRebind(
        1, snapshot.routeGeneration, route.routeId, route.bindingRevision, 100))
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
        val before = service.routes
        service.prepareHandler(2, Handler { Responses.text("new-generation", status = 201) })
        val publication = RoutePublication(
            activationId = 1,
            expectedRouteGeneration = before.routeGeneration,
            expectedBindingChangeSequence = before.bindingChangeSequence,
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
        val after = service.routes
        check(after.routeGeneration > before.routeGeneration && after.routes.single().routeId == 2L)
        val refused = service.publishRoutes(publication.copy(activationId = 2))
        check(refused.code != RouteControlCode.APPLIED)
        check(service.routes.routeGeneration == after.routeGeneration)
        check(request(service.port, "/old").first == 404)
    } finally {
        service.close()
    }
}

/** Submit and consume one request on the runtime-owned outbound lane. */
private fun demonstrateOutbound(service: Service) {
    for ((path, status) in listOf("/source" to 200, "/missing" to 404)) {
        val call = service.submitOutbound(
            OutboundRequest(OUTBOUND_TARGET, "GET", path, timeoutMillis = 3_000),
        )
        val terminal = checkNotNull(service.takeOutbound(5_000)) { "outbound request timed out" }
        check(terminal.call == call && terminal.reason == OutboundReason.RESPONSE)
        check(terminal.responseStatus == status)
        if (status == 200) check(String(terminal.responseBody(), StandardCharsets.UTF_8) == "outbound-ready")
    }
}

/** Java8-compatible bounded read; the caller retains stream ownership. */
internal fun readBounded(source: InputStream, maximumBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val chunk = ByteArray(4096)
    while (true) {
        val count = source.read(chunk)
        if (count < 0) return output.toByteArray()
        check(count <= maximumBytes - output.size()) { "sample body exceeded its bound" }
        output.write(chunk, 0, count)
    }
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
    return try {
        val status = connection.responseCode
        val stream = if (status >= 400) connection.errorStream else connection.inputStream
        status to (stream?.use { String(readBounded(it, 65_536), StandardCharsets.UTF_8) } ?: "")
    } finally {
        connection.disconnect()
    }
}

/** Validate representative routes over the actual listener. */
private fun smoke(service: Service, compression: Boolean) {
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
    if (compression) {
        val connection = URI("http://127.0.0.1:${service.port}/compressed").toURL()
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.setRequestProperty("Accept-Encoding", "gzip")
        try {
            check(connection.responseCode == 200 && connection.getHeaderField("Content-Encoding") == "gzip")
            java.util.zip.GZIPInputStream(connection.inputStream).use {
                check(String(readBounded(it, 4096), Charsets.UTF_8) == "compression-ready ".repeat(128))
            }
        } finally {
            connection.disconnect()
        }
    }
    println("kotlin-smoke=pass")
}

/** Own startup, signal wait, and reverse-order graceful shutdown. */
fun main(arguments: Array<String>) {
    val protocolIndex = arguments.indexOf("--protocol")
    if (protocolIndex >= 0) {
        val fixturesIndex = arguments.indexOf("--protocol-fixtures")
        require(protocolIndex + 1 < arguments.size && fixturesIndex >= 0 && fixturesIndex + 1 < arguments.size)
        runProtocol(arguments[protocolIndex + 1], File(arguments[fixturesIndex + 1]))
        return
    }
    if (arguments.contains("--adverse-smoke")) {
        runAdverseSmoke()
        return
    }
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
    val shutdownCompleted = CountDownLatch(1)
    val stopped = CountDownLatch(1)
    var primaryFailure: Throwable? = null
    try {
        service = createService(selected, upstream.port)
        verifySettings(service, selected.tuning, selected.singleCpu)
        demonstrateMonitorReload(service)
        activateReplacement(service)
        demonstrateOutbound(service)
        if (!selected.smoke) {
            // Install before readiness. JVM waits for hooks, not main; the hook
            // must remain alive until both bounded Core close calls finish.
            Runtime.getRuntime().addShutdownHook(Thread {
                stopped.countDown()
                shutdownCompleted.await()
            })
        }
        val health = service.probeLiveness(1_000)
        val page = service.readMonitorEvents(maximumEvents = 8)
        val runtimeInfo = service.runtimeInfo()
        println(
            "ready=${health.ready} monitor-latest=${page.latestSequence} retained=${page.events.size} " +
                "io-uring-requested=${runtimeInfo.ioUringRequested} io-uring-effective=${runtimeInfo.ioUringEffective}",
        )
        println("kotlin-sample=http://127.0.0.1:${service.port}")
        if (selected.smoke) {
            smoke(service, selected.compression)
        } else {
            stopped.await()
        }
    } catch (failure: Throwable) {
        primaryFailure = failure
        throw failure
    } finally {
        try {
            // Nested use preserves the first close failure while still closing
            // the other owner; no shutdown timeout or error is suppressed.
            upstream.use { service?.use { } }
            println("kotlin-shutdown=complete")
        } catch (closeFailure: Throwable) {
            val original = primaryFailure
            if (original == null) throw closeFailure
            original.addSuppressed(closeFailure)
        } finally {
            shutdownCompleted.countDown()
        }
    }
}
