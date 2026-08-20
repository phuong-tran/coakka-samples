package coakka.quarkus;

import coakka.v2.connector.RuntimeClient;
import coakka.v2.connector.protocol.ConnectorEnvelope;
import coakka.v2.connector.protocol.ConnectorPayloadIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Reply helpers that preserve request correlation and explicit schema identity. */
public final class CoAkkaReplies {
    private CoAkkaReplies() {
    }

    /**
     * Encodes a JSON reply correlated to the supplied request.
     *
     * @param request borrowed request being answered
     * @param source reply source target
     * @param payload application reply object
     * @param payloadIdentity explicit reply schema identity
     * @param objectMapper application JSON mapper
     * @return correlated reply envelope
     * @throws Exception when JSON encoding fails
     */
    public static ConnectorEnvelope json(
        ConnectorEnvelope request,
        String source,
        Object payload,
        ConnectorPayloadIdentity payloadIdentity,
        ObjectMapper objectMapper
    ) throws Exception {
        return RuntimeClient.Companion.replyTo(
            request,
            source,
            objectMapper.writeValueAsString(payload),
            payloadIdentity
        );
    }
}
