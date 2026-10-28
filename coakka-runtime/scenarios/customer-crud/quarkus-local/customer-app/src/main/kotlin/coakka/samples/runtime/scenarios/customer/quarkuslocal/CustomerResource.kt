package coakka.samples.runtime.scenarios.customer.quarkuslocal

import coakka.quarkus.CoAkkaRuntime
import coakka.quarkus.CoAkkaRuntimeClient
import coakka.samples.runtime.scenarios.customer.contract.CustomerDraft
import coakka.samples.runtime.scenarios.customer.contract.DeadletterView
import coakka.samples.runtime.scenarios.customer.contract.DeleteCustomerRequest
import coakka.samples.runtime.scenarios.customer.contract.ListCustomersRequest
import coakka.samples.runtime.scenarios.customer.contract.ListResponse
import coakka.samples.runtime.scenarios.customer.contract.MutationResponse
import coakka.samples.runtime.scenarios.customer.contract.RuntimeDiagnosticsView
import coakka.samples.runtime.scenarios.customer.contract.UpdateCustomerRequest
import coakka.v2.connector.DeadletterException
import coakka.v2.connector.protocol.ConnectorPayloadIdentity
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.logging.Logger

@Path("/api/customers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
class CustomerResource(
    private val runtime: CoAkkaRuntime,
    private val coakka: CoAkkaRuntimeClient,
) {
    private val logger = Logger.getLogger(CustomerResource::class.java)

    @GET
    fun listCustomers(): ListResponse = ask(
        payload = ListCustomersRequest(requestedBy = "customer-quarkus-local"),
        identity = CustomerPayloads.LIST,
        operation = "list_customers",
        responseType = ListResponse::class.java,
    )

    @POST
    fun createCustomer(request: CustomerDraft): Response {
        logger.infof("customer-web create id=%s name=%s tier=%s", request.id, request.name, request.tier)
        val reply = ask(
            payload = request,
            identity = CustomerPayloads.CREATE,
            operation = "create_customer",
            responseType = MutationResponse::class.java,
        )
        return Response.status(Response.Status.CREATED).entity(reply).build()
    }

    @PUT
    @Path("/{id}")
    fun updateCustomer(
        @PathParam("id") id: String,
        request: UpdateCustomerRequest,
    ): MutationResponse {
        val payload = CustomerDraft(
            id = id,
            name = request.name,
            email = request.email,
            tier = request.tier,
            notes = request.notes,
        )
        logger.infof("customer-web update id=%s tier=%s", id, request.tier)
        return ask(
            payload = payload,
            identity = CustomerPayloads.UPDATE,
            operation = "update_customer",
            responseType = MutationResponse::class.java,
        )
    }

    @DELETE
    @Path("/{id}")
    fun deleteCustomer(@PathParam("id") id: String): MutationResponse {
        logger.infof("customer-web delete id=%s", id)
        return ask(
            payload = DeleteCustomerRequest(id = id),
            identity = CustomerPayloads.DELETE,
            operation = "delete_customer",
            responseType = MutationResponse::class.java,
        )
    }

    @POST
    @Path("/route-miss")
    fun routeMiss(): DeadletterView {
        try {
            coakka.askBlocking(
                CustomerTargets.MISSING,
                ListCustomersRequest(requestedBy = "route-miss"),
                CustomerPayloads.LIST,
                ListResponse::class.java,
                "route_miss",
                2_000,
            )
            error("expected route-miss deadletter")
        } catch (error: DeadletterException) {
            return DeadletterView(
                reason = error.deadletter.reason,
                target = error.deadletter.originalEnvelope.target,
                generation = error.deadletter.activeGeneration,
            )
        }
    }

    @GET
    @Path("/runtime")
    fun runtimeDiagnostics(): RuntimeDiagnosticsView {
        val runtimeInfo = runtime.runtimeInfo()
        val runtimeConfig = runtime.runtimeConfig()
        val clientStats = runtime.clientStats()
        return RuntimeDiagnosticsView(
            runtimeInfo = mapOf(
                "abiVersion" to runtimeInfo.abiVersion,
                "runtimeVersion" to runtimeInfo.runtimeVersion,
                "gitCommit" to runtimeInfo.gitCommit,
                "featureFlagsText" to runtimeInfo.featureFlagsText,
            ),
            runtimeConfig = mapOf(
                "systemName" to runtimeConfig.systemName,
                "nodeId" to runtimeConfig.nodeId,
                "runtimeStateName" to runtimeConfig.runtimeStateName,
                "appliedGeneration" to runtimeConfig.appliedGeneration,
                "routeCount" to runtimeConfig.routeCount,
            ),
            clientStats = mapOf(
                "pendingRequests" to clientStats.pendingRequests,
                "deliveredRequests" to clientStats.deliveredRequests,
                "matchedResponses" to clientStats.matchedResponses,
                "matchedDeadletters" to clientStats.matchedDeadletters,
                "lateResponses" to clientStats.lateResponses,
                "unhandledDeadletters" to clientStats.unhandledDeadletters,
            ),
            connector = mapOf(
                "serviceRole" to "customer-quarkus-local",
                "demoMode" to "quarkus-local",
                "businessTransport" to "runtime-local-message",
                "remoteTransport" to "not-exercised",
                "frontendTarget" to CustomerTargets.FRONTEND,
                "storeTarget" to CustomerTargets.STORE,
                "configuredGeneration" to 1,
                "storeEndpoint" to "127.0.0.1:19182",
            ),
        )
    }

    private fun <T : Any> ask(
        payload: Any,
        identity: ConnectorPayloadIdentity,
        operation: String,
        responseType: Class<T>,
    ): T = coakka.askBlocking(
        CustomerTargets.STORE,
        payload,
        identity,
        responseType,
        operation,
        5_000,
    )
}
