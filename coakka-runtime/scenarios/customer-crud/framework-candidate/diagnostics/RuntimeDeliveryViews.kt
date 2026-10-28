package coakka.samples.runtime.scenarios.customer.diagnostics

/** Application JSON views preserve Core's numeric cause and absent request. */
data class DeadletterView(val reason: Int, val target: String?, val generation: Long)

data class RuntimeDeliveryFailureView(
    val status: String,
    val reason: Int,
    val detail: String,
    val target: String?,
    val resolvedEndpoint: String,
    val hint: String,
)
