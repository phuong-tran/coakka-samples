package coakka.samples.runtime.scenarios.customer.starterlocal

import coakka.samples.runtime.scenarios.customer.contract.CustomerDraft
import coakka.samples.runtime.scenarios.customer.contract.CustomerView
import coakka.samples.runtime.scenarios.customer.contract.DeadletterView
import coakka.samples.runtime.scenarios.customer.contract.DeleteCustomerRequest
import coakka.samples.runtime.scenarios.customer.contract.ListCustomersRequest
import coakka.samples.runtime.scenarios.customer.contract.ListResponse
import coakka.samples.runtime.scenarios.customer.contract.MutationResponse
import coakka.samples.runtime.scenarios.customer.contract.RuntimeDeliveryFailureView
import coakka.samples.runtime.scenarios.customer.contract.RuntimeDiagnosticsView
import coakka.samples.runtime.scenarios.customer.contract.UpdateCustomerRequest
import coakka.spring.CoAkkaHandler
import coakka.spring.CoAkkaRuntimeClient
import coakka.v2.connector.DeadletterException
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@SpringBootApplication
class CustomerStarterLocalApplication {
    @Bean
    fun startupLog(runtimeClient: CoAkkaRuntimeClient) = ApplicationRunner {
        val info = runtimeClient.runtimeInfo()
        val config = runtimeClient.runtimeConfig()
        LoggerFactory.getLogger("CustomerStarterLocalStartup").info(
            "customer-starter-local ready runtimeVersion={} backend={} node={} routes={}",
            info.runtimeVersion,
            info.southboundBackend,
            config.nodeId,
            config.routeCount,
        )
    }
}

fun main(args: Array<String>) {
    runApplication<CustomerStarterLocalApplication>(*args)
}

object CustomerTargets {
    const val CREATE = "samples.customer.create"
    const val UPDATE = "samples.customer.update"
    const val DELETE = "samples.customer.delete"
    const val LIST = "samples.customer.list"
    const val MISSING = "samples.customer.missing"
}

@Service
class InMemoryCustomerStore {
    private val customers = ConcurrentHashMap<String, CustomerView>()
    private val revision = AtomicLong(0)

    fun create(customer: CustomerDraft): MutationResponse {
        val nextRevision = revision.incrementAndGet()
        customers[customer.id] = customer.toView(nextRevision)
        return mutation("create", customer.id, nextRevision)
    }

    fun update(customer: CustomerDraft): MutationResponse {
        val nextRevision = revision.incrementAndGet()
        customers[customer.id] = customer.toView(nextRevision)
        return mutation("update", customer.id, nextRevision)
    }

    fun delete(id: String): MutationResponse {
        customers.remove(id)
        val nextRevision = revision.incrementAndGet()
        return mutation("delete", id, nextRevision)
    }

    fun list(): ListResponse = ListResponse(
        customers = customers.values.sortedWith(compareBy<CustomerView> { it.id }.thenBy { it.revision }),
    )

    private fun CustomerDraft.toView(nextRevision: Long) = CustomerView(
        id = id,
        name = name,
        email = email,
        tier = tier,
        notes = notes,
        revision = nextRevision,
    )

    private fun mutation(operation: String, id: String, nextRevision: Long) = MutationResponse(
        status = "ACCEPTED",
        operation = operation,
        customerId = id,
        revision = nextRevision,
        handledBy = "customer-starter-local-handler",
    )
}

@Component
class CustomerCapabilityHandlers(private val customerStore: InMemoryCustomerStore) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @CoAkkaHandler(CustomerTargets.CREATE)
    fun create(command: CustomerDraft): MutationResponse {
        logger.info("starter handler create id={} name={}", command.id, command.name)
        return customerStore.create(command)
    }

    @CoAkkaHandler(CustomerTargets.UPDATE)
    fun update(command: CustomerDraft): MutationResponse {
        logger.info("starter handler update id={} tier={}", command.id, command.tier)
        return customerStore.update(command)
    }

    @CoAkkaHandler(CustomerTargets.DELETE)
    fun delete(command: DeleteCustomerRequest): MutationResponse {
        logger.info("starter handler delete id={}", command.id)
        return customerStore.delete(command.id)
    }

    @CoAkkaHandler(CustomerTargets.LIST)
    fun list(command: ListCustomersRequest): ListResponse {
        logger.info("starter handler list requestedBy={}", command.requestedBy)
        return customerStore.list()
    }
}

@RestController
@RequestMapping("/api/customers")
class CustomerStarterLocalController(private val runtimeClient: CoAkkaRuntimeClient) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun listCustomers(): ListResponse {
        return runtimeClient.askBlocking(
            CustomerTargets.LIST,
            ListCustomersRequest(requestedBy = "customer-starter-local"),
            ListResponse::class.java,
            "list_customers",
            5_000,
        )
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCustomer(@RequestBody request: CustomerDraft): MutationResponse {
        logger.info("customer-web create id={} name={} tier={}", request.id, request.name, request.tier)
        return runtimeClient.askBlocking(
            CustomerTargets.CREATE,
            request,
            MutationResponse::class.java,
            "create_customer",
            5_000,
        )
    }

    @PutMapping("/{id}")
    fun updateCustomer(
        @PathVariable id: String,
        @RequestBody request: UpdateCustomerRequest,
    ): MutationResponse {
        val payload = CustomerDraft(
            id = id,
            name = request.name,
            email = request.email,
            tier = request.tier,
            notes = request.notes,
        )
        logger.info("customer-web update id={} tier={}", id, request.tier)
        return runtimeClient.askBlocking(
            CustomerTargets.UPDATE,
            payload,
            MutationResponse::class.java,
            "update_customer",
            5_000,
        )
    }

    @DeleteMapping("/{id}")
    fun deleteCustomer(@PathVariable id: String): MutationResponse {
        logger.info("customer-web delete id={}", id)
        return runtimeClient.askBlocking(
            CustomerTargets.DELETE,
            DeleteCustomerRequest(id = id),
            MutationResponse::class.java,
            "delete_customer",
            5_000,
        )
    }

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
                reason = error.deadletter.reason,
                target = error.deadletter.originalEnvelope.target,
                generation = error.deadletter.activeGeneration,
            )
        }
    }

    @GetMapping("/runtime")
    fun runtimeDiagnostics(): RuntimeDiagnosticsView {
        val runtimeInfo = runtimeClient.runtimeInfo()
        val runtimeConfig = runtimeClient.runtimeConfig()
        val clientStats = runtimeClient.clientStats()
        return RuntimeDiagnosticsView(
            runtimeInfo = mapOf(
                "abiVersion" to runtimeInfo.abiVersion,
                "runtimeVersion" to runtimeInfo.runtimeVersion,
                "gitCommit" to runtimeInfo.gitCommit,
                "southboundBackend" to runtimeInfo.southboundBackend,
                "allocatorBackend" to runtimeInfo.allocatorBackend,
                "featureFlagsText" to runtimeInfo.featureFlagsText,
            ),
            runtimeConfig = mapOf(
                "systemName" to runtimeConfig.systemName,
                "nodeId" to runtimeConfig.nodeId,
                "runtimeStateName" to runtimeConfig.runtimeStateName,
                "appliedGeneration" to runtimeConfig.appliedGeneration,
                "routeCount" to runtimeConfig.routeCount,
                "southboundBindHost" to (runtimeConfig.southboundBindHost ?: ""),
                "southboundBindPort" to runtimeConfig.southboundBindPort,
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
                "configuredGeneration" to 1,
            ),
        )
    }
}

@RestControllerAdvice
class RuntimeErrorAdvice {
    @ExceptionHandler(DeadletterException::class)
    fun deadletter(error: DeadletterException): ResponseEntity<RuntimeDeliveryFailureView> {
        val deadletter = error.deadletter
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
            RuntimeDeliveryFailureView(
                status = "RUNTIME_DELIVERY_FAILED",
                reason = deadletter.reason,
                detail = deadletter.detail,
                target = deadletter.originalEnvelope.target,
                resolvedEndpoint = "${deadletter.resolvedHost}:${deadletter.resolvedPort}",
                hint = "This starter-local scenario expects @CoAkkaHandler local capability routes.",
            ),
        )
    }
}
