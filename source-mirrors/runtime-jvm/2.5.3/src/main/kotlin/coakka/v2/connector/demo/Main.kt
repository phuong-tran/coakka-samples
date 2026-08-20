package coakka.v2.connector.demo

import coakka.v2.connector.RuntimeClient
import coakka.v2.connector.protocol.ConnectorPayloadFormat
import coakka.v2.connector.protocol.ConnectorPayloadIdentity
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val runtimeLibPath = System.getProperty("coakka.runtime.lib")
        ?: error("missing -Dcoakka.runtime.lib=/abs/path/to/libcoakka_runtime_v2.so")
    val client = RuntimeClient.startLocal(
        runtimeLibPath = runtimeLibPath,
        localTargets = listOf("svc.echo"),
    )
    client.registerHandler("svc.echo") { request ->
        RuntimeClient.replyTypedTo(
            request = request,
            source = "svc.echo",
            payloadUtf8 = "echo:${request.payloadUtf8()}",
        )
    }

    val response = client.ask(
        source = "demo-client",
        target = "svc.echo",
        payloadUtf8 = "hello",
        payloadIdentity = ConnectorPayloadIdentity(
            messageType = "demo.echo.request.v1",
            payloadSchemaVersion = 1,
            payloadFormat = ConnectorPayloadFormat.TEXT,
        ),
    )
    println("response=${response.payloadUtf8()}")
    client.shutdown()
}
