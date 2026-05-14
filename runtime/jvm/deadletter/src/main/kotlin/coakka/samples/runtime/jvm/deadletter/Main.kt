package coakka.samples.runtime.jvm.deadletter

import coakka.v2.connector.ConnectorOrchestrator
import coakka.v2.connector.DeadletterException
import coakka.v2.connector.RuntimeEndpointFlags
import coakka.v2.connector.RuntimeEndpointSpec
import coakka.v2.connector.RuntimeRouteSpec
import coakka.v2.connector.RuntimeStartSpec
import coakka.v2.connector.protocol.ConnectorDeliveryHint
import coakka.v2.connector.protocol.ConnectorPayloadFormat
import coakka.v2.connector.protocol.ConnectorPayloadIdentity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

fun main() = runBlocking {
    val liveTarget = "samples.runtime.jvm.deadletter.live"
    val missingTarget = "samples.runtime.jvm.deadletter.missing"
    val startSpec = RuntimeStartSpec(
        systemName = "jvm-deadletter-sample",
        nodeId = "jvm-deadletter-sample-node",
        queueCapacity = 128,
        strictNoDrop = true,
        generation = 1,
        routes = listOf(
            RuntimeRouteSpec(
                target = liveTarget,
                endpoints = listOf(
                    RuntimeEndpointSpec(
                        host = "127.0.0.1",
                        port = 19401,
                        flags = RuntimeEndpointFlags.LOCAL,
                    )
                ),
            )
        ),
    )

    val orchestrator = ConnectorOrchestrator.start(startSpec = startSpec)
    try {
        val subscribed = CompletableDeferred<Unit>()
        val observedDeadletter = async {
            orchestrator.kotlin.deadletters()
                .onStart { subscribed.complete(Unit) }
                .first()
        }
        subscribed.await()

        val deadletter = try {
            orchestrator.kotlin.ask(
                source = "samples-runtime-jvm-deadletter-client",
                target = missingTarget,
                payloadUtf8 = """{"message":"route-miss"}""",
                payloadIdentity = ConnectorPayloadIdentity(
                    messageType = "samples.runtime.jvm.deadletter.request.v1",
                    payloadSchemaVersion = 1,
                    payloadFormat = ConnectorPayloadFormat.JSON,
                ),
                timeoutMs = 2_000,
                operation = "route-miss",
                deliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
            )
            error("expected route miss deadletter")
        } catch (error: DeadletterException) {
            error.deadletter
        }

        val stats = orchestrator.stats()
        val clientStats = orchestrator.clientStats()

        check(deadletter.reason == "DEADLETTER_REASON_ROUTE_MISS") {
            "expected DEADLETTER_REASON_ROUTE_MISS, got ${deadletter.reason}"
        }
        check(deadletter.originalEnvelope.target == missingTarget) {
            "expected target=$missingTarget, got ${deadletter.originalEnvelope.target}"
        }
        check(stats.routeMissCount == 1L && stats.deadletterCount == 1L) {
            "expected routeMissCount=1 deadletterCount=1, got $stats"
        }
        check(clientStats.matchedDeadletters == 1L) {
            "expected matchedDeadletters=1, got ${clientStats.matchedDeadletters}"
        }
        val observed = withTimeout(1_000) { observedDeadletter.await() }
        check(observed.matchedPendingRequest) {
            "expected observed deadletter to match pending request"
        }
        check(observed.deadletter.originalEnvelope.target == missingTarget) {
            "expected observed target=$missingTarget, got ${observed.deadletter.originalEnvelope.target}"
        }

        println(
            "coakka_runtime_deadletter reason=${deadletter.reason} " +
                "target=${deadletter.originalEnvelope.target} generation=${deadletter.activeGeneration}"
        )
        println(
            "coakka_runtime_deadletter_observed matchedPending=${observed.matchedPendingRequest} " +
                "target=${observed.deadletter.originalEnvelope.target}"
        )
        println(
            "coakka_runtime_stats routeMisses=${stats.routeMissCount} " +
                "deadletters=${stats.deadletterCount} matchedDeadletters=${clientStats.matchedDeadletters}"
        )
    } finally {
        orchestrator.kotlin.shutdown()
    }
}
