package coakka.samples.runtime.scenarios.customer.quarkuslocal

import coakka.quarkus.CoAkkaHandler
import coakka.quarkus.CoAkkaLocalHandler
import coakka.quarkus.CoAkkaReplies
import coakka.samples.runtime.scenarios.customer.contract.CustomerDraft
import coakka.samples.runtime.scenarios.customer.contract.DeleteCustomerRequest
import coakka.samples.runtime.scenarios.customer.contract.ListCustomersRequest
import coakka.v2.connector.protocol.ConnectorEnvelope
import coakka.v2.connector.protocol.ConnectorPayloadIdentity
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger

@ApplicationScoped
@CoAkkaHandler(CustomerTargets.STORE)
class CustomerCapabilityHandler(
    private val objectMapper: ObjectMapper,
    private val customerStore: InMemoryCustomerStore,
) : CoAkkaLocalHandler {
    private val logger = Logger.getLogger(CustomerCapabilityHandler::class.java)

    override fun handle(request: ConnectorEnvelope, objectMapper: ObjectMapper): ConnectorEnvelope {
        val reply = handleCustomerRequest(request)
        return CoAkkaReplies.json(
            request,
            CustomerTargets.STORE,
            reply.payload,
            reply.identity,
            objectMapper,
        )
    }

    private fun handleCustomerRequest(request: ConnectorEnvelope): ReplyPayload = when (request.messageType) {
        CustomerPayloads.CREATE.messageType -> {
            val customer = objectMapper.readValue(request.payload, CustomerDraft::class.java)
            logger.infof("local store create id=%s name=%s correlation=%s", customer.id, customer.name, request.correlationId)
            ReplyPayload(customerStore.create(customer), CustomerPayloads.MUTATION_RESPONSE)
        }
        CustomerPayloads.UPDATE.messageType -> {
            val customer = objectMapper.readValue(request.payload, CustomerDraft::class.java)
            logger.infof("local store update id=%s tier=%s correlation=%s", customer.id, customer.tier, request.correlationId)
            ReplyPayload(customerStore.update(customer), CustomerPayloads.MUTATION_RESPONSE)
        }
        CustomerPayloads.DELETE.messageType -> {
            val delete = objectMapper.readValue(request.payload, DeleteCustomerRequest::class.java)
            logger.infof("local store delete id=%s correlation=%s", delete.id, request.correlationId)
            ReplyPayload(customerStore.delete(delete.id), CustomerPayloads.MUTATION_RESPONSE)
        }
        CustomerPayloads.LIST.messageType -> {
            val list = objectMapper.readValue(request.payload, ListCustomersRequest::class.java)
            logger.infof("local store list requestedBy=%s correlation=%s", list.requestedBy, request.correlationId)
            ReplyPayload(customerStore.list(), CustomerPayloads.LIST_RESPONSE)
        }
        else -> error("unsupported customer message type: ${request.messageType}")
    }
}

data class ReplyPayload(val payload: Any, val identity: ConnectorPayloadIdentity)
