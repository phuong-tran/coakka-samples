package coakka.samples.runtime.scenarios.customer.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CustomerRuntimeContractTest {
    @Test
    fun `message types are stable and operation specific`() {
        val requestTypes = setOf(
            CustomerMessageTypes.CREATE_REQUEST,
            CustomerMessageTypes.UPDATE_REQUEST,
            CustomerMessageTypes.DELETE_REQUEST,
            CustomerMessageTypes.LIST_REQUEST,
        )

        assertEquals(4, requestTypes.size)
        assertEquals("samples.customer.create.request.v1", CustomerMessageTypes.CREATE_REQUEST)
        assertEquals("samples.customer.mutation.response.v1", CustomerMessageTypes.MUTATION_RESPONSE)
        assertNotEquals(CustomerMessageTypes.LIST_RESPONSE, CustomerMessageTypes.MUTATION_RESPONSE)
        assertEquals(1, CustomerPayloadContract.SCHEMA_VERSION)
        assertEquals("JSON", CustomerPayloadContract.FORMAT)
    }

    @Test
    fun `business replies default to runtime delivery`() {
        val mutation = MutationResponse(
            status = "ACCEPTED",
            operation = "create",
            customerId = "cust-001",
            revision = 1,
            handledBy = "customer-store",
        )
        val list = ListResponse(customers = emptyList())

        assertEquals(CustomerDeliveryModes.RUNTIME, mutation.deliveryMode)
        assertEquals(CustomerDeliveryModes.RUNTIME, list.deliveryMode)
    }
}
