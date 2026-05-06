package coakka.samples.runtime.scenarios.customer.web

import coakka.samples.runtime.scenarios.customer.contract.CustomerDeliveryModes
import coakka.samples.runtime.scenarios.customer.contract.CustomerDraft
import coakka.samples.runtime.scenarios.customer.contract.CustomerMessageTypes
import coakka.samples.runtime.scenarios.customer.contract.CustomerPayloadContract
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
import coakka.v2.connector.RuntimeEndpointFlags
import coakka.v2.connector.RuntimeEndpointSpec
import coakka.v2.connector.RuntimeRouteSpec
import coakka.v2.connector.RuntimeStartSpec
import coakka.v2.connector.protocol.ConnectorDeliveryHint
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
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

@SpringBootApplication
@EnableConfigurationProperties(CustomerWebConnectorProperties::class)
class CustomerWebApplication

fun main(args: Array<String>) {
    runApplication<CustomerWebApplication>(*args)
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
 * Runtime and fallback wiring for the customer web process.
 *
 * `localTarget` is the runtime address served by this process. `peerTarget`
 * is the store address that business requests are sent to. The public runtime
 * artifact currently reports `southboundBackend=stub`, so `storeHttpBaseUrl`
 * keeps the sample usable by falling back to the store HTTP API after a runtime
 * delivery deadletter. When a remote-capable runtime backend is published,
 * integrations can disable the fallback and keep the same route targets.
 */
@ConfigurationProperties("sample.connector")
data class CustomerWebConnectorProperties(
    var systemName: String = "customer-web",
    var nodeId: String = "customer-web-node",
    var localTarget: String = "samples.customer.frontend",
    var localHost: String = "127.0.0.1",
    var localPort: Int = 19101,
    var peerTarget: String = "samples.customer.store",
    var peerHost: String = "127.0.0.1",
    var peerPort: Int = 19102,
    var generation: Long = 1,
    var storeHttpBaseUrl: String = "http://127.0.0.1:8082",
    var storeHttpFallbackEnabled: Boolean = true,
)

class ManagedConnector(val orchestrator: ConnectorOrchestrator) : AutoCloseable {
    override fun close() {
        runBlocking { orchestrator.kotlin.shutdown() }
    }
}

class CustomerStoreFallbackClient(
    private val objectMapper: ObjectMapper,
    private val properties: CustomerWebConnectorProperties,
) {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    fun listCustomers(): ListResponse =
        send(
            request = requestBuilder("/api/customers").GET().build(),
            responseType = ListResponse::class.java,
        )

    fun createCustomer(customer: CustomerDraft): MutationResponse =
        sendJson(
            method = "POST",
            path = "/api/customers",
            payload = customer,
            responseType = MutationResponse::class.java,
        )

    fun updateCustomer(customer: CustomerDraft): MutationResponse =
        sendJson(
            method = "PUT",
            path = "/api/customers/${pathSegment(customer.id)}",
            payload = customer,
            responseType = MutationResponse::class.java,
        )

    fun deleteCustomer(id: String): MutationResponse =
        send(
            request = requestBuilder("/api/customers/${pathSegment(id)}")
                .DELETE()
                .build(),
            responseType = MutationResponse::class.java,
        )

    private fun <T : Any> sendJson(
        method: String,
        path: String,
        payload: Any,
        responseType: Class<T>,
    ): T = send(
        request = requestBuilder(path)
            .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .header("Content-Type", "application/json")
            .build(),
        responseType = responseType,
    )

    private fun <T : Any> send(request: HttpRequest, responseType: Class<T>): T {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            error("customer-store HTTP fallback returned ${response.statusCode()}: ${response.body()}")
        }
        return objectMapper.readValue(response.body(), responseType)
    }

    private fun requestBuilder(path: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create("${properties.storeHttpBaseUrl.trimEnd('/')}$path"))
            .timeout(Duration.ofSeconds(5))

    private fun pathSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
}

@Configuration
class CustomerWebConnectorConfiguration {
    @Bean
    fun runtimeClient(properties: CustomerWebConnectorProperties): ManagedConnector {
        /*
         * Two routes are installed:
         *
         * - localTarget -> LOCAL endpoint in this process, used for diagnostics
         *   and future inbound requests to the web process.
         * - peerTarget -> remote endpoint for the customer store process.
         *
         * The default route generation is 1 because this sample publishes one
         * static route snapshot at startup. A real service should increment the
         * configured generation whenever it applies a new route table.
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
        return ManagedConnector(orchestrator)
    }

    @Bean
    fun customerStoreFallbackClient(
        objectMapper: ObjectMapper,
        properties: CustomerWebConnectorProperties,
    ): CustomerStoreFallbackClient = CustomerStoreFallbackClient(objectMapper, properties)

    @Bean
    fun connectorStartupLog(
        managedConnector: ManagedConnector,
        properties: CustomerWebConnectorProperties,
    ) = ApplicationRunner {
        val logger = LoggerFactory.getLogger("CustomerWebConnectorStartup")
        val info = managedConnector.orchestrator.runtimeInfo()
        val config = managedConnector.orchestrator.runtimeConfig()
        logger.info(
            "customer-web connector ready runtimeVersion={} backend={} node={} localTarget={} peerTarget={} routeCount={}",
            info.runtimeVersion,
            info.southboundBackend,
            config.nodeId,
            properties.localTarget,
            properties.peerTarget,
            config.routeCount,
        )
    }
}

@RestController
@RequestMapping("/api/customers")
class CustomerWebController(
    private val managedConnector: ManagedConnector,
    private val objectMapper: ObjectMapper,
    private val properties: CustomerWebConnectorProperties,
    private val fallbackClient: CustomerStoreFallbackClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun listCustomers(): ListResponse = runBlocking {
        ask(
            payload = ListCustomersRequest(requestedBy = properties.systemName),
            identity = CustomerPayloads.LIST,
            operation = "list_customers",
            responseType = ListResponse::class.java,
            fallback = {
                fallbackClient.listCustomers().copy(
                    deliveryMode = CustomerDeliveryModes.RUNTIME_DEADLETTER_HTTP_FALLBACK,
                )
            },
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
            fallback = {
                fallbackClient.createCustomer(request).copy(
                    deliveryMode = CustomerDeliveryModes.RUNTIME_DEADLETTER_HTTP_FALLBACK,
                )
            },
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
            fallback = {
                fallbackClient.updateCustomer(payload).copy(
                    deliveryMode = CustomerDeliveryModes.RUNTIME_DEADLETTER_HTTP_FALLBACK,
                )
            },
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
            fallback = {
                fallbackClient.deleteCustomer(id).copy(
                    deliveryMode = CustomerDeliveryModes.RUNTIME_DEADLETTER_HTTP_FALLBACK,
                )
            },
        )
    }

    @PostMapping("/route-miss")
    fun routeMiss(): DeadletterView = runBlocking {
        try {
            managedConnector.orchestrator.kotlin.ask(
                source = properties.localTarget,
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
    fun runtimeDiagnostics(): RuntimeDiagnosticsView = runtimeDiagnosticsView(
        managedConnector = managedConnector,
        localTarget = properties.localTarget,
        peerTarget = properties.peerTarget,
        configuredGeneration = properties.generation,
        localEndpoint = "${properties.localHost}:${properties.localPort}",
        peerEndpoint = "${properties.peerHost}:${properties.peerPort}",
        serviceRole = "customer-web",
        storeHttpBaseUrl = properties.storeHttpBaseUrl,
        storeHttpFallback = if (properties.storeHttpFallbackEnabled) {
            "enabled for backend=stub"
        } else {
            "disabled"
        },
    )

    private suspend fun <T : Any> ask(
        payload: Any,
        identity: ConnectorPayloadIdentity,
        operation: String,
        responseType: Class<T>,
        fallback: () -> T,
    ): T {
        return try {
            val response = managedConnector.orchestrator.kotlin.ask(
                source = properties.localTarget,
                target = properties.peerTarget,
                payloadUtf8 = objectMapper.writeValueAsString(payload),
                payloadIdentity = identity,
                timeoutMs = 5_000,
                operation = operation,
                deliveryHint = ConnectorDeliveryHint.REQUIRE_REMOTE,
            )
            objectMapper.readValue(response.payload, responseType)
        } catch (error: DeadletterException) {
            if (!properties.storeHttpFallbackEnabled || !runtimeBackendNeedsFallback()) {
                throw error
            }
            logger.warn(
                "customer-web runtime delivery failed operation={} reason={} backend={}; using HTTP fallback {}",
                operation,
                error.deadletter.reason,
                managedConnector.orchestrator.runtimeInfo().southboundBackend,
                properties.storeHttpBaseUrl,
            )
            fallback()
        }
    }

    private fun runtimeBackendNeedsFallback(): Boolean =
        managedConnector.orchestrator.runtimeInfo().southboundBackend.equals("stub", ignoreCase = true)
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
                hint = "The current public runtime v2 artifact reports backend=stub. Cross-process customer scenarios need a runtime release with a remote southbound backend.",
            ),
        )
    }
}

fun runtimeDiagnosticsView(
    managedConnector: ManagedConnector,
    localTarget: String,
    peerTarget: String,
    configuredGeneration: Long,
    localEndpoint: String,
    peerEndpoint: String,
    serviceRole: String,
    storeHttpBaseUrl: String = "",
    storeHttpFallback: String = "",
): RuntimeDiagnosticsView {
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
            "serviceRole" to serviceRole,
            "localTarget" to localTarget,
            "peerTarget" to peerTarget,
            "configuredGeneration" to configuredGeneration,
            "localEndpoint" to localEndpoint,
            "peerEndpoint" to peerEndpoint,
            "storeHttpBaseUrl" to storeHttpBaseUrl,
            "storeHttpFallback" to storeHttpFallback,
            "createType" to CustomerPayloads.CREATE.messageType,
            "updateType" to CustomerPayloads.UPDATE.messageType,
            "deleteType" to CustomerPayloads.DELETE.messageType,
            "listType" to CustomerPayloads.LIST.messageType,
        ),
    )
}
