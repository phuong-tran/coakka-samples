package coakka.v2.connector

import kotlinx.coroutines.runBlocking

/**
 * Narrow downstream smoke for a real Windows guest.
 *
 * It proves the connector can load the packaged embedded runtime DLL by
 * default, or an explicit runtime DLL override when one is provided, complete
 * one local ask/reply round trip, and surface one route-miss deadletter
 * without needing Gradle inside the guest.
 */
fun main(args: Array<String>) = runBlocking {
    val runtimeLibPath = System.getProperty("coakka.runtime.lib")
    val client = RuntimeClient.startLocal(
        runtimeLibPath = runtimeLibPath,
        localTargets = listOf("svc.echo"),
        systemName = args.firstOrNull().takeUnless(String?::isNullOrBlank) ?: "windows-guest-smoke",
        nodeId = "node-windows-guest-smoke",
    )

    try {
        client.registerHandler("svc.echo") { request ->
            RuntimeClient.replyTo(
                request = request,
                source = "svc.echo",
                payloadUtf8 = "reply:${request.payloadUtf8()}",
            )
        }

        val response = client.ask(
            source = "windows-smoke-client",
            target = "svc.echo",
            payloadUtf8 = "hello-from-windows-jvm",
        )
        check(response.payloadUtf8() == "reply:hello-from-windows-jvm") {
            "unexpected response payload=${response.payloadUtf8()}"
        }
        println("ASK_OK payload=${response.payloadUtf8()} target=${response.target}")

        val deadletter = try {
            client.ask(
                source = "windows-smoke-client",
                target = "svc.missing",
                payloadUtf8 = "route-miss-please",
            )
            error("expected route-miss deadletter")
        } catch (error: DeadletterException) {
            error.deadletter
        }
        check(deadletter.reason == "DEADLETTER_REASON_ROUTE_MISS") {
            "unexpected deadletter reason=${deadletter.reason}"
        }
        println(
            "DEADLETTER_OK reason=${deadletter.reason} target=${deadletter.originalEnvelope.target}",
        )

        val stats = client.snapshotStats()
        println(
            "STATS matchedResponses=${stats.matchedResponses} " +
                "matchedDeadletters=${stats.matchedDeadletters} " +
                "pendingRequests=${stats.pendingRequests}",
        )
    } finally {
        client.shutdown()
    }
}
