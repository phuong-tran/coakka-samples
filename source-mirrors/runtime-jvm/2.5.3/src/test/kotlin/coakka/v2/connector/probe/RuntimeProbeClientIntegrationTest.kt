package coakka.v2.connector.probe

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuntimeProbeClientIntegrationTest {
    @Test
    fun planAndRunLocalEchoPingAgainstNativeProbeBinary() {
        val coreDir = RuntimeProbeWorkspace.resolveCoreDir()
        val executable = RuntimeProbeWorkspace.probeExecutablePath(coreDir)
        assertTrue(Files.isDirectory(coreDir), "expected sibling core dir at $coreDir")
        assertTrue(Files.isRegularFile(executable), "expected native probe binary at $executable")

        val client = RuntimeProbeClient(executable = executable)
        val selection = RuntimeProbeWorkspace.localEchoPingSelection(coreDir)

        val plan = client.plan(selection)
        assertEquals("ready", plan.selectionStatus)
        assertEquals("runnable_now", plan.runSupportStatus)
        assertEquals("embedded_runtime_smoke", plan.targetBridgeKind)
        assertEquals("runtime_smoke", plan.executionModeHint)
        assertEquals("runtime_public_c_abi", plan.runtimeTruthSourceHint)

        val result = client.run(selection)
        assertEquals("accepted", result.scenarioStatus)
        assertEquals("matched", result.expectationStatus)
        assertEquals("runtime_smoke", result.executionMode)
        assertEquals("reply", result.observedOutcome)
        assertNull(result.observedReasonCode)
        assertTrue(result.trace.size >= 5)
    }

    @Test
    fun runLocalEchoMissingRouteAgainstNativeProbeBinary() {
        val coreDir = RuntimeProbeWorkspace.resolveCoreDir()
        val executable = RuntimeProbeWorkspace.probeExecutablePath(coreDir)
        assertTrue(Files.isRegularFile(executable), "expected native probe binary at $executable")

        val client = RuntimeProbeClient(executable = executable)
        val selection = RuntimeProbeWorkspace.localEchoMissingRouteSelection(coreDir)

        val result = client.run(selection)
        assertEquals("accepted", result.scenarioStatus)
        assertEquals("matched", result.expectationStatus)
        assertEquals("deadletter", result.observedOutcome)
        assertEquals("ROUTE_MISS", result.observedReasonCode)
        assertEquals("runtime_public_c_abi", result.runtimeTruthSource)
        assertTrue(result.trace.size >= 5)
    }
}
