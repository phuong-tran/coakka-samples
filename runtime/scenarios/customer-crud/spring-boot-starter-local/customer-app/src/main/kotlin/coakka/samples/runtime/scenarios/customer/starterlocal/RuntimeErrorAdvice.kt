package coakka.samples.runtime.scenarios.customer.starterlocal

import coakka.samples.runtime.scenarios.customer.contract.RuntimeDeliveryFailureView
import coakka.v2.connector.DeadletterException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

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
                hint = "This starter-local scenario expects @CoAkkaHandler local capability routes.",
            ),
        )
    }
}
