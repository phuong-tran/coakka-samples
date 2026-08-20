package coakka.quarkus;

import coakka.v2.connector.RuntimeClientStats;
import coakka.v2.connector.RuntimeConfigSnapshot;
import coakka.v2.connector.RuntimeInfoSnapshot;
import coakka.v2.connector.protocol.ConnectorDeliveryHint;
import coakka.v2.connector.protocol.ConnectorEnvelope;
import coakka.v2.connector.protocol.ConnectorPayloadIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Blocking JSON request/reply facade backed by the application-scoped runtime. */
@ApplicationScoped
public class CoAkkaRuntimeClient {
    private final CoAkkaRuntime runtime;
    private final ObjectMapper objectMapper;

    /**
     * Creates a CDI facade over the supplied runtime and application mapper.
     *
     * @param runtime application-scoped runtime
     * @param objectMapper mapper used to encode requests and decode replies
     */
    public CoAkkaRuntimeClient(CoAkkaRuntime runtime, ObjectMapper objectMapper) {
        this.runtime = runtime;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends one JSON request and blocks for its typed JSON reply.
     *
     * @param target destination capability name
     * @param payload request object encoded by the application mapper
     * @param payloadIdentity explicit request schema identity
     * @param responseType reply type decoded by the application mapper
     * @param operation diagnostic operation name
     * @param timeoutMs positive runtime request timeout in milliseconds
     * @param <T> decoded reply type
     * @return decoded reply
     * @throws Exception when encoding, routing, handling, timeout, or decoding fails
     */
    public <T> T askBlocking(
        String target,
        Object payload,
        ConnectorPayloadIdentity payloadIdentity,
        Class<T> responseType,
        String operation,
        long timeoutMs
    ) throws Exception {
        ConnectorEnvelope response;
        try {
            response = runtime.connector().getJava().ask(
                runtime.sourceTarget(),
                target,
                objectMapper.writeValueAsString(payload),
                payloadIdentity,
                timeoutMs,
                operation,
                ConnectorDeliveryHint.ROUTER_DEFAULT
            ).get(timeoutMs + 1_000, TimeUnit.MILLISECONDS);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error fatal) {
                throw fatal;
            }
            throw error;
        }
        return objectMapper.readValue(response.payloadUtf8(), responseType);
    }

    /** @return immutable native runtime build metadata */
    public RuntimeInfoSnapshot runtimeInfo() {
        return runtime.runtimeInfo();
    }

    /** @return effective state for the application-scoped runtime */
    public RuntimeConfigSnapshot runtimeConfig() {
        return runtime.runtimeConfig();
    }

    /** @return request/reply client counters */
    public RuntimeClientStats clientStats() {
        return runtime.clientStats();
    }
}
