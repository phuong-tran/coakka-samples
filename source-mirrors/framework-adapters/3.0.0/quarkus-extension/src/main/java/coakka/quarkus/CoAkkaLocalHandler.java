package coakka.quarkus;

import coakka.v2.connector.protocol.ConnectorEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Application-owned CDI handler for a local CoAkka target. */
public interface CoAkkaLocalHandler {
    /**
     * Resolves the target from {@link CoAkkaHandler}; implementations may override it.
     *
     * @return unique local runtime target
     */
    default String target() {
        return CoAkkaTargets.fromAnnotation(getClass());
    }

    /**
     * Handles one borrowed request and returns an owned reply envelope.
     *
     * @param request request envelope valid for this synchronous callback
     * @param objectMapper application JSON mapper
     * @return reply envelope correlated to the request
     * @throws Exception when decoding or application handling fails
     */
    ConnectorEnvelope handle(ConnectorEnvelope request, ObjectMapper objectMapper) throws Exception;
}
