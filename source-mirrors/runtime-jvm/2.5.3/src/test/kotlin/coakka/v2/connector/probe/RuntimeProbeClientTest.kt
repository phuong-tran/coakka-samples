package coakka.v2.connector.probe

import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RuntimeProbeClientTest {
    @OptIn(ExperimentalPathApi::class)
    @Test
    fun planAndRunUseExpectedArgumentsAndParseTruth() {
        val tempDir = Files.createTempDirectory("probe-client-test")
        try {
            val logFile = tempDir.resolve("args.log")
            val script = tempDir.resolve("fake-probe.sh")
            script.writeText(
                """
                #!/bin/sh
                printf '%s\n' "$@" > "${logFile.toAbsolutePath()}"
                if [ "$1" = "plan" ]; then
                  cat <<'JSON'
                {
                  "selection_status": "ready",
                  "run_support_status": "runnable_now",
                  "target_bridge_kind": "docker_container_runtime_bridge",
                  "execution_mode_hint": "inventory_runtime_bridge",
                  "runtime_truth_source_hint": "runtime_public_c_abi",
                  "bridge_driver_candidate": "docker_container_runtime_bridge",
                  "next_slice_hint": "none",
                  "target_id": "docker.demo.worker",
                  "capability_id": "demo.notify",
                  "sample_id": "accepted_notify",
                  "inventory_id": "docker-demo-inventory",
                  "machine_id": "docker-compose-demo",
                  "runtime_instance_id": "worker-runtime",
                  "route_target": "svc.notify",
                  "interaction": "fire_and_forget",
                  "expected_outcome": "accepted",
                  "delivery_hint": "router_default"
                }
                JSON
                elif [ "$1" = "run" ]; then
                  cat <<'JSON'
                {
                  "result_version": 1,
                  "addon_id": "coakka.runtime.addon.probe",
                  "scenario_id": "docker.demo.worker.demo.notify.accepted_notify",
                  "interaction": "fire_and_forget",
                  "execution_mode": "inventory_runtime_bridge",
                  "scenario_status": "accepted",
                  "expectation_status": "matched",
                  "runtime_truth_source": "runtime_public_c_abi",
                  "expected_outcome": "accepted",
                  "observed_outcome": "accepted",
                  "target_bridge_kind": "docker_container_runtime_bridge",
                  "selected_target_id": "docker.demo.worker",
                  "selected_runtime_instance_id": "worker-runtime",
                  "trace": [
                    {
                      "sequence": 1,
                      "monotonic_time_ns": 10,
                      "event": "scenario_loaded",
                      "message": "loaded"
                    },
                    {
                      "sequence": 2,
                      "monotonic_time_ns": 20,
                      "event": "expectation_evaluated",
                      "message": "matched"
                    }
                  ]
                }
                JSON
                else
                  echo "unexpected command" >&2
                  exit 3
                fi
                """.trimIndent() + "\n",
            )
            script.toFile().setExecutable(true)

            val client = RuntimeProbeClient(script)
            val selection = RuntimeProbeSelection(
                inventoryPath = "/tmp/inventory.json",
                metadataPaths = listOf("/tmp/echo.json", "/tmp/notify.json"),
                targetId = "docker.demo.worker",
                capabilityId = "demo.notify",
                sampleId = "accepted_notify",
            )

            val plan = client.plan(selection)
            assertEquals("runnable_now", plan.runSupportStatus)
            assertEquals("docker_container_runtime_bridge", plan.targetBridgeKind)
            assertEquals("accepted", plan.expectedOutcome)

            val run = client.run(selection)
            assertEquals("accepted", run.observedOutcome)
            assertEquals("inventory_runtime_bridge", run.executionMode)
            assertEquals(2, run.trace.size)

            val args = logFile.readLines()
            assertEquals("run", args[0])
            assertEquals("--inventory", args[1])
            assertEquals("/tmp/inventory.json", args[2])
            assertEquals("--metadata", args[3])
            assertEquals("/tmp/echo.json", args[4])
            assertEquals("--metadata", args[5])
            assertEquals("/tmp/notify.json", args[6])
            assertEquals("--target-id", args[7])
            assertEquals("docker.demo.worker", args[8])
            assertEquals("--capability-id", args[9])
            assertEquals("demo.notify", args[10])
            assertEquals("--sample-id", args[11])
            assertEquals("accepted_notify", args[12])
            assertEquals("--json", args[13])
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun nonZeroExitRaisesProbeException() {
        val runner = RuntimeProbeProcessRunner { _, _, _ ->
            RuntimeProbeProcessResult(
                stdout = "",
                stderr = "boom",
                exitCode = 17,
            )
        }
        val client = RuntimeProbeClient(
            executable = Files.createTempFile("probe-client", ".bin"),
            processRunner = runner,
        )

        val error = assertFailsWith<RuntimeProbeException> {
            client.plan(
                RuntimeProbeSelection(
                    inventoryPath = "/tmp/inventory.json",
                    metadataPaths = listOf("/tmp/notify.json"),
                    targetId = "docker.demo.worker",
                    capabilityId = "demo.notify",
                    sampleId = "remote_failure_probe",
                ),
            )
        }

        assertEquals(17, error.exitCode)
        assertEquals("boom", error.stderr)
    }

    @Test
    fun defaultResolverHonorsExplicitBinaryProperty() {
        val previous = System.getProperty("coakka.runtime.probe.bin")
        val fakePath = "/tmp/fake-probe-bin"
        try {
            System.setProperty("coakka.runtime.probe.bin", fakePath)
            assertTrue(RuntimeProbeClient.resolveDefaultExecutable().toString().endsWith("fake-probe-bin"))
        } finally {
            if (previous == null) {
                System.clearProperty("coakka.runtime.probe.bin")
            } else {
                System.setProperty("coakka.runtime.probe.bin", previous)
            }
        }
    }
}
