package coakka.samples.runtime.scenarios.customer.contract

object CustomerMessageTypes {
    const val CREATE_REQUEST = "samples.customer.create.request.v1"
    const val UPDATE_REQUEST = "samples.customer.update.request.v1"
    const val DELETE_REQUEST = "samples.customer.delete.request.v1"
    const val LIST_REQUEST = "samples.customer.list.request.v1"
    const val MUTATION_RESPONSE = "samples.customer.mutation.response.v1"
    const val LIST_RESPONSE = "samples.customer.list.response.v1"
}

object CustomerPayloadContract {
    const val SCHEMA_VERSION = 1
    const val FORMAT = "JSON"
}

object CustomerDeliveryModes {
    const val RUNTIME = "runtime"
    const val STORE_HTTP_DIRECT = "store-http-direct"
}

data class CustomerDraft(
    val id: String,
    val name: String,
    val email: String,
    val tier: String,
    val notes: String = "",
)

data class UpdateCustomerRequest(
    val name: String,
    val email: String,
    val tier: String,
    val notes: String = "",
)

data class DeleteCustomerRequest(val id: String)
data class ListCustomersRequest(val requestedBy: String)

data class CustomerView(
    val id: String,
    val name: String,
    val email: String,
    val tier: String,
    val notes: String,
    val revision: Long,
)

data class MutationResponse(
    val status: String,
    val operation: String,
    val customerId: String,
    val revision: Long,
    val handledBy: String,
    val deliveryMode: String = CustomerDeliveryModes.RUNTIME,
)

data class ListResponse(
    val customers: List<CustomerView>,
    val deliveryMode: String = CustomerDeliveryModes.RUNTIME,
)

data class DeadletterView(val reason: String, val target: String, val generation: Long)

data class RuntimeDeliveryFailureView(
    val status: String,
    val reason: String,
    val detail: String,
    val target: String,
    val resolvedEndpoint: String,
    val hint: String,
)

data class RuntimeDiagnosticsView(
    val runtimeInfo: Map<String, Any>,
    val runtimeConfig: Map<String, Any>,
    val clientStats: Map<String, Any>,
    val connector: Map<String, Any>,
)
