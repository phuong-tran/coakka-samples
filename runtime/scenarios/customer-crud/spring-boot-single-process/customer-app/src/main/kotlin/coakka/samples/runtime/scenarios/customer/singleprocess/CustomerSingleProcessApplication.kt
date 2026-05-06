package coakka.samples.runtime.scenarios.customer.singleprocess

import coakka.samples.runtime.scenarios.customer.contract.CustomerDraft
import coakka.samples.runtime.scenarios.customer.contract.CustomerMessageTypes
import coakka.samples.runtime.scenarios.customer.contract.CustomerPayloadContract
import coakka.samples.runtime.scenarios.customer.contract.CustomerView
import coakka.samples.runtime.scenarios.customer.contract.DeadletterView
import coakka.samples.runtime.scenarios.customer.contract.DeleteCustomerRequest
import coakka.samples.runtime.scenarios.customer.contract.ListCustomersRequest
import coakka.samples.runtime.scenarios.customer.contract.ListResponse
import coakka.samples.runtime.scenarios.customer.contract.MutationResponse
import coakka.samples.runtime.scenarios.customer.contract.RuntimeDeliveryFailureView
import coakka.samples.runtime.scenarios.customer.contract.RuntimeDiagnosticsView
import coakka.samples.runtime.scenarios.customer.contract.UpdateCustomerRequest
import coakka.v2.connector.ConnectorOrchestrator
import coakka.v2.connector.DeadletterException
import coakka.v2.connector.RuntimeClient
import coakka.v2.connector.RuntimeEndpointFlags
import coakka.v2.connector.RuntimeEndpointSpec
import coakka.v2.connector.RuntimeRouteSpec
import coakka.v2.connector.RuntimeStartSpec
import coakka.v2.connector.protocol.ConnectorDeliveryHint
import coakka.v2.connector.protocol.ConnectorEnvelope
import coakka.v2.connector.protocol.ConnectorPayloadFormat
import coakka.v2.connector.protocol.ConnectorPayloadIdentity
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
@EnableConfigurationProperties(CustomerSingleProcessProperties::class)
class CustomerSingleProcessApplication

fun main(args: Array<String>) {
    runApplication<CustomerSingleProcessApplication>(*args)
}

object CustomerPayloads {
    val CREATE = identity(CustomerMessageTypes.CREATE_REQUEST)
    val UPDATE = identity(CustomerMessageTypes.UPDATE_REQUEST)
    val DELETE = identity(CustomerMessageTypes.DELETE_REQUEST)
    val LIST = identity(CustomerMessageTypes.LIST_REQUEST)
    val MUTATION_RESPONSE = identity(CustomerMessageTypes.MUTATION_RESPONSE)
    val LIST_RESPONSE = identity(CustomerMessageTypes.LIST_RESPONSE)

    private fun identity(messageType: String) = ConnectorPayloadIdentity(
        messageType = messageType,
        payloadSchemaVersion = CustomerPayloadContract.SCHEMA_VERSION,
        payloadFormat = payloadFormat(),
    )

    private fun payloadFormat(): ConnectorPayloadFormat = when (CustomerPayloadContract.FORMAT) {
        "JSON" -> ConnectorPayloadFormat.JSON
        else -> error("unsupported customer payload format: ${CustomerPayloadContract.FORMAT}")
    }
}

@ConfigurationProperties("sample.connector")
data class CustomerSingleProcessProperties(
    var systemName: String = "customer-single-process",
    var nodeId: String = "customer-single-process-node",
    var frontendTarget: String = "samples.customer.frontend",
    var frontendHost: String = "127.0.0.1",
    var frontendPort: Int = 19141,
    var storeTarget: String = "samples.customer.store",
    var storeHost: String = "127.0.0.1",
    var storePort: Int = 19142,
    var generation: Long = 1,
)

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
        handledBy = "customer-store-local-handler",
    )
}

class ManagedConnector(val orchestrator: ConnectorOrchestrator) : AutoCloseable {
    override fun close() {
        runBlocking { orchestrator.kotlin.shutdown() }
    }
}

@Configuration
class CustomerSingleProcessConfiguration {
    @Bean
    fun customerStore(): InMemoryCustomerStore = InMemoryCustomerStore()

    @Bean
    fun runtimeClient(
        properties: CustomerSingleProcessProperties,
        objectMapper: ObjectMapper,
        customerStore: InMemoryCustomerStore,
    ): ManagedConnector {
        val orchestrator = ConnectorOrchestrator.start(
            startSpec = RuntimeStartSpec(
                systemName = properties.systemName,
                nodeId = properties.nodeId,
                queueCapacity = 128,
                strictNoDrop = true,
                separateDeliveredRequestLane = true,
                generation = properties.generation,
                routes = listOf(
                    RuntimeRouteSpec(
                        target = properties.storeTarget,
                        endpoints = listOf(
                            RuntimeEndpointSpec(
                                host = properties.storeHost,
                                port = properties.storePort,
                                flags = RuntimeEndpointFlags.LOCAL,
                            ),
                        ),
                    ),
                ),
            ),
        )

        val logger = LoggerFactory.getLogger("CustomerSingleProcessStoreHandler")
        orchestrator.registerHandler(properties.storeTarget) { request ->
            val reply = handleCustomerRequest(request, objectMapper, customerStore, logger)
            RuntimeClient.replyTo(
                request = request,
                source = properties.storeTarget,
                payloadUtf8 = objectMapper.writeValueAsString(reply.payload),
                payloadIdentity = reply.identity,
            )
        }

        return ManagedConnector(orchestrator)
    }

    @Bean
    fun connectorStartupLog(
        managedConnector: ManagedConnector,
        properties: CustomerSingleProcessProperties,
    ) = ApplicationRunner {
        val logger = LoggerFactory.getLogger("CustomerSingleProcessStartup")
        val info = managedConnector.orchestrator.runtimeInfo()
        val config = managedConnector.orchestrator.runtimeConfig()
        logger.info(
            "customer-single-process ready runtimeVersion={} backend={} node={} frontendTarget={} storeTarget={} routeCount={}",
            info.runtimeVersion,
            info.southboundBackend,
            config.nodeId,
            properties.frontendTarget,
            properties.storeTarget,
            config.routeCount,
        )
    }
}

data class ReplyPayload(val payload: Any, val identity: ConnectorPayloadIdentity)

suspend fun handleCustomerRequest(
    request: ConnectorEnvelope,
    objectMapper: ObjectMapper,
    customerStore: InMemoryCustomerStore,
    logger: org.slf4j.Logger,
): ReplyPayload = when (request.messageType) {
    CustomerPayloads.CREATE.messageType -> {
        val customer = objectMapper.readValue(request.payload, CustomerDraft::class.java)
        logger.info("local store create id={} name={} correlation={}", customer.id, customer.name, request.correlationId)
        ReplyPayload(customerStore.create(customer), CustomerPayloads.MUTATION_RESPONSE)
    }
    CustomerPayloads.UPDATE.messageType -> {
        val customer = objectMapper.readValue(request.payload, CustomerDraft::class.java)
        logger.info("local store update id={} tier={} correlation={}", customer.id, customer.tier, request.correlationId)
        ReplyPayload(customerStore.update(customer), CustomerPayloads.MUTATION_RESPONSE)
    }
    CustomerPayloads.DELETE.messageType -> {
        val delete = objectMapper.readValue(request.payload, DeleteCustomerRequest::class.java)
        logger.info("local store delete id={} correlation={}", delete.id, request.correlationId)
        ReplyPayload(customerStore.delete(delete.id), CustomerPayloads.MUTATION_RESPONSE)
    }
    CustomerPayloads.LIST.messageType -> {
        val list = objectMapper.readValue(request.payload, ListCustomersRequest::class.java)
        logger.info("local store list requestedBy={} correlation={}", list.requestedBy, request.correlationId)
        ReplyPayload(customerStore.list(), CustomerPayloads.LIST_RESPONSE)
    }
    else -> error("unsupported customer message type: ${request.messageType}")
}

@RestController
@RequestMapping("/api/customers")
class CustomerSingleProcessController(
    private val managedConnector: ManagedConnector,
    private val objectMapper: ObjectMapper,
    private val properties: CustomerSingleProcessProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun listCustomers(): ListResponse = runBlocking {
        ask(
            payload = ListCustomersRequest(requestedBy = properties.systemName),
            identity = CustomerPayloads.LIST,
            operation = "list_customers",
            responseType = ListResponse::class.java,
        )
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCustomer(@RequestBody request: CustomerDraft): MutationResponse = runBlocking {
        logger.info("customer-web create id={} name={} tier={}", request.id, request.name, request.tier)
        ask(
            payload = request,
            identity = CustomerPayloads.CREATE,
            operation = "create_customer",
            responseType = MutationResponse::class.java,
        )
    }

    @PutMapping("/{id}")
    fun updateCustomer(
        @PathVariable id: String,
        @RequestBody request: UpdateCustomerRequest,
    ): MutationResponse = runBlocking {
        val payload = CustomerDraft(
            id = id,
            name = request.name,
            email = request.email,
            tier = request.tier,
            notes = request.notes,
        )
        logger.info("customer-web update id={} tier={}", id, request.tier)
        ask(
            payload = payload,
            identity = CustomerPayloads.UPDATE,
            operation = "update_customer",
            responseType = MutationResponse::class.java,
        )
    }

    @DeleteMapping("/{id}")
    fun deleteCustomer(@PathVariable id: String): MutationResponse = runBlocking {
        logger.info("customer-web delete id={}", id)
        ask(
            payload = DeleteCustomerRequest(id = id),
            identity = CustomerPayloads.DELETE,
            operation = "delete_customer",
            responseType = MutationResponse::class.java,
        )
    }

    @PostMapping("/route-miss")
    fun routeMiss(): DeadletterView = runBlocking {
        try {
            managedConnector.orchestrator.kotlin.ask(
                source = properties.frontendTarget,
                target = "samples.customer.missing",
                payloadUtf8 = """{"message":"missing customer route"}""",
                payloadIdentity = CustomerPayloads.LIST,
                timeoutMs = 2_000,
                operation = "route_miss",
                deliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
            )
            error("expected route-miss deadletter")
        } catch (error: DeadletterException) {
            DeadletterView(
                reason = error.deadletter.reason,
                target = error.deadletter.originalEnvelope.target,
                generation = error.deadletter.activeGeneration,
            )
        }
    }

    @GetMapping("/runtime")
    fun runtimeDiagnostics(): RuntimeDiagnosticsView {
        val runtimeInfo = managedConnector.orchestrator.runtimeInfo()
        val runtimeConfig = managedConnector.orchestrator.runtimeConfig()
        val clientStats = managedConnector.orchestrator.clientStats()
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
                "serviceRole" to "customer-single-process",
                "demoMode" to "single-process",
                "businessTransport" to "runtime-local-message",
                "remoteTransport" to "not-exercised",
                "frontendTarget" to properties.frontendTarget,
                "storeTarget" to properties.storeTarget,
                "configuredGeneration" to properties.generation,
                "frontendEndpoint" to "source-only",
                "storeEndpoint" to "${properties.storeHost}:${properties.storePort}",
                "createType" to CustomerPayloads.CREATE.messageType,
                "updateType" to CustomerPayloads.UPDATE.messageType,
                "deleteType" to CustomerPayloads.DELETE.messageType,
                "listType" to CustomerPayloads.LIST.messageType,
            ),
        )
    }

    private suspend fun <T : Any> ask(
        payload: Any,
        identity: ConnectorPayloadIdentity,
        operation: String,
        responseType: Class<T>,
    ): T {
        val response = managedConnector.orchestrator.kotlin.ask(
            source = properties.frontendTarget,
            target = properties.storeTarget,
            payloadUtf8 = objectMapper.writeValueAsString(payload),
            payloadIdentity = identity,
            timeoutMs = 5_000,
            operation = operation,
            deliveryHint = ConnectorDeliveryHint.ROUTER_DEFAULT,
        )
        return objectMapper.readValue(response.payload, responseType)
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
                hint = "This single-process scenario expects local runtime delivery. A delivery failure here means the route table or local handler registration is wrong.",
            ),
        )
    }
}
