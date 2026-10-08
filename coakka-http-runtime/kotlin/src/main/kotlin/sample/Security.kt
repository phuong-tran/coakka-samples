package sample

import coakka.http.Handler
import coakka.http.Listener
import coakka.http.ListenerProtocol
import coakka.http.Responses
import coakka.http.Service
import coakka.http.ServiceBuilder
import coakka.http.TransportSecurity
import coakka.http.OutboundEndpoint
import coakka.http.OutboundTarget
import coakka.http.OutboundStrategy
import coakka.http.OutboundRequest
import coakka.http.OutboundReason
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.KeyFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/** Exercise server identity, client trust, and mandatory client identity. */
internal fun runSecuritySmoke(fixtures: File) {
    require(fixtures.isDirectory) { "identity directory is not readable: $fixtures" }
    val cases = listOf(
        Triple(TransportSecurity.TLS, 7L, "tls-ready"),
        Triple(TransportSecurity.MUTUAL_TLS, 9L, "mtls-ready"),
    )
    for ((security, generation, body) in cases) {
        val service = ServiceBuilder()
            .listener(
                Listener(
                    bindAddress = "127.0.0.1",
                    protocol = ListenerProtocol.HTTP_1_1,
                    security = security,
                    credentialGeneration = generation,
                    credentialId = "kotlin-sample-server",
                    certificateChainFile = File(fixtures, "server.pem").absolutePath,
                    privateKeyFile = File(fixtures, "server.key").absolutePath,
                    trustRootsFile = if (security == TransportSecurity.MUTUAL_TLS) {
                        File(fixtures, "ca.pem").absolutePath
                    } else {
                        ""
                    },
                ),
            )
            .get("/secure", Handler { Responses.text(body) })
            .start()
        try {
            if (security == TransportSecurity.MUTUAL_TLS) {
                val rejected = try {
                    secureGet(service, fixtures, withIdentity = false)
                    false
                } catch (_: IOException) {
                    true
                }
                check(rejected) { "mutual TLS accepted a client without an identity" }
            }
            check(
                secureGet(
                    service,
                    fixtures,
                    withIdentity = security == TransportSecurity.MUTUAL_TLS,
                ) == (200 to body),
            ) { "secure response mismatch" }
            // A separate client runtime owns outbound TLS and mTLS. Numeric
            // connect address does not disable peer-name/certificate checking.
            val client = ServiceBuilder().outboundTrust(11, File(fixtures, "ca.pem").readBytes())
            if (security == TransportSecurity.MUTUAL_TLS) {
                client.outboundIdentity(12, File(fixtures, "client.pem").readBytes(),
                    File(fixtures, "client.key").readBytes())
            }
            client.outboundTarget(OutboundTarget("sample.secure", 3,
                OutboundStrategy.SINGLE_OWNER, listOf(OutboundEndpoint(
                    "loopback", "127.0.0.1", service.port, "localhost:${service.port}",
                    security = security, tlsPeerIdentity = "localhost", tlsTrustGeneration = 11,
                    tlsClientIdentityGeneration = if (security == TransportSecurity.MUTUAL_TLS) 12 else 0,
                )))).start().use { caller ->
                    val call = caller.submitOutbound(OutboundRequest(
                        "sample.secure", "GET", "/secure", timeoutMillis = 3_000))
                    val terminal = checkNotNull(caller.takeOutbound(5_000))
                    check(terminal.call == call && terminal.reason == OutboundReason.RESPONSE)
                    check(terminal.responseStatus == 200 && String(terminal.responseBody(), Charsets.UTF_8) == body)
                }
        } finally {
            service.close()
        }
    }
    println("kotlin-security-smoke=pass")
}

/** Complete one certificate-verifying loopback request with finite timeouts. */
private fun secureGet(service: Service, fixtures: File, withIdentity: Boolean): Pair<Int, String> {
    val connection = URL("https://localhost:${service.port}/secure")
        .openConnection() as HttpsURLConnection
    connection.sslSocketFactory = clientTlsContext(fixtures, withIdentity).socketFactory
    connection.connectTimeout = 5_000
    connection.readTimeout = 5_000
    return try {
        connection.responseCode to connection.inputStream.use { String(readBounded(it, 4096), Charsets.UTF_8) }
    } finally {
        connection.disconnect()
    }
}

/** Build process-local trust and optional client identity from PEM fixtures. */
private fun clientTlsContext(fixtures: File, withIdentity: Boolean): SSLContext {
    val certificates = CertificateFactory.getInstance("X.509")
    val authority = File(fixtures, "ca.pem").inputStream().use {
        certificates.generateCertificate(it) as X509Certificate
    }
    val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
        load(null)
        setCertificateEntry("sample-authority", authority)
    }
    val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
        init(trustStore)
    }
    val keyManagers = if (withIdentity) {
        val clientCertificate = File(fixtures, "client.pem").inputStream().use {
            certificates.generateCertificate(it) as X509Certificate
        }
        val encodedKey = File(fixtures, "client.key").readText()
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .filterNot(Char::isWhitespace)
            .let(Base64.getDecoder()::decode)
        val privateKey = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(encodedKey))
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null)
            setKeyEntry("sample-client", privateKey, CharArray(0), arrayOf(clientCertificate, authority))
        }
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, CharArray(0))
        }.keyManagers
    } else {
        null
    }
    return SSLContext.getInstance("TLS").apply {
        init(keyManagers, trustManagers.trustManagers, SecureRandom())
    }
}
