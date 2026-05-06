package coakka.samples.runtime.scenarios.customer.store

import coakka.samples.runtime.scenarios.customer.contract.CustomerDraft
import coakka.samples.runtime.scenarios.customer.contract.CustomerMessageTypes
import coakka.samples.runtime.scenarios.customer.contract.CustomerPayloadContract
import coakka.samples.runtime.scenarios.customer.contract.CustomerView
import coakka.samples.runtime.scenarios.customer.contract.DeleteCustomerRequest
import coakka.samples.runtime.scenarios.customer.contract.ListCustomersRequest
import coakka.samples.runtime.scenarios.customer.contract.ListResponse
import coakka.samples.runtime.scenarios.customer.contract.MutationResponse
import coakka.v2.connector.ConnectorOrchestrator
import coakka.v2.connector.RuntimeClient
import coakka.v2.connector.RuntimeEndpointFlags
import coakka.v2.connector.RuntimeEndpointSpec
import coakka.v2.connector.RuntimeRouteSpec
import coakka.v2.connector.RuntimeStartSpec
import coakka.v2.connector.protocol.ConnectorEnvelope
import coakka.v2.connector.protocol.ConnectorPayloadFormat
import coakka.v2.connector.protocol.ConnectorPayloadIdentity
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong

@SpringBootApplication
@EnableConfigurationProperties(CustomerStoreConnectorProperties::class)
class CustomerStoreApplication

fun main(args: Array<String>) {
    runApplication<CustomerStoreApplication>(*args)
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

/**
 * Runtime wiring for the customer store process.
 *
 * `localTarget` is the address this store serves. `peerTarget` is the web
 * process address used by the route table. Ports `19101/19102` are runtime
 * endpoints, not HTTP ports. This store is headless; customer traffic enters
 * only through the runtime handler registered below.
 */
@ConfigurationProperties("sample.connector")
data class CustomerStoreConnectorProperties(
    var systemName: String = "customer-store",
    var nodeId: String = "customer-store-node",
    var localTarget: String = "samples.customer.store",
    var localHost: String = "127.0.0.1",
    var localPort: Int = 19102,
    var peerTarget: String = "samples.customer.frontend",
    var peerHost: String = "127.0.0.1",
    var peerPort: Int = 19101,
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
        handledBy = "customer-store",
    )
}

class ManagedConnector(val orchestrator: ConnectorOrchestrator) : AutoCloseable {
    override fun close() {
        runBlocking { orchestrator.kotlin.shutdown() }
    }
}

@Configuration
class CustomerStoreConnectorConfiguration {
    @Bean
    fun objectMapper(): ObjectMapper = jacksonObjectMapper()

    @Bean
    fun customerStore(): InMemoryCustomerStore = InMemoryCustomerStore()

    @Bean
    fun runtimeClient(
        properties: CustomerStoreConnectorProperties,
        objectMapper: ObjectMapper,
        customerStore: InMemoryCustomerStore,
    ): ManagedConnector {
        /*
         * The store owns the customer handler, so its local route is marked LOCAL.
         * The peer route points back to the web process for topologies that later
         * need replies/events to flow in the opposite direction.
         *
         * queueCapacity=128 and strictNoDrop=true are deliberately conservative:
         * this is a sample, but it should still demonstrate bounded queues and
         * explicit failure instead of unbounded memory growth or silent drops.
         */
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
                        target = properties.localTarget,
                        endpoints = listOf(
                            RuntimeEndpointSpec(
                                host = properties.localHost,
                                port = properties.localPort,
                                flags = RuntimeEndpointFlags.LOCAL,
                            ),
                        ),
                    ),
                    RuntimeRouteSpec(
                        target = properties.peerTarget,
                        endpoints = listOf(
                            RuntimeEndpointSpec(
                                host = properties.peerHost,
                                port = properties.peerPort,
                                flags = 0,
                            ),
                        ),
                    ),
                ),
            ),
        )

        val logger = LoggerFactory.getLogger("CustomerRuntimeHandler")
        orchestrator.registerHandler(properties.localTarget) { request ->
            val reply = handleCustomerRequest(request, objectMapper, customerStore, logger)
            RuntimeClient.replyTo(
                request = request,
                source = properties.localTarget,
                payloadUtf8 = objectMapper.writeValueAsString(reply.payload),
                payloadIdentity = reply.identity,
            )
        }

        return ManagedConnector(orchestrator)
    }

    @Bean
    fun connectorStartupLog(
        managedConnector: ManagedConnector,
        properties: CustomerStoreConnectorProperties,
    ) = ApplicationRunner {
        val logger = LoggerFactory.getLogger("CustomerStoreConnectorStartup")
        val info = managedConnector.orchestrator.runtimeInfo()
        val config = managedConnector.orchestrator.runtimeConfig()
        logger.info(
            "customer-store connector ready runtimeVersion={} backend={} node={} localTarget={} peerTarget={} routeCount={}",
            info.runtimeVersion,
            info.southboundBackend,
            config.nodeId,
            properties.localTarget,
            properties.peerTarget,
            config.routeCount,
        )
        CountDownLatch(1).await()
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
        logger.info("customer-store create id={} name={} correlation={}", customer.id, customer.name, request.correlationId)
        ReplyPayload(customerStore.create(customer), CustomerPayloads.MUTATION_RESPONSE)
    }
    CustomerPayloads.UPDATE.messageType -> {
        val customer = objectMapper.readValue(request.payload, CustomerDraft::class.java)
        logger.info("customer-store update id={} tier={} correlation={}", customer.id, customer.tier, request.correlationId)
        ReplyPayload(customerStore.update(customer), CustomerPayloads.MUTATION_RESPONSE)
    }
    CustomerPayloads.DELETE.messageType -> {
        val delete = objectMapper.readValue(request.payload, DeleteCustomerRequest::class.java)
        logger.info("customer-store delete id={} correlation={}", delete.id, request.correlationId)
        ReplyPayload(customerStore.delete(delete.id), CustomerPayloads.MUTATION_RESPONSE)
    }
    CustomerPayloads.LIST.messageType -> {
        val list = objectMapper.readValue(request.payload, ListCustomersRequest::class.java)
        logger.info("customer-store list requestedBy={} correlation={}", list.requestedBy, request.correlationId)
        ReplyPayload(customerStore.list(), CustomerPayloads.LIST_RESPONSE)
    }
    else -> error("unsupported customer message type: ${request.messageType}")
}
