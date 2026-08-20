package coakka.spring;

import coakka.v2.connector.RuntimeClientStats;
import coakka.v2.connector.RuntimeConfigSnapshot;
import coakka.v2.connector.RuntimeInfoSnapshot;
import coakka.v2.connector.protocol.ConnectorDeliveryHint;
import coakka.v2.connector.protocol.ConnectorEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Blocking JSON request/reply facade backed by the context-owned runtime. */
public final class CoAkkaRuntimeClient {
    private final CoAkkaRuntime runtime;
    private final ObjectMapper objectMapper;

    /**
     * Creates a facade over the supplied runtime and application JSON mapper.
     *
     * @param runtime context-owned runtime
     * @param objectMapper mapper used to encode requests and decode replies
     */
    public CoAkkaRuntimeClient(CoAkkaRuntime runtime, ObjectMapper objectMapper) {
        this.runtime = runtime;
        this.objectMapper = objectMapper;
    }

    /**
     * Sends one JSON request and blocks for its typed JSON reply.
     *
     * <p>The runtime timeout is extended by one second only for the Java future
     * wait so the runtime can surface its own terminal timeout first.</p>
     *
     * @param target destination capability name
     * @param payload request object encoded by the application mapper
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
        Class<T> responseType,
        String operation,
        long timeoutMs
    ) throws Exception {
        ConnectorEnvelope response;
        try {
            response = runtime.getOrchestrator().getJava().ask(
                runtime.getProperties().getSourceTarget(),
                target,
                objectMapper.writeValueAsString(payload),
                CoAkkaRuntime.payloadIdentity(payload),
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

    /** @return effective state for the context-owned runtime */
    public RuntimeConfigSnapshot runtimeConfig() {
        return runtime.runtimeConfig();
    }

    /** @return request/reply client counters */
    public RuntimeClientStats clientStats() {
        return runtime.clientStats();
    }
}
