import coakka.http.Handler
import coakka.http.Listener
import coakka.http.ListenerProtocol
import coakka.http.Responses
import coakka.http.ServiceBuilder
import coakka.http.TransportSecurity
import java.io.File
import java.util.concurrent.CountDownLatch

fun main(args: Array<String>) {
    require(args.size == 2) { "usage: server http1|http2|http3 certificate-directory" }
    val protocol = when (args[0]) {
        "http1" -> ListenerProtocol.HTTP_1_1
        "http2" -> ListenerProtocol.HTTP_2
        "http3" -> ListenerProtocol.HTTP_3
        else -> error("choose http1, http2 or http3")
    }
    val listener = if (protocol == ListenerProtocol.HTTP_1_1) {
        Listener(port = 8080)
    } else {
        Listener(
            port = 8443,
            protocol = protocol,
            security = TransportSecurity.TLS,
            credentialGeneration = 1,
            credentialId = "local-example",
            certificateChainFile = File(args[1], "server.pem").absolutePath,
            privateKeyFile = File(args[1], "server.key").absolutePath,
        )
    }
    val stop = CountDownLatch(1)
    val closed = CountDownLatch(1)
    try {
        ServiceBuilder().listener(listener)
            .get("/hello", Handler { Responses.text("hello") })
            .start().use { service ->
                Runtime.getRuntime().addShutdownHook(Thread {
                    stop.countDown()
                    closed.await()
                })
                println("listening on 127.0.0.1:${service.port}")
                stop.await()
            }
    } finally {
        closed.countDown()
    }
}
