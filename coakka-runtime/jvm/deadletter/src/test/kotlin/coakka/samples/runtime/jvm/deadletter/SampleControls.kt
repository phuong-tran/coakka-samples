package coakka.samples.runtime.jvm.deadletter

import coakka.v2.connector.RuntimeTcpConnectionMode
import coakka.v2.connector.CoAkka
import coakka.v2.connector.ConnectorOrchestrator
import coakka.v2.connector.RuntimeClient
import coakka.v2.connector.RuntimeStartSpec
import coakka.v2.connector.protocol.ConnectorDeadletter
import coakka.v2.connector.protocol.ConnectorDeadletterReason
import coakka.v2.connector.protocol.ConnectorEnvelope
import coakka.v2.connector.protocol.ConnectorMessageKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

fun main() = runBlocking {
    val original = ConnectorEnvelope(
        messageId = "control", source = "sample-test", target = "missing",
        kind = ConnectorMessageKind.REQUEST, oneWay = false,
    )
    val good = ConnectorDeadletter(
        originalEnvelope = original, reason = ConnectorDeadletterReason(2), detail = "not classified",
        activeGeneration = 1, resolvedHost = "", resolvedPort = 0, endpointAttemptCount = 0,
        failure = null, failureDetails = emptyMap(),
    )
    check(requireRouteMiss(good, "missing") === original)
    check(RuntimeTcpConnectionMode(3) == RuntimeTcpConnectionMode.MULTIPLEXING)
    val invalid = listOf(
        good.copy(originalEnvelope = null),
        good.copy(originalEnvelope = original.copy(target = "other")),
        good.copy(reason = ConnectorDeadletterReason(123456), detail = "ROUTE_MISS"),
        good.copy(reason = ConnectorDeadletterReason.NO_RESPONSIBLE_HOST),
    )
    for (value in invalid) {
        val failure = runCatching { requireRouteMiss(value, "missing") }.exceptionOrNull()
        check(failure is IllegalStateException)
    }
    val entered = CompletableDeferred<Unit>()
    val retired = CompletableDeferred<Unit>()
    val stopped = CompletableDeferred<Unit>()
    val owner = async(start = CoroutineStart.UNDISPATCHED) {
        val host = ConnectorOrchestrator.start(startSpec = RuntimeStartSpec(
            systemName = "sample-cancel-control", nodeId = "sample-cancel-control-node",
            routes = listOf(RuntimeClient.localRoute("cancel-control")),
        ))
        try {
            val observer = async(start = CoroutineStart.UNDISPATCHED) { host.kotlin.deadletters(1).first() }
            try {
                entered.complete(Unit)
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    observer.cancelAndJoin()
                    check(observer.isCancelled)
                    retired.complete(Unit)
                }
            }
        } finally {
            withContext(NonCancellable) {
                check(retired.isCompleted)
                host.kotlin.shutdown()
                stopped.complete(Unit)
            }
        }
    }
    entered.await()
    withTimeout(3_000) { owner.cancelAndJoin() }
    check(stopped.isCompleted)
    CoAkka.local("sample-after-cancel").use { runtime ->
        runtime.handler("echo") { it }
        check(runtime.ask("echo", "after-cancel") == "after-cancel")
    }
    println("kotlin_sample_controls cases=8")
}
