package coakka.v2.connector

import java.net.InetSocketAddress
import java.net.ServerSocket

internal object TestSupport {
    fun availableLoopbackPort(): Int = ServerSocket().use { probe ->
        // A reuse-enabled probe can admit a port still in TIME_WAIT. Require
        // exclusive IPv4 admission. This is not a reservation: real Core bind
        // failures still propagate without retry or error reclassification.
        probe.reuseAddress = false
        probe.bind(InetSocketAddress("127.0.0.1", 0))
        probe.localPort
    }

    fun runtimeLibPath(): String =
        System.getProperty("coakka.runtime.lib")
            ?: error("missing coakka.runtime.lib")

    fun localRoutes(vararg targets: String): List<RuntimeRouteSpec> =
        RuntimeClient.localRoutes(targets.toList())
}
