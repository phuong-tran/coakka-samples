package sample

import coakka.http.Handler
import coakka.http.Limits
import coakka.http.OutboundEndpoint
import coakka.http.OutboundReason
import coakka.http.OutboundRequest
import coakka.http.OutboundStrategy
import coakka.http.OutboundTarget
import coakka.http.Responses
import coakka.http.ServiceBuilder
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.TimeUnit

/** Finite application-level deadline and outbound cancellation demonstrations. */
internal fun runAdverseSmoke() {
    handlerDeadline()
    boundedPressure()
    outboundTerminal(OutboundReason.CANCELLED)
    outboundTerminal(OutboundReason.DEADLINE_EXCEEDED)
    println("kotlin-adverse-smoke=pass")
}

/** Observe bounded admission without guessing which owner refused the burst. */
private fun boundedPressure() {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    val clients = Executors.newFixedThreadPool(9)
    val completed = ExecutorCompletionService<Int>(clients)
    try {
        ServiceBuilder().concurrency(1)
            .limits(Limits(requestQueueCapacity = 1, requestTimeoutMillis = 10_000))
            .get("/held", Handler {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS)) { "diagnostic pressure release expired" }
                Responses.text("ready")
            })
            .get("/ready", Handler { Responses.text("ready") })
            .start().use { service ->
                val requests = ArrayList<java.util.concurrent.Future<Int>>()
                try {
                    requests += completed.submit { status(service.port, "/held") }
                    check(entered.await(2, TimeUnit.SECONDS))
                    repeat(8) { requests += completed.submit { status(service.port, "/held") } }
                    // The active worker cannot finish yet. At least one request
                    // must be refused, rather than waiting for unbounded space.
                    val refusal = checkNotNull(completed.poll(3, TimeUnit.SECONDS))
                    check(refusal.get() == 503) { "bounded admission did not refuse the burst" }
                } finally {
                    release.countDown()
                }
                for (response in requests) check(response.get(5, TimeUnit.SECONDS) in listOf(200, 503))
                check(status(service.port, "/ready") == 200)
                // JVM scheduling and Core ingress have independent bounds.
                // HTTP503 alone must not be reclassified as either one's cause.
            }
    } finally {
        release.countDown()
        clients.shutdownNow()
        check(clients.awaitTermination(5, TimeUnit.SECONDS)) { "pressure clients did not stop" }
    }
}

/** Diagnostic HTTP client; these five-second waits are not runtime policy. */
private fun status(port: Int, path: String): Int {
    val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
    connection.connectTimeout = 5_000
    connection.readTimeout = 5_000
    return try {
        val result = connection.responseCode
        val input = if (result >= 400) connection.errorStream else connection.inputStream
        input?.use { readBounded(it, 4096) }
        result
    } finally {
        connection.disconnect()
    }
}

/** Core expires the HTTP exchange; application work is released independently. */
private fun handlerDeadline() {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    val client = Executors.newSingleThreadExecutor()
    try {
        ServiceBuilder().concurrency(1).limits(Limits(requestTimeoutMillis = 250))
            .get("/held", Handler {
                entered.countDown()
                check(release.await(4, TimeUnit.SECONDS)) { "diagnostic handler release expired" }
                Responses.text("late result")
            })
            .get("/ready", Handler { Responses.text("ready") })
            .start().use { service ->
                try {
                    val response = client.submit<Int> { status(service.port, "/held") }
                    check(entered.await(2, TimeUnit.SECONDS)) { "handler was not admitted" }
                    check(response.get(3, TimeUnit.SECONDS) == 504) { "Core deadline was not observed" }
                } finally {
                    release.countDown()
                }
                // With one application worker, this response also proves that
                // the late handler returned before the subsequent work ran.
                check(status(service.port, "/ready") == 200)
                check(service.pollError() == null)
            }
    } finally {
        release.countDown()
        client.shutdownNow()
        check(client.awaitTermination(5, TimeUnit.SECONDS)) { "diagnostic client did not stop" }
    }
}

/** Typed Core outcome, one terminal per call, then successful call-slot reuse. */
private fun outboundTerminal(expected: OutboundReason) {
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    ServiceBuilder().concurrency(1)
        .get("/held", Handler {
            entered.countDown()
            check(release.await(4, TimeUnit.SECONDS)) { "diagnostic upstream release expired" }
            Responses.text("late upstream result")
        })
        .get("/ready", Handler { Responses.text("ready") })
        .start().use { upstream ->
            try {
                ServiceBuilder().outboundTarget(OutboundTarget(
                    "sample.adverse", 1, OutboundStrategy.SINGLE_OWNER,
                    listOf(OutboundEndpoint("loopback", "127.0.0.1", upstream.port,
                        "127.0.0.1:${upstream.port}")),
                )).start().use { caller ->
                    val call = caller.submitOutbound(OutboundRequest(
                        "sample.adverse", "GET", "/held",
                        timeoutMillis = if (expected == OutboundReason.CANCELLED) 3_000 else 800,
                    ))
                    try {
                        check(entered.await(2, TimeUnit.SECONDS)) { "upstream was not admitted" }
                        if (expected == OutboundReason.CANCELLED) caller.cancelOutbound(call)
                        val terminal = checkNotNull(caller.takeOutbound(3_000))
                        check(terminal.call == call && terminal.reason == expected)
                    } finally {
                        // Cancellation/timeout is not permission to abandon a
                        // business operation. This sample explicitly releases it.
                        release.countDown()
                    }
                    check(status(upstream.port, "/ready") == 200)
                    val next = caller.submitOutbound(OutboundRequest(
                        "sample.adverse", "GET", "/ready", timeoutMillis = 3_000))
                    val completed = checkNotNull(caller.takeOutbound(4_000))
                    check(completed.call == next && completed.reason == OutboundReason.RESPONSE)
                    check(completed.responseStatus == 200)
                    check(caller.takeOutbound(0) == null)
                }
            } finally {
                release.countDown()
            }
        }
}
