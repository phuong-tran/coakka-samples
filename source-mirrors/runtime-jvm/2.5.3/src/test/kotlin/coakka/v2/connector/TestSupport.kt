package coakka.v2.connector

internal object TestSupport {
    fun runtimeLibPath(): String =
        System.getProperty("coakka.runtime.lib")
            ?: error("missing coakka.runtime.lib")

    fun localRoutes(vararg targets: String): List<RuntimeRouteSpec> =
        RuntimeClient.localRoutes(targets.toList())
}
