import coakka.http.Handler
import coakka.http.Listener
import coakka.http.ListenerProtocol
import coakka.http.MultiProtocolListener
import coakka.http.Responses
import coakka.http.ServiceBuilder
import coakka.http.TransportSecurity
import java.io.File
import java.util.concurrent.CountDownLatch

fun main(args: Array<String>) {
    require(args.size == 2) { "usage: server http1|http2|http3|multi certificate-directory" }
    val certificates = File(args[1]).absoluteFile
    val certificateChain = certificates.resolve("server.pem").path
    val privateKey = certificates.resolve("server.key").path
    val builder = ServiceBuilder()
    when (args[0]) {
        "http1" -> builder.listen("127.0.0.1", 8080)
        "http2", "http3" -> builder.listener(
            Listener(
                port = 8443,
                protocol = if (args[0] == "http2") ListenerProtocol.HTTP_2 else ListenerProtocol.HTTP_3,
                security = TransportSecurity.TLS,
                credentialGeneration = 1,
                credentialId = "local-example",
                certificateChainFile = certificateChain,
                privateKeyFile = privateKey,
            ),
        )
        "multi" -> builder.multiProtocolListener(
            MultiProtocolListener(
                certificateChainFile = certificateChain,
                privateKeyFile = privateKey,
                credentialId = "local-example",
                port = 8443,
            ),
        )
        else -> error("choose http1, http2, http3 or multi")
    }
    val stop = CountDownLatch(1)
    val closed = CountDownLatch(1)
    try {
        builder
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
