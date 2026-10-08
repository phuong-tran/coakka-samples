package sample

import coakka.http.Handler
import coakka.http.Listener
import coakka.http.ListenerProtocol
import coakka.http.Responses
import coakka.http.ServiceBuilder
import coakka.http.TransportSecurity
import java.io.File
import java.util.concurrent.CountDownLatch

/** A real Core-owned TLS HTTP2/3 listener; application handlers remain in Kotlin. */
internal fun runProtocol(protocol: String, fixtures: File) {
    val selected = when (protocol) {
        "http2" -> ListenerProtocol.HTTP_2
        "http3" -> ListenerProtocol.HTTP_3
        else -> error("protocol must be http2 or http3")
    }
    require(fixtures.isDirectory)
    val stopped = CountDownLatch(1)
    val closed = CountDownLatch(1)
    try {
        ServiceBuilder().listener(Listener(
            bindAddress = "127.0.0.1", protocol = selected, security = TransportSecurity.TLS,
            credentialGeneration = 1, credentialId = "kotlin-protocol",
            certificateChainFile = File(fixtures, "server.pem").absolutePath,
            privateKeyFile = File(fixtures, "server.key").absolutePath,
        )).get("/protocol", Handler {
            println("kotlin-protocol-requested")
            Responses.text("protocol-ready")
        }).start().use { service ->
            // Hook is a JVM lifetime barrier only; Core owns graceful drain.
            Runtime.getRuntime().addShutdownHook(Thread {
                stopped.countDown()
                closed.await()
            })
            println("kotlin-protocol=$protocol https://localhost:${service.port}/protocol")
            stopped.await()
        }
        println("kotlin-shutdown=complete")
    } finally {
        // A failed close never emits the success marker. Unblock the hook so
        // the diagnostic exit can finish rather than deadlocking the process.
        closed.countDown()
    }
}
