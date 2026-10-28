package coakka.samples.runtime.scenarios.customer.starterlocal

import coakka.samples.runtime.scenarios.customer.diagnostics.DeadletterView
import coakka.samples.runtime.scenarios.customer.contract.ListCustomersRequest
import coakka.samples.runtime.scenarios.customer.contract.ListResponse
import coakka.samples.runtime.scenarios.customer.contract.RuntimeDiagnosticsView
import coakka.spring.CoAkkaRuntimeClient
import coakka.v2.connector.DeadletterException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/customers")
class CustomerRuntimeDiagnosticsController(private val runtimeClient: CoAkkaRuntimeClient) {
    @PostMapping("/route-miss")
    fun routeMiss(): DeadletterView {
        try {
            runtimeClient.askBlocking(
                CustomerTargets.MISSING,
                ListCustomersRequest(requestedBy = "route-miss-smoke"),
                ListResponse::class.java,
                "route_miss",
                2_000,
            )
            error("expected route-miss deadletter")
        } catch (error: DeadletterException) {
            return DeadletterView(
                reason = error.deadletter.reason.value,
                target = error.deadletter.originalEnvelope?.target,
                generation = error.deadletter.activeGeneration,
            )
        }
    }

    @GetMapping("/runtime")
    fun runtimeDiagnostics(): RuntimeDiagnosticsView {
        val runtimeInfo = runtimeClient.runtimeInfo()
        val runtimeConfig = runtimeClient.runtimeConfig()
        val clientStats = runtimeClient.clientStats()
        val startup = runtimeClient.startupConfig()
        return RuntimeDiagnosticsView(
            runtimeInfo = mapOf(
                "abiVersion" to runtimeInfo.abiVersion,
                "runtimeVersion" to runtimeInfo.runtimeVersion,
                "gitCommit" to runtimeInfo.gitCommit,
                "featureFlagsText" to runtimeInfo.featureFlagsText,
            ),
            runtimeConfig = mapOf(
                "systemName" to runtimeConfig.systemName,
                "nodeId" to runtimeConfig.nodeId,
                "runtimeStateName" to runtimeConfig.runtimeStateName,
                "appliedGeneration" to runtimeConfig.appliedGeneration,
                "routeCount" to runtimeConfig.routeCount,
                "requestedCpuPolicy" to startup.execution.requestedPolicy,
                "cpuPlacementSupported" to startup.execution.supported,
                "cpuPlacementApplied" to startup.execution.startupApplied,
                "selectedCpuIds" to startup.execution.selectedCpuIds,
                "connectionMode" to startup.connection.mode.value,
                "monitorEnabled" to startup.monitorEnabled,
                "defaultedStartupGroups" to startup.defaultedGroups,
            ),
            clientStats = mapOf(
                "pendingRequests" to clientStats.pendingRequests,
                "deliveredRequests" to clientStats.deliveredRequests,
                "matchedResponses" to clientStats.matchedResponses,
                "matchedDeadletters" to clientStats.matchedDeadletters,
                "lateResponses" to clientStats.lateResponses,
                "unhandledDeadletters" to clientStats.unhandledDeadletters,
            ),
            connector = mapOf(
                "serviceRole" to "customer-starter-local",
                "demoMode" to "spring-boot-starter-local",
                "businessTransport" to "runtime-local-capability",
                "remoteTransport" to "not-exercised",
                "frontendTarget" to "samples.customer.frontend",
                "capabilityTargets" to listOf(
                    CustomerTargets.CREATE,
                    CustomerTargets.UPDATE,
                    CustomerTargets.DELETE,
                    CustomerTargets.LIST,
                ),
            ),
        )
    }
}
