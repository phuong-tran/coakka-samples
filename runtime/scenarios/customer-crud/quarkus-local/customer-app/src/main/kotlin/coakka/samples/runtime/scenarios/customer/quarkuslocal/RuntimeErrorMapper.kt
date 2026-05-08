package coakka.samples.runtime.scenarios.customer.quarkuslocal

import coakka.samples.runtime.scenarios.customer.contract.RuntimeDeliveryFailureView
import coakka.v2.connector.DeadletterException
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

@Provider
class RuntimeErrorMapper : ExceptionMapper<DeadletterException> {
    override fun toResponse(error: DeadletterException): Response {
        val deadletter = error.deadletter
        return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(
            RuntimeDeliveryFailureView(
                status = "RUNTIME_DELIVERY_FAILED",
                reason = deadletter.reason,
                detail = deadletter.detail,
                target = deadletter.originalEnvelope.target,
                resolvedEndpoint = "${deadletter.resolvedHost}:${deadletter.resolvedPort}",
                hint = "This Quarkus-local scenario expects local runtime delivery. A delivery failure here means the route table or local handler registration is wrong.",
            ),
        ).build()
    }
}
