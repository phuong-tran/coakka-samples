package coakka.v2.connector

/**
 * Narrow Windows guest smoke for runtime lifecycle and monitor coverage.
 *
 * It proves the JVM connector can:
 * - open a runtime handle on a real Windows guest through the packaged
 *   embedded runtime DLL by default, or an explicit override when provided
 * - consume monitor doorbells without relying on host CRT fd translation
 * - observe initial apply, start, and control-snapshot updates
 * - stop and close cleanly
 */
fun main(args: Array<String>) {
    val runtimeLibPath = System.getProperty("coakka.runtime.lib")
    val systemName = args.firstOrNull().takeUnless(String?::isNullOrBlank) ?: "windows-guest-lifecycle"
    val handle = RuntimeHandle.open(
        runtimeLibPath = runtimeLibPath,
        startSpec = RuntimeStartSpec(
            systemName = systemName,
            nodeId = "node-$systemName",
            generation = 41,
            routes = RuntimeClient.localRoutes(listOf("svc.echo")),
        ),
    )

    try {
        check(handle.monitor.isEnabled()) { "runtime monitor must be enabled for lifecycle smoke" }

        val info = handle.runtimeInfo()
        println(
            "INFO_OK abi=${info.abiVersion} runtime=${info.runtimeVersion} " +
                "features=${info.featureFlagsText}",
        )

        val initial = requireNotNull(handle.monitor.awaitNextBlocking(1_000)) {
            "expected initial monitor wake after open/apply"
        }
        check(initial.signalCount > 0) { "expected positive initial signal count" }
        check(initial.health.appliedGeneration == 41L) {
            "unexpected initial generation=${initial.health.appliedGeneration}"
        }
        println(
            "MONITOR_INITIAL_OK signalCount=${initial.signalCount} " +
                "generation=${initial.health.appliedGeneration} " +
                "lifetime=${initial.stats.monitorEventEmittedLifetimeCount}",
        )

        handle.start()

        val started = requireNotNull(handle.monitor.awaitNextBlocking(1_000)) {
            "expected started monitor wake"
        }
        check((started.health.flags and CoakkaHealthFlags.RUNTIME_STARTED) != 0) {
            "started health flags missing runtime_started=${started.health.flagsText}"
        }
        println(
            "MONITOR_STARTED_OK signalCount=${started.signalCount} " +
                "state=${started.health.runtimeStateName} flags=${started.health.flagsText}",
        )

        handle.controlClient.applySnapshot(
            generation = 42,
            routes = RuntimeClient.localRoutes(listOf("svc.echo", "svc.audit")),
            sourceConnector = systemName,
        )

        val updated = requireNotNull(handle.monitor.awaitNextBlocking(1_000)) {
            "expected control update monitor wake"
        }
        check(updated.health.appliedGeneration == 42L) {
            "unexpected updated generation=${updated.health.appliedGeneration}"
        }
        check(updated.stats.routeCount == 2L) {
            "unexpected updated routeCount=${updated.stats.routeCount}"
        }
        println(
            "MONITOR_UPDATE_OK signalCount=${updated.signalCount} " +
                "generation=${updated.health.appliedGeneration} routes=${updated.stats.routeCount}",
        )

        val health = handle.health()
        val stats = handle.stats()
        val config = handle.config()
        check(health.appliedGeneration == 42L) {
            "unexpected health generation=${health.appliedGeneration}"
        }
        check(stats.routeCount == 2L) { "unexpected stats routeCount=${stats.routeCount}" }
        check(config.routeCount == 2L) { "unexpected config routeCount=${config.routeCount}" }
        println(
            "SNAPSHOT_OK state=${health.runtimeStateName} flags=${health.flagsText} " +
                "configRoutes=${config.routeCount} emittedLifetime=${stats.monitorEventEmittedLifetimeCount}",
        )

        handle.stop()
        val stopped = handle.health()
        check((stopped.flags and CoakkaHealthFlags.RUNTIME_STARTED) == 0) {
            "runtime_started still set after stop flags=${stopped.flagsText}"
        }
        println("STOP_OK state=${stopped.runtimeStateName} flags=${stopped.flagsText}")
    } finally {
        handle.close()
        println("CLOSE_OK")
    }
}
