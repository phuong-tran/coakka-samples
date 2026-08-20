package coakka.v2.connector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RuntimeHandleLifecycleTest {
    @Test
    fun runtimeInfoExposesReadableMetadata() {
        val runtimeInfo = RuntimeHandle.readRuntimeInfo(TestSupport.runtimeLibPath())

        assertEquals(CoakkaV2Library.ABI_VERSION, runtimeInfo.abiVersion)
        assertTrue(runtimeInfo.runtimeVersion.isNotBlank())
        assertTrue(runtimeInfo.southboundBackend.isNotBlank())
        assertTrue(runtimeInfo.allocatorBackend.isNotBlank())
        assertTrue(runtimeInfo.featureFlagsText.isNotBlank())
        assertTrue((runtimeInfo.featureFlags and CoakkaRuntimeFeatures.REQUEST_PIPE) != 0)
        assertTrue((runtimeInfo.featureFlags and CoakkaRuntimeFeatures.CONTROL_PIPE) != 0)
        assertTrue((runtimeInfo.featureFlags and CoakkaRuntimeFeatures.MONITOR) != 0)
        assertTrue((runtimeInfo.featureFlags and CoakkaRuntimeFeatures.NATIVE_SUBMIT) != 0)
        assertTrue((runtimeInfo.featureFlags and CoakkaRuntimeFeatures.CONTROL_JSON) != 0)
        assertTrue((runtimeInfo.featureFlags and CoakkaRuntimeFeatures.DELIVERED_REQUEST_PIPE) != 0)
    }

    @Test
    fun openAppliesInitialSnapshotBeforeStart() {
        val handle = RuntimeHandle.open(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "connector-kotlin-test",
                nodeId = "node-lifecycle",
                generation = 7,
                overloadPolicy = RuntimeOverloadPolicySpec(
                    remoteOutboundMode = RuntimeOverloadMode.DROP_ONE_WAY_FIRST,
                    remoteOutboundReplyReserveSlots = 1,
                ),
                routes = TestSupport.localRoutes("svc.echo"),
            ),
        )

        try {
            val health = handle.health()
            val stats = handle.stats()
            val config = handle.config()

            assertEquals(7, health.appliedGeneration)
            assertTrue(health.runtimeStateName.isNotBlank())
            assertTrue(health.flagsText.isNotBlank())
            assertEquals(7, stats.appliedGeneration)
            assertEquals(1, stats.routeCount)
            assertTrue(stats.runtimeStateName.isNotBlank())
            assertTrue(stats.ingressOverloadModeName.isNotBlank())
            assertTrue(stats.localDeliveryOverloadModeName.isNotBlank())
            assertTrue(stats.remoteOutboundOverloadModeName.isNotBlank())
            assertTrue(stats.localWorkQueueCapacity >= 0)
            assertTrue(stats.deliveredRequestOutboundQueueCapacity >= 0)
            assertTrue(stats.responseOutboundQueueCapacity >= 0)
            assertTrue(stats.deadletterOutboundQueueCapacity >= 0)
            assertTrue(stats.remoteOutboundQueueCapacity >= 0)
            assertTrue(stats.remoteOutboundQueueDepth >= 0)
            assertTrue(stats.remoteOutboundQueueHighWatermark >= 0)
            assertEquals(0, stats.remoteOutboundQueueRejectedCount)
            assertEquals(0, stats.remoteOutboundExpiredDropCount)
            assertEquals(1, stats.remoteOutboundReplyReserveSlots)
            assertEquals(0, stats.remoteOutboundReplyReservationRejectCount)
            assertTrue(handle.hostHandles.delivered_request_read_fd >= 0)
            assertEquals("connector-kotlin-test", config.systemName)
            assertEquals("node-lifecycle", config.nodeId)
            assertEquals(128, config.queueCapacity)
            assertEquals(7, config.appliedGeneration)
            assertEquals(1, config.routeCount)
            assertTrue(config.snapshotPresent)
            assertEquals(health.runtimeState, config.runtimeState)
            assertEquals(health.runtimeStateName, config.runtimeStateName)
            assertTrue(config.configuredIngressOverloadModeName.isNotBlank())
            assertTrue(config.effectiveIngressOverloadModeName.isNotBlank())
            assertEquals("drop_one_way_first", config.effectiveRemoteOutboundOverloadModeName)
            assertEquals(1, config.effectiveRemoteOutboundReplyReserveSlots)
            assertEquals("drop_one_way_first", stats.remoteOutboundOverloadModeName)

            handle.controlClient.applySnapshot(
                generation = 8,
                routes = TestSupport.localRoutes("svc.echo"),
                sourceConnector = "connector-kotlin-test",
            )
            val reloadedConfig = handle.config()
            assertEquals(8, reloadedConfig.appliedGeneration)
            assertEquals("drop_one_way_first", reloadedConfig.effectiveRemoteOutboundOverloadModeName)
            assertEquals(1, reloadedConfig.effectiveRemoteOutboundReplyReserveSlots)

            assertTrue((health.flags and CoakkaHealthFlags.CONTROL_SNAPSHOT_PRESENT) != 0)
            assertTrue((health.flags and CoakkaHealthFlags.RUNTIME_STARTED) == 0)
            assertTrue((health.flags and CoakkaHealthFlags.DATAPLANE_READY) == 0)
        } finally {
            handle.close()
        }
    }

    @Test
    fun embeddedRejectsRemoteRouteAndFailedStartupReleasesHandles() {
        assertFailsWith<IllegalStateException> {
            RuntimeHandle.open(
                runtimeLibPath = TestSupport.runtimeLibPath(),
                startSpec = RuntimeStartSpec(
                    systemName = "connector-kotlin-network-test",
                    nodeId = "node-invalid-embedded-remote",
                    routes = listOf(
                        RuntimeRouteSpec(
                            target = "svc.remote",
                            endpoints = listOf(
                                RuntimeEndpointSpec(host = "127.0.0.1", port = 19301),
                            ),
                        ),
                    ),
                ),
            )
        }

        RuntimeHandle.open(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "connector-kotlin-network-test",
                nodeId = "node-after-network-rejection",
                routes = TestSupport.localRoutes("svc.echo"),
            ),
        ).close()
    }

    @Test
    fun openWithSeparateDeliveredRequestLaneExportsDedicatedReadFd() {
        val handle = RuntimeHandle.open(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "connector-kotlin-test",
                nodeId = "node-separate-lane",
                generation = 8,
                separateDeliveredRequestLane = true,
                routes = TestSupport.localRoutes("svc.echo"),
            ),
        )

        try {
            assertTrue(handle.hostHandles.delivered_request_read_fd >= 0)
            assertNotEquals(
                handle.hostHandles.response_read_fd,
                handle.hostHandles.delivered_request_read_fd,
            )
        } finally {
            handle.close()
        }
    }

    @Test
    fun openCanDisableSeparateDeliveredRequestLaneForAdvancedHosts() {
        val handle = RuntimeHandle.open(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "connector-kotlin-test",
                nodeId = "node-mixed-lane",
                separateDeliveredRequestLane = false,
                routes = TestSupport.localRoutes("svc.echo"),
            ),
        )

        try {
            assertTrue(handle.hostHandles.delivered_request_read_fd < 0)
        } finally {
            handle.close()
        }
    }

    @Test
    fun controlClientCanApplyNewerSnapshotAfterStart() {
        val handle = RuntimeHandle.open(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "connector-kotlin-test",
                nodeId = "node-control",
                generation = 1,
                routes = TestSupport.localRoutes("svc.echo"),
            ),
        )

        try {
            handle.start()
            handle.controlClient.applySnapshot(
                generation = 2,
                routes = TestSupport.localRoutes("svc.echo", "svc.audit"),
                sourceConnector = "connector-kotlin-test",
            )

            val health = handle.health()
            val stats = handle.stats()

            assertEquals(2, health.appliedGeneration)
            assertEquals(2, stats.appliedGeneration)
            assertEquals(2, stats.routeCount)
            assertTrue((health.flags and CoakkaHealthFlags.RUNTIME_STARTED) != 0)
            assertTrue((health.flags and CoakkaHealthFlags.DATAPLANE_READY) != 0)
        } finally {
            handle.close()
        }
    }

    @Test
    fun staleGenerationIsRejectedAfterStart() {
        val handle = RuntimeHandle.open(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "connector-kotlin-test",
                nodeId = "node-stale-generation",
                generation = 3,
                routes = TestSupport.localRoutes("svc.echo"),
            ),
        )

        try {
            handle.start()

            val error = assertFailsWith<IllegalStateException> {
                handle.controlClient.applySnapshot(
                    generation = 3,
                    routes = TestSupport.localRoutes("svc.echo"),
                    sourceConnector = "connector-kotlin-test",
                )
            }

            assertTrue(error.message?.contains("apply_control_envelope failed rc=-4") == true)
            assertEquals(3, handle.health().appliedGeneration)
        } finally {
            handle.close()
        }
    }

    @Test
    fun monitorReflectsInitialApplyStartAndControlUpdates() {
        val handle = RuntimeHandle.open(
            runtimeLibPath = TestSupport.runtimeLibPath(),
            startSpec = RuntimeStartSpec(
                systemName = "connector-kotlin-test",
                nodeId = "node-monitor",
                generation = 11,
                routes = TestSupport.localRoutes("svc.echo"),
            ),
        )

        try {
            assertTrue(handle.monitor.isEnabled())

            val initial = assertNotNull(handle.monitor.awaitNextBlocking(1_000))
            assertTrue(initial.signalCount > 0)
            assertEquals(11, initial.health.appliedGeneration)
            assertEquals(11, initial.stats.appliedGeneration)
            assertEquals(1, initial.stats.monitorEventEmittedLifetimeCount)
            assertTrue(initial.health.flagsText.isNotBlank())
            assertTrue(initial.stats.runtimeStateName.isNotBlank())

            handle.start()

            val started = assertNotNull(handle.monitor.awaitNextBlocking(1_000))
            assertTrue((started.health.flags and CoakkaHealthFlags.RUNTIME_STARTED) != 0)
            assertEquals(1, started.stats.monitorEventEmittedCount)
            assertEquals(2, started.stats.monitorEventEmittedLifetimeCount)

            handle.controlClient.applySnapshot(
                generation = 12,
                routes = TestSupport.localRoutes("svc.echo", "svc.audit"),
                sourceConnector = "connector-kotlin-test",
            )

            val updated = assertNotNull(handle.monitor.awaitNextBlocking(1_000))
            assertEquals(12, updated.health.appliedGeneration)
            assertEquals(12, updated.stats.appliedGeneration)
            assertEquals(2, updated.stats.routeCount)
            assertEquals(2, updated.stats.monitorEventEmittedCount)
            assertEquals(3, updated.stats.monitorEventEmittedLifetimeCount)
            assertNotEquals("", updated.health.runtimeStateName)
            assertNotEquals("", updated.health.flagsText)
        } finally {
            handle.close()
        }
    }
}
