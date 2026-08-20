package coakka.v2.connector.probe

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class RuntimeProbeException(
    message: String,
    val exitCode: Int? = null,
    val stdout: String? = null,
    val stderr: String? = null,
) : RuntimeException(message)

data class RuntimeProbeSelection(
    val inventoryPath: String,
    val metadataPaths: List<String>,
    val targetId: String,
    val capabilityId: String,
    val sampleId: String,
) {
    init {
        require(inventoryPath.isNotBlank()) { "inventoryPath must not be blank" }
        require(metadataPaths.isNotEmpty()) { "metadataPaths must not be empty" }
        require(metadataPaths.all { it.isNotBlank() }) { "metadataPaths must not contain blank entries" }
        require(targetId.isNotBlank()) { "targetId must not be blank" }
        require(capabilityId.isNotBlank()) { "capabilityId must not be blank" }
        require(sampleId.isNotBlank()) { "sampleId must not be blank" }
    }
}

enum class RuntimeProbeCommand(val wireName: String) {
    PLAN("plan"),
    RUN("run"),
}

data class RuntimeProbeTraceEntry(
    val sequence: Long,
    val monotonicTimeNs: Long,
    val event: String,
    val message: String,
)

data class RuntimeProbePlan(
    val selectionStatus: String,
    val runSupportStatus: String,
    val targetBridgeKind: String,
    val executionModeHint: String,
    val runtimeTruthSourceHint: String,
    val bridgeDriverCandidate: String,
    val nextSliceHint: String,
    val targetId: String,
    val capabilityId: String,
    val sampleId: String,
    val inventoryId: String?,
    val machineId: String?,
    val runtimeInstanceId: String?,
    val routeTarget: String?,
    val interaction: String?,
    val expectedOutcome: String?,
    val deliveryHint: String?,
    val runBlocker: String?,
    val rawJson: String,
)

data class RuntimeProbeResult(
    val resultVersion: Long,
    val addonId: String,
    val scenarioId: String,
    val interaction: String,
    val executionMode: String,
    val scenarioStatus: String,
    val expectationStatus: String,
    val runtimeTruthSource: String,
    val expectedOutcome: String?,
    val observedOutcome: String?,
    val observedReasonCode: String?,
    val observedDetail: String?,
    val rejectionReasonCode: String?,
    val targetBridgeKind: String?,
    val selectedTargetId: String?,
    val selectedRuntimeInstanceId: String?,
    val trace: List<RuntimeProbeTraceEntry>,
    val rawJson: String,
)

fun interface RuntimeProbeProcessRunner {
    fun run(
        executable: Path,
        command: RuntimeProbeCommand,
        selection: RuntimeProbeSelection,
    ): RuntimeProbeProcessResult
}

data class RuntimeProbeProcessResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
)

class RuntimeProbeClient @JvmOverloads constructor(
    private val executable: Path = resolveDefaultExecutable(),
    private val processRunner: RuntimeProbeProcessRunner = DefaultRuntimeProbeProcessRunner,
) {
    fun plan(selection: RuntimeProbeSelection): RuntimeProbePlan {
        val output = invoke(RuntimeProbeCommand.PLAN, selection)
        return parsePlan(output.stdout)
    }

    fun run(selection: RuntimeProbeSelection): RuntimeProbeResult {
        val output = invoke(RuntimeProbeCommand.RUN, selection)
        return parseResult(output.stdout)
    }

    private fun invoke(
        command: RuntimeProbeCommand,
        selection: RuntimeProbeSelection,
    ): RuntimeProbeProcessResult {
        val result = processRunner.run(executable, command, selection)
        if (result.exitCode != 0) {
            throw RuntimeProbeException(
                message = "probe ${command.wireName} failed exitCode=${result.exitCode}",
                exitCode = result.exitCode,
                stdout = result.stdout,
                stderr = result.stderr,
            )
        }
        if (result.stdout.isBlank()) {
            throw RuntimeProbeException(
                message = "probe ${command.wireName} returned empty stdout",
                exitCode = result.exitCode,
                stdout = result.stdout,
                stderr = result.stderr,
            )
        }
        return result
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = false
            explicitNulls = false
        }

        @JvmStatic
        fun resolveDefaultExecutable(): Path {
            val explicitBinary = System.getProperty("coakka.runtime.probe.bin")
                ?: System.getenv("COAKKA_RUNTIME_PROBE_BIN")
            if (!explicitBinary.isNullOrBlank()) {
                return Paths.get(explicitBinary).toAbsolutePath().normalize()
            }

            return RuntimeProbeWorkspace.probeExecutablePath()
        }

        internal fun parsePlan(text: String): RuntimeProbePlan {
            val root = parseObject(text)
            return RuntimeProbePlan(
                selectionStatus = root.requireString("selection_status"),
                runSupportStatus = root.requireString("run_support_status"),
                targetBridgeKind = root.requireString("target_bridge_kind"),
                executionModeHint = root.requireString("execution_mode_hint"),
                runtimeTruthSourceHint = root.requireString("runtime_truth_source_hint"),
                bridgeDriverCandidate = root.requireString("bridge_driver_candidate"),
                nextSliceHint = root.requireString("next_slice_hint"),
                targetId = root.requireString("target_id"),
                capabilityId = root.requireString("capability_id"),
                sampleId = root.requireString("sample_id"),
                inventoryId = root.optionalString("inventory_id"),
                machineId = root.optionalString("machine_id"),
                runtimeInstanceId = root.optionalString("runtime_instance_id"),
                routeTarget = root.optionalString("route_target"),
                interaction = root.optionalString("interaction"),
                expectedOutcome = root.optionalString("expected_outcome"),
                deliveryHint = root.optionalString("delivery_hint"),
                runBlocker = root.optionalString("run_blocker"),
                rawJson = text.trim(),
            )
        }

        internal fun parseResult(text: String): RuntimeProbeResult {
            val root = parseObject(text)
            return RuntimeProbeResult(
                resultVersion = root.requireLong("result_version"),
                addonId = root.requireString("addon_id"),
                scenarioId = root.requireString("scenario_id"),
                interaction = root.requireString("interaction"),
                executionMode = root.requireString("execution_mode"),
                scenarioStatus = root.requireString("scenario_status"),
                expectationStatus = root.requireString("expectation_status"),
                runtimeTruthSource = root.requireString("runtime_truth_source"),
                expectedOutcome = root.optionalString("expected_outcome"),
                observedOutcome = root.optionalString("observed_outcome"),
                observedReasonCode = root.optionalString("observed_reason_code"),
                observedDetail = root.optionalString("observed_detail"),
                rejectionReasonCode = root.optionalString("rejection_reason_code"),
                targetBridgeKind = root.optionalString("target_bridge_kind"),
                selectedTargetId = root.optionalString("selected_target_id"),
                selectedRuntimeInstanceId = root.optionalString("selected_runtime_instance_id"),
                trace = root.requireTrace("trace"),
                rawJson = text.trim(),
            )
        }

        private fun parseObject(text: String): JsonObject =
            try {
                json.parseToJsonElement(text).jsonObject
            } catch (ex: Exception) {
                throw RuntimeProbeException("failed to parse probe json: ${ex.message}")
            }
    }
}

object RuntimeProbeWorkspace {
    private fun findSiblingCoreDir(start: Path): Path? {
        var current: Path? = start.toAbsolutePath().normalize()
        while (current != null) {
            val candidate = current.resolveSibling("coakkaCoreNativeDev").normalize()
            if (Files.isDirectory(candidate)) {
                return candidate
            }
            current = current.parent
        }
        return null
    }

    @JvmStatic
    fun resolveCoreDir(): Path {
        val explicitCoreDir = System.getProperty("coakka.core.dir")
            ?: System.getenv("COAKKA_CORE_DIR")
        if (!explicitCoreDir.isNullOrBlank()) {
            return Paths.get(explicitCoreDir).toAbsolutePath().normalize()
        }
        val currentDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return findSiblingCoreDir(currentDir)
            ?: currentDir.resolve("../coakkaCoreNativeDev").normalize()
    }

    @JvmStatic
    fun localDevInventoryPath(coreDir: Path = resolveCoreDir()): Path =
        coreDir.resolve("v2/addons/runtime-addon-probe/example.target_inventory.local_dev.json")

    @JvmStatic
    fun echoMetadataPath(coreDir: Path = resolveCoreDir()): Path =
        coreDir.resolve("v2/addons/runtime-addon-probe/example.capability.echo.metadata.json")

    @JvmStatic
    fun notifyMetadataPath(coreDir: Path = resolveCoreDir()): Path =
        coreDir.resolve("v2/addons/runtime-addon-probe/example.capability.notify.metadata.json")

    @JvmStatic
    fun probeExecutablePath(coreDir: Path = resolveCoreDir()): Path =
        coreDir.resolve("build-v2/coakka_v2_runtime_addon_probe_ui_alpha")

    @JvmStatic
    fun localEchoPingSelection(coreDir: Path = resolveCoreDir()): RuntimeProbeSelection =
        RuntimeProbeSelection(
            inventoryPath = localDevInventoryPath(coreDir).toString(),
            metadataPaths = listOf(echoMetadataPath(coreDir).toString()),
            targetId = "macbook.dev.web",
            capabilityId = "demo.echo",
            sampleId = "ping",
        )

    @JvmStatic
    fun localEchoMissingRouteSelection(coreDir: Path = resolveCoreDir()): RuntimeProbeSelection =
        RuntimeProbeSelection(
            inventoryPath = localDevInventoryPath(coreDir).toString(),
            metadataPaths = listOf(echoMetadataPath(coreDir).toString()),
            targetId = "macbook.dev.web",
            capabilityId = "demo.echo",
            sampleId = "missing_route_probe",
        )
}

object DefaultRuntimeProbeProcessRunner : RuntimeProbeProcessRunner {
    override fun run(
        executable: Path,
        command: RuntimeProbeCommand,
        selection: RuntimeProbeSelection,
    ): RuntimeProbeProcessResult {
        val resolvedExecutable = executable.toAbsolutePath().normalize()
        if (!Files.isRegularFile(resolvedExecutable)) {
            throw RuntimeProbeException("probe executable does not exist: $resolvedExecutable")
        }
        if (!Files.isExecutable(resolvedExecutable)) {
            throw RuntimeProbeException("probe executable is not executable: $resolvedExecutable")
        }

        val arguments = mutableListOf(
            resolvedExecutable.toString(),
            command.wireName,
            "--inventory",
            selection.inventoryPath,
        )
        selection.metadataPaths.forEach { metadataPath ->
            arguments += listOf("--metadata", metadataPath)
        }
        arguments += listOf(
            "--target-id", selection.targetId,
            "--capability-id", selection.capabilityId,
            "--sample-id", selection.sampleId,
            "--json",
        )

        val process = ProcessBuilder(arguments)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .start()

        val stdoutBuffer = ByteArrayOutputStream()
        val stderrBuffer = ByteArrayOutputStream()
        val stdoutThread = streamPump(process.inputStream, stdoutBuffer)
        val stderrThread = streamPump(process.errorStream, stderrBuffer)
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            stdoutThread.join(1_000)
            stderrThread.join(1_000)
            throw RuntimeProbeException(
                message = "probe ${command.wireName} timed out after 60s",
                stdout = stdoutBuffer.toString(StandardCharsets.UTF_8.name()),
                stderr = stderrBuffer.toString(StandardCharsets.UTF_8.name()),
            )
        }
        stdoutThread.join(1_000)
        stderrThread.join(1_000)

        return RuntimeProbeProcessResult(
            stdout = stdoutBuffer.toString(StandardCharsets.UTF_8.name()),
            stderr = stderrBuffer.toString(StandardCharsets.UTF_8.name()),
            exitCode = process.exitValue(),
        )
    }

    private fun streamPump(
        input: java.io.InputStream,
        output: ByteArrayOutputStream,
    ): Thread =
        Thread {
            input.use { stream ->
                stream.copyTo(output)
            }
        }.apply { start() }
}

private fun JsonObject.requireString(key: String): String =
    optionalString(key) ?: throw RuntimeProbeException("missing string field: $key")

private fun JsonObject.optionalString(key: String): String? {
    val element = this[key] ?: return null
    if (element is JsonNull) {
        return null
    }
    return (element as? JsonPrimitive)?.contentOrNull
        ?: throw RuntimeProbeException("field $key must be a string")
}

private fun JsonObject.requireLong(key: String): Long {
    val element = this[key] ?: throw RuntimeProbeException("missing numeric field: $key")
    val primitive = element as? JsonPrimitive
        ?: throw RuntimeProbeException("field $key must be numeric")
    return primitive.longOrThrow(key)
}

private fun JsonObject.requireTrace(key: String): List<RuntimeProbeTraceEntry> {
    val element = this[key] ?: throw RuntimeProbeException("missing trace field: $key")
    val array = (element as? JsonArray)
        ?: throw RuntimeProbeException("field $key must be an array")
    return array.mapIndexed { index, entry ->
        val obj = (entry as? JsonObject)
            ?: throw RuntimeProbeException("trace[$index] must be an object")
        RuntimeProbeTraceEntry(
            sequence = obj.requireLong("sequence"),
            monotonicTimeNs = obj.requireLong("monotonic_time_ns"),
            event = obj.requireString("event"),
            message = obj.requireString("message"),
        )
    }
}

private fun JsonPrimitive.longOrThrow(key: String): Long {
    val directLong = contentOrNull?.toLongOrNull()
    if (directLong != null) {
        return directLong
    }
    val doubleValue = doubleOrNull
        ?: throw RuntimeProbeException("field $key must be numeric")
    return doubleValue.toLong()
}
