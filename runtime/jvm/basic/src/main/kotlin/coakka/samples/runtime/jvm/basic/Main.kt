package coakka.samples.runtime.jvm.basic

import coakka.v2.connector.ConnectorOrchestrator
import coakka.v2.connector.RuntimeClient
import coakka.v2.connector.RuntimeEndpointFlags
import coakka.v2.connector.RuntimeEndpointSpec
import coakka.v2.connector.RuntimeRouteSpec
import coakka.v2.connector.RuntimeStartSpec
import coakka.v2.connector.protocol.ConnectorDeliveryHint
import coakka.v2.connector.protocol.ConnectorPayloadFormat
import coakka.v2.connector.protocol.ConnectorPayloadIdentity
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val target = "samples.runtime.jvm.echo"
    /*
     * Minimal single-process runtime configuration.
     *
     * - `systemName` groups diagnostics for one logical runtime participant.
     * - `nodeId` identifies this concrete process in logs and runtime snapshots.
     * - `queueCapacity = 128` is intentionally bounded but roomy enough for a sample.
     * - `strictNoDrop = true` makes overload visible as an error/deadletter instead
     *   of silently dropping messages.
     * - `generation = 1` is the first route-table version. Real integrations should
     *   increment it when publishing a new route snapshot.
     * - `RuntimeEndpointFlags.LOCAL` means the handler is registered in this process.
     */
    val startSpec = RuntimeStartSpec(
        systemName = "jvm-runtime-sample",
        nodeId = "jvm-runtime-sample-node",
        queueCapacity = 128,
        strictNoDrop = true,
        generation = 1,
        routes = listOf(
            RuntimeRouteSpec(
                target = target,
                endpoints = listOf(
                    RuntimeEndpointSpec(
                        host = "127.0.0.1",
                        port = 19301,
                        flags = RuntimeEndpointFlags.LOCAL,
                    )
                ),
            )
        ),
    )

    val orchestrator = ConnectorOrchestrator.start(startSpec = startSpec)
    try {
        val info = orchestrator.runtimeInfo()
        println(
            "coakka_runtime_info abi=${info.abiVersion} " +
                "version=${info.runtimeVersion} git=${info.gitCommit}"
        )

        orchestrator.registerHandler(target) { request ->
            RuntimeClient.replyTypedTo(
                request = request,
                source = target,
                payloadUtf8 = """{"echo":"hello-runtime-jvm"}""",
            )
        }

        val response = orchestrator.kotlin.ask(
            source = "samples-runtime-jvm-client",
            target = target,
            payloadUtf8 = """{"message":"hello-runtime-jvm"}""",
            payloadIdentity = ConnectorPayloadIdentity(
                messageType = "samples.runtime.jvm.echo.request.v1",
                payloadSchemaVersion = 1,
                payloadFormat = ConnectorPayloadFormat.JSON,
            ),
            timeoutMs = 2_000,
            operation = "echo",
            deliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
        )

        println("coakka_runtime_response payload=${response.payloadUtf8()}")

        val stats = orchestrator.stats()
        val clientStats = orchestrator.clientStats()
        println(
            "coakka_runtime_stats generation=${stats.appliedGeneration} " +
                "routes=${stats.routeCount} delivered=${clientStats.deliveredRequests} " +
                "matchedResponses=${clientStats.matchedResponses}"
        )
    } finally {
        orchestrator.kotlin.shutdown()
    }
}
