package coakka.samples.runtime.jvm.deadletter

import coakka.v2.connector.ConnectorOrchestrator
import coakka.v2.connector.DeadletterException
import coakka.v2.connector.CoakkaRuntimeDeliveryModes
import coakka.v2.connector.CoakkaRuntimeStartupCpuPolicies
import coakka.v2.connector.RuntimeClient
import coakka.v2.connector.RuntimeStartSpec
import coakka.v2.connector.RuntimeTcpConnectionMode
import coakka.v2.connector.protocol.ConnectorDeadletter
import coakka.v2.connector.protocol.ConnectorDeadletterReason
import coakka.v2.connector.protocol.ConnectorDeliveryHint
import coakka.v2.connector.protocol.ConnectorEnvelope
import coakka.v2.connector.protocol.ConnectorPayloadFormat
import coakka.v2.connector.protocol.ConnectorPayloadIdentity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal fun requireRouteMiss(deadletter: ConnectorDeadletter, target: String): ConnectorEnvelope {
    check(deadletter.reason == ConnectorDeadletterReason.ROUTE_MISS) { "expected ROUTE_MISS, got ${deadletter.reason}" }
    val original = checkNotNull(deadletter.originalEnvelope) { "route miss must retain its original" }
    check(original.target == target) { "expected target=$target, got ${original.target}" }
    return original
}

fun main() = runBlocking {
    val liveTarget = "samples.runtime.jvm.deadletter.live"
    val missingTarget = "samples.runtime.jvm.deadletter.missing"
    val startSpec = RuntimeStartSpec(
        systemName = "jvm-deadletter-sample",
        nodeId = "jvm-deadletter-sample-node",
        routes = listOf(RuntimeClient.localRoute(liveTarget)),
    )

    val orchestrator = ConnectorOrchestrator.start(startSpec = startSpec)
    var primaryFailure: Throwable? = null
    try {
        val startup = orchestrator.startupConfig()
        val delivery = orchestrator.deliveryConfig()
        check(startup.execution.requestedPolicy == CoakkaRuntimeStartupCpuPolicies.AUTO &&
            startup.connection.mode == RuntimeTcpConnectionMode.MULTIPLEXING && !startup.monitorEnabled)
        check(delivery.requestMode == CoakkaRuntimeDeliveryModes.OWNED_QUEUE &&
            delivery.responseMode == CoakkaRuntimeDeliveryModes.OWNED_QUEUE)
        // Enter collection before asking; keep one bounded, application-owned observer.
        val observedDeadletter = async(start = CoroutineStart.UNDISPATCHED) {
            orchestrator.kotlin.deadletters(bufferCapacity = 1).first()
        }
        try {
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

            val original = requireRouteMiss(deadletter, missingTarget)
            check(stats.routeMissCount == 1L && stats.deadletterCount == 1L) {
                "expected routeMissCount=1 deadletterCount=1, got $stats"
            }
            check(clientStats.matchedDeadletters == 1L && clientStats.pendingRequests == 0) {
                "expected matchedDeadletters=1, got ${clientStats.matchedDeadletters}"
            }
            val observed = withTimeout(1_000) { observedDeadletter.await() }
            check(observed.matchedPendingRequest) {
                "expected observed deadletter to match pending request"
            }
            val observedOriginal = requireRouteMiss(observed.deadletter, missingTarget)

            println(
                "coakka_runtime_deadletter reason=${deadletter.reason.value} " +
                    "target=${original.target} generation=${deadletter.activeGeneration}"
            )
            println(
                "coakka_runtime_deadletter_observed matchedPending=${observed.matchedPendingRequest} " +
                    "target=${observedOriginal.target}"
            )
            println(
                "coakka_runtime_stats routeMisses=${stats.routeMissCount} " +
                    "deadletters=${stats.deadletterCount} matchedDeadletters=${clientStats.matchedDeadletters}"
            )
        } finally {
            // Retire the observer before the host, including caller cancellation.
            withContext(NonCancellable) { observedDeadletter.cancelAndJoin() }
        }
    } catch (failure: Throwable) {
        primaryFailure = failure
        throw failure
    } finally {
        withContext(NonCancellable) {
            try {
                orchestrator.kotlin.shutdown()
            } catch (cleanup: Throwable) {
                val primary = primaryFailure
                if (primary == null) throw cleanup
                primary.addSuppressed(cleanup)
            }
        }
    }
}
