package coakka.samples.runtime.scenarios.customer.starterlocal

import coakka.samples.runtime.scenarios.customer.contract.CustomerDraft
import coakka.samples.runtime.scenarios.customer.contract.CustomerView
import coakka.samples.runtime.scenarios.customer.contract.ListResponse
import coakka.samples.runtime.scenarios.customer.contract.MutationResponse
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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
