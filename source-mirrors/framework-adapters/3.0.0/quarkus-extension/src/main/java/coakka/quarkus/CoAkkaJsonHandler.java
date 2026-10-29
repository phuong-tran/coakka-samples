package coakka.quarkus;

import coakka.v2.connector.protocol.ConnectorEnvelope;
import coakka.v2.connector.protocol.ConnectorPayloadIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Typed JSON convenience contract for a local CDI handler.
 *
 * @param <P> decoded request type
 * @param <R> encoded reply type
 */
public interface CoAkkaJsonHandler<P, R> extends CoAkkaLocalHandler {
    /** @return request type decoded by the application mapper */
    Class<P> requestType();

    /**
     * Returns the schema identity attached to a reply.
     *
     * @param response application reply
     * @return explicit reply payload identity
     */
    ConnectorPayloadIdentity responseIdentity(R response);

    /**
     * Handles one decoded request.
     *
     * @param request decoded request
     * @return application reply
     * @throws Exception when application handling fails
     */
    R handlePayload(P request) throws Exception;

    /** {@inheritDoc} */
    @Override
    default ConnectorEnvelope handle(ConnectorEnvelope request, ObjectMapper objectMapper) throws Exception {
        P payload = objectMapper.readValue(request.payloadUtf8(), requestType());
        R response = handlePayload(payload);
        return CoAkkaReplies.json(request, target(), response, responseIdentity(response), objectMapper);
    }
}
