package coakka.samples.runtime.scenarios.customer.starterlocal

import coakka.samples.runtime.scenarios.customer.contract.CustomerDraft
import coakka.samples.runtime.scenarios.customer.contract.DeleteCustomerRequest
import coakka.samples.runtime.scenarios.customer.contract.ListCustomersRequest
import coakka.samples.runtime.scenarios.customer.contract.ListResponse
import coakka.samples.runtime.scenarios.customer.contract.MutationResponse
import coakka.spring.CoAkkaHandler
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

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
