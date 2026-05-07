package coakka.samples.runtime.scenarios.customer.starterlocal

import coakka.samples.runtime.scenarios.customer.contract.CustomerDraft
import coakka.samples.runtime.scenarios.customer.contract.DeleteCustomerRequest
import coakka.samples.runtime.scenarios.customer.contract.ListCustomersRequest
import coakka.samples.runtime.scenarios.customer.contract.ListResponse
import coakka.samples.runtime.scenarios.customer.contract.MutationResponse
import coakka.samples.runtime.scenarios.customer.contract.UpdateCustomerRequest
import coakka.spring.CoAkkaRuntimeClient
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/customers")
class CustomerStarterLocalController(private val runtimeClient: CoAkkaRuntimeClient) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun listCustomers(): ListResponse {
        return runtimeClient.askBlocking(
            CustomerTargets.LIST,
            ListCustomersRequest(requestedBy = "customer-starter-local"),
            ListResponse::class.java,
            "list_customers",
            5_000,
        )
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCustomer(@RequestBody request: CustomerDraft): MutationResponse {
        logger.info("customer-web create id={} name={} tier={}", request.id, request.name, request.tier)
        return runtimeClient.askBlocking(
            CustomerTargets.CREATE,
            request,
            MutationResponse::class.java,
            "create_customer",
            5_000,
        )
    }

    @PutMapping("/{id}")
    fun updateCustomer(
        @PathVariable id: String,
        @RequestBody request: UpdateCustomerRequest,
    ): MutationResponse {
        val payload = CustomerDraft(
            id = id,
            name = request.name,
            email = request.email,
            tier = request.tier,
            notes = request.notes,
        )
        logger.info("customer-web update id={} tier={}", id, request.tier)
        return runtimeClient.askBlocking(
            CustomerTargets.UPDATE,
            payload,
            MutationResponse::class.java,
            "update_customer",
            5_000,
        )
    }

    @DeleteMapping("/{id}")
    fun deleteCustomer(@PathVariable id: String): MutationResponse {
        logger.info("customer-web delete id={}", id)
        return runtimeClient.askBlocking(
            CustomerTargets.DELETE,
            DeleteCustomerRequest(id = id),
            MutationResponse::class.java,
            "delete_customer",
            5_000,
        )
    }
}
