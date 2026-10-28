package coakka.samples.runtime.scenarios.customer.store

import coakka.samples.runtime.scenarios.customer.contract.CustomerDeliveryModes
import coakka.samples.runtime.scenarios.customer.contract.CustomerDraft
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryCustomerStoreTest {
    @Test
    fun `store operations return runtime delivery by default`() {
        val store = InMemoryCustomerStore()
        val customer = CustomerDraft(
            id = "cust-001",
            name = "Ada Lovelace",
            email = "ada@example.com",
            tier = "silver",
            notes = "contract test",
        )

        assertEquals(CustomerDeliveryModes.RUNTIME, store.create(customer).deliveryMode)
        assertEquals(CustomerDeliveryModes.RUNTIME, store.update(customer.copy(tier = "gold")).deliveryMode)
        assertEquals(CustomerDeliveryModes.RUNTIME, store.list().deliveryMode)
        assertEquals(CustomerDeliveryModes.RUNTIME, store.delete(customer.id).deliveryMode)
    }
}
